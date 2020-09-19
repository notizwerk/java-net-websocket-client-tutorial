package de.notizwerk.tutorial.websocket

import io.vertx.core.AsyncResult
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Vertx
import java.time.Duration

fun main() {
    val vertx = Vertx.vertx()
    val symbol = "tBTCUSD"
    vertx.rxDeployVerticle(BitfinexConnection::class.java.name)
        .subscribe(
            { id ->
                LOGGER.info("deployed bitfinex connection {}", id)
                val subscribeMessage = JsonObject().bfxSubscribeTickerMessage(symbol)
                val address = "ticker." + symbol
                vertx.eventBus().consumer<JsonArray>(address).handler { jsonMsg ->
                    LOGGER.info("received {} {}", address, jsonMsg.body().encodePrettily())
                }
                vertx.eventBus().send(BITFINEX_EB_ADDRESS, subscribeMessage)

            },
            { t: Throwable? -> LOGGER.error("deployment of bitfinex connection failed", t) }
        )
    Runtime.getRuntime().addShutdownHook(Thread(Runnable {
        LOGGER.info("SHUTTING DOWN")
        vertx.eventBus()
            .send(BITFINEX_EB_ADDRESS, JsonObject().bfxUnSubscribeTickerMessage(symbol))
        vertx.setTimer(1000) { id ->
            vertx.close { async: AsyncResult<Void?> ->
                LOGGER.info("SHUTDOWN {}", if (async.succeeded()) "SUCCESS" else "FAILED"
                )
            }
        }
    }))
}

fun JsonObject.bfxSubscribeTickerMessage(symbol: String): JsonObject {
    this.put("event", "subscribe")
    this.put("channel", "ticker")
    this.put("symbol", symbol)
    return this
}

fun JsonObject.bfxUnSubscribeTickerMessage(symbol: String): JsonObject {
    this.put("event","unsubscribe")
    this.put("channel", "ticker")
    this.put("symbol",symbol)
    return this
}
