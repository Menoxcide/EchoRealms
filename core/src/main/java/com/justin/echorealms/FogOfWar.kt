// core/src/main/java/com/justin/echorealms/FogOfWar.kt
package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import kotlin.math.pow

class FogOfWar(
    private val mapManager: MapManager,
    private val player: Player
) {
    private val explored = Array(mapManager.mapTileWidth) { BooleanArray(mapManager.mapTileHeight) { false } }
    private val shapeRenderer = ShapeRenderer()
    private val visionRadius = 10f
    private val fadeRange = 2f

    init {
        update(player.playerX, player.playerY)
    }

    fun update(playerX: Float, playerY: Float) {
        val minX = maxOf(0f, playerX - visionRadius - fadeRange).toInt()
        val maxX = minOf((mapManager.mapTileWidth - 1).toFloat(), playerX + visionRadius + fadeRange).toInt()
        val minY = maxOf(0f, playerY - visionRadius - fadeRange).toInt()
        val maxY = minOf((mapManager.mapTileHeight - 1).toFloat(), playerY + visionRadius + fadeRange).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val dx = x - playerX
                val dy = y - playerY
                val dist = (dx * dx + dy * dy).toFloat().pow(0.5f)
                if (dist <= visionRadius) {
                    explored[x][y] = true
                }
            }
        }
        Gdx.app.log("FogOfWar", "Updated explored area around ($playerX, $playerY)")
    }

    fun isExplored(x: Int, y: Int): Boolean {
        return if (x in 0 until mapManager.mapTileWidth && y in 0 until mapManager.mapTileHeight) {
            explored[x][y]
        } else {
            false
        }
    }

    fun render(camera: OrthographicCamera, tileSize: Float) {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color(0f, 0f, 0f, 0.8f)

        val playerX = player.playerX
        val playerY = player.playerY
        val viewportWidth = camera.viewportWidth * camera.zoom
        val viewportHeight = camera.viewportHeight * camera.zoom
        val minX = maxOf(0f, (camera.position.x - viewportWidth / 2) / tileSize).toInt()
        val maxX = minOf((mapManager.mapTileWidth - 1).toFloat(), (camera.position.x + viewportWidth / 2) / tileSize).toInt()
        val minY = maxOf(0f, (camera.position.y - viewportHeight / 2) / tileSize).toInt()
        val maxY = minOf((mapManager.mapTileHeight - 1).toFloat(), (camera.position.y + viewportHeight / 2) / tileSize).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                val dx = x - playerX
                val dy = y - playerY
                val dist = (dx * dx + dy * dy).toFloat().pow(0.5f)
                val alpha = if (dist <= visionRadius) {
                    0f
                } else if (dist <= visionRadius + fadeRange) {
                    ((dist - visionRadius) / fadeRange).pow(2f) * 0.8f
                } else {
                    0.8f
                }
                if (alpha > 0f) {
                    shapeRenderer.color = Color(0f, 0f, 0f, alpha)
                    shapeRenderer.rect(x * tileSize, y * tileSize, tileSize, tileSize)
                }
            }
        }
        shapeRenderer.end()
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
