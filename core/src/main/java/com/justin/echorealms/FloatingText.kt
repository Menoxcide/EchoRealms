package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.Array

class FloatingTextManager {
    private val texts = Array<FloatingText>()
    private val batch = SpriteBatch()
    private val font = BitmapFont()

    init {
        font.color = Color.RED
        font.data.setScale(1.5f) // Slightly larger for visibility
    }

    fun addText(text: String, x: Float, y: Float, isCritical: Boolean = false) {
        texts.add(FloatingText(text, x, y, isCritical = isCritical))
    }

    fun update(delta: Float) {
        val iterator = texts.iterator()
        while (iterator.hasNext()) {
            val ft = iterator.next()
            ft.update(delta)
            if (ft.lifetime <= 0) {
                iterator.remove()
            }
        }
    }

    fun render(camera: OrthographicCamera, tileSize: Float) {
        batch.projectionMatrix = camera.combined
        batch.begin()
        texts.forEach { ft ->
            font.color = if (ft.isCritical) Color.YELLOW else Color.RED
            font.color.a = ft.lifetime
            font.draw(batch, ft.text, ft.x * tileSize, (ft.y * tileSize) + ft.offsetY + 50f)
        }
        batch.end()
        font.color.a = 1f
    }

    fun dispose() {
        batch.dispose()
        font.dispose()
    }
}

data class FloatingText(val text: String, var x: Float, var y: Float, var lifetime: Float = 1f, var offsetY: Float = 0f, val isCritical: Boolean = false) {
    fun update(delta: Float) {
        lifetime -= delta
        offsetY += delta * 20f
    }
}
