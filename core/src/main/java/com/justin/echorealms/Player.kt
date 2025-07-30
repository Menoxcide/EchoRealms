package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType

class Player(
    private val tileSize: Float,
    startX: Float,
    startY: Float,
    private val floatingTextManager: FloatingTextManager,
    var targetedMonster: Monster?,
    private val inventoryManager: InventoryManager,
    private val levelingSystem: LevelingSystem,
    private val assetManager: AssetManager,
    private val mapManager: MapManager,
    private val eventBus: EventBus
) {
    var playerX = startX
    var playerY = startY
    var playerTileX = startX.toInt()
    var playerTileY = startY.toInt()
    var currentHp = 100
    var maxHp = 100
    private var baseAttack = 1
    private var baseStrength = 1
    private var baseMagic = 1
    private var baseRanged = 1
    private var basePrayer = 1
    private var baseWoodcutting = 1
    private var baseMining = 1
    private var baseAgility = 1
    private var baseDefense = 1
    var speed = 2f
    private var monsterManager: MonsterManager? = null
    private val sprite = Sprite(assetManager.get("player/base/human_male.png", Texture::class.java))
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    var criticalHitTimer = 0f
    var isUnderAttackTimer = 0f
    private var isDead = false
    private var immuneTimer = 0f
    private val immuneDuration = 2f
    var cameraManager: CameraManager? = null

    init {
        sprite.setSize(tileSize, tileSize)
        updateStatsFromLevelingSystem()
    }

    fun setMonsterManager(monsterManager: MonsterManager) {
        this.monsterManager = monsterManager
    }

    fun getLevelingSystem(): LevelingSystem = levelingSystem

    fun isDead(): Boolean = isDead

    fun isImmune(): Boolean = immuneTimer > 0f

    private fun updateStatsFromLevelingSystem() {
        baseAttack = levelingSystem.getStat("attack")
        baseStrength = levelingSystem.getStat("strength")
        baseMagic = levelingSystem.getStat("magic")
        baseRanged = levelingSystem.getStat("ranged")
        basePrayer = levelingSystem.getStat("prayer")
        baseWoodcutting = levelingSystem.getStat("woodcutting")
        baseMining = levelingSystem.getStat("mining")
        baseAgility = levelingSystem.getStat("agility")
        baseDefense = levelingSystem.getStat("defense")
        maxHp = levelingSystem.getStat("HP")
        currentHp = minOf(currentHp, maxHp)
        Gdx.app.log("Player", "Updated base stats: HP=$maxHp, attack=$baseAttack, strength=$baseStrength, defense=$baseDefense")
    }

    fun getEffectiveAttack(): Int = baseAttack + inventoryManager.getEffectiveModifiers().getOrDefault("attack", 0)

    fun getEffectiveStrength(): Int = baseStrength + inventoryManager.getEffectiveModifiers().getOrDefault("strength", 0)

    fun getEffectiveMagic(): Int = baseMagic + inventoryManager.getEffectiveModifiers().getOrDefault("magic", 0)

    fun getEffectiveRanged(): Int = baseRanged + inventoryManager.getEffectiveModifiers().getOrDefault("ranged", 0)

    fun getEffectivePrayer(): Int = basePrayer + inventoryManager.getEffectiveModifiers().getOrDefault("prayer", 0)

    fun getEffectiveWoodcutting(): Int = baseWoodcutting + inventoryManager.getEffectiveModifiers().getOrDefault("woodcutting", 0)

    fun getEffectiveMining(): Int = baseMining + inventoryManager.getEffectiveModifiers().getOrDefault("mining", 0)

    fun getEffectiveAgility(): Int = baseAgility + inventoryManager.getEffectiveModifiers().getOrDefault("agility", 0)

    fun getEffectiveDefense(): Int = baseDefense + inventoryManager.getEffectiveModifiers().getOrDefault("defense", 0)

    fun update(delta: Float, movementManager: MovementManager) {
        if (isDead) return

        criticalHitTimer -= delta
        isUnderAttackTimer -= delta
        immuneTimer -= delta
        if (criticalHitTimer < 0f) criticalHitTimer = 0f
        if (isUnderAttackTimer < 0f) isUnderAttackTimer = 0f
        if (immuneTimer < 0f) immuneTimer = 0f

        updateStatsFromLevelingSystem()
        movementManager.updateContinuousDirection(this, delta)
    }

    fun render(tileSize: Float, camera: OrthographicCamera) {
        if (isDead) return

        batch.projectionMatrix = camera.combined
        batch.begin()
        sprite.setPosition(playerX * tileSize, playerY * tileSize)
        sprite.draw(batch)
        batch.end()

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        val hpPercentage = currentHp.toFloat() / maxHp
        val hpBarColor = when {
            hpPercentage > 0.5f -> Color.GREEN
            hpPercentage > 0.2f -> Color.YELLOW
            else -> Color.RED
        }
        val barWidth = 30f
        val barHeight = 5f
        val hpWidth = barWidth * hpPercentage
        val centerX = playerX * tileSize + (tileSize - barWidth) / 2f
        val barY = playerY * tileSize + tileSize + barHeight
        shapeRenderer.color = Color.BLACK
        shapeRenderer.rect(centerX, barY, barWidth, barHeight)
        shapeRenderer.color = hpBarColor
        shapeRenderer.rect(centerX, barY, hpWidth, barHeight)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeType.Line)
        if (isUnderAttackTimer > 0f) {
            shapeRenderer.color = Color.BLACK.cpy().apply { a = (MathUtils.sin(isUnderAttackTimer * 10f) + 1f) / 2f }
            shapeRenderer.rect(playerX * tileSize, playerY * tileSize, tileSize, tileSize)
        }
        if (criticalHitTimer > 0f) {
            shapeRenderer.color = Color.YELLOW.cpy().apply { a = (MathUtils.sin(criticalHitTimer * 15f) + 1f) / 2f }
            val pulseWidth = tileSize * 1.3f
            val pulseHeight = tileSize * 1.3f
            val pulseOffsetX = (pulseWidth - tileSize) / 2f
            val pulseOffsetY = (pulseHeight - tileSize) / 2f
            shapeRenderer.rect((playerX * tileSize) - pulseOffsetX, (playerY * tileSize) - pulseOffsetY, pulseWidth, pulseHeight)
        }
        shapeRenderer.end()
    }

    fun die() {
        isDead = true
        immuneTimer = immuneDuration
        Gdx.app.log("Player", "Player died")
    }

    fun respawn() {
        currentHp = maxHp
        isDead = false
        immuneTimer = immuneDuration
        playerX = (mapManager.mapTileWidth / 2f)
        playerY = (mapManager.mapTileHeight / 2f)
        playerTileX = playerX.toInt()
        playerTileY = playerY.toInt()
        Gdx.app.log("Player", "Player respawned at ($playerX, $playerY)")
    }

    fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
    }
}
