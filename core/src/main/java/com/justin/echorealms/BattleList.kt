package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.OrthographicCamera
import kotlin.math.abs
import kotlin.math.max

class BattleList(
    private val monsterManager: MonsterManager,
    private val player: Player,
    private val skin: Skin
) {
    val table = Table()
    var offsetX = Gdx.graphics.width - 200f - 10f
    var offsetY = Gdx.graphics.height - 200f - 210f
    var size = 200f
    private var dragOffset = Vector2()
    private val resizeCornerSize = 20f
    private val minSize = 150f
    private val maxSize = minOf(Gdx.graphics.width * 0.5f, Gdx.graphics.height * 0.5f)

    init {
        table.setSize(size, size)
        table.setPosition(offsetX, offsetY)
        table.background = skin.getDrawable("window")
        table.isVisible = true
        table.addListener(object : DragListener() {
            override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                dragOffset.set(x, y)
                Gdx.app.log("BattleList", "Started dragging at ($x, $y)")
            }
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val newX = (table.x + (x - dragOffset.x)).coerceIn(0f, Gdx.graphics.width.toFloat() - size)
                val newY = (table.y + (y - dragOffset.y)).coerceIn(0f, Gdx.graphics.height.toFloat() - size)
                offsetX = newX
                offsetY = newY
                table.setPosition(offsetX, offsetY)
                Gdx.app.log("BattleList", "Dragged to ($offsetX, $offsetY)")
            }
        })
    }

    fun isInBounds(x: Float, y: Float): Boolean {
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
        Gdx.app.log("BattleList", "Started dragging at ($x, $y)")
    }

    fun drag(x: Float, y: Float) {
        // Handled by DragListener
    }

    fun stopDragging() {
        Gdx.app.log("BattleList", "Stopped dragging")
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
        table.setSize(size, size)
        table.setPosition(offsetX, offsetY)
        Gdx.app.log("BattleList", "Resized to size ($size, $size), offset ($offsetX, $offsetY)")
    }

    fun render(camera: OrthographicCamera) {
        if (!table.isVisible) return
        table.clear()
        var y = size - 80f
        monsterManager.getMonsters().forEach { monster ->
            val dist = max(abs(player.playerTileX - monster.x.toInt()), abs(player.playerTileY - monster.y.toInt()))
            if (dist <= 15) {
                val hpPercentage = monster.currentHp.toFloat() / monster.stats.hp.toFloat()
                val hpBarStyle = skin.get("default-horizontal", ProgressBar.ProgressBarStyle::class.java)
                val hpBar = ProgressBar(0f, 1f, 0.01f, false, hpBarStyle)
                hpBar.value = hpPercentage
                hpBar.setSize(size - 100f, 60f)
                val isTargeted = monster == player.targetedMonster
                val label = Label("${monster.stats.name} (${monster.currentHp}/${monster.stats.hp})", skin, if (isTargeted) "white" else "default")
                table.add(Image(monster.sprite.texture)).size(80f, 80f).pad(10f)
                table.add(hpBar).width(size - 100f).height(60f).pad(10f)
                table.add(label).pad(10f)
                table.row()
                y -= 100f
            }
        }
        // Render resize corners
        val shapeRenderer = ShapeRenderer()
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(offsetX, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.end()
        shapeRenderer.dispose()
        Gdx.app.log("BattleList", "Rendered at ($offsetX, $offsetY), size ($size, $size)")
    }

    fun dispose() {
        // No need to dispose stage since it's managed by UIManager
    }
}
