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
    private var globalAttackCooldown = 0f
    private val globalAttackTick = 1f
    private var regenTimer = 0f
    private val regenInterval = 10f
    private val regenPercentage = 0.05f
    private val attackRange = 5
    private val meleeRange = 1
    private val rangedRange = 5
    private val magicRange = 3
    private val criticalChance = 0.1f

    fun update(delta: Float) {
        if (player.isDead()) return

        globalAttackCooldown -= delta
        if (globalAttackCooldown < 0f) globalAttackCooldown = 0f

        regenTimer -= delta
        if (regenTimer <= 0f) {
            val regenAmount = (player.maxHp * regenPercentage).toInt().coerceAtLeast(1)
            player.currentHp = minOf(player.currentHp + regenAmount, player.maxHp)
            if (regenAmount > 0) {
                floatingTextManager.addText("+$regenAmount", player.playerX, player.playerY, false)
                chatWindow.addCombatLog("Player regenerated $regenAmount HP")
                Gdx.app.log("CombatManager", "Player regenerated $regenAmount HP, current HP: ${player.currentHp}/${player.maxHp}")
            }
            regenTimer = regenInterval
        }

        player.targetedMonster?.let { monster ->
            val dx = player.playerX - monster.x
            val dy = player.playerY - monster.y
            val dist = max(abs(dx), abs(dy))
            if (globalAttackCooldown <= 0f && !player.isImmune()) {
                when {
                    dist <= meleeRange -> {
                        performMeleeAttack(monster)
                        globalAttackCooldown = globalAttackTick
                    }
                    dist <= rangedRange && player.getEffectiveRanged() > 1 -> {
                        performRangedAttack(monster)
                        globalAttackCooldown = globalAttackTick
                    }
                    dist <= magicRange && player.getEffectiveMagic() > 1 -> {
                        performMagicAttack(monster)
                        globalAttackCooldown = globalAttackTick
                    }
                    dist > attackRange -> {
                        player.targetedMonster = null
                        Gdx.app.log("CombatManager", "Cleared targeted monster due to distance")
                    }
                }
            }
        }
    }

    private fun performMeleeAttack(monster: Monster) {
        val baseDamage = (player.getEffectiveAttack() + player.getEffectiveStrength()) / 2
        val isCritical = Random.nextFloat() < criticalChance
        val damage = (baseDamage * if (isCritical) 2 else 1) * (0.8f + Random.nextFloat() * 0.4f)
        val finalDamage = damage.toInt().coerceAtLeast(0)
        monster.currentHp -= finalDamage
        monster.criticalHitTimer = if (isCritical) 0.5f else 0f
        monster.hitTimer = 0.5f
        floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage", monster.x, monster.y, isCritical)
        chatWindow.addCombatLog("Player dealt $finalDamage${if (isCritical) " (CRITICAL!)" else ""} damage to ${monster.stats.name}")
        val exp = monster.stats.exp
        player.getLevelingSystem().addExperience("attack", exp / 2)
        player.getLevelingSystem().addExperience("strength", exp / 2)
        handleMonsterDeath(monster)
    }

    private fun performRangedAttack(monster: Monster) {
        val baseDamage = player.getEffectiveRanged() * 2
        val isCritical = Random.nextFloat() < criticalChance
        val damage = (baseDamage * if (isCritical) 2 else 1) * (0.8f + Random.nextFloat() * 0.4f)
        val finalDamage = damage.toInt().coerceAtLeast(0)
        monster.currentHp -= finalDamage
        monster.criticalHitTimer = if (isCritical) 0.5f else 0f
        monster.hitTimer = 0.5f
        floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage (Ranged)", monster.x, monster.y, isCritical)
        chatWindow.addCombatLog("Player dealt $finalDamage${if (isCritical) " (CRITICAL!)" else ""} ranged damage to ${monster.stats.name}")
        val exp = monster.stats.exp
        player.getLevelingSystem().addExperience("attack", exp / 2)
        player.getLevelingSystem().addExperience("strength", exp / 2)
        handleMonsterDeath(monster)
    }

    private fun performMagicAttack(monster: Monster) {
        val baseDamage = player.getEffectiveMagic() * 3
        val isCritical = Random.nextFloat() < criticalChance
        val damage = (baseDamage * if (isCritical) 2 else 1) * (0.8f + Random.nextFloat() * 0.4f)
        val finalDamage = damage.toInt().coerceAtLeast(0)
        monster.currentHp -= finalDamage
        monster.criticalHitTimer = if (isCritical) 0.5f else 0f
        monster.hitTimer = 0.5f
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
            val lootId = monster.stats.loot.random()
            inventoryManager.addItem(lootId)
            player.getLevelingSystem().addExperience("strength", monster.stats.exp / 2)
            player.getLevelingSystem().addExperience("HP", monster.stats.exp / 2)
            monsterManager.eventBus.fire("onMonsterDeath", monster.stats.name, monster.x, monster.y)
            chatWindow.addCombatLog("${monster.stats.name} was defeated")
            player.targetedMonster = null
        }
    }
}
