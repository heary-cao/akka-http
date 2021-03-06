/*
 * Copyright (C) 2009-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl.server

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, WebSocketRequest }
import akka.http.scaladsl.settings.{ ClientConnectionSettings, ServerSettings }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink }
import akka.util.ByteString
import docs.CompileOnlySpec

import scala.io.StdIn
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WebSocketExampleSpec extends AnyWordSpec with Matchers with CompileOnlySpec {
  "core-example" in compileOnlySpec {
    //#websocket-example-using-core
    import akka.actor.ActorSystem
    import akka.stream.ActorMaterializer
    import akka.stream.scaladsl.{ Source, Flow }
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.model.ws.UpgradeToWebSocket
    import akka.http.scaladsl.model.ws.{ TextMessage, Message }
    import akka.http.scaladsl.model.{ HttpResponse, Uri, HttpRequest }
    import akka.http.scaladsl.model.HttpMethods._

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    //#websocket-handler
    // The Greeter WebSocket Service expects a "name" per message and
    // returns a greeting message for that name
    val greeterWebSocketService =
      Flow[Message]
        .mapConcat {
          // we match but don't actually consume the text message here,
          // rather we simply stream it back as the tail of the response
          // this means we might start sending the response even before the
          // end of the incoming message has been received
          case tm: TextMessage => TextMessage(Source.single("Hello ") ++ tm.textStream) :: Nil
          case bm: BinaryMessage =>
            // ignore binary messages but drain content to avoid the stream being clogged
            bm.dataStream.runWith(Sink.ignore)
            Nil
        }
    //#websocket-handler

    //#websocket-request-handling
    val requestHandler: HttpRequest => HttpResponse = {
      case req @ HttpRequest(GET, Uri.Path("/greeter"), _, _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) => upgrade.handleMessages(greeterWebSocketService)
          case None          => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }
    //#websocket-request-handling

    val bindingFuture =
      Http().bindAndHandleSync(requestHandler, interface = "localhost", port = 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()

    import system.dispatcher // for the future transformations
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
  "routing-example" in compileOnlySpec {
    import akka.actor.ActorSystem
    import akka.stream.ActorMaterializer
    import akka.stream.scaladsl.{ Source, Flow }
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.model.ws.{ TextMessage, Message }
    import akka.http.scaladsl.server.Directives

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    import Directives._

    // The Greeter WebSocket Service expects a "name" per message and
    // returns a greeting message for that name
    val greeterWebSocketService =
      Flow[Message]
        .collect {
          case tm: TextMessage => TextMessage(Source.single("Hello ") ++ tm.textStream)
          // ignore binary messages
          // TODO #20096 in case a Streamed message comes in, we should runWith(Sink.ignore) its data
        }

    //#websocket-routing
    val route =
      path("greeter") {
        get {
          handleWebSocketMessages(greeterWebSocketService)
        }
      }
    //#websocket-routing

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()

    import system.dispatcher // for the future transformations
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  "ping-server-example" in compileOnlySpec {
    implicit val system: ActorSystem = ???
    implicit val mat: ActorMaterializer = ???
    //#websocket-ping-payload-server
    val defaultSettings = ServerSettings(system)

    val pingCounter = new AtomicInteger()
    val customWebsocketSettings =
      defaultSettings.websocketSettings
        .withPeriodicKeepAliveData(() => ByteString(s"debug-${pingCounter.incrementAndGet()}"))

    val customServerSettings =
      defaultSettings.withWebsocketSettings(customWebsocketSettings)

    Http().bindAndHandle(???, "127.0.0.1", settings = customServerSettings)
    //#websocket-ping-payload-server
  }

  "ping-example" in compileOnlySpec {
    implicit val system: ActorSystem = ???
    implicit val mat: ActorMaterializer = ???
    //#websocket-client-ping-payload
    val defaultSettings = ClientConnectionSettings(system)

    val pingCounter = new AtomicInteger()
    val customWebsocketSettings =
      defaultSettings.websocketSettings
        .withPeriodicKeepAliveData(() => ByteString(s"debug-${pingCounter.incrementAndGet()}"))

    val customSettings =
      defaultSettings.withWebsocketSettings(customWebsocketSettings)

    val request = WebSocketRequest("ws://127.0.0.1")

    Http().singleWebSocketRequest(
      request,
      Flow[Message],
      Http().defaultClientHttpsContext,
      None,
      customSettings,
      system.log
    )
    //#websocket-client-ping-payload
  }
}
