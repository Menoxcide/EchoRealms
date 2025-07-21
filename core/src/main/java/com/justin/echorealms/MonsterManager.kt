package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.math.Rectangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.math.sign

class MonsterManager(
    private val mapManager: MapManager,
    private val player: Player,
    val eventBus: EventBus,
    private val pathFinder: PathFinder,
    val floatingTextManager: FloatingTextManager,
    private val assetManager: AssetManager,
    private val cameraManager: CameraManager,
    private val mapManagerRef: MapManager
) {
    private val monsters = Array<Monster>()
    private val batch = SpriteBatch()
    private val json = Json()
    val aggroRange = 10
    private val despawnRange = 15
    private val shapeRenderer = ShapeRenderer()
    private val font = BitmapFont()
    private val criticalChance = 0.1f
    private var coarseWaypoint: Vector2? = null
    private val coarseRecalcTimer = 2f
    private var coarseTimer = coarseRecalcTimer
    private val attackRange = 5
    private val maxPathfindingPerFrame = 5
    private val lodDistance = 20

    init {
        font.color = Color.WHITE
        font.data.setScale(1f)
        eventBus.subscribe("playerMoved") { args ->
            coarseTimer = 0f
        }
    }

    fun getMonsters(): Array<Monster> {
        return monsters
    }

    fun loadMonsters() {
        val monstersDir = Gdx.files.internal("monster/")
        if (!monstersDir.exists() || !monstersDir.isDirectory) {
            Gdx.app.log("MonsterManager", "No monsters directory found")
            return
        }

        val texturePaths = mutableListOf<String>()
        monstersDir.list().forEach { categoryDir ->
            if (categoryDir.isDirectory) {
                categoryDir.list().forEach { file ->
                    if (file.extension() == "png") {
                        val texturePath = "monster/${categoryDir.name()}/${file.name()}"
                        texturePaths.add(texturePath)
                        assetManager.load(texturePath, Texture::class.java)
                    }
                }
            }
        }

        assetManager.finishLoading()

        monstersDir.list().forEach { categoryDir ->
            if (categoryDir.isDirectory) {
                categoryDir.list().forEach { file ->
                    if (file.extension() == "png") {
                        val statsFile = categoryDir.child(file.nameWithoutExtension() + ".json")
                        if (statsFile.exists()) {
                            val stats = json.fromJson(MonsterStats::class.java, statsFile.readString())
                            val texturePath = "monster/${categoryDir.name()}/${file.name()}"
                            val sprite = Sprite(assetManager.get(texturePath, Texture::class.java)).apply {
                                setSize(width * 1.333f, height * 1.333f)
                            }
                            var spawnX = Random.nextInt(mapManagerRef.mapTileWidth).toFloat()
                            var spawnY = Random.nextInt(mapManagerRef.mapTileHeight).toFloat()
                            while (!mapManagerRef.isWalkable(spawnX.toInt(), spawnY.toInt()) || isTileOccupied(spawnX.toInt(), spawnY.toInt())) {
                                spawnX = Random.nextInt(mapManagerRef.mapTileWidth).toFloat()
                                spawnY = Random.nextInt(mapManagerRef.mapTileHeight).toFloat()
                            }
                            monsters.add(Monster(sprite, spawnX, spawnY, stats, stats.hp, Array(), 0.5f, 0f, 0f, spawnX.toInt(), spawnY.toInt(), null))
                        }
                    }
                }
            }
        }
        Gdx.app.log("MonsterManager", "Loaded ${monsters.size} monsters")
    }

    fun update(delta: Float) {
        if (player.isDead()) return

        val camera = cameraManager.camera
        val zoom = camera.zoom
        val viewport = Rectangle(
            camera.position.x - (camera.viewportWidth * zoom) / 2,
            camera.position.y - (camera.viewportHeight * zoom) / 2,
            camera.viewportWidth * zoom,
            camera.viewportHeight * zoom
        )

        coarseTimer -= delta
        if (coarseTimer <= 0f) {
            coarseWaypoint = null
            coarseTimer = coarseRecalcTimer
        }

        val monstersToUpdate = Array<Monster>()
        monsters.forEach { monster ->
            val worldX = monster.x * mapManagerRef.tileSize
            val worldY = monster.y * mapManagerRef.tileSize
            if (viewport.contains(worldX, worldY)) {
                monstersToUpdate.add(monster)
                if (!monster.isInViewport) {
                    monster.isInViewport = true
                }
            } else if (monster.isInViewport) {
                monster.isInViewport = false
            }
        }

        var pathfindingCount = 0
        for (monster in monstersToUpdate) {
            val dx = player.playerX - monster.x
            val dy = player.playerY - monster.y
            val dist = max(abs(dx), abs(dy))
            monster.update(delta, pathFinder, player, this, mapManagerRef)
            monster.pathRecalcTimer -= delta
            monster.isAttackedTimer -= delta
            monster.criticalHitTimer -= delta
            monster.hitTimer -= delta
            if (monster.isAttackedTimer < 0f) monster.isAttackedTimer = 0f
            if (monster.hitTimer < 0f) monster.hitTimer = 0f
            monster.criticalHitTimer = monster.criticalHitTimer.coerceAtLeast(0f)
            monster.attackCooldown -= delta
            if (monster.attackCooldown < 0f) monster.attackCooldown = 0f

            if (dist <= aggroRange && monster.pathRecalcTimer <= 0f && pathfindingCount < maxPathfindingPerFrame) {
                if (dist <= lodDistance) {
                    val target = Vector2(player.playerTileX.toFloat() + 0.5f, player.playerTileY.toFloat() + 0.5f)
                    val path = pathFinder.findPath(monster.x.toInt(), monster.y.toInt(), target.x.toInt(), target.y.toInt(), this)
                    monster.currentPath.clear()
                    if (path.size > 0) {
                        monster.currentPath.addAll(path)
                        Gdx.app.log("MonsterManager", "Path set for ${monster.stats.name} to ($target)")
                    }
                    monster.pathRecalcTimer = 0.5f
                    pathfindingCount++
                } else {
                    val moveX = sign(dx) * min(delta * monster.stats.speed, abs(dx))
                    val moveY = sign(dy) * min(delta * monster.stats.speed, abs(dy))
                    val newX = monster.x + moveX
                    val newY = monster.y + moveY
                    if (mapManagerRef.isWalkable(newX.toInt(), newY.toInt()) && !isTileOccupied(newX.toInt(), newY.toInt())) {
                        monster.x = newX
                        monster.y = newY
                        Gdx.app.log("MonsterManager", "${monster.stats.name} moved to ($newX, $newY)")
                    }
                    monster.pathRecalcTimer = 1f
                }
            }

            if (monster == player.targetedMonster && dist <= attackRange && (dx == 0f || dy == 0f) && monster.attackCooldown <= 0f && !player.isImmune()) {
                val baseDamage = monster.stats.attack
                val isCritical = Random.nextFloat() < criticalChance
                val reducedDamage = (baseDamage * (1f - (player.defense / 100f))) * (if (isCritical) 2f else 1f)
                val finalDamage = (reducedDamage * (0.8f + Random.nextFloat() * 0.4f)).toInt().coerceAtLeast(0)
                player.currentHp = max(0, player.currentHp - finalDamage)
                player.criticalHitTimer = if (isCritical) 0.5f else 0f
                floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage", player.playerX, player.playerY, isCritical)
                player.isUnderAttackTimer = 1f
                monster.attackCooldown = 1f
                player.getLevelingSystem().addExperience("defense", monster.stats.exp / 2)
                if (player.currentHp <= 0) {
                    Gdx.app.log("MonsterManager", "${monster.stats.name} at (${monster.x}, ${monster.y}) killed the player")
                    player.die()
                }
            }

            val distFromSpawn = max(abs(monster.spawnX - monster.x.toInt()), abs(monster.spawnY - monster.y.toInt()))
            if (distFromSpawn > despawnRange) {
                monsters.removeValue(monster, true)
                Gdx.app.log("MonsterManager", "${monster.stats.name} despawned at (${monster.x}, ${monster.y})")
            }
        }
    }

    fun render(camera: OrthographicCamera, tileSize: Float) {
        if (player.isDead()) return

        val viewport = Rectangle(
            camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
            camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom
        )

        batch.projectionMatrix = camera.combined
        batch.begin()
        monsters.forEach { monster ->
            val worldX = monster.x * tileSize
            val worldY = monster.y * tileSize
            if (viewport.contains(worldX, worldY)) {
                monster.sprite.setPosition(worldX, worldY)
                monster.sprite.draw(batch)
            }
        }
        batch.end()

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        monsters.forEach { monster ->
            val worldX = monster.x * tileSize
            val worldY = monster.y * tileSize
            if (viewport.contains(worldX, worldY)) {
                batch.begin()
                font.draw(batch, monster.stats.name, worldX - 15f, (monster.y + 1.5f) * tileSize + 10f)
                batch.end()

                val hpPercentage = monster.currentHp.toFloat() / monster.stats.hp.toFloat()
                val hpBarColor = when {
                    hpPercentage > 0.5f -> Color.GREEN
                    hpPercentage > 0.2f -> Color.YELLOW
                    else -> Color.RED
                }
                val barWidth = 30f
                val barHeight = 5f
                val hpWidth = barWidth * hpPercentage
                val centerX = worldX + tileSize / 2f - barWidth / 2f
                shapeRenderer.color = Color.BLACK
                shapeRenderer.rect(centerX, (monster.y + 1) * tileSize + barHeight, barWidth, barHeight)
                shapeRenderer.color = hpBarColor
                shapeRenderer.rect(centerX, (monster.y + 1) * tileSize + barHeight, hpWidth, barHeight)
            }
        }
        shapeRenderer.end()

        shapeRenderer.begin(ShapeType.Line)
        monsters.forEach { monster ->
            val worldX = monster.x * tileSize
            val worldY = monster.y * tileSize
            if (viewport.contains(worldX, worldY)) {
                if (monster.isAttackedTimer > 0f) {
                    shapeRenderer.color = Color(1f, 0f, 0f, (MathUtils.sin(monster.isAttackedTimer * 10f) + 1f) / 2f)
                    shapeRenderer.rect(worldX, worldY, tileSize, tileSize)
                }
                if (monster.criticalHitTimer > 0f) {
                    shapeRenderer.color = Color.YELLOW.cpy().apply { a = (MathUtils.sin(monster.criticalHitTimer * 15f) + 1f) / 2f }
                    val pulseWidth = monster.sprite.width * 1.3f
                    val pulseHeight = monster.sprite.height * 1.3f
                    val pulseOffsetX = (pulseWidth - monster.sprite.width) / 2f
                    val pulseOffsetY = (pulseHeight - monster.sprite.height) / 2f
                    shapeRenderer.rect(worldX - pulseOffsetX, worldY - pulseOffsetY, pulseWidth, pulseHeight)
                }
                if (monster == player.targetedMonster && monster.hitTimer > 0f) {
                    shapeRenderer.color = Color.RED.cpy().apply { a = (MathUtils.sin(monster.hitTimer * 15f) + 1f) / 2f }
                    shapeRenderer.rect(worldX, worldY, tileSize, tileSize)
                }
            }
        }
        shapeRenderer.end()
    }

    fun isTileOccupied(x: Int, y: Int): Boolean {
        return monsters.any { it.x.toInt() == x && it.y.toInt() == y } || (player.playerTileX == x && player.playerTileY == y && !player.isDead())
    }

    fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }
}

data class Monster(
    val sprite: Sprite,
    var x: Float,
    var y: Float,
    val stats: MonsterStats,
    var currentHp: Int,
    val currentPath: Array<Vector2>,
    var pathRecalcTimer: Float,
    var isAttackedTimer: Float,
    var attackCooldown: Float,
    val spawnX: Int,
    val spawnY: Int,
    var targetedPlayer: Player? = null,
    var criticalHitTimer: Float = 0f,
    var hitTimer: Float = 0f,
    var isInViewport: Boolean = false
) {
    fun update(delta: Float, pathFinder: PathFinder, player: Player, monsterManager: MonsterManager, mapManager: MapManager) {
        if (player.isDead()) return

        if (currentPath.size > 0) {
            val target = currentPath[0]
            val mdx = target.x - x
            val mdy = target.y - y
            val dist = max(abs(mdx), abs(mdy))
            if (dist > 0) {
                val moveDist = delta * stats.speed
                val moveX = sign(mdx) * min(moveDist, abs(mdx))
                val moveY = sign(mdy) * min(moveDist, abs(mdy))
                val newX = x + moveX
                val newY = y + moveY
                if (mapManager.isWalkable(newX.toInt(), newY.toInt()) && !monsterManager.isTileOccupied(newX.toInt(), newY.toInt())) {
                    x = newX
                    y = newY
                    Gdx.app.log("Monster", "${stats.name} moved to ($newX, $newY)")
                } else {
                    currentPath.clear()
                    Gdx.app.log("Monster", "${stats.name} path blocked at ($newX, $newY), clearing path")
                }
                if (abs(x - target.x) <= moveDist && abs(y - target.y) <= moveDist) {
                    x = target.x
                    y = target.y
                    currentPath.removeIndex(0)
                    Gdx.app.log("Monster", "${stats.name} reached waypoint ($target), remaining path: ${currentPath.size}")
                }
            } else {
                currentPath.removeIndex(0)
                Gdx.app.log("Monster", "${stats.name} reached waypoint ($target), remaining path: ${currentPath.size}")
            }
        }
    }

    fun canAttack(playerX: Float, playerY: Float): Boolean {
        val dx = abs(x - playerX)
        val dy = abs(y - playerY)
        return (dx <= 5f && dy == 0f) || (dx == 0f && dy <= 5f)
    }

    fun isDead(): Boolean = currentHp <= 0
}

data class MonsterStats(
    val name: String = "Unknown",
    val speed: Float = 1f,
    val attack: Int = 5,
    val level: Int = 1,
    val exp: Int = 10,
    val loot: Array<String> = Array(arrayOf("gold", "potion")),
    val hp: Int = 50
)
