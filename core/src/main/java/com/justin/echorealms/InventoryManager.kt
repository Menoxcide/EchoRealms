package com.justin.echorealms

import com.badlogic.gdx.utils.Array

class InventoryManager {
    private val items = Array<String>()

    fun addItem(item: String) = items.add(item)
    fun removeItem(item: String): Boolean = items.removeValue(item, true)
    fun getItems(): Array<String> = items
    fun hasItem(item: String): Boolean = items.contains(item, false)
}
