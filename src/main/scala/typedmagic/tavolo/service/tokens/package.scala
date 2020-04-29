package typedmagic.tavolo.service

import java.time.{ZoneId, Clock => JavaClock}

import javax.crypto.SecretKey
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import typedmagic.tavolo.model.Account
import zio.clock.Clock
import zio.{Has, ZIO, ZLayer}

package object tokens {

  type Tokens = Has[Tokens.Service]

  object Tokens {

    trait Service {
      def makeLoginToken(a: Account, key: SecretKey): ZIO[Any, TokenError, String]
      def validateLoginToken(token: String, key: SecretKey): ZIO[Any, TokenError, JwtClaim]
    }

    val live: ZLayer[Clock, Nothing, Tokens] = ZLayer.fromService { clock =>
      new Service {
        override def makeLoginToken(a: Account, key: SecretKey): ZIO[Any, TokenError, String] =
          (
            clock.currentDateTime map { d =>
               implicit val jc: JavaClock = JavaClock.fixed(d.toInstant, ZoneId.systemDefault())
               Jwt.encode(JwtClaim(s"""{"sub":"${a.id}", "name": "${a.moniker}"}""").issuedNow.expiresIn(24 * 3600), key, JwtAlgorithm.HS256)
            }
          ).mapError(ex => CannotGenerateToken(ex.getMessage))

        override def validateLoginToken(token: String, key: SecretKey): ZIO[Any, TokenError, JwtClaim] =
          (
            clock.currentDateTime map { d =>
              implicit val jc: JavaClock = JavaClock.fixed(d.toInstant, ZoneId.systemDefault())
              Jwt.decode(token, key, Seq(JwtAlgorithm.HS256)).toOption
            }
          ).someOrFail(TokenInvalid("Login token is invalid"))
           .mapError(ex => TokenInvalid(ex.getMessage))
      }
    }

    def makeLoginToken(a: Account, key: SecretKey): ZIO[Tokens, TokenError, String] =
      ZIO.accessM(_.get.makeLoginToken(a, key))
    def validateLoginToken(token: String, key: SecretKey): ZIO[Tokens, TokenError, JwtClaim] =
      ZIO.accessM(_.get.validateLoginToken(token, key))

  }

  sealed trait TokenError
  case class CannotGenerateToken(message: String) extends RuntimeException(message) with TokenError
  case class TokenInvalid(message: String) extends RuntimeException(message) with TokenError

}
