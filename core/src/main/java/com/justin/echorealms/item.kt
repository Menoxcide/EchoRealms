package com.justin.echorealms

data class Item(
    val id: String,
    val name: String,
    val texturePath: String,
    val description: String,
    val isEquippable: Boolean = false,
    val statModifiers: Map<String, Int> = emptyMap(),
    val slotType: String = "none" // e.g., "weapon", "left_hand", "helmet", "legs", "feet", "ring", "amulet"
) {
    companion object {
        fun fromXml(element: com.badlogic.gdx.utils.XmlReader.Element): Item {
            val id = element.get("id", "")
            val name = element.get("name", "")
            val texturePath = element.get("texturePath", "")
            val description = element.get("description", "")
            val slotType = element.get("slotType", "none")
            val isEquippable = slotType in listOf("weapon", "left_hand", "helmet", "legs", "feet", "ring", "amulet")
            val modifiers = mutableMapOf<String, Int>()

            // Heuristic based on description and name
            if (description.contains("attack", ignoreCase = true) || description.contains("damage", ignoreCase = true) ||
                name.contains("Sword") || name.contains("Blade")) {
                modifiers["attack"] = if (name.contains("(New)")) 5 else if (name.contains("(Old)")) 3 else 4
            }
            if (description.contains("strength", ignoreCase = true) || description.contains("power", ignoreCase = true) ||
                name.contains("Axe") || name.contains("Mace")) {
                modifiers["strength"] = if (name.contains("(New)")) 4 else if (name.contains("(Old)")) 2 else 3
            }
            if (description.contains("magic", ignoreCase = true) || name.contains("Staff") || name.contains("Wand")) {
                modifiers["magic"] = if (name.contains("(New)")) 5 else if (name.contains("(Old)")) 3 else 4
            }
            if (description.contains("ranged", ignoreCase = true) || name.contains("Bow") || name.contains("Arrow")) {
                modifiers["ranged"] = if (name.contains("(New)")) 5 else if (name.contains("(Old)")) 3 else 4
            }
            if (description.contains("defense", ignoreCase = true) || description.contains("protection", ignoreCase = true)) {
                modifiers["defense"] = if (name.contains("(New)")) 4 else if (name.contains("(Old)")) 2 else 3
            }

            return Item(id, name, texturePath, description, isEquippable, modifiers, slotType)
        }
    }
}
