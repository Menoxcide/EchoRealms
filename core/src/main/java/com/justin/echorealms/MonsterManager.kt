package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
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
    val positionTolerance = 0.2f
    private val targetProximityTolerance = 1.5f
    internal val clusterSize = 16
    private val clusters = mutableMapOf<Pair<Int, Int>, Array<Monster>>()
    private var globalAttackCooldown = 0f
    private val globalAttackTick = 1f

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

    fun getMonstersInCluster(clusterX: Int, clusterY: Int): Array<Monster> {
        return clusters[Pair(clusterX, clusterY)] ?: Array()
    }

    fun getMonstersInClusters(clusterCoords: List<Pair<Int, Int>>): Array<Monster> {
        val result = Array<Monster>()
        clusterCoords.forEach { coord ->
            clusters[coord]?.let { result.addAll(it) }
        }
        return result
    }

    private fun getClusterForPosition(x: Float, y: Float): Pair<Int, Int> {
        return Pair((x / clusterSize).toInt(), (y / clusterSize).toInt())
    }

    private fun addMonsterToCluster(monster: Monster) {
        val (clusterX, clusterY) = getClusterForPosition(monster.x, monster.y)
        clusters.getOrPut(Pair(clusterX, clusterY)) { Array() }.add(monster)
        Gdx.app.log("MonsterManager", "Added ${monster.stats.name} to cluster ($clusterX, $clusterY)")
    }

    private fun updateMonsterCluster(monster: Monster, oldX: Float, oldY: Float) {
        val oldCluster = getClusterForPosition(oldX, oldY)
        val newCluster = getClusterForPosition(monster.x, monster.y)
        if (oldCluster != newCluster) {
            clusters[oldCluster]?.removeValue(monster, true)
            clusters.getOrPut(newCluster) { Array() }.add(monster)
            Gdx.app.log("MonsterManager", "Moved ${monster.stats.name} from cluster $oldCluster to $newCluster")
        }
    }

    fun loadMonsters() {
        val monstersDir = Gdx.files.internal("monster/")
        if (!monstersDir.exists() || !monstersDir.isDirectory) {
            Gdx.app.log("MonsterManager", "No monsters directory found")
            return
        }

        val texturePaths = mutableListOf<String>()
        for (i in 0 until monstersDir.list().size) {
            val categoryDir = monstersDir.list()[i]
            if (categoryDir.isDirectory) {
                for (j in 0 until categoryDir.list().size) {
                    val file = categoryDir.list()[j]
                    if (file.extension() == "png") {
                        val texturePath = "monster/${categoryDir.name()}/${file.name()}"
                        texturePaths.add(texturePath)
                        assetManager.load(texturePath, Texture::class.java)
                    }
                }
            }
        }

        assetManager.finishLoading()

        val clusterCountX = (mapManagerRef.mapTileWidth + clusterSize - 1) / clusterSize
        val clusterCountY = (mapManagerRef.mapTileHeight + clusterSize - 1) / clusterSize
        for (i in 0 until monstersDir.list().size) {
            val categoryDir = monstersDir.list()[i]
            if (categoryDir.isDirectory) {
                for (j in 0 until categoryDir.list().size) {
                    val file = categoryDir.list()[j]
                    if (file.extension() == "png") {
                        val statsFile = categoryDir.child(file.nameWithoutExtension() + ".json")
                        if (statsFile.exists()) {
                            val stats = json.fromJson(MonsterStats::class.java, statsFile.readString())
                            val texturePath = "monster/${categoryDir.name()}/${file.name()}"
                            val texture = assetManager.get(texturePath, Texture::class.java)
                            repeat(3) {
                                val sprite = Sprite(texture).apply {
                                    setSize(width * 1.333f, height * 1.333f)
                                }
                                val clusterX = Random.nextInt(clusterCountX)
                                val clusterY = Random.nextInt(clusterCountY)
                                var spawnX: Float
                                var spawnY: Float
                                var attempts = 0
                                val maxAttempts = 50
                                do {
                                    spawnX = (clusterX * clusterSize + Random.nextInt(clusterSize)).toFloat()
                                    spawnY = (clusterY * clusterSize + Random.nextInt(clusterSize)).toFloat()
                                    attempts++
                                    if (attempts >= maxAttempts) {
                                        Gdx.app.log("MonsterManager", "Failed to find valid spawn for ${stats.name} in cluster ($clusterX, $clusterY) after $maxAttempts attempts")
                                        return@repeat
                                    }
                                } while (spawnX >= mapManagerRef.mapTileWidth || spawnY >= mapManagerRef.mapTileHeight ||
                                    !mapManagerRef.isWalkable(spawnX.toInt(), spawnY.toInt()) ||
                                    isTileOccupied(spawnX.toInt(), spawnY.toInt(), null, null, monsters))
                                val monster = Monster(sprite, spawnX, spawnY, stats, stats.hp, Array<Vector2>(), 1.0f, 0f, 0f, spawnX.toInt(), spawnY.toInt(), null)
                                monsters.add(monster)
                                addMonsterToCluster(monster)
                                Gdx.app.log("MonsterManager", "Spawned ${stats.name} at ($spawnX, $spawnY) in cluster ($clusterX, $clusterY)")
                            }
                        }
                    }
                }
            }
        }
        Gdx.app.log("MonsterManager", "Loaded ${monsters.size} monsters across ${clusters.size} clusters")
    }

    fun update(delta: Float) {
        if (player.isDead()) return

        globalAttackCooldown -= delta
        if (globalAttackCooldown < 0f) globalAttackCooldown = 0f

        val camera = cameraManager.camera
        val zoom = camera.zoom
        val viewport = Rectangle(
            camera.position.x - (camera.viewportWidth * zoom) / 2,
            camera.position.y - (camera.viewportHeight * zoom) / 2,
            camera.viewportWidth * zoom,
            camera.viewportHeight * zoom
        )

        val minClusterX = max(0, ((viewport.x / mapManagerRef.tileSize) / clusterSize).toInt())
        val maxClusterX = min((mapManagerRef.mapTileWidth / clusterSize).toInt(), ((viewport.x + viewport.width) / mapManagerRef.tileSize / clusterSize).toInt())
        val minClusterY = max(0, ((viewport.y / mapManagerRef.tileSize) / clusterSize).toInt())
        val maxClusterY = min((mapManagerRef.mapTileHeight / clusterSize).toInt(), ((viewport.y + viewport.height) / mapManagerRef.tileSize / clusterSize).toInt())
        val activeClusters = mutableListOf<Pair<Int, Int>>()
        for (x in minClusterX..maxClusterX) {
            for (y in minClusterY..maxClusterY) {
                activeClusters.add(Pair(x, y))
            }
        }
        Gdx.app.log("MonsterManager", "Processing ${activeClusters.size} clusters: $activeClusters")

        coarseTimer -= delta
        if (coarseTimer <= 0f) {
            coarseWaypoint = null
            coarseTimer = coarseRecalcTimer
        }

        val monsterSnapshot = getMonstersInClusters(activeClusters)

        val monstersToUpdate = mutableListOf<Monster>()
        for (monster in monsterSnapshot) {
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

        val monstersToDespawn = mutableListOf<Monster>()
        var pathfindingCount = 0
        for (monster in monstersToUpdate) {
            val oldX = monster.x
            val oldY = monster.y
            val dx = player.playerX - monster.x
            val dy = player.playerY - monster.y
            val dist = max(abs(dx), abs(dy))
            monster.update(delta, pathFinder, player, this, mapManagerRef, monsterSnapshot)
            updateMonsterCluster(monster, oldX, oldY)
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
                    if (monster.currentPath.size == 0 || !isPathValid(monster, target.x.toInt(), target.y.toInt(), monsterSnapshot) || hasPlayerMovedSignificantly(monster, target)) {
                        val path = pathFinder.findPath(monster.x.toInt(), monster.y.toInt(), target.x.toInt(), target.y.toInt(), this, monsterSnapshot)
                        monster.currentPath.clear()
                        if (path.size > 0) {
                            val filteredPath = Array<Vector2>()
                            for (k in 0 until path.size) {
                                val point = path[k]
                                if (!(point.x.toInt() == monster.x.toInt() && point.y.toInt() == monster.y.toInt()) &&
                                    !(monster.currentPath.contains(point) && point != path[path.size - 1]) &&
                                    !(dist <= attackRange && point != path[path.size - 1] && !(abs(point.x - target.x) <= attackRange && point.y == target.y || abs(point.y - target.y) <= attackRange && point.x == target.x))
                                ) {
                                    filteredPath.add(point)
                                }
                            }
                            monster.currentPath.addAll(filteredPath)
                            Gdx.app.log("MonsterManager", "Path set for ${monster.stats.name} to ($target): ${filteredPath.joinToString()}")
                            monster.pathRecalcTimer = if (dist <= attackRange) 2.0f else 1.0f
                        } else {
                            Gdx.app.log("MonsterManager", "No valid path found for ${monster.stats.name} to ($target)")
                            monster.pathRecalcTimer = 2.0f
                        }
                        pathfindingCount++
                    }
                } else {
                    val moveX = sign(dx) * min(delta * monster.stats.speed, abs(dx))
                    val moveY = sign(dy) * min(delta * monster.stats.speed, abs(dy))
                    val newX = monster.x + moveX
                    val newY = monster.y + moveY
                    if (mapManager.isWalkable(newX.toInt(), newY.toInt()) && !isTileOccupied(newX.toInt(), newY.toInt(), player, monster, monsterSnapshot)) {
                        monster.x = newX
                        monster.y = newY
                        Gdx.app.log("MonsterManager", "${monster.stats.name} moved to ($newX, $newY)")
                    }
                    monster.pathRecalcTimer = 1f
                }
            }

            if (dist <= attackRange && (dx == 0f || dy == 0f) && globalAttackCooldown <= 0f && !player.isImmune() && !monster.isDead()) {
                val baseDamage = monster.stats.attack
                val isCritical = Random.nextFloat() < criticalChance
                val reducedDamage = (baseDamage * (1f - (player.getEffectiveDefense() / 100f))) * (if (isCritical) 2f else 1f)
                val finalDamage = (reducedDamage * (0.8f + Random.nextFloat() * 0.4f)).toInt().coerceAtLeast(0)
                player.currentHp = max(0, player.currentHp - finalDamage)
                player.criticalHitTimer = if (isCritical) 0.5f else 0f
                floatingTextManager.addText(if (isCritical) "-$finalDamage (CRITICAL!)" else "-$finalDamage", player.playerX, player.playerY, isCritical)
                player.isUnderAttackTimer = 1f
                globalAttackCooldown = globalAttackTick
                player.getLevelingSystem().addExperience("defense", monster.stats.exp / 2)
                Gdx.app.log("MonsterManager", "${monster.stats.name} dealt $finalDamage${if (isCritical) " (CRITICAL!)" else ""} damage to player")
                if (player.currentHp <= 0) {
                    Gdx.app.log("MonsterManager", "${monster.stats.name} at (${monster.x}, ${monster.y}) killed the player")
                    player.die()
                }
            }

            val distFromSpawn = max(abs(monster.spawnX - monster.x.toInt()), abs(monster.spawnY - monster.y.toInt()))
            if (distFromSpawn > despawnRange) {
                monstersToDespawn.add(monster)
            }
        }

        for (monster in monstersToDespawn) {
            val cluster = getClusterForPosition(monster.x, monster.y)
            clusters[cluster]?.removeValue(monster, true)
            monsters.removeValue(monster, true)
            Gdx.app.log("MonsterManager", "${monster.stats.name} despawned at (${monster.x}, ${monster.y})")
        }
    }

    private fun isPathValid(monster: Monster, targetX: Int, targetY: Int, monsterSnapshot: Array<Monster>): Boolean {
        if (monster.currentPath.size == 0) return false
        val lastWaypoint = monster.currentPath[monster.currentPath.size - 1]
        val distanceToTarget = max(abs(lastWaypoint.x.toInt() - targetX), abs(lastWaypoint.y.toInt() - targetY))
        val isValid = (distanceToTarget <= targetProximityTolerance ||
            (abs(lastWaypoint.x - targetX) <= attackRange && lastWaypoint.y.toInt() == targetY) ||
            (abs(lastWaypoint.y - targetY) <= attackRange && lastWaypoint.x.toInt() == targetX)) &&
            mapManager.isWalkable(lastWaypoint.x.toInt(), lastWaypoint.y.toInt()) &&
            !isTileOccupied(lastWaypoint.x.toInt(), lastWaypoint.y.toInt(), player, monster, monsterSnapshot)
        if (!isValid) {
            Gdx.app.log("MonsterManager", "Path invalid for ${monster.stats.name}: target=($targetX, $targetY), lastWaypoint=$lastWaypoint, distance=$distanceToTarget")
        }
        return isValid
    }

    private fun hasPlayerMovedSignificantly(monster: Monster, target: Vector2): Boolean {
        val lastTarget = if (monster.currentPath.size > 0) monster.currentPath[monster.currentPath.size - 1] else null
        if (lastTarget == null) return true
        val distance = max(abs(target.x - lastTarget.x), abs(target.y - lastTarget.y))
        val significantMove = distance > targetProximityTolerance
        if (significantMove) {
            Gdx.app.log("MonsterManager", "Player moved significantly for ${monster.stats.name}: from $lastTarget to $target")
        }
        return significantMove
    }

    fun isTileOccupied(x: Int, y: Int, excludePlayer: Player? = null, excludeMonster: Monster? = null, monsterSnapshot: Array<Monster>): Boolean {
        for (monster in monsterSnapshot) {
            if (monster != excludeMonster && monster.x.toInt() == x && monster.y.toInt() == y) {
                Gdx.app.log("MonsterManager", "Tile ($x, $y) occupied by monster ${monster.stats.name}")
                return true
            }
        }
        val isOccupiedByPlayer = excludePlayer == null && player.playerTileX == x && player.playerTileY == y && !player.isDead()
        if (isOccupiedByPlayer) {
            Gdx.app.log("MonsterManager", "Tile ($x, $y) occupied by player")
        }
        return isOccupiedByPlayer
    }

    fun render(camera: OrthographicCamera, tileSize: Float) {
        if (player.isDead()) return

        val viewport = Rectangle(
            camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
            camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom
        )

        val minClusterX = max(0, ((viewport.x / tileSize) / clusterSize).toInt())
        val maxClusterX = min((mapManagerRef.mapTileWidth / clusterSize).toInt(), ((viewport.x + viewport.width) / tileSize / clusterSize).toInt())
        val minClusterY = max(0, ((viewport.y / tileSize) / clusterSize).toInt())
        val maxClusterY = min((mapManagerRef.mapTileHeight / clusterSize).toInt(), ((viewport.y + viewport.height) / tileSize / clusterSize).toInt())
        val activeClusters = mutableListOf<Pair<Int, Int>>()
        for (x in minClusterX..maxClusterX) {
            for (y in minClusterY..maxClusterY) {
                activeClusters.add(Pair(x, y))
            }
        }

        batch.projectionMatrix = camera.combined
        batch.begin()
        for (monster in getMonstersInClusters(activeClusters)) {
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
        for (monster in getMonstersInClusters(activeClusters)) {
            val worldX = monster.x * tileSize
            val worldY = monster.y * tileSize
            if (viewport.contains(worldX, worldY)) {
                batch.begin()
                val filteredName = monster.stats.name.replace(Regex("\\s*\\((?i)(old|new)\\)", RegexOption.IGNORE_CASE), "").trim()
                val layout = GlyphLayout(font, filteredName)
                val nameX = worldX + (monster.sprite.width - layout.width) / 2f
                val nameY = worldY + monster.sprite.height + layout.height + 15f
                font.draw(batch, layout, nameX, nameY)
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
                val centerX = worldX + (monster.sprite.width - barWidth) / 2f
                val barY = worldY + monster.sprite.height + barHeight + 5f
                shapeRenderer.color = Color.BLACK
                shapeRenderer.rect(centerX, barY, barWidth, barHeight)
                shapeRenderer.color = hpBarColor
                shapeRenderer.rect(centerX, barY, hpWidth, barHeight)
                Gdx.app.log("MonsterManager", "Rendered ${monster.stats.name} (filtered: $filteredName) HP bar at ($centerX, $barY), name at ($nameX, $nameY)")
            }
        }
        shapeRenderer.end()

        shapeRenderer.begin(ShapeType.Line)
        for (monster in getMonstersInClusters(activeClusters)) {
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
    fun update(delta: Float, pathFinder: PathFinder, player: Player, monsterManager: MonsterManager, mapManager: MapManager, monsterSnapshot: Array<Monster>) {
        if (player.isDead()) return

        if (currentPath.size > 0) {
            val target = currentPath[0]
            val targetTileX = target.x.toInt()
            val targetTileY = target.y.toInt()
            val mdx = target.x - x
            val mdy = target.y - y
            val dist = max(abs(mdx), abs(mdy))
            Gdx.app.log("Monster", "${stats.name} moving towards ($targetTileX, $targetTileY) with mdx=$mdx, mdy=$mdy, dist=$dist")
            if (dist > monsterManager.positionTolerance) {
                val moveDist = delta * stats.speed
                val moveX = sign(mdx) * min(moveDist, abs(mdx))
                val moveY = sign(mdy) * min(moveDist, abs(mdy))
                val newX = x + moveX
                val newY = y + moveY

                if (mapManager.isWalkable(targetTileX, targetTileY) && !monsterManager.isTileOccupied(targetTileX, targetTileY, player, this, monsterSnapshot)) {
                    x = newX
                    y = newY
                    Gdx.app.log("Monster", "${stats.name} moved to ($newX, $newY)")
                } else {
                    Gdx.app.log("Monster", "${stats.name} path blocked at target tile ($targetTileX, $targetTileY), clearing path")
                    currentPath.clear()
                    return
                }

                if (abs(x - target.x) <= monsterManager.positionTolerance && abs(y - target.y) <= monsterManager.positionTolerance) {
                    x = target.x
                    y = target.y
                    currentPath.removeIndex(0)
                    Gdx.app.log("Monster", "${stats.name} reached waypoint ($target), remaining path: ${currentPath.size}")
                }
            } else {
                x = target.x
                y = target.y
                currentPath.removeIndex(0)
                Gdx.app.log("Monster", "${stats.name} reached waypoint ($target), remaining path: ${currentPath.size}")
            }

            if (currentPath.size == 0) {
                Gdx.app.log("Monster", "${stats.name} reached final waypoint, clearing path")
                currentPath.clear()
                if (canAttack(player.playerX, player.playerY)) {
                    pathRecalcTimer = 2.0f
                }
            }
        }
    }

    fun canAttack(playerX: Float, playerY: Float): Boolean {
        val dx = abs(this.x - playerX).toFloat()
        val dy = abs(this.y - playerY).toFloat()
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
