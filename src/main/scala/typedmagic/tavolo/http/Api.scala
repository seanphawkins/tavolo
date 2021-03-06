package typedmagic.tavolo.http

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import typedmagic.tavolo.http.WebSocketSupport._
import typedmagic.tavolo.model._
import typedmagic.tavolo.service.authentication.{Authentication, AuthenticationTokenInvalid}
import zio.logging.Logging.Logging
import zio.stm.TMap
import zio.{Ref, Runtime, ZIO, ZLayer}
import eu.timepit.refined._

import scala.concurrent.duration._
import scala.language.implicitConversions

sealed trait ApiError
case object InvalidSessionId extends ApiError
case object LoginInvalid extends ApiError

class Api(sessions: TMap[String, Ref[EstimationSession]], layer: ZLayer[Any, Nothing, Authentication with Logging])(implicit system: ActorSystem) extends ZioSupport {

  override val environment = ()
  override val platform = Runtime.default.platform

  private implicit val lrcodec: JsonValueCodec[LoginRequest] = JsonCodecMaker.make[LoginRequest](CodecMakerConfig)
  private implicit val rrcodec: JsonValueCodec[RegistrationRequest] = JsonCodecMaker.make[RegistrationRequest](CodecMakerConfig)

  implicit val domainErrorMapper: ErrorMapper[ApiError] = new ErrorMapper[ApiError] {
    def toHttpResponse(e: ApiError): HttpResponse =
      HttpResponse(StatusCodes.BadRequest, entity = "Login information Invalid") // FIXME - vary by error type
  }

  private val bearerToken: Directive1[Option[String]] =
    extractCredentials.flatMap {
      case Some(c) => provide(Option(c.token()))
      case _ => provide(None)
    }

  val rejectionHandler = corsRejectionHandler.withFallback(RejectionHandler.default)
  val exceptionHandler = ExceptionHandler {
    case e: Throwable => println(e); complete(StatusCodes.BadRequest -> e.getMessage)
  }
  val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)

  lazy val route: Route =
    path("session" / Remaining) { sessionId =>
      get {
        parameters("canVote".?) { voteOption =>
          parameters("token".?) { t =>
          extractRequest { req =>
            req.header[UpgradeToWebSocket] match {
              case Some(upgrade) =>
                complete {
                  (for {
                    token <- ZIO.succeed(t).someOrFail(AuthenticationTokenInvalid)
                    id <- Authentication.validateToken(token).orElseFail(AuthenticationTokenInvalid).catchAll(_ => ZIO.fail(AuthenticationTokenInvalid))
                    (src, aRef) = createSource()
                    participant = Participant(id._1, aRef, id._2, canVote = voteOption.forall(_.toBoolean), None)
                    sessionRef <- sessions.get(sessionId).commit.someOrFail(InvalidSessionId)
                    _ <- sessionRef.update(_ + participant)
                    s <- sessionRef.get
                    _ <- s.pushAll
                  } yield upgrade.handleMessagesWithSinkSource(createSink(sessionRef, id._1, this), src))
                    .provideLayer(layer)
                    .mapError{ ex => println(ex); HttpResponse(StatusCodes.BadRequest, entity = "Cannot create session")}
                }
              case _ =>
                complete(StatusCodes.BadRequest)
            }
          }
        }
      }}
    } ~
    handleErrors { cors() { handleErrors {
    path("") {
      get {
        complete("Tavolo is online")
      }
    } ~
    path("account") {
      post {
        extractStrictEntity(3.seconds) { entity =>
          complete {
            val registrationRequest = readFromString[RegistrationRequest](entity.data.utf8String)
            val email: Either[String, EmailAddress] = refineV[Email](registrationRequest.email)
            val emailAddress = email match {
              case Right(e) => e
              case Left(ex) => throw new RuntimeException(ex)
            }
            Authentication
              .register(emailAddress, Password(registrationRequest.password + registrationRequest.email), registrationRequest.moniker)
              .provideLayer(layer).bimap(_ => HttpResponse(StatusCodes.BadRequest, entity = "Couldn't create account"), resp => HttpResponse(StatusCodes.OK, entity = s"""{"token":"${resp._1}", "moniker":"${resp._2.moniker}", "id":${resp._2.id}}"""))
          }
        }
      }
    } ~
    path("login") {
      post {
        extractStrictEntity(3.seconds) { entity =>
          complete {
            val loginRequest = readFromString[LoginRequest](entity.data.utf8String)
            val email: Either[String, EmailAddress] = refineV[Email](loginRequest.email)
            val emailAddress = email match {
              case Right(e) => e
              case Left(ex) => throw new RuntimeException(ex)
            }
            Authentication
              .login(emailAddress, Password(loginRequest.password + emailAddress))
              .provideLayer(layer).bimap(_ => HttpResponse(StatusCodes.BadRequest, entity = "Login request was invalid"), resp => HttpResponse(StatusCodes.OK, entity = s"""{"token":"${resp._1}", "moniker":"${resp._2.moniker}", "id":${resp._2.id}}"""))
          }
        }
      }
    } ~
      path("session") {
        post {
          bearerToken { t =>
            complete {
              (for {
                token <- ZIO.succeed(t).someOrFail(AuthenticationTokenInvalid)
                id <- Authentication.validateToken(token).orElseFail(AuthenticationTokenInvalid).catchAll(_ => ZIO.fail(AuthenticationTokenInvalid))
                sessionId <- ZIO.effect {
                  UUID.randomUUID().toString
                }
                newSession <- Ref.make(EstimationSession(id._1, "", EstimationDeck.FibPoints, Seq.empty))
                _ <- sessions.put(sessionId, newSession).commit
              } yield sessionId)
                .provideLayer(layer).bimap(_ => HttpResponse(StatusCodes.BadRequest, entity = "Cannot create session"), sessionId => HttpResponse(StatusCodes.OK, entity = s"""{"sessionId":"$sessionId"}"""))
            }
          }
        }
      }

  }}}

}

case class LoginRequest(email: String, password: String)

case class RegistrationRequest(email: String, password: String, moniker: String)
