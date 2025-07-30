package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType

class WorldMap(
    private val mapManager: MapManager,
    private val player: Player,
    private val skin: Skin,
    private val fogOfWar: FogOfWar
) {
    val window = Window("World Map", skin, "default")
    private val mapTable = Table()
    private val scrollPane = ScrollPane(mapTable, skin, "list")
    private val minimizeButton = TextButton("-", skin, "default")
    private var isMinimized = false
    private val minimizedTable = Table()
    private val minimizedButton = TextButton("World Map", skin, "default")
    var sizeX = minOf(Gdx.graphics.width * 0.8f, mapManager.mapPixelWidth)
    var sizeY = minOf(Gdx.graphics.height * 0.8f, mapManager.mapPixelHeight)
    var offsetX = (Gdx.graphics.width - sizeX) / 2f
    var offsetY = (Gdx.graphics.height - sizeY) / 2f
    private val shapeRenderer = ShapeRenderer()
    var minimizedSize = Vector2(100f, 50f)
    var minimizedOffsetX = offsetX
    var minimizedOffsetY = offsetY
    private val minSizeX = 400f
    private val minSizeY = 400f
    private val maxSizeX = minOf(Gdx.graphics.width * 0.9f, mapManager.mapPixelWidth)
    private val maxSizeY = minOf(Gdx.graphics.height * 0.9f, mapManager.mapPixelHeight)
    private val resizeCornerSize = 20f

    init {
        window.setFillParent(false)
        window.setSize(sizeX, sizeY)
        window.setPosition(offsetX, offsetY)
        window.background = skin.getDrawable("window")
        window.isMovable = true
        window.isVisible = false

        scrollPane.setSize(sizeX, sizeY - 50f)
        scrollPane.setScrollbarsVisible(true)
        window.add(scrollPane).colspan(1).grow().pad(5f)
        window.add(minimizeButton).size(30f, 30f).top().right().pad(5f)
        window.row()

        mapTable.setSize(mapManager.mapPixelWidth, mapManager.mapPixelHeight)
        mapTable.bottom().left()
        mapTable.add(object : Actor() {
            override fun draw(batch: Batch?, parentAlpha: Float) {
                batch?.end()
                shapeRenderer.projectionMatrix = stage.camera.combined
                shapeRenderer.begin(ShapeType.Filled)
                val tileSize = minOf(sizeX / mapManager.mapTileWidth, sizeY / mapManager.mapTileHeight)
                for (x in 0 until mapManager.mapTileWidth) {
                    for (y in 0 until mapManager.mapTileHeight) {
                        if (fogOfWar.isExplored(x, y)) {
                            shapeRenderer.color = if (mapManager.isWalkable(x, y)) Color.GREEN else Color.GRAY
                            shapeRenderer.rect(x * tileSize, y * tileSize, tileSize, tileSize)
                        } else {
                            shapeRenderer.color = Color(0f, 0f, 0f, 0.8f)
                            shapeRenderer.rect(x * tileSize, y * tileSize, tileSize, tileSize)
                        }
                    }
                }
                val playerX = player.playerTileX * tileSize
                val playerY = player.playerTileY * tileSize
                shapeRenderer.color = Color.RED
                shapeRenderer.rect(playerX, playerY, tileSize * 1.5f, tileSize * 1.5f)
                shapeRenderer.end()
                batch?.begin()
            }
        }).grow()

        minimizeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isMinimized = true
                window.isVisible = false
                minimizedTable.isVisible = true
                minimizedOffsetX = offsetX
                minimizedOffsetY = offsetY
                minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
                Gdx.app.log("WorldMap", "Minimized world map")
            }
        })

        minimizedTable.setSize(minimizedSize.x, minimizedSize.y)
        minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
        minimizedTable.background = skin.getDrawable("window")
        minimizedTable.add(minimizedButton).size(minimizedSize.x, minimizedSize.y)
        minimizedTable.isVisible = false
        minimizedButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                offsetX = minimizedOffsetX
                offsetY = minimizedOffsetY
                window.setPosition(offsetX, offsetY)
                isMinimized = false
                window.isVisible = true
                minimizedTable.isVisible = false
                Gdx.app.log("WorldMap", "Restored world map at ($offsetX, $offsetY)")
            }
        })

        window.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                offsetX = (x + window.x - sizeX / 2).coerceIn(0f, Gdx.graphics.width - sizeX)
                offsetY = (y + window.y - sizeY / 2).coerceIn(0f, Gdx.graphics.height - sizeY)
                window.setPosition(offsetX, offsetY)
                Gdx.app.log("WorldMap", "Dragged to ($offsetX, $offsetY)")
            }
        })

        minimizedTable.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                if (isMinimized) {
                    minimizedOffsetX = (x + minimizedTable.x - minimizedSize.x / 2).coerceIn(0f, Gdx.graphics.width - minimizedSize.x)
                    minimizedOffsetY = (y + minimizedTable.y - minimizedSize.y / 2).coerceIn(0f, Gdx.graphics.height - minimizedSize.y)
                    minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
                    Gdx.app.log("WorldMap", "Minimized button dragged to ($minimizedOffsetX, $minimizedOffsetY)")
                }
            }
        })
    }

    fun isInBounds(x: Float, y: Float): Boolean {
        if (isMinimized) {
            return x in minimizedOffsetX..(minimizedOffsetX + minimizedSize.x) &&
                y in minimizedOffsetY..(minimizedOffsetY + minimizedSize.y)
        }
        return x in offsetX..(offsetX + sizeX) && y in offsetY..(offsetY + sizeY)
    }

    fun isInResizeCorner(x: Float, y: Float): Boolean {
        if (isMinimized) return false
        val left = offsetX
        val right = offsetX + sizeX
        val bottom = offsetY
        val top = offsetY + sizeY
        return when {
            x in left..(left + resizeCornerSize) && y in bottom..(bottom + resizeCornerSize) -> true
            x in (right - resizeCornerSize)..right && y in bottom..(bottom + resizeCornerSize) -> true
            x in left..(left + resizeCornerSize) && y in (top - resizeCornerSize)..top -> true
            x in (right - resizeCornerSize)..right && y in (top - resizeCornerSize)..top -> true
            else -> false
        }
    }

    fun getResizeCorner(x: Float, y: Float): String {
        if (isMinimized) return ""
        val left = offsetX
        val right = offsetX + sizeX
        val bottom = offsetY
        val top = offsetY + sizeY
        return when {
            x in left..(left + resizeCornerSize) && y in bottom..(bottom + resizeCornerSize) -> "bottom-left"
            x in (right - resizeCornerSize)..right && y in bottom..(bottom + resizeCornerSize) -> "bottom-right"
            x in left..(left + resizeCornerSize) && y in (top - resizeCornerSize)..top -> "top-left"
            x in (right - resizeCornerSize)..right && y in (top - resizeCornerSize)..top -> "top-right"
            else -> ""
        }
    }

    fun startDragging(x: Float, y: Float) {
        // Handled by DragListener
    }

    fun drag(x: Float, y: Float) {
        // Handled by DragListener
    }

    fun stopDragging() {
        Gdx.app.log("WorldMap", "Stopped dragging")
    }

    fun resize(corner: String, deltaX: Float, deltaY: Float) {
        if (isMinimized) return
        when (corner) {
            "top-left" -> {
                val newSizeX = sizeX - deltaX
                val newSizeY = sizeY - deltaY
                if (newSizeX in minSizeX..maxSizeX && newSizeY in minSizeY..maxSizeY) {
                    offsetX += deltaX
                    offsetY += deltaY
                    sizeX = newSizeX
                    sizeY = newSizeY
                }
            }
            "top-right" -> {
                val newSizeX = sizeX + deltaX
                val newSizeY = sizeY - deltaY
                if (newSizeX in minSizeX..maxSizeX && newSizeY in minSizeY..maxSizeY) {
                    sizeX = newSizeX
                    sizeY = newSizeY
                }
            }
            "bottom-left" -> {
                val newSizeX = sizeX - deltaX
                val newSizeY = sizeY + deltaY
                if (newSizeX in minSizeX..maxSizeX && newSizeY in minSizeY..maxSizeY) {
                    offsetX += deltaX
                    sizeX = newSizeX
                    sizeY = newSizeY
                }
            }
            "bottom-right" -> {
                val newSizeX = sizeX + deltaX
                val newSizeY = sizeY + deltaY
                if (newSizeX in minSizeX..maxSizeX && newSizeY in minSizeY..maxSizeY) {
                    sizeX = newSizeX
                    sizeY = newSizeY
                }
            }
        }
        sizeX = sizeX.coerceIn(minSizeX, maxSizeX)
        sizeY = sizeY.coerceIn(minSizeY, maxSizeY)
        offsetX = offsetX.coerceIn(0f, Gdx.graphics.width - sizeX)
        offsetY = offsetY.coerceIn(0f, Gdx.graphics.height - sizeY)
        window.setSize(sizeX, sizeY)
        scrollPane.setSize(sizeX, sizeY - 50f)
        window.setPosition(offsetX, offsetY)
        Gdx.app.log("WorldMap", "Resized to size ($sizeX, $sizeY), offset ($offsetX, $offsetY)")
    }

    fun render(camera: OrthographicCamera) {
        if (!window.isVisible) return
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(offsetX, offsetY + sizeY - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + sizeX - resizeCornerSize, offsetY + sizeY - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + sizeX - resizeCornerSize, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.end()
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
