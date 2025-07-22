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
    var attack = 1
    var strength = 1
    var magic = 1
    var ranged = 1
    var prayer = 1
    var woodcutting = 1
    var mining = 1
    var agility = 1
    var defense = 1
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
        sprite.setSize(tileSize, tileSize) // Set sprite size to match tile size without scaling
    }

    fun setMonsterManager(monsterManager: MonsterManager) {
        this.monsterManager = monsterManager
    }

    fun getLevelingSystem(): LevelingSystem = levelingSystem

    fun isDead(): Boolean = isDead

    fun isImmune(): Boolean = immuneTimer > 0f

    fun update(delta: Float, movementManager: MovementManager) {
        if (isDead) return

        criticalHitTimer -= delta
        isUnderAttackTimer -= delta
        immuneTimer -= delta
        if (criticalHitTimer < 0f) criticalHitTimer = 0f
        if (isUnderAttackTimer < 0f) isUnderAttackTimer = 0f
        if (immuneTimer < 0f) immuneTimer = 0f

        // Update position based on movementManager's path
        movementManager.updateContinuousDirection(this, delta)
    }

    fun render(tileSize: Float, camera: OrthographicCamera) {
        if (isDead) return

        batch.projectionMatrix = camera.combined
        batch.begin()
        sprite.setPosition(playerX * tileSize, playerY * tileSize) // Align sprite with tile grid
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
        val centerX = playerX * tileSize + (tileSize - barWidth) / 2f // Center HP bar above sprite
        val barY = playerY * tileSize + tileSize + barHeight // Position HP bar above sprite
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
