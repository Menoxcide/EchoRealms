package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.assets.AssetManager
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

class Player(
    val tileSize: Float,
    initialX: Float,
    initialY: Float,
    private val floatingTextManager: FloatingTextManager,
    private var monsterManager: MonsterManager?,
    private val inventoryManager: InventoryManager,
    private val levelingSystem: LevelingSystem,
    private val assetManager: AssetManager,
    private val mapManager: MapManager
) {
    private val texture: Texture = if (assetManager.isLoaded("player/base/human_male.png")) {
        assetManager.get("player/base/human_male.png", Texture::class.java)
    } else {
        Gdx.app.error("Player", "Failed to load player/base/human_male.png, using default")
        Texture(Gdx.files.internal("badlogic.jpg"))
    }
    val sprite = Sprite(texture).apply {
        setSize(width * 1.333f, height * 1.333f)
    }
    private val batch = SpriteBatch()
    var playerTileX = initialX.toInt()
    var playerTileY = initialY.toInt()
    var playerX = initialX
    var playerY = initialY
    private var targetX = initialX
    private var targetY = initialY
    var moveProgress = 0f
    private val moveSpeed = 4f // 4 tiles per second
    private var walkTime = 0f
    private val bobAmplitude = 2f
    private val bobFrequency = 8f
    var exp = 0
    var attackCooldown = 0f
    var isUnderAttackTimer = 0f
    private var immunityTimer = 10f
    var criticalHitTimer = 0f
    val strength = levelingSystem.getStat("strength")
    val attack = levelingSystem.getStat("attack")
    var currentHp = levelingSystem.getStat("HP")
    val maxHp = currentHp
    val defense = levelingSystem.getStat("defense")
    val magic = levelingSystem.getStat("magic")
    val ranged = levelingSystem.getStat("ranged")
    val prayer = levelingSystem.getStat("prayer")
    val woodcutting = levelingSystem.getStat("woodcutting")
    val mining = levelingSystem.getStat("mining")
    val agility = levelingSystem.getStat("agility")
    var targetedMonster: Monster? = null
    var camera: OrthographicCamera? = null
    private var isDead = false
    private val initialTileX = initialX.toInt()
    private val initialTileY = initialY.toInt()
    private var headMessage: String? = null
    private var messageTimer = 0f

    init {
        levelingSystem.addExperience("HP", 0)
    }

    fun setMonsterManager(manager: MonsterManager) {
        monsterManager = manager
    }

    fun getInventoryManager(): InventoryManager = inventoryManager
    fun getLevelingSystem(): LevelingSystem = levelingSystem

    fun isDead(): Boolean = isDead

    fun isImmune(): Boolean = immunityTimer > 0f

    fun setHeadMessage(message: String, duration: Float = 3f) {
        headMessage = message
        messageTimer = duration
    }

    fun canAttack(monsterX: Float, monsterY: Float): Boolean {
        val dx = abs(playerTileX - monsterX.toInt())
        val dy = abs(playerTileY - monsterY.toInt())
        return (dx <= 5 && dy == 0) || (dx == 0 && dy <= 5) && !isImmune()
    }

    fun setTargetTile(x: Int, y: Int) {
        targetX = x.toFloat()
        targetY = y.toFloat()
        moveProgress = 0f
        Gdx.app.log("Player", "Set target tile to ($x, $y)")
    }

    fun update(delta: Float, movementManager: MovementManager) {
        if (isDead) return

        val normalizedDelta = min(delta, 0.1f)
        immunityTimer = (immunityTimer - normalizedDelta).coerceAtLeast(0f)
        messageTimer = (messageTimer - normalizedDelta).coerceAtLeast(0f)
        criticalHitTimer = (criticalHitTimer - normalizedDelta).coerceAtLeast(0f)
        if (messageTimer <= 0f) headMessage = null

        if (targetedMonster != null && !canAttack(targetedMonster!!.x, targetedMonster!!.y)) {
            targetedMonster = null
        }

        if (isUnderAttackTimer > 0f) {
            isUnderAttackTimer = (isUnderAttackTimer - normalizedDelta).coerceAtLeast(0f)
            movementManager.clearPath()
            (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f)
            return
        }

        // Smooth movement along path
        if (movementManager.getCurrentPath().size > 0 && moveProgress == 0f) {
            val nextTile = movementManager.getCurrentPath()[0]
            if (mapManager.isWalkable(nextTile.x.toInt(), nextTile.y.toInt()) && !monsterManager!!.isTileOccupied(nextTile.x.toInt(), nextTile.y.toInt())) {
                setTargetTile(nextTile.x.toInt(), nextTile.y.toInt())
            } else {
                movementManager.clearPath()
            }
        }

        // Interpolate movement
        if (moveProgress < 1f && (playerX != targetX || playerY != targetY)) {
            moveProgress += normalizedDelta * moveSpeed
            if (moveProgress >= 1f) {
                moveProgress = 1f
                playerX = targetX
                playerY = targetY
                playerTileX = targetX.toInt()
                playerTileY = targetY.toInt()
                if (movementManager.getCurrentPath().size > 0) {
                    movementManager.getCurrentPath().removeIndex(0)
                }
                moveProgress = 0f // Reset for next tile
            } else {
                playerX = MathUtils.lerp(playerX, targetX, moveProgress)
                playerY = MathUtils.lerp(playerY, targetY, moveProgress)
            }
            walkTime += normalizedDelta * bobFrequency
            Gdx.app.log("Player", "Interpolating to ($playerX, $playerY), progress: $moveProgress")
        }

        attackCooldown = (attackCooldown - normalizedDelta).coerceAtLeast(0f)
    }

    fun render(tileSize: Float, camera: OrthographicCamera) {
        if (isDead) return
        this.camera = camera
        batch.projectionMatrix = camera.combined
        batch.begin()
        val offsetX = tileSize / 2f - sprite.width / 2f
        val offsetY = tileSize / 2f - sprite.height / 2f
        sprite.setPosition(playerX * tileSize + offsetX, playerY * tileSize + offsetY)
        sprite.draw(batch)
        if (headMessage != null) {
            val font = BitmapFont().apply { color = Color.WHITE; data.setScale(1.5f) }
            font.draw(batch, headMessage!!, playerX * tileSize + offsetX, (playerY + 1) * tileSize + offsetY + 50f)
            font.dispose()
        }
        batch.end()

        val hpPercentage = currentHp.toFloat() / maxHp.toFloat()
        val hpBarColor = when {
            hpPercentage > 0.5f -> Color.GREEN
            hpPercentage > 0.2f -> Color.YELLOW
            else -> Color.RED
        }
        val barWidth = 30f
        val barHeight = 5f
        val hpWidth = barWidth * hpPercentage
        val centerX = playerX * tileSize + tileSize / 2f - barWidth / 2f
        val renderer = ShapeRenderer().apply {
            projectionMatrix = camera.combined
            begin(ShapeType.Filled)
            color = Color.BLACK
            rect(centerX, (playerY + 1) * tileSize + barHeight, barWidth, barHeight)
            color = hpBarColor
            rect(centerX, (playerY + 1) * tileSize + barHeight, hpWidth, barHeight)
            end()
            dispose()
        }

        if (isImmune()) {
            val alpha = (MathUtils.sin(immunityTimer * 10f) + 1f) / 2f
            val renderer = ShapeRenderer().apply {
                projectionMatrix = camera.combined
                begin(ShapeType.Line)
                color = Color.WHITE.cpy().lerp(Color.BLUE, alpha)
                val pulseWidth = sprite.width * (1 + 0.2f * alpha)
                val pulseHeight = sprite.height * (1 + 0.2f * alpha)
                val pulseOffsetX = (pulseWidth - sprite.width) / 2f
                val pulseOffsetY = (pulseHeight - sprite.height) / 2f
                rect(playerX * tileSize - pulseOffsetX, playerY * tileSize - pulseOffsetY, pulseWidth, pulseHeight)
                end()
                dispose()
            }
        }

        if (isUnderAttackTimer > 0f && !isDead) {
            val renderer = ShapeRenderer().apply {
                projectionMatrix = camera.combined
                begin(ShapeType.Line)
                color = Color(0f, 0f, 0f, (MathUtils.sin(isUnderAttackTimer * 10f) + 1f) / 2f)
                rect(playerX * tileSize, playerY * tileSize, tileSize, tileSize)
                end()
                dispose()
            }
        }

        if (criticalHitTimer > 0f) {
            val renderer = ShapeRenderer().apply {
                projectionMatrix = camera.combined
                begin(ShapeType.Line)
                color = Color.YELLOW.cpy().apply { a = (MathUtils.sin(criticalHitTimer * 15f) + 1f) / 2f }
                val pulseWidth = sprite.width * 1.3f
                val pulseHeight = sprite.height * 1.3f
                val pulseOffsetX = (pulseWidth - sprite.width) / 2f
                val pulseOffsetY = (pulseHeight - sprite.height) / 2f
                rect(playerX * tileSize - pulseOffsetX, playerY * tileSize - pulseOffsetY, pulseWidth, pulseHeight)
                end()
                dispose()
            }
        }
    }

    fun die() {
        Gdx.app.log("Player", "Player died at position ($playerTileX, $playerTileY)")
        isDead = true
        isUnderAttackTimer = 0f
        targetedMonster = null
        inventoryManager.getItems().forEach { item ->
            inventoryManager.removeItem(item)
            Gdx.app.log("Player", "Dropped $item at $playerTileX, $playerTileY")
        }
        (Gdx.app.applicationListener as? MyGame)?.clearPlayerPath()
    }

    fun respawn() {
        isDead = false
        currentHp = maxHp
        playerTileX = initialTileX
        playerTileY = initialTileY
        playerX = initialTileX.toFloat()
        playerY = initialTileY.toFloat()
        targetX = playerX
        targetY = playerY
        moveProgress = 0f
        targetedMonster = null
        immunityTimer = 10f
        (Gdx.app.applicationListener as? MyGame)?.clearPlayerPath()
        Gdx.app.log("Player", "Player respawned at ($playerTileX, $playerTileY)")
    }

    fun dispose() {
        batch.dispose()
    }
}
