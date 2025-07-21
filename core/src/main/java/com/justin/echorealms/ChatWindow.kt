// core/src/main/java/com/justin/echorealms/ChatWindow.kt
package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.OrthographicCamera

class ChatWindow(skin: Skin, private val player: Player) {
    private val stage = Stage()
    val table = Table()
    private val textField = TextField("", skin)
    private val chatArea = Table()
    private val scrollPane = com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(chatArea, skin)
    private val generalButton = TextButton("General", skin)
    private val tradeButton = TextButton("Trade", skin)
    private val clanButton = TextButton("Clan", skin)
    private val combatLogButton = TextButton("Combat Log", skin)
    private val minimizeButton = TextButton("-", skin)
    private val minimizedButton = TextButton("Chat/Log", skin)
    private val minimizedTable = Table()
    private var currentTab = "General"
    var sizeX = 600f
    var sizeY = 225f
    var offsetX = (Gdx.graphics.width - sizeX) / 2f
    var offsetY = 10f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val shapeRenderer = ShapeRenderer()
    private var isMinimized = false
    var minimizedSize = Vector2(100f, 50f)
    var minimizedOffsetX = offsetX
    var minimizedOffsetY = offsetY + sizeY - minimizedSize.y
    private val minSizeX = 400f
    private val minSizeY = 200f
    private val maxSizeX = minOf(Gdx.graphics.width * 0.5f, 800f)
    private val maxSizeY = 400f
    private val resizeCornerSize = 20f

    init {
        table.setFillParent(false)
        table.setSize(sizeX, sizeY)
        table.setPosition(offsetX, offsetY)

        chatArea.bottom().left()
        scrollPane.setScrollingDisabled(true, false)
        scrollPane.setSize(sizeX, sizeY - 50f)
        scrollPane.color = Color(0f, 0f, 0f, 1f) // Black background
        scrollPane.setScrollbarsVisible(true)
        table.add(scrollPane).colspan(4).growX().height(sizeY - 50f).pad(5f)
        table.add(minimizeButton).size(30f, 30f).top().right().pad(5f)
        table.row()

        generalButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                currentTab = "General"
                updateChatArea()
                Gdx.app.log("ChatWindow", "Switched to General tab")
            }
        })
        tradeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                currentTab = "Trade"
                updateChatArea()
                Gdx.app.log("ChatWindow", "Switched to Trade tab")
            }
        })
        clanButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                currentTab = "Clan"
                updateChatArea()
                Gdx.app.log("ChatWindow", "Switched to Clan tab")
            }
        })
        combatLogButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                currentTab = "Combat Log"
                updateChatArea()
                Gdx.app.log("ChatWindow", "Switched to Combat Log tab")
            }
        })
        minimizeButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isMinimized = true
                table.isVisible = false
                minimizedTable.isVisible = true
                minimizedOffsetX = offsetX
                minimizedOffsetY = offsetY
                minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
                Gdx.app.log("ChatWindow", "Minimized chat window")
            }
        })

        table.add(generalButton).pad(5f)
        table.add(tradeButton).pad(5f)
        table.add(clanButton).pad(5f)
        table.add(combatLogButton).pad(5f)
        table.row()

        textField.setTextFieldListener { _, c ->
            if (c == '\n' && textField.text.isNotEmpty()) {
                val message = "${player.playerTileX},${player.playerTileY}: ${textField.text}"
                addMessage(message, currentTab)
                textField.text = ""
                Gdx.app.log("ChatWindow", "Message sent: $message")
            }
        }
        table.add(textField).colspan(4).growX().pad(5f)

        minimizedTable.setSize(minimizedSize.x, minimizedSize.y)
        minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
        minimizedTable.color = Color(1f, 1f, 1f, 0.5f)
        minimizedTable.add(minimizedButton).size(minimizedSize.x, minimizedSize.y)
        minimizedTable.isVisible = false
        minimizedButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isMinimized = false
                table.isVisible = true
                minimizedTable.isVisible = false
                table.setPosition(minimizedOffsetX, minimizedOffsetY)
                updateChatArea()
                Gdx.app.log("ChatWindow", "Restored chat window at ($minimizedOffsetX, $minimizedOffsetY)")
            }
        })

        stage.addActor(table)
        stage.addActor(minimizedTable)
        updateChatArea()
    }

    fun addMessage(message: String, tab: String) {
        val labelStyle = Label.LabelStyle().apply {
            font = BitmapFont().apply { data.setScale(2f) }
            fontColor = Color.WHITE
        }
        val label = Label("[$tab] $message", labelStyle)
        label.setWrap(true)
        label.setAlignment(Align.left)
        chatArea.add(label).width(sizeX - 30f).pad(5f).left()
        chatArea.row()
        scrollPane.scrollTo(0f, 0f, 0f, 0f)
        scrollPane.layout()
        Gdx.app.log("ChatWindow", "Added message to $tab: $message")
    }

    fun addCombatLog(message: String) {
        addMessage(message, "Combat Log")
    }

    private fun updateChatArea() {
        chatArea.clear()
        addMessage("Welcome to $currentTab chat!", currentTab)
    }

    fun startDragging(screenX: Float, screenY: Float) {
        if (isMinimized) {
            dragStartX = screenX - minimizedOffsetX
            dragStartY = screenY - minimizedOffsetY
        } else {
            dragStartX = screenX - offsetX
            dragStartY = screenY - offsetY
        }
    }

    fun drag(screenX: Float, screenY: Float) {
        if (isMinimized) {
            minimizedOffsetX = (screenX - dragStartX).coerceIn(0f, Gdx.graphics.width - minimizedSize.x)
            minimizedOffsetY = (screenY - dragStartY).coerceIn(0f, Gdx.graphics.height - minimizedSize.y)
            minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
            Gdx.app.log("ChatWindow", "Minimized button dragged to ($minimizedOffsetX, $minimizedOffsetY)")
        } else {
            offsetX = (screenX - dragStartX).coerceIn(0f, Gdx.graphics.width - sizeX)
            offsetY = (screenY - dragStartY).coerceIn(0f, Gdx.graphics.height - sizeY)
            table.setPosition(offsetX, offsetY)
            Gdx.app.log("ChatWindow", "Dragged to ($offsetX, $offsetY)")
        }
    }

    fun stopDragging() {}

    fun isInBounds(screenX: Float, screenY: Float): Boolean {
        if (isMinimized) {
            return screenX in minimizedOffsetX..(minimizedOffsetX + minimizedSize.x) &&
                screenY in minimizedOffsetY..(minimizedOffsetY + minimizedSize.y)
        }
        return screenX in offsetX..(offsetX + sizeX) && screenY in offsetY..(offsetY + sizeY)
    }

    fun isInResizeCorner(screenX: Float, screenY: Float): Boolean {
        if (isMinimized) return false
        val left = offsetX
        val right = offsetX + sizeX
        val bottom = offsetY
        val top = offsetY + sizeY
        return when {
            screenX in left..(left + resizeCornerSize) && screenY in bottom..(bottom + resizeCornerSize) -> true
            screenX in (right - resizeCornerSize)..right && screenY in bottom..(bottom + resizeCornerSize) -> true
            screenX in left..(left + resizeCornerSize) && screenY in (top - resizeCornerSize)..top -> true
            screenX in (right - resizeCornerSize)..right && screenY in (top - resizeCornerSize)..top -> true
            else -> false
        }
    }

    fun getResizeCorner(screenX: Float, screenY: Float): String {
        if (isMinimized) return ""
        val left = offsetX
        val right = offsetX + sizeX
        val bottom = offsetY
        val top = offsetY + sizeY
        return when {
            screenX in left..(left + resizeCornerSize) && screenY in bottom..(bottom + resizeCornerSize) -> "bottom-left"
            screenX in (right - resizeCornerSize)..right && screenY in bottom..(bottom + resizeCornerSize) -> "bottom-right"
            screenX in left..(left + resizeCornerSize) && screenY in (top - resizeCornerSize)..top -> "top-left"
            screenX in (right - resizeCornerSize)..right && screenY in (top - resizeCornerSize)..top -> "top-right"
            else -> ""
        }
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
        table.setSize(sizeX, sizeY)
        scrollPane.setSize(sizeX, sizeY - 50f)
        table.setPosition(offsetX, offsetY)
        Gdx.app.log("ChatWindow", "Resized to size ($sizeX, $sizeY), offset ($offsetX, $offsetY)")
    }

    fun updateMinimizedPosition(x: Float, y: Float) {
        minimizedOffsetX = x.coerceIn(0f, Gdx.graphics.width - minimizedSize.x)
        minimizedOffsetY = y.coerceIn(0f, Gdx.graphics.height - minimizedSize.y)
        minimizedTable.setPosition(minimizedOffsetX, minimizedOffsetY)
        Gdx.app.log("ChatWindow", "Updated minimized position to ($minimizedOffsetX, $minimizedOffsetY)")
    }

    fun renderResizeCorners(camera: OrthographicCamera) {
        if (isMinimized) return
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.YELLOW
        shapeRenderer.rect(offsetX, offsetY + sizeY - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + sizeX - resizeCornerSize, offsetY + sizeY - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + sizeX - resizeCornerSize, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.end()
    }

    fun draw() {
        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    fun dispose() {
        stage.dispose()
        shapeRenderer.dispose()
    }
}
