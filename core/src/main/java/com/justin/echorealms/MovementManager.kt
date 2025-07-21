// core/src/main/java/com/justin/echorealms/MovementManager.kt
package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import kotlin.math.abs
import kotlin.math.sign

class MovementManager(
    private val mapManager: MapManager,
    private val pathFinder: PathFinder,
    private val monsterManager: MonsterManager
) {
    private val currentPath = Array<Vector2>()
    private var lastDx = 0
    private var lastDy = 0
    private var targetTileX = 0
    private var targetTileY = 0

    fun getCurrentPath(): Array<Vector2> {
        return currentPath
    }

    fun clearPath() {
        currentPath.clear()
        lastDx = 0
        lastDy = 0
        targetTileX = 0
        targetTileY = 0
        Gdx.app.log("MovementManager", "Path cleared")
    }

    fun moveToTile(entity: Any, tileX: Int, tileY: Int, delta: Float): Boolean {
        if (entity !is Player && entity !is Monster) {
            Gdx.app.log("MovementManager", "Invalid entity type for movement")
            return false
        }

        val currentX = if (entity is Player) entity.playerTileX else (entity as Monster).x.toInt()
        val currentY = if (entity is Player) entity.playerTileY else (entity as Monster).y.toInt()

        val (validTileX, validTileY) = findNearestWalkableTile(tileX, tileY)
        if (validTileX == -1 || validTileY == -1) {
            Gdx.app.log("MovementManager", "No walkable tile found near ($tileX, $tileY)")
            clearPath()
            return false
        }

        if (currentPath.size == 0 || (currentPath[currentPath.size - 1].x.toInt() != validTileX || currentPath[currentPath.size - 1].y.toInt() != validTileY)) {
            val path = pathFinder.findPath(currentX, currentY, validTileX, validTileY, monsterManager)
            currentPath.clear()
            if (path.size > 0) {
                currentPath.addAll(path)
                Gdx.app.log("MovementManager", "Pathfinding to ($validTileX, $validTileY) for entity at ($currentX, $currentY): ${path.joinToString()}")
                if (entity is Player) {
                    entity.moveProgress = 0f
                    (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f)
                }
            } else {
                Gdx.app.log("MovementManager", "No valid path to ($validTileX, $validTileY) for entity at ($currentX, $currentY)")
                return false
            }
        }

        return true
    }

    fun moveInDirection(entity: Any, dx: Int, dy: Int, isContinuous: Boolean): Boolean {
        if (entity !is Player) {
            Gdx.app.log("MovementManager", "Invalid entity type for directional movement")
            return false
        }

        val currentX = entity.playerTileX
        val currentY = entity.playerTileY

        if (!isContinuous) {
            val targetX = currentX + dx
            val targetY = currentY + dy
            if (targetX in 0 until mapManager.mapTileWidth && targetY in 0 until mapManager.mapTileHeight &&
                mapManager.isWalkable(targetX, targetY) && !monsterManager.isTileOccupied(targetX, targetY)) {
                entity.setTargetTile(targetX, targetY)
                entity.moveProgress = 0f
                Gdx.app.log("MovementManager", "Player moved in direction ($dx, $dy) to ($targetX, $targetY)")
                lastDx = dx
                lastDy = dy
                return true
            } else {
                Gdx.app.log("MovementManager", "Cannot move in direction ($dx, $dy) to ($targetX, $targetY): unwalkable or occupied")
                return false
            }
        } else {
            val targetX = currentX + dx
            val targetY = currentY + dy
            if (lastDx != dx || lastDy != dy || currentPath.size == 0 || (targetTileX != targetX || targetTileY != targetY)) {
                targetTileX = targetX.coerceIn(0, mapManager.mapTileWidth - 1)
                targetTileY = targetY.coerceIn(0, mapManager.mapTileHeight - 1)
                val (validTileX, validTileY) = findNearestWalkableTile(targetTileX, targetTileY)
                if (validTileX == -1 || validTileY == -1) {
                    Gdx.app.log("MovementManager", "No walkable tile found near ($targetTileX, $targetTileY)")
                    return false
                }
                val path = pathFinder.findPath(currentX, currentY, validTileX, validTileY, monsterManager)
                currentPath.clear()
                if (path.size > 0) {
                    currentPath.addAll(path)
                    targetTileX = validTileX
                    targetTileY = validTileY
                    Gdx.app.log("MovementManager", "Continuous pathfinding to ($validTileX, $validTileY) for player at ($currentX, $currentY)")
                    entity.moveProgress = 0f
                    lastDx = dx
                    lastDy = dy
                    return true
                } else {
                    Gdx.app.log("MovementManager", "No valid continuous path to ($validTileX, $validTileY)")
                    return false
                }
            }
            return true
        }
    }

    fun updateContinuousDirection(entity: Player, delta: Float) {
        if (entity.moveProgress > 0f || lastDx == 0 && lastDy == 0) return
        moveInDirection(entity, lastDx, lastDy, true)
    }

    fun findNearestWalkableTile(tileX: Int, tileY: Int): Pair<Int, Int> {
        if (tileX in 0 until mapManager.mapTileWidth && tileY in 0 until mapManager.mapTileHeight &&
            mapManager.isWalkable(tileX, tileY) && !monsterManager.isTileOccupied(tileX, tileY)) {
            Gdx.app.log("MovementManager", "Tile ($tileX, $tileY) is walkable and unoccupied")
            return Pair(tileX, tileY)
        }

        val queue = Array<Vector2>()
        val visited = Array(mapManager.mapTileWidth) { BooleanArray(mapManager.mapTileHeight) { false } }
        queue.add(Vector2(tileX.toFloat(), tileY.toFloat()))
        visited[tileX][tileY] = true

        val directions = arrayOf(
            Vector2(1f, 0f), Vector2(-1f, 0f), Vector2(0f, 1f), Vector2(0f, -1f),
            Vector2(1f, 1f), Vector2(-1f, 1f), Vector2(1f, -1f), Vector2(-1f, -1f)
        )

        var radius = 0
        while (queue.size > 0 && radius < 10) {
            val size = queue.size
            repeat(size) {
                val current = queue.removeIndex(0)
                val x = current.x.toInt()
                val y = current.y.toInt()
                if (x in 0 until mapManager.mapTileWidth && y in 0 until mapManager.mapTileHeight &&
                    mapManager.isWalkable(x, y) && !monsterManager.isTileOccupied(x, y)) {
                    Gdx.app.log("MovementManager", "Found nearest walkable tile at ($x, $y) after searching radius $radius")
                    return Pair(x, y)
                }
                for (dir in directions) {
                    val nx = (x + dir.x).toInt()
                    val ny = (y + dir.y).toInt()
                    if (nx in 0 until mapManager.mapTileWidth && ny in 0 until mapManager.mapTileHeight && !visited[nx][ny]) {
                        queue.add(Vector2(nx.toFloat(), ny.toFloat()))
                        visited[nx][ny] = true
                        Gdx.app.log("MovementManager", "Added tile ($nx, $ny) to BFS queue")
                    }
                }
            }
            radius++
        }
        Gdx.app.log("MovementManager", "No walkable tile found near ($tileX, $tileY) after searching radius $radius")
        return Pair(-1, -1)
    }

    private fun getEntityX(entity: Any): Float {
        return if (entity is Player) entity.playerX else (entity as Monster).x
    }

    private fun getEntityY(entity: Any): Float {
        return if (entity is Player) entity.playerY else (entity as Monster).y
    }
}
