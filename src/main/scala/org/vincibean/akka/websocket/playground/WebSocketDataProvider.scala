package org.vincibean.akka.websocket.playground

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.{Done, NotUsed}
import org.reactivestreams.Publisher

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object WebSocketDataProvider {

  implicit val as: ActorSystem = ActorSystem("example")
  implicit val am: ActorMaterializer = ActorMaterializer()

  def webSocketFlow()(implicit as: ActorSystem, am: ActorMaterializer, ec: ExecutionContext): Flow[Message, TextMessage.Strict, NotUsed] = {
    val publisher = Source.repeat(() => Random.alphanumeric.take(6).mkString)
      .map(f => f())
      .throttle(1, 1.second)
      .runWith(Sink.asPublisher(fanout = false))

    Flow.fromSinkAndSource(sink, source(publisher))
  }

  private def sink(implicit am: ActorMaterializer): Sink[Message, Future[Done]] = Sink.foreach[Message] {
    case tm: TextMessage =>
      tm.textStream.runWith(Sink.ignore)
      ()
    case bm: BinaryMessage =>
      bm.dataStream.runWith(Sink.ignore)
      ()
  }

  private def source(publisher: Publisher[String]): Source[TextMessage.Strict, NotUsed] =
    Source.fromPublisher(publisher).map(TextMessage(_))
}