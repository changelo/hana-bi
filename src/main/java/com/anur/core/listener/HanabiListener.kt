package com.anur.core.listener

import com.anur.core.coordinate.apis.recovery.FollowerClusterRecoveryManager
import org.slf4j.LoggerFactory
import java.util.function.BiFunction

/**
 * Created by Anur IjuoKaruKas on 2019/7/9
 */
object HanabiListener {

    private val logger = LoggerFactory.getLogger(FollowerClusterRecoveryManager::class.java)

    private val EVENT: MutableMap<String, Registion> = mutableMapOf()

    @Synchronized
    fun register(event: EventEnum, action: () -> Unit) {
        EVENT.compute(event.name, BiFunction { _, v ->
            return@BiFunction (v ?: Registion()).also { r -> r.register(action) }
        })
    }

    @Synchronized
    fun register(event: EventEnum, key: String, action: () -> Unit) {
        EVENT.compute(event.name + key.let { " - $it" }, BiFunction { _, v ->
            return@BiFunction (v ?: Registion()).also { r -> r.register(action) }
        })
    }

    @Synchronized
    fun clear(event: EventEnum) {
        EVENT.remove(event.name)
    }

    @Synchronized
    fun clear(event: EventEnum, key: String) {
        EVENT.remove(event.name + key.let { " - $it" })
    }

    fun onEvent(event: EventEnum) {
        logger.info("Event ${event.name} is triggered")
        EVENT[event.name]?.onEvent()
    }

    fun onEvent(event: EventEnum, key: String) {
        val event = event.name + key.let { " - $it" }
        logger.info("Event $event is triggered")
        EVENT[event]?.onEvent()
    }

    class Registion {
        private val actionRegister: MutableList<() -> Unit> = mutableListOf()

        fun register(action: () -> Unit) {
            actionRegister.add(action)
        }

        fun onEvent() {
            actionRegister.forEach { function: () -> Unit -> function.invoke() }
        }
    }
}
