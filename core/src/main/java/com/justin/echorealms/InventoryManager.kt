package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.XmlReader

class InventoryManager {
    private val items = Array<Item>()
    private val allItems = mutableMapOf<String, Item>()
    private val equippedItems = mutableMapOf<String, Item?>() // Maps slotType to equipped item

    init {
        loadItemsFromXml()
        // Initialize all equipment slots
        listOf("weapon", "left_hand", "helmet", "legs", "feet", "ring", "amulet").forEach { slot ->
            equippedItems[slot] = null
        }
    }

    private fun loadItemsFromXml() {
        val xmlFile = Gdx.files.internal("weapons.xml")
        if (xmlFile.exists()) {
            val reader = XmlReader()
            val root = reader.parse(xmlFile)
            val itemElements = root.getChildrenByName("item")
            for (i in 0 until itemElements.size) {
                val element = itemElements.get(i)
                val item = Item.fromXml(element)
                allItems[item.id] = item
                Gdx.app.log("InventoryManager", "Loaded item: ${item.name} (${item.id}, slot: ${item.slotType})")
            }
        } else {
            Gdx.app.error("InventoryManager", "weapons.xml not found")
        }
    }

    fun addItem(itemId: String) {
        val item = allItems[itemId]
        if (item != null) {
            items.add(item)
            Gdx.app.log("InventoryManager", "Added item: ${item.name} (${item.id})")
        } else {
            Gdx.app.log("InventoryManager", "Item not found: $itemId")
        }
    }

    fun removeItem(item: Item): Boolean {
        val removed = items.removeValue(item, true)
        if (removed && equippedItems.containsValue(item)) {
            equippedItems[item.slotType] = null
            Gdx.app.log("InventoryManager", "Unequipped ${item.name} from ${item.slotType} due to removal")
        }
        return removed
    }

    fun getItems(): Array<Item> = items

    fun hasItem(item: Item): Boolean = items.contains(item, false)

    fun equipItem(item: Item?) {
        if (item == null || (item.isEquippable && items.contains(item, true))) {
            if (item != null) {
                equippedItems[item.slotType] = item
                Gdx.app.log("InventoryManager", "Equipped ${item.name} to ${item.slotType}")
            }
        } else {
            Gdx.app.log("InventoryManager", "Cannot equip item: ${item?.name ?: "null"} (not equippable or not in inventory)")
        }
    }

    fun unequipItem(slotType: String) {
        if (equippedItems.containsKey(slotType)) {
            equippedItems[slotType] = null
            Gdx.app.log("InventoryManager", "Unequipped item from $slotType")
        }
    }

    fun getEquippedItem(slotType: String): Item? = equippedItems[slotType]

    fun getEffectiveModifiers(): Map<String, Int> {
        val modifiers = mutableMapOf<String, Int>()
        equippedItems.values.filterNotNull().forEach { item ->
            item.statModifiers.forEach { (stat, value) ->
                modifiers[stat] = (modifiers[stat] ?: 0) + value
            }
        }
        return modifiers
    }
}
