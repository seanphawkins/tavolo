package typedmagic.tavolo.service

import java.time.Instant

import io.getquill.{PostgresJdbcContext, SnakeCase}
import typedmagic.tavolo.model.{Account, AccountStatus, EmailAddress, Password}
import zio.clock.Clock
import zio.{Has, IO, Layer, ZIO, ZLayer}

package object accounts {

  type Accounts = Has[Accounts.Service]

  object Accounts {
    trait Service {
      def create(a: Account): IO[AccountsError, Account]
      def readById(id: Long): IO[AccountsError, Option[Account]]
      def readByEmail(email: EmailAddress): IO[AccountsError, Option[Account]]
      def update(a: Account): IO[AccountsError, Account]
      def delete(id: Long): IO[AccountsError, Unit]
    }

    val live: ZLayer[Clock, Nothing, Accounts] = ZLayer.fromFunction{ clock =>
      new Service {

        import io.getquill.{MappedEncoding, PostgresJdbcContext, SnakeCase}
        import zio.IO
        import LiveDatabaseContext.ctx
        import ctx._
        implicit val encodeInstant: MappedEncoding[Instant, Long] = MappedEncoding[Instant, Long](_.toEpochMilli)
        implicit val decodeInstant: MappedEncoding[Long, Instant] = MappedEncoding[Long, Instant](Instant.ofEpochMilli)
        implicit val encodeAccountStatus: MappedEncoding[AccountStatus, Int] = MappedEncoding[AccountStatus, Int](_.value)
        implicit val decodeAccountStatus: MappedEncoding[Int, AccountStatus] = MappedEncoding[Int, AccountStatus](AccountStatus.apply)
        implicit val encodeEmailAddress: MappedEncoding[EmailAddress, String] = MappedEncoding[EmailAddress, String](_.value)
        implicit val decodeEmailAddress: MappedEncoding[String, EmailAddress] = MappedEncoding[String, EmailAddress](EmailAddress.apply)
        implicit val encodePassword: MappedEncoding[Password, String] = MappedEncoding[Password, String](_.value)
        implicit val decodePassword: MappedEncoding[String, Password] = MappedEncoding[String, Password](Password.apply)

        override def create(a: Account): IO[AccountsError, Account] = (
          clock.get.currentDateTime map { d =>
            val aWithTimestamps = a.copy(created = d.toInstant, updated = d.toInstant)
            val id = ctx.run(quote(query[Account].insert(lift(aWithTimestamps)).returningGenerated(_.id)))
            ctx.run(quote(query[Account].filter(_.id == lift(id)).take(1))).headOption.get
          }).mapError(ex => AccountsError(ex.getMessage))

        override def readById(id: Long): IO[AccountsError, Option[Account]] =
          IO.effect {
            ctx.run(quote(query[Account].filter(_.id == lift(id)).take(1))).headOption
          }.mapError(t => AccountsError(t.getMessage))

        override def readByEmail(email: EmailAddress): IO[AccountsError, Option[Account]] =
          IO.effect {
            ctx.run(quote(query[Account].filter(_.email == lift(email)).take(1))).headOption
          }.mapError(t => AccountsError(t.getMessage))

        override def update(a: Account): IO[AccountsError, Account] =
          (clock.get.currentDateTime.map { d =>
            val aWithTimestamps = a.copy(updated = d.toInstant)
            ctx.run(query[Account].filter(_.id == lift(aWithTimestamps.id)).update(lift(aWithTimestamps)))
            ctx.run(quote(query[Account].filter(_.id == lift(aWithTimestamps.id)).take(1))).headOption.get
          }).mapError(ex => AccountsError(ex.getMessage))

        override def delete(id: Long): IO[AccountsError, Unit] =
          IO.effect {
            val throwaway = ctx.run(quote(query[Account].filter(_.id == lift(id)).delete))
          }.mapError(t => AccountsError(t.getMessage))
      }
    }

    def create(a: Account): ZIO[Accounts, AccountsError, Account] = ZIO.accessM(_.get.create(a))
    def readById(id: Long): ZIO[Accounts, AccountsError, Option[Account]] = ZIO.accessM(_.get.readById(id))
    def readByEmail(email: EmailAddress): ZIO[Accounts, AccountsError, Option[Account]] = ZIO.accessM(_.get.readByEmail(email))
    def update(a: Account): ZIO[Accounts, AccountsError, Account] = ZIO.accessM(_.get.update(a))
    def delete(id: Long): ZIO[Accounts, AccountsError, Unit] = ZIO.accessM(_.get.delete(id))
  }

  case class AccountsError(message: String) extends RuntimeException(message)

}

object LiveDatabaseContext {
  lazy val ctx = new PostgresJdbcContext(SnakeCase, "ctx")
}