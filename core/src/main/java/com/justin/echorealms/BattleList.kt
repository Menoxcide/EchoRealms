package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import kotlin.math.abs
import kotlin.math.max

class BattleList(
    private val monsterManager: MonsterManager,
    private val player: Player,
    private val camera: OrthographicCamera
) {
    var offsetX = Gdx.graphics.width - 200f - 10f // Anchored below minimap
    var offsetY = Gdx.graphics.height - 200f - 210f
    var size = 200f // Single size for square
    private var dragOffset = Vector2()
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val font = BitmapFont().apply { color = Color.WHITE; data.setScale(1.2f) }
    private val layout = GlyphLayout()
    private val resizeCornerSize = 20f
    private val minSize = 150f
    private val maxSize = minOf(Gdx.graphics.width * 0.5f, Gdx.graphics.height * 0.5f)

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
        offsetX = (x - dragOffset.x).coerceIn(0f, Gdx.graphics.width - size)
        offsetY = (y - dragOffset.y).coerceIn(0f, Gdx.graphics.height - size)
        Gdx.app.log("BattleList", "Dragged to ($offsetX, $offsetY)")
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
        offsetX = offsetX.coerceIn(0f, Gdx.graphics.width - size)
        offsetY = offsetY.coerceIn(0f, Gdx.graphics.height - size)
        Gdx.app.log("BattleList", "Resized to size ($size, $size), offset ($offsetX, $offsetY)")
    }

    fun render() {
        batch.projectionMatrix = camera.combined
        shapeRenderer.projectionMatrix = camera.combined

        // Draw background
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(offsetX, offsetY, size, size)
        val borderThickness = size / 50f
        shapeRenderer.color = Color.WHITE
        shapeRenderer.rectLine(offsetX, offsetY, offsetX + size, offsetY, borderThickness)
        shapeRenderer.rectLine(offsetX, offsetY, offsetX, offsetY + size, borderThickness)
        shapeRenderer.rectLine(offsetX + size, offsetY, offsetX + size, offsetY + size, borderThickness)
        shapeRenderer.rectLine(offsetX, offsetY + size, offsetX + size, offsetY + size, borderThickness)
        shapeRenderer.end()

        // Draw HP bars
        shapeRenderer.begin(ShapeType.Filled)
        var y = offsetY + size - 20f
        monsterManager.getMonsters().forEach { monster ->
            val dist = max(abs(player.playerTileX - monster.x.toInt()), abs(player.playerTileY - monster.y.toInt()))
            if (dist <= 15) {
                val hpPercentage = monster.currentHp.toFloat() / monster.stats.hp.toFloat()
                val hpBarColor = when {
                    hpPercentage > 0.5f -> Color.GREEN
                    hpPercentage > 0.2f -> Color.YELLOW
                    else -> Color.RED
                }
                val barWidth = size - 25f
                val barHeight = 15f
                val hpWidth = barWidth * hpPercentage
                shapeRenderer.color = Color.BLACK
                shapeRenderer.rect(offsetX + 20f, y - 15f, barWidth, barHeight)
                shapeRenderer.color = hpBarColor
                shapeRenderer.rect(offsetX + 20f, y - 15f, hpWidth, barHeight)
                y -= 25f
            }
        }
        shapeRenderer.end()

        // Draw sprites and names
        batch.begin()
        y = offsetY + size - 20f
        monsterManager.getMonsters().forEach { monster ->
            val dist = max(abs(player.playerTileX - monster.x.toInt()), abs(player.playerTileY - monster.y.toInt()))
            if (dist <= 15) {
                val isTargeted = monster == player.targetedMonster
                font.color = if (isTargeted) Color.YELLOW else Color.WHITE
                monster.sprite.setSize(20f, 20f)
                monster.sprite.setPosition(offsetX + 5f, y - 20f)
                monster.sprite.draw(batch)
                val text = "${monster.stats.name} (${monster.currentHp}/${monster.stats.hp})"
                layout.setText(font, text)
                font.draw(batch, text, offsetX + 30f, y - 5f)
                y -= 25f
            }
        }
        batch.end()

        // Draw resize corners
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(offsetX, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.end()

        Gdx.app.log("BattleList", "Rendered at ($offsetX, $offsetY), size ($size, $size)")
    }

    fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }
}
