package de.notizwerk.tutorial.websocket

import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.shareddata.LocalMap
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.random.Random

class BitfinexListener(val vertx: Vertx, val subscriptions: LocalMap<Int, String>) : WebSocket.Listener {

    val random = Random(21928)

    override fun onOpen(webSocket: WebSocket?) {
        super.onOpen(webSocket)
        LOGGER.info("websocket opened")
        this.vertx.periodicStream(60000).toObservable().subscribe { i ->
            val pingTxt = JsonObject().put("event", "ping").put("cid", random.nextInt()).encode()
            webSocket?.sendText(pingTxt, true)?.thenRun { -> LOGGER.info("sent ping {}", pingTxt) }
        }
    }

    var parts: MutableList<CharSequence?> = MutableList(0) { index: Int -> "" }
    var accumulatedMessage: CompletableFuture<*> = CompletableFuture<Any>()

    override fun onText(webSocket: WebSocket, message: CharSequence?, last: Boolean): CompletionStage<*>? {
        parts.add(message)
        webSocket.request(1)
        if (last) {
            val completeMessage = parts.joinToString(separator = "") { charSequence -> charSequence ?: "" }
            LOGGER.debug("message completed {}", completeMessage)
            parts.clear()
            accumulatedMessage.complete(null)
            val cf: CompletionStage<*> = accumulatedMessage
            accumulatedMessage = CompletableFuture<Any>()
            onMessage(completeMessage)
            return cf
        }
        return accumulatedMessage
    }

    fun onMessage(message: String) {
        val bitfinexMessage = Json.decodeValue(message)
        if (bitfinexMessage is JsonObject) {
            val event = bitfinexMessage.getString("event")
            when (event) {
                "subscribed" -> {
                    val channelId = bitfinexMessage.getInteger("chanId")
                    val symbol = bitfinexMessage.getString("symbol")
                    this.subscriptions.put(channelId, symbol)
                }
                "unsubscribed" -> {
                    this.subscriptions.remove(bitfinexMessage.getInteger("chanId"))
                }
                "error" -> {
                    LOGGER.error("an error {}", bitfinexMessage)
                }
                "info" -> LOGGER.info(bitfinexMessage)
                "pong" -> LOGGER.debug(bitfinexMessage)
                "conf" -> LOGGER.info(bitfinexMessage)
            }
        } else if (bitfinexMessage is JsonArray) {
            val chanId = bitfinexMessage.getInteger(0)
            val symbol = this.subscriptions.get(chanId)
            if (symbol != null) vertx.eventBus().publish("ticker." + symbol, bitfinexMessage)
        } else {
            LOGGER.warn("received unknown message from bitfinex {} ", bitfinexMessage)
        }
    }
}
