// core/src/com/justin/echorealms/EventBus.kt
package com.justin.echorealms

class EventBus {
    private val listeners = mutableMapOf<String, MutableList<(Array<out Any>) -> Unit>>()

    fun subscribe(event: String, listener: (Array<out Any>) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(listener)
    }

    fun fire(event: String, vararg args: Any) {
        listeners[event]?.forEach { it(args) }
    }
}
