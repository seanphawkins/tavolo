package typedmagic.tavolo

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import typedmagic.tavolo.http.Api
import typedmagic.tavolo.model.EstimationSession
import typedmagic.tavolo.service.accounts.Accounts
import typedmagic.tavolo.service.authentication.Authentication
import typedmagic.tavolo.service.tokens.Tokens
import zio._
import zio.clock.Clock
import zio.duration._
import zio.stm.TMap
import zio.logging._
import zio.logging.slf4j._

object Main extends App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = program.fold(_ => 1, _ => 0)

  val layer = (Clock.live >>> Tokens.live) ++ (Clock.live >>> Accounts.live) >>> Authentication.live ++
    Slf4jLogger.make{(_, message) =>
      message
    } ++ Clock.live

  val program =
    (for {
      sessions <- TMap.empty[String, Ref[EstimationSession]].commit
      _        <- log.info("Tavolo server is starting")
      _        <- Managed.make(Task(ActorSystem("tavolo"))) (sys => Task.fromFuture(_ => sys.terminate()).ignore).use { implicit actorSystem =>
                    ZIO.effect {
                      val api = new Api(sessions, layer)
                      Http().bindAndHandle(api.route, "0.0.0.0", 8372)
                    } *> ZIO.sleep(10.seconds).forever
                  }
    } yield ()).provideLayer(layer)
}
