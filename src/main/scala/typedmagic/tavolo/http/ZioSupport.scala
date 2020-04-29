package typedmagic.tavolo.http

import akka.http.scaladsl.marshalling.{ Marshaller, Marshalling, PredefinedToResponseMarshallers }
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.{ Route, RouteResult }
import zio.{ IO, Runtime, ZIO }

import scala.concurrent.{ Future, Promise }
import scala.language.implicitConversions
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult.Complete

trait ErrorMapper[E] {
  def toHttpResponse(e: E): HttpResponse
}

trait ZioSupport extends Runtime[Unit] { self =>

  implicit def errorMarshaller[E: ErrorMapper]: Marshaller[E, HttpResponse] =
    Marshaller { implicit ec => a =>
      PredefinedToResponseMarshallers.fromResponse(implicitly[ErrorMapper[E]].toHttpResponse(a))
    }

  implicit def zioMarshaller[A, E](implicit m1: Marshaller[A, HttpResponse], m2: Marshaller[E, HttpResponse]): Marshaller[ZIO[Any, E, A], HttpResponse] =
    Marshaller { implicit ec => a => {
      val r = a.foldM(
        e => IO.fromFuture(implicit ec => m2(e)),
        a => IO.fromFuture(implicit ec => m1(a))
      )
      val p = Promise[List[Marshalling[HttpResponse]]]()
      self.unsafeRunAsync(r) { exit =>
        exit.fold(e => p.failure(e.squash), s => p.success(s))
      }
      p.future
    }}

  implicit def zioRoute[E: ErrorMapper](z: ZIO[Any, E, Route]): Route = ctx => {
    val p = Promise[RouteResult]()
    val f = z.fold(
      e => (ctx: RequestContext) => Future.successful(Complete(implicitly[ErrorMapper[E]].toHttpResponse(e))),
      a => a
    )
    self.unsafeRunAsync(f) { exit =>
      exit.fold(e => p.failure(e.squash), s => p.completeWith(s.apply(ctx)))
    }
    p.future
  }

}