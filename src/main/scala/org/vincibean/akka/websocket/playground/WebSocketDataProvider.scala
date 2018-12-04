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

  private val graph: RunnableGraph[(ActorRef, Publisher[String])] = Source
    .actorRef[String](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)

  def webSocketFlow()(implicit as: ActorSystem, am: ActorMaterializer, ec: ExecutionContext): Flow[Message, TextMessage.Strict, NotUsed] = {
    val (down, publisher) = graph.run()

    as.scheduler.schedule(0.seconds, 0.5.second, new Runnable {
      override def run(): Unit = {
        down ! Random.nextInt.toString
      }
    })

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