package com.justin.echorealms

import com.badlogic.gdx.Gdx
import kotlin.math.max
import kotlin.math.abs
import kotlin.random.Random

class CombatManager(
    private val player: Player,
    private val monsterManager: MonsterManager,
    private val floatingTextManager: FloatingTextManager,
    private val inventoryManager: InventoryManager,
    private val chatWindow: ChatWindow
) {
    private var attackCooldown = 0f
    private val attackRange = 5 // Integer tile range
    private val meleeRange = 1   // Integer tile range
    private val rangedRange = 5  // Integer tile range
    private val magicRange = 3   // Integer tile range
    private val criticalChance = 0.1f // 10% chance for critical hit

    fun update(delta: Float) {
        if (player.isDead()) return // Skip updates when player is dead

        attackCooldown -= delta
        if (attackCooldown < 0f) attackCooldown = 0f

        // Only process attacks for the explicitly targeted monster
        player.targetedMonster?.let { monster ->
            val dx = player.playerX - monster.x
            val dy = player.playerY - monster.y
            val dist = max(abs(dx), abs(dy))
            if (dist <= meleeRange && attackCooldown <= 0f && !player.isImmune()) {
                performMeleeAttack(monster)
                attackCooldown = 1f
            } else if (dist <= rangedRange && player.ranged > 1 && attackCooldown <= 0f && !player.isImmune()) {
                performRangedAttack(monster)
                attackCooldown = 2f
            } else if (dist <= magicRange && player.magic > 1 && attackCooldown <= 0f && !player.isImmune()) {
                performMagicAttack(monster)
                attackCooldown = 3f
            } else if (dist > attackRange) {
                // Clear target if out of range
                player.targetedMonster = null
                Gdx.app.log("CombatManager", "Cleared targeted monster due to distance")
            }
        }
    }

    private fun performMeleeAttack(monster: Monster) {
        val baseDamage = (player.attack + player.strength) / 2
        val isCritical = Random.nextFloat() < criticalChance
        val damage = (baseDamage * if (isCritical) 2 else 1) * (0.8f + Random.nextFloat() * 0.4f)
        val finalDamage = damage.toInt().coerceAtLeast(0)
        monster.currentHp -= finalDamage
        monster.criticalHitTimer = if (isCritical) 0.5f else 0f
        monster.hitTimer = 0.5f // Trigger blinking red rectangle
        floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage", monster.x, monster.y, isCritical)
        chatWindow.addCombatLog("Player dealt $finalDamage${if (isCritical) " (CRITICAL!)" else ""} damage to ${monster.stats.name}")
        val exp = monster.stats.exp
        player.getLevelingSystem().addExperience("attack", exp / 2)
        player.getLevelingSystem().addExperience("strength", exp / 2)
        handleMonsterDeath(monster)
    }

    private fun performRangedAttack(monster: Monster) {
        val baseDamage = player.ranged * 2
        val isCritical = Random.nextFloat() < criticalChance
        val damage = (baseDamage * if (isCritical) 2 else 1) * (0.8f + Random.nextFloat() * 0.4f)
        val finalDamage = damage.toInt().coerceAtLeast(0)
        monster.currentHp -= finalDamage
        monster.criticalHitTimer = if (isCritical) 0.5f else 0f
        monster.hitTimer = 0.5f // Trigger blinking red rectangle
        floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage (Ranged)", monster.x, monster.y, isCritical)
        chatWindow.addCombatLog("Player dealt $finalDamage${if (isCritical) " (CRITICAL!)" else ""} ranged damage to ${monster.stats.name}")
        val exp = monster.stats.exp
        player.getLevelingSystem().addExperience("attack", exp / 2)
        player.getLevelingSystem().addExperience("strength", exp / 2)
        handleMonsterDeath(monster)
    }

    private fun performMagicAttack(monster: Monster) {
        val baseDamage = player.magic * 3
        val isCritical = Random.nextFloat() < criticalChance
        val damage = (baseDamage * if (isCritical) 2 else 1) * (0.8f + Random.nextFloat() * 0.4f)
        val finalDamage = damage.toInt().coerceAtLeast(0)
        monster.currentHp -= finalDamage
        monster.criticalHitTimer = if (isCritical) 0.5f else 0f
        monster.hitTimer = 0.5f // Trigger blinking red rectangle
        floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage (Magic)", monster.x, monster.y, isCritical)
        chatWindow.addCombatLog("Player dealt $finalDamage${if (isCritical) " (CRITICAL!)" else ""} magic damage to ${monster.stats.name}")
        val exp = monster.stats.exp
        player.getLevelingSystem().addExperience("attack", exp / 2)
        player.getLevelingSystem().addExperience("strength", exp / 2)
        handleMonsterDeath(monster)
    }

    private fun handleMonsterDeath(monster: Monster) {
        if (monster.currentHp <= 0) {
            monsterManager.getMonsters().removeValue(monster, true)
            inventoryManager.addItem(monster.stats.loot.random())
            player.getLevelingSystem().addExperience("strength", monster.stats.exp / 2)
            player.getLevelingSystem().addExperience("HP", monster.stats.exp / 2)
            monsterManager.eventBus.fire("onMonsterDeath", monster.stats.name, monster.x, monster.y)
            chatWindow.addCombatLog("${monster.stats.name} was defeated")
            player.targetedMonster = null
        }
    }
}
