package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.Vector2

class Minimap(
    private val mapManager: MapManager,
    private val player: Player,
    private val cameraManager: CameraManager,
    private val skin: Skin
) {
    val table = Table()
    var offsetX = Gdx.graphics.width - 200f - 10f
    var offsetY = Gdx.graphics.height - 200f
    var size = 200f
    private var dragOffset = Vector2()
    private val shapeRenderer = ShapeRenderer()
    private val resizeCornerSize = 20f
    private val minSize = 150f
    private val maxSize = minOf(Gdx.graphics.width * 0.5f, Gdx.graphics.height * 0.5f)

    init {
        table.setSize(size, size)
        table.setPosition(offsetX, offsetY)
        table.background = skin.getDrawable("window")
        table.isVisible = true
        table.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                offsetX = (x + table.x - size / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - size)
                offsetY = (y + table.y - size / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - size)
                table.setPosition(offsetX, offsetY)
                Gdx.app.log("Minimap", "Dragged to ($offsetX, $offsetY)")
            }
        })
        table.add(object : Image() {
            override fun draw(batch: com.badlogic.gdx.graphics.g2d.Batch?, parentAlpha: Float) {
                batch?.end()
                shapeRenderer.projectionMatrix = stage.camera.combined
                shapeRenderer.begin(ShapeType.Filled)
                val tileSize = size / mapManager.mapTileWidth
                for (x in 0 until mapManager.mapTileWidth) {
                    for (y in 0 until mapManager.mapTileHeight) {
                        shapeRenderer.color = if (mapManager.isWalkable(x, y)) Color.GREEN else Color.GRAY
                        shapeRenderer.rect(offsetX + x * tileSize, offsetY + y * tileSize, tileSize, tileSize)
                    }
                }
                shapeRenderer.color = Color.RED
                shapeRenderer.rect(offsetX + player.playerTileX * tileSize, offsetY + player.playerTileY * tileSize, tileSize, tileSize)
                shapeRenderer.end()
                batch?.begin()
            }
        }).size(size, size)
    }

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
        // Handled by DragListener
    }

    fun stopDragging() {
        Gdx.app.log("Minimap", "Stopped dragging")
    }

    fun resize(corner: String, deltaX: Float, deltaY: Float) {
        val delta = (Math.abs(deltaX) + Math.abs(deltaY)) / 2
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
        table.setSize(size, size)
        table.setPosition(offsetX, offsetY)
        Gdx.app.log("Minimap", "Resized to size ($size, $size), offset ($offsetX, $offsetY)")
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
