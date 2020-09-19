package de.notizwerk.tutorial.websocket

import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.AbstractVerticle
import org.apache.logging.log4j.LogManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket

val LOGGER = LogManager.getLogger("Bitfinex")
const val BITFINEX_EB_ADDRESS = "bitfinex"

class BitfinexConnection : AbstractVerticle() {

    var webSocket: WebSocket? = null

    override fun start() {
        LOGGER.info("deploying BitfinexConnection")
        val s = vertx.sharedData().getLocalMap<Int,String>("biitfinex.subscriptions")
        val listener = BitfinexListener(this.vertx, s)
        val client = HttpClient.newHttpClient()
        this.webSocket = client.newWebSocketBuilder()
            .buildAsync(URI.create("wss://api-pub.bitfinex.com/ws/2"), listener)
            .join()


        vertx.eventBus().consumer<JsonObject>(BITFINEX_EB_ADDRESS).handler { jsonMsg ->
            if ( webSocket==null || webSocket?.isOutputClosed()!!) {
                jsonMsg.reply(JsonObject().put("message", "websocket closed").put("statusCode",503), DeliveryOptions().addHeader("statusCode","503"))
                return@handler
            }
            val bitfinexMessage = jsonMsg.body().encode()
            this.webSocket?.sendText(bitfinexMessage,true)?.thenRun{ LOGGER.debug("delivered {} ", bitfinexMessage)}
        }
    }

    override fun stop() {
        this.webSocket?.sendClose(200, "closing")
    }

}