package com.justin.echorealms

import com.badlogic.gdx.Gdx
import kotlin.math.floor
import kotlin.math.pow

class LevelingSystem(private val textRenderer: TextRenderer) {
    private val maxLevel = 99
    private val xpThresholds = IntArray(maxLevel + 1) { 0 }
    private val stats = mutableMapOf(
        "HP" to 10,
        "attack" to 1,
        "strength" to 1,
        "defense" to 1,
        "magic" to 1,
        "ranged" to 1,
        "prayer" to 1,
        "woodcutting" to 1,
        "mining" to 1,
        "agility" to 1
    )
    private val levels = mutableMapOf(
        "HP" to 1,
        "attack" to 1,
        "strength" to 1,
        "defense" to 1,
        "magic" to 1,
        "ranged" to 1,
        "prayer" to 1,
        "woodcutting" to 1,
        "mining" to 1,
        "agility" to 1
    )
    private val xp = mutableMapOf(
        "HP" to 0,
        "attack" to 0,
        "strength" to 0,
        "defense" to 0,
        "magic" to 0,
        "ranged" to 0,
        "prayer" to 0,
        "woodcutting" to 0,
        "mining" to 0,
        "agility" to 0
    )

    init {
        // Precompute experience thresholds for levels 1 to 99
        var totalXp = 0
        for (level in 1 until maxLevel) {
            val xpForLevel = floor((level + 300 * 2.0.pow(level / 7.0)) / 4).toInt()
            totalXp += xpForLevel
            xpThresholds[level] = totalXp
        }
        xpThresholds[maxLevel] = totalXp
    }

    fun addExperience(stat: String, amount: Int) {
        if (!xp.containsKey(stat)) return
        xp[stat] = (xp[stat] ?: 0) + amount
        checkLevelUp(stat)
        // Distribute 1/3 of experience to HP for attack, strength, defense
        if (stat in listOf("attack", "strength", "defense")) {
            val hpAmount = amount / 3
            xp["HP"] = (xp["HP"] ?: 0) + hpAmount
            checkLevelUp("HP")
        }
    }

    private fun checkLevelUp(stat: String) {
        val currentXp = xp[stat] ?: 0
        val currentLevel = levels[stat] ?: 1
        if (currentLevel >= maxLevel) return

        if (currentXp >= xpThresholds[currentLevel] && currentLevel < maxLevel) {
            levels[stat] = currentLevel + 1
            if (stat == "HP") {
                stats[stat] = 10 + (currentLevel + 1) * 5
            } else {
                stats[stat] = (stats[stat] ?: 1) + 1
            }
            textRenderer.showLevelUpMessage("$stat Level Up!", currentLevel + 1)
            Gdx.app.log("LevelingSystem", "$stat leveled up to ${levels[stat]}!")
        }
    }

    fun getStat(stat: String): Int = stats[stat] ?: 1
    fun getLevel(stat: String): Int = levels[stat] ?: 1
    fun getXP(stat: String): Int = xp[stat] ?: 0
}
