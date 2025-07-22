package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Array
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

class MovementManager(
    private val mapManager: MapManager,
    private val pathFinder: PathFinder,
    private val monsterManager: MonsterManager
) {
    private val currentPath = Array<Vector2>()
    private var pathRecalcTimer = 0f
    private val pathRecalcInterval = 0.5f
    private val positionTolerance = 0.2f
    private var targetTileX: Int? = null
    private var targetTileY: Int? = null

    fun getCurrentPath(): Array<Vector2> = currentPath

    fun clearPath() {
        currentPath.clear()
        targetTileX = null
        targetTileY = null
        Gdx.app.log("MovementManager", "Path cleared")
    }

    fun moveToTile(player: Player, tileX: Int, tileY: Int, delta: Float) {
        if (player.isDead()) return

        val camera = player.cameraManager?.camera
        if (camera != null) {
            val viewportWidth = camera.viewportWidth * camera.zoom
            val viewportHeight = camera.viewportHeight * camera.zoom
            val minX = (camera.position.x - viewportWidth / 2) / mapManager.tileSize
            val maxX = (camera.position.x + viewportWidth / 2) / mapManager.tileSize
            val minY = (camera.position.y - viewportHeight / 2) / mapManager.tileSize
            val maxY = (camera.position.y + viewportHeight / 2) / mapManager.tileSize

            if (tileX < minX || tileX > maxX || tileY < minY || tileY > maxY) {
                Gdx.app.log("MovementManager", "Target tile ($tileX, $tileY) is outside the viewport")
                return
            }
        }

        targetTileX = tileX
        targetTileY = tileY

        pathRecalcTimer -= delta
        if (pathRecalcTimer <= 0f || currentPath.size == 0 || !isPathValid(player, tileX, tileY)) {
            val path = pathFinder.findPath(
                player.playerTileX, player.playerTileY,
                tileX, tileY, monsterManager, monsterManager.getMonsters()
            )
            currentPath.clear()
            if (path.size > 0) {
                currentPath.addAll(path)
                Gdx.app.log("MovementManager", "New path set to ($tileX, $tileY): ${path.joinToString()}")
            } else {
                Gdx.app.log("MovementManager", "No valid path found to ($tileX, $tileY)")
            }
            pathRecalcTimer = pathRecalcInterval
        }
    }

    private fun isPathValid(player: Player, targetX: Int, targetY: Int): Boolean {
        if (currentPath.size == 0 || targetTileX != targetX || targetTileY != targetY) return false
        val lastWaypoint = currentPath[currentPath.size - 1]
        return lastWaypoint.x.toInt() == targetX && lastWaypoint.y.toInt() == targetY &&
            mapManager.isWalkable(targetX, targetY) &&
            !monsterManager.isTileOccupied(targetX, targetY, player, null, monsterManager.getMonsters())
    }

    fun moveInDirection(player: Player, dx: Int, dy: Int, continuous: Boolean): Boolean {
        if (player.isDead()) return false
        if (currentPath.size > 0) {
            Gdx.app.log("MovementManager", "Ignoring DPAD movement (dx=$dx, dy=$dy) due to active path")
            return false
        }

        val newTileX = player.playerTileX + dx
        val newTileY = player.playerTileY + dy

        if (newTileX in 0 until mapManager.mapTileWidth && newTileY in 0 until mapManager.mapTileHeight) {
            if (mapManager.isWalkable(newTileX, newTileY) && !monsterManager.isTileOccupied(newTileX, newTileY, player, null, monsterManager.getMonsters())) {
                Gdx.app.log("MovementManager", "Tile ($newTileX, $newTileY) is walkable and unoccupied")
                if (continuous) {
                    moveToTile(player, newTileX, newTileY, 0f)
                } else {
                    player.playerTileX = newTileX
                    player.playerTileY = newTileY
                    player.playerX = newTileX.toFloat() + 0.5f
                    player.playerY = newTileY.toFloat() + 0.5f
                    clearPath()
                    Gdx.app.log("MovementManager", "Moved player to ($newTileX, $newTileY)")
                }
                return true
            } else {
                Gdx.app.log("MovementManager", "Tile ($newTileX, $newTileY) is blocked or occupied")
                clearPath()
                return false
            }
        }
        Gdx.app.log("MovementManager", "Tile ($newTileX, $newTileY) out of bounds")
        return false
    }

    fun updateContinuousDirection(player: Player, delta: Float) {
        if (player.isDead() || currentPath.size == 0) return

        pathRecalcTimer -= delta
        if (pathRecalcTimer <= 0f && targetTileX != null && targetTileY != null && !isPathValid(player, targetTileX!!, targetTileY!!)) {
            val path = pathFinder.findPath(
                player.playerTileX, player.playerTileY,
                targetTileX!!, targetTileY!!, monsterManager, monsterManager.getMonsters()
            )
            currentPath.clear()
            if (path.size > 0) {
                currentPath.addAll(path)
                Gdx.app.log("MovementManager", "Recalculated path to ($targetTileX, $targetTileY): ${path.joinToString()}")
            } else {
                Gdx.app.log("MovementManager", "Recalculation failed, no valid path to ($targetTileX, $targetTileY)")
                clearPath()
                return
            }
            pathRecalcTimer = pathRecalcInterval
        }

        if (currentPath.size > 0) {
            val target = currentPath[0]
            val targetTileX = target.x.toInt()
            val targetTileY = target.y.toInt()
            val dx = target.x - player.playerX
            val dy = target.y - player.playerY
            val dist = maxOf(abs(dx), abs(dy))
            Gdx.app.log("MovementManager", "Moving towards ($targetTileX, $targetTileY) with dx=$dx, dy=$dy, dist=$dist")
            if (dist > positionTolerance) {
                val moveDist = delta * player.speed
                val moveX = sign(dx) * min(moveDist, abs(dx))
                val moveY = sign(dy) * min(moveDist, abs(dy))
                val newX = player.playerX + moveX
                val newY = player.playerY + moveY

                if (mapManager.isWalkable(targetTileX, targetTileY) && !monsterManager.isTileOccupied(targetTileX, targetTileY, player, null, monsterManager.getMonsters())) {
                    player.playerX = newX
                    player.playerY = newY
                    player.playerTileX = newX.toInt()
                    player.playerTileY = newY.toInt()
                    Gdx.app.log("MovementManager", "Moving player to ($newX, $newY)")
                } else {
                    Gdx.app.log("MovementManager", "Target tile ($targetTileX, $targetTileY) is blocked or occupied, clearing and recalculating")
                    clearPath()
                    pathRecalcTimer = 0f
                    return
                }

                if (abs(player.playerX - target.x) <= positionTolerance && abs(player.playerY - target.y) <= positionTolerance) {
                    player.playerX = target.x
                    player.playerY = target.y
                    player.playerTileX = targetTileX
                    player.playerTileY = targetTileY
                    currentPath.removeIndex(0)
                    Gdx.app.log("MovementManager", "Reached waypoint ($target), remaining path: ${currentPath.size}")
                    if (currentPath.size == 0) {
                        clearPath()
                    }
                }
            } else {
                player.playerX = target.x
                player.playerY = target.y
                player.playerTileX = targetTileX
                player.playerTileY = targetTileY
                currentPath.removeIndex(0)
                Gdx.app.log("MovementManager", "Reached waypoint ($target), remaining path: ${currentPath.size}")
                if (currentPath.size == 0) {
                    clearPath()
                }
            }
        }
    }
}
