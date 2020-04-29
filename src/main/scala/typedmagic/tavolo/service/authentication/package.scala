package typedmagic.tavolo.service

import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.core._
import javax.crypto.spec.SecretKeySpec
import typedmagic.tavolo.model.AccountStatus.{ACTIVE, LOCKED, UNVERIFIED}
import typedmagic.tavolo.model.{Account, EmailAddress, Password}
import typedmagic.tavolo.service.accounts.Accounts
import typedmagic.tavolo.service.tokens.Tokens
import zio.{Has, ZIO, ZLayer}

package object authentication {

  type Authentication = Has[Authentication.Service]

  object Authentication {
    trait Service {
      def register(email: EmailAddress, password: Password, moniker: String): ZIO[Any, AuthenticationError, (String, Account)]
      def login(email: EmailAddress, password: Password): ZIO[Any, AuthenticationError, (String, Account)]
      def validateToken(t: String): ZIO[Any, AuthenticationError, (Long, String)]
    }

    val live: ZLayer[Accounts with Tokens, Nothing, Authentication] = ZLayer.fromServices[Accounts.Service, Tokens.Service, Service] { (accounts, tokens) =>
      new Service {

        private val SECRET_KEY = new SecretKeySpec("my secretkey".getBytes, "AES") // FIXME - read from config

        private implicit val codec: JsonValueCodec[CustomClaims] = JsonCodecMaker.make[CustomClaims](CodecMakerConfig)

        override def register(email: EmailAddress, password: Password, moniker: String): ZIO[Any, AuthenticationError, (String, Account)] =
          ( for {
              a     <- accounts.create(Account(-1, email, password, moniker, ACTIVE))
              token <- tokens.makeLoginToken(a, SECRET_KEY)
            } yield (token, a)
          ).mapError(_ => RegistrationFailed)

        override def login(email: EmailAddress, password: Password): ZIO[Any, AuthenticationError, (String, Account)] =
          ( for {
              a     <- accounts
                         .readByEmail(email)
                         .someOrFail(UserNotFound)
                         .filterOrFail(_.password == password)(PasswordIncorrect)
                         .filterOrFail(_.status != LOCKED)(AccountLocked)
                         .filterOrFail(_.status != UNVERIFIED)(AccountNotValidated)
              token <- tokens.makeLoginToken(a, SECRET_KEY)
            } yield (token, a)
          ).mapError {
              case a: AuthenticationError => a
              case _ => UserNotFound
            }

        override def validateToken(t: String): ZIO[Any, AuthenticationError, (Long, String)] =
          tokens
            .validateLoginToken(t, SECRET_KEY)
            .map(c => c.subject.map(s => (s, c.content)))
            .someOrFail(AuthenticationTokenInvalid)
            .map( v => (v._1.toLong, readFromString(v._2).name))
            .mapError(ex => AuthenticationTokenInvalid)
            .catchAll(_ => ZIO.fail( AuthenticationTokenInvalid))
      }
    }

    def login(email: EmailAddress, password: Password): ZIO[Authentication, AuthenticationError, (String, Account)] =
      ZIO.accessM(_.get.login(email, password))

    def validateToken(t: String): ZIO[Authentication, AuthenticationError, (Long, String)] =
      ZIO.accessM(_.get.validateToken(t))

    def register(email: EmailAddress, password: Password, moniker: String): ZIO[Authentication, AuthenticationError, (String, Account)] =
      ZIO.accessM(_.get.register(email, password, moniker))
  }

  sealed trait AuthenticationError
  case object RegistrationFailed extends AuthenticationError
  case object UserNotFound extends AuthenticationError
  case object PasswordIncorrect extends AuthenticationError
  case object AccountLocked extends AuthenticationError
  case object AccountNotValidated extends AuthenticationError
  case object AuthenticationTokenInvalid extends AuthenticationError
}

case class CustomClaims(name: String)
