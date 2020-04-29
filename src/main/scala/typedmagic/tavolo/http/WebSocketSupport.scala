package typedmagic.tavolo.http

import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.actor.typed.scaladsl.adapter._
import typedmagic.tavolo.model.{EstimationDeck, EstimationSession}
import zio._

object WebSocketSupport {

  def createSink(sr: Ref[EstimationSession], id: Long, rt: Runtime[_]): Sink[Message, _] =
    Flow[Message]
      .map(messageToCommand(sr, _))
      .to(Sink.foreach(z => rt.unsafeRun(z)))

  def messageToCommand(sr: Ref[EstimationSession], msg: Message): ZIO[Any, Throwable, Unit] = {
    val c = msg.asTextMessage.getStrictText
    val cmd = c.head.toUpper
    val payload = c.tail
    cmd match {
      case 'V' =>
        val v = payload.split(':')
        for {
          _ <- sr.update(s => s.addVote(v(0).toLong, Option(v(1))))
          s <- sr.get
          _ <- s.pushAll
        } yield ()
      case 'I' =>
        for {
          _ <- sr.update(s => s.setIssue(payload))
          s <- sr.get
          _ <- s.pushAll
        } yield ()
      case 'C' =>
        for {
          _ <- sr.update(s => s.clearVotes())
          s <- sr.get
          _ <- s.pushAll
        } yield ()
      case 'K' =>
        val pid = payload.toLong
        for {
          _ <- sr.update(_ - pid)
          s <- sr.get
          _ <- s.pushAll
        } yield ()
      case 'D' =>
        val deck = EstimationDeck(payload.toInt)
        for {
          _ <- sr.update(_.setDeck(deck).clearVotes())
          s <- sr.get
          _ <- s.pushAll
        } yield ()
      case _ =>
        ZIO.unit
    }
  }

  def createSource()(implicit mat: Materializer) : (Source[Message, _], ActorRef[EstimationSession])= {
    val (aRef, p) = Source.actorRef[EstimationSession](
      100, OverflowStrategy.fail)
      .map(msg => TextMessage.Strict(msg.asJson))
      .toMat(Sink.asPublisher(false))(Keep.both).run()
    (Source.fromPublisher(p), aRef)
  }

}
