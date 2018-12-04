package org.vincibean.akka.websocket.playground

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext

object Route {

  case object GetWebsocketFlow

  implicit val as: ActorSystem = ActorSystem("example")
  implicit val am: ActorMaterializer = ActorMaterializer()

  def websocketRoute(implicit ec: ExecutionContext): Route =
    pathEndOrSingleSlash {
      complete("WS server is alive\n")
    } ~ path("connect") {
      handleWebSocketMessages(WebSocketDataProvider.webSocketFlow())
    }
}