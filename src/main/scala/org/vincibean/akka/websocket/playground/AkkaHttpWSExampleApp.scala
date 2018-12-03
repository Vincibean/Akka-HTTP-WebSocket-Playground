package org.vincibean.akka.websocket.playground

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import org.vincibean.akka.websocket.playground.Route.GetWebsocketFlow

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

object AkkaHttpWSExampleApp extends App {
  implicit val system: ActorSystem = ActorSystem("my-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  val bindingFuture = Http().bindAndHandle(Route.websocketRoute, "localhost", 8080)

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}

object Route {

  case object GetWebsocketFlow

  implicit val as: ActorSystem = ActorSystem("example")
  implicit val am: ActorMaterializer = ActorMaterializer()

  def websocketRoute(implicit ec: ExecutionContext): Route =
    pathEndOrSingleSlash {
      complete("WS server is alive\n")
    } ~ path("connect") {

      val handler = as.actorOf(ClientHandlerActor.props)
      val futureFlow = (handler ? GetWebsocketFlow) (3.seconds).mapTo[Flow[Message, Message, _]]

      onComplete(futureFlow) {
        case Success(flow) => handleWebSocketMessages(flow)
        case Failure(err) => complete(err.toString)
      }

    }
}

object ClientHandlerActor {
  def props(implicit ec: ExecutionContext) = Props(new ClientHandlerActor()(ec))
}

class ClientHandlerActor(implicit ec: ExecutionContext) extends Actor {

  implicit val as: ActorSystem = context.system
  implicit val am: ActorMaterializer = ActorMaterializer()

  val (down, publisher) = Source
    .actorRef[String](1000, OverflowStrategy.fail)
    .toMat(Sink.asPublisher(fanout = false))(Keep.both)
    .run()

  // test
  var counter = 0
  as.scheduler.schedule(0.seconds, 0.5.second, new Runnable {
    override def run(): Unit = {
      counter = counter + 1
      self ! counter
    }
  })

  override def receive: Receive = {
    case GetWebsocketFlow =>

      val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>
        val textMsgFlow = b.add(Flow[Message]
          .mapAsync(1) {
            case tm: TextMessage => tm.toStrict(3.seconds).map(_.text)
            case bm: BinaryMessage =>
              // consume the stream
              bm.dataStream.runWith(Sink.ignore)
              Future.failed(new Exception("yuck"))
          })

        val pubSrc = b.add(Source.fromPublisher(publisher).map(TextMessage(_)))

        textMsgFlow ~> Sink.foreach[String](self ! _)
        FlowShape(textMsgFlow.in, pubSrc.out)
      })

      sender ! flow

    // replies with "hello XXX"
    case s: String =>
      println(s"client actor received $s")
      down ! "Hello " + s + "!"

    // passes any int down the websocket
    case n: Int =>
      println(s"client actor received $n")
      down ! n.toString
  }
}
