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
    private var movementTimer = 0f
    private val movementInterval = 0.25f // 4 tiles per second

    fun getCurrentPath(): Array<Vector2> {
        return currentPath
    }

    fun clearPath() {
        currentPath.clear()
        Gdx.app.log("MovementManager", "Path cleared")
    }

    fun moveToTile(entity: Any, tileX: Int, tileY: Int, delta: Float): Boolean {
        if (entity !is Player && entity !is Monster) {
            Gdx.app.log("MovementManager", "Invalid entity type for movement")
            return false
        }

        // Validate target tile
        if (tileX < 0 || tileY < 0 || tileX >= mapManager.mapTileWidth || tileY >= mapManager.mapTileHeight || !mapManager.isWalkable(tileX, tileY) || monsterManager.isTileOccupied(tileX, tileY)) {
            Gdx.app.log("MovementManager", "Invalid target tile ($tileX, $tileY) for entity at (${getEntityX(entity)}, ${getEntityY(entity)})")
            clearPath()
            return false
        }

        // Calculate current position
        val currentX = if (entity is Player) entity.playerTileX else (entity as Monster).x.toInt()
        val currentY = if (entity is Player) entity.playerTileY else (entity as Monster).y.toInt()

        // Update path if needed
        if (currentPath.size == 0 || (currentPath[currentPath.size - 1].x.toInt() != tileX || currentPath[currentPath.size - 1].y.toInt() != tileY)) {
            val path = pathFinder.findPath(currentX, currentY, tileX, tileY, monsterManager)
            currentPath.clear()
            if (path.size > 0) {
                currentPath.addAll(path)
                Gdx.app.log("MovementManager", "Pathfinding to ($tileX, $tileY) for entity at ($currentX, $currentY): ${path.joinToString()}")
                if (entity is Player) {
                    entity.moveProgress = 0f // Reset interpolation for new path
                    (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f) // Clear DPAD influence
                }
            } else {
                Gdx.app.log("MovementManager", "No valid path to ($tileX, $tileY) for entity at ($currentX, $currentY)")
                return false
            }
        }

        return true
    }

    fun moveInDirection(entity: Any, dx: Int, dy: Int): Boolean {
        if (entity !is Player) {
            Gdx.app.log("MovementManager", "Invalid entity type for directional movement")
            return false
        }

        val currentX = entity.playerTileX
        val currentY = entity.playerTileY
        val targetX = currentX + dx
        val targetY = currentY + dy

        if (mapManager.isWalkable(targetX, targetY) && !monsterManager.isTileOccupied(targetX, targetY)) {
            entity.setTargetTile(targetX, targetY)
            entity.moveProgress = 0f // Reset interpolation for smooth movement
            clearPath()
            Gdx.app.log("MovementManager", "Player moved in direction ($dx, $dy) to ($targetX, $targetY)")
            return true
        } else {
            Gdx.app.log("MovementManager", "Cannot move in direction ($dx, $dy) to ($targetX, $targetY): unwalkable or occupied")
            return false
        }
    }

    private fun getEntityX(entity: Any): Float {
        return if (entity is Player) entity.playerX else (entity as Monster).x
    }

    private fun getEntityY(entity: Any): Float {
        return if (entity is Player) entity.playerY else (entity as Monster).y
    }
}
