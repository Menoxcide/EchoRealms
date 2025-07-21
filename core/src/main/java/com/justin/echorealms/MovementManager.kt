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

    fun getCurrentPath(): Array<Vector2> = currentPath

    fun clearPath() {
        currentPath.clear()
        Gdx.app.log("MovementManager", "Path cleared")
    }

    fun moveToTile(player: Player, tileX: Int, tileY: Int, delta: Float) {
        if (player.isDead()) return

        // Simplified movement: Directly move to the target tile if adjacent and walkable
        val dx = tileX - player.playerTileX
        val dy = tileY - player.playerTileY
        if (abs(dx) <= 1 && abs(dy) <= 1 && (dx == 0 || dy == 0)) {
            if (mapManager.isWalkable(tileX, tileY) && !monsterManager.isTileOccupied(tileX, tileY)) {
                player.playerTileX = tileX
                player.playerTileY = tileY
                player.playerX = tileX.toFloat() + 0.5f
                player.playerY = tileY.toFloat() + 0.5f
                clearPath()
                Gdx.app.log("MovementManager", "Moved player directly to ($tileX, $tileY)")
                return
            } else {
                Gdx.app.log("MovementManager", "Direct move to ($tileX, $tileY) blocked or occupied")
                return
            }
        }

        // Use pathfinding for non-adjacent tiles
        pathRecalcTimer -= delta
        if (pathRecalcTimer <= 0f || currentPath.size == 0) {
            val path = pathFinder.findPath(
                player.playerTileX, player.playerTileY,
                tileX, tileY, monsterManager
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

    fun moveInDirection(player: Player, dx: Int, dy: Int, continuous: Boolean): Boolean {
        if (player.isDead()) return false

        val newTileX = player.playerTileX + dx
        val newTileY = player.playerTileY + dy

        if (newTileX in 0 until mapManager.mapTileWidth && newTileY in 0 until mapManager.mapTileHeight) {
            if (mapManager.isWalkable(newTileX, newTileY) && !monsterManager.isTileOccupied(newTileX, newTileY)) {
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
        if (pathRecalcTimer <= 0f) {
            val targetX = currentPath.last().x.toInt()
            val targetY = currentPath.last().y.toInt()
            val path = pathFinder.findPath(
                player.playerTileX, player.playerTileY,
                targetX, targetY, monsterManager
            )
            currentPath.clear()
            if (path.size > 0) {
                currentPath.addAll(path)
                Gdx.app.log("MovementManager", "Recalculated path to ($targetX, $targetY): ${path.joinToString()}")
            } else {
                Gdx.app.log("MovementManager", "Recalculation failed, no valid path to ($targetX, $targetY)")
                return
            }
            pathRecalcTimer = pathRecalcInterval
        }

        if (currentPath.size > 0) {
            val target = currentPath[0]
            val dx = target.x - player.playerX
            val dy = target.y - player.playerY
            val dist = maxOf(abs(dx), abs(dy))
            if (dist > 0) {
                val moveDist = delta * player.speed
                val moveX = sign(dx) * min(moveDist, abs(dx))
                val moveY = sign(dy) * min(moveDist, abs(dy))
                val newX = player.playerX + moveX
                val newY = player.playerY + moveY
                if (mapManager.isWalkable(newX.toInt(), newY.toInt()) && !monsterManager.isTileOccupied(newX.toInt(), newY.toInt())) {
                    player.playerX = newX
                    player.playerY = newY
                    player.playerTileX = newX.toInt()
                    player.playerTileY = newY.toInt()
                    Gdx.app.log("MovementManager", "Moving player to ($newX, $newY)")
                } else {
                    Gdx.app.log("MovementManager", "Path blocked at ($newX, $newY), clearing and recalculating")
                    clearPath()
                    pathRecalcTimer = 0f
                    return
                }
                if (abs(player.playerX - target.x) <= moveDist && abs(player.playerY - target.y) <= moveDist) {
                    player.playerX = target.x
                    player.playerY = target.y
                    player.playerTileX = target.x.toInt()
                    player.playerTileY = target.y.toInt()
                    currentPath.removeIndex(0)
                    Gdx.app.log("MovementManager", "Reached waypoint ($target), remaining path: ${currentPath.size}")
                }
            } else {
                player.playerX = target.x
                player.playerY = target.y
                player.playerTileX = target.x.toInt()
                player.playerTileY = target.y.toInt()
                currentPath.removeIndex(0)
                Gdx.app.log("MovementManager", "Reached waypoint ($target), remaining path: ${currentPath.size}")
            }
        }
    }
}
