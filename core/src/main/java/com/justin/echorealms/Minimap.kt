package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import kotlin.math.abs

class Minimap(
    private val mapManager: MapManager,
    private val player: Player
) {
    var offsetX = Gdx.graphics.width - 200f - 10f // Anchored to top-right
    var offsetY = Gdx.graphics.height - 200f - 10f
    var size = 200f // Single size for square
    private var dragOffset = Vector2()
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val resizeCornerSize = 15f
    private val minSize = 150f
    private val maxSize = minOf(Gdx.graphics.width * 0.5f, Gdx.graphics.height * 0.5f)

    fun isInMinimap(x: Float, y: Float): Boolean {
        return x in offsetX..(offsetX + size) && y in offsetY..(offsetY + size)
    }

    fun isInResizeCorner(x: Float, y: Float): Boolean {
        return getResizeCorner(x, y).isNotEmpty()
    }

    fun getResizeCorner(x: Float, y: Float): String {
        val left = offsetX
        val right = offsetX + size
        val bottom = offsetY
        val top = offsetY + size
        return when {
            x in left..(left + resizeCornerSize) && y in (top - resizeCornerSize)..top -> "top-left"
            x in (right - resizeCornerSize)..right && y in (top - resizeCornerSize)..top -> "top-right"
            x in left..(left + resizeCornerSize) && y in bottom..(bottom + resizeCornerSize) -> "bottom-left"
            x in (right - resizeCornerSize)..right && y in bottom..(bottom + resizeCornerSize) -> "bottom-right"
            else -> ""
        }
    }

    fun startDragging(x: Float, y: Float) {
        dragOffset.set(x - offsetX, y - offsetY)
        Gdx.app.log("Minimap", "Started dragging at ($x, $y)")
    }

    fun drag(x: Float, y: Float) {
        offsetX = (x - dragOffset.x).coerceIn(0f, Gdx.graphics.width - size)
        offsetY = (y - dragOffset.y).coerceIn(0f, Gdx.graphics.height - size)
        Gdx.app.log("Minimap", "Dragged to ($offsetX, $offsetY)")
    }

    fun stopDragging() {
        Gdx.app.log("Minimap", "Stopped dragging")
    }

    fun resize(corner: String, deltaX: Float, deltaY: Float) {
        val delta = (abs(deltaX) + abs(deltaY)) / 2 // Average for square resizing
        when (corner) {
            "top-left" -> {
                val newSize = size - delta
                if (newSize in minSize..maxSize) {
                    offsetX += delta
                    offsetY += delta
                    size = newSize
                }
            }
            "top-right" -> {
                val newSize = size + delta
                if (newSize in minSize..maxSize) {
                    size = newSize
                }
            }
            "bottom-left" -> {
                val newSize = size + delta
                if (newSize in minSize..maxSize) {
                    offsetX += delta
                    size = newSize
                }
            }
            "bottom-right" -> {
                val newSize = size + delta
                if (newSize in minSize..maxSize) {
                    size = newSize
                }
            }
        }
        size = size.coerceIn(minSize, maxSize)
        offsetX = offsetX.coerceIn(0f, Gdx.graphics.width - size)
        offsetY = offsetY.coerceIn(0f, Gdx.graphics.height - size)
        Gdx.app.log("Minimap", "Resized to size ($size, $size), offset ($offsetX, $offsetY)")
    }

    fun render(camera: OrthographicCamera) {
        batch.projectionMatrix = camera.combined
        batch.begin()
        shapeRenderer.projectionMatrix = camera.combined

        // Calculate tileSize to fit map within minimap bounds
        val tileSize = minOf(size / mapManager.mapTileWidth, size / mapManager.mapTileHeight)

        // Set up scissor rectangle to clip rendering to minimap bounds
        val scissor = com.badlogic.gdx.math.Rectangle(offsetX, offsetY, size, size)
        ScissorStack.pushScissors(scissor)

        shapeRenderer.begin(ShapeType.Filled)
        for (x in 0 until mapManager.mapTileWidth) {
            for (y in 0 until mapManager.mapTileHeight) {
                shapeRenderer.color = if (mapManager.isWalkable(x, y)) Color.GREEN else Color.GRAY
                shapeRenderer.rect(offsetX + x * tileSize, offsetY + y * tileSize, tileSize, tileSize)
            }
        }
        val playerX = player.playerTileX * tileSize + offsetX
        val playerY = player.playerTileY * tileSize + offsetY
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(playerX, playerY, tileSize * 1.5f, tileSize * 1.5f)
        shapeRenderer.end()

        // Render border
        val borderThickness = size / 50f
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rectLine(offsetX, offsetY, offsetX + size, offsetY, borderThickness)
        shapeRenderer.rectLine(offsetX, offsetY, offsetX, offsetY + size, borderThickness)
        shapeRenderer.rectLine(offsetX + size, offsetY, offsetX + size, offsetY + size, borderThickness)
        shapeRenderer.rectLine(offsetX, offsetY + size, offsetX + size, offsetY + size, borderThickness)
        shapeRenderer.end()

        // Render resize corners
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(offsetX, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.end()

        ScissorStack.popScissors()
        batch.end()
        Gdx.app.log("Minimap", "Rendered at ($offsetX, $offsetY), size ($size, $size), player at ($playerX, $playerY)")
    }

    fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
    }
}
