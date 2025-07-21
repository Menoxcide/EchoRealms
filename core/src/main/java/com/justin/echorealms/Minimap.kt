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
import kotlin.math.floor
import kotlin.math.ceil

class Minimap(
    private val mapManager: MapManager,
    private val player: Player,
    private val cameraManager: CameraManager
) {
    var offsetX = Gdx.graphics.width - 200f - 10f // Anchored to top-right
    var offsetY = Gdx.graphics.height - 200f - 10f
    var size = 200f // Single size for square
    private var dragOffset = Vector2()
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val resizeCornerSize = 15f
    private val minSize = 150f
    private val maxSize = minOf(Gdx.graphics.width.toFloat() * 0.5f, Gdx.graphics.height.toFloat() * 0.5f)
    var minTileX = 0f
    var minTileY = 0f
    var maxTileX = 0f
    var maxTileY = 0f

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
        offsetX = (x - dragOffset.x).coerceIn(0f, Gdx.graphics.width.toFloat() - size)
        offsetY = (y - dragOffset.y).coerceIn(0f, Gdx.graphics.height.toFloat() - size)
        Gdx.app.log("Minimap", "Dragged to ($offsetX, $offsetY)")
    }

    fun stopDragging() {
        Gdx.app.log("Minimap", "Stopped dragging")
    }

    fun resize(corner: String, deltaX: Float, deltaY: Float) {
        val delta = (abs(deltaX) + abs(deltaY)) / 2
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
        offsetX = offsetX.coerceIn(0f, Gdx.graphics.width.toFloat() - size)
        offsetY = offsetY.coerceIn(0f, Gdx.graphics.height.toFloat() - size)
        Gdx.app.log("Minimap", "Resized to size ($size, $size), offset ($offsetX, $offsetY)")
    }

    fun getTileSize(): Float {
        val camera = cameraManager.camera
        val viewportWidth = camera.viewportWidth * camera.zoom * 2f
        val viewportHeight = camera.viewportHeight * camera.zoom * 2f
        val tilesWide = (viewportWidth / mapManager.tileSize).toInt()
        val tilesHigh = (viewportHeight / mapManager.tileSize).toInt()
        return minOf(size / tilesWide.toFloat(), size / tilesHigh.toFloat())
    }

    fun render(camera: OrthographicCamera) {
        batch.projectionMatrix = camera.combined
        batch.begin()
        shapeRenderer.projectionMatrix = camera.combined

        // Calculate viewport*2 bounds
        val camera = cameraManager.camera
        val viewportWidth = camera.viewportWidth * camera.zoom * 2f
        val viewportHeight = camera.viewportHeight * camera.zoom * 2f
        minTileX = floor((camera.position.x - viewportWidth / 2) / mapManager.tileSize).toFloat()
        minTileY = floor((camera.position.y - viewportHeight / 2) / mapManager.tileSize).toFloat()
        maxTileX = ceil((camera.position.x + viewportWidth / 2) / mapManager.tileSize).toFloat()
        maxTileY = ceil((camera.position.y + viewportHeight / 2) / mapManager.tileSize).toFloat()

        // Clamp to map boundaries
        minTileX = minOf(0f, mapManager.mapTileWidth.toFloat()).coerceAtLeast(0f)
        minTileY = minOf(0f, mapManager.mapTileHeight.toFloat()).coerceAtLeast(0f)
        maxTileX = minOf(maxTileX, mapManager.mapTileWidth.toFloat())
        maxTileY = minOf(maxTileY, mapManager.mapTileHeight.toFloat())

        val tilesWide = (maxTileX - minTileX).toInt()
        val tilesHigh = (maxTileY - minTileY).toInt()
        val tileSize = minOf(size / tilesWide.toFloat(), size / tilesHigh.toFloat())

        // Set up scissor rectangle
        val scissor = com.badlogic.gdx.math.Rectangle(offsetX, offsetY, size, size)
        ScissorStack.pushScissors(scissor)

        shapeRenderer.begin(ShapeType.Filled)
        for (x in minTileX.toInt() until maxTileX.toInt()) {
            for (y in minTileY.toInt() until maxTileY.toInt()) {
                if (x in 0 until mapManager.mapTileWidth && y in 0 until mapManager.mapTileHeight) {
                    shapeRenderer.color = if (mapManager.isWalkable(x, y)) Color.GREEN else Color.GRAY
                    shapeRenderer.rect(
                        offsetX + (x - minTileX) * tileSize,
                        offsetY + (y - minTileY) * tileSize,
                        tileSize,
                        tileSize
                    )
                }
            }
        }
        val playerX = (player.playerTileX - minTileX) * tileSize + offsetX
        val playerY = (player.playerTileY - minTileY) * tileSize + offsetY
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
