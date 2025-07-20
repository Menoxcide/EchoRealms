package com.justin.echorealms

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.Gdx

class TextRenderer {
    private val font = BitmapFont().apply {
        data.setScale(1.5f) // Adjusted for readability
        color = Color.WHITE // Default color
    }
    private val batch = SpriteBatch()
    private var message: String? = null
    private var messageTimer = 0f
    private val messageDuration = 5f

    fun update(delta: Float) {
        messageTimer = (messageTimer - delta).coerceAtLeast(0f)
        if (messageTimer <= 0f) {
            message = null
        }
    }

    fun render(camera: OrthographicCamera, playerX: Float, playerY: Float, tileSize: Float) {
        message?.let { msg ->
            batch.projectionMatrix = camera.combined
            batch.begin()
            val layout = GlyphLayout(font, msg)
            val x = playerX * tileSize - layout.width / 2
            val y = (playerY + 1) * tileSize + layout.height
            font.draw(batch, layout, x, y)
            batch.end()
        }
    }

    fun showMessage(text: String) {
        message = text
        messageTimer = messageDuration
        Gdx.app.log("TextRenderer", "Showing message: $text")
    }

    fun showLevelUpMessage(text: String, level: Int) {
        message = "$text (Level $level)"
        messageTimer = messageDuration
        font.color = Color.CHARTREUSE // Chartreuse for level-up
        Gdx.app.log("TextRenderer", "Showing level-up message: $text (Level $level)")
    }

    fun renderHeadMessage(camera: OrthographicCamera, playerX: Float, playerY: Float, tileSize: Float, message: String) {
        batch.projectionMatrix = camera.combined
        batch.begin()
        val layout = GlyphLayout(font, message)
        val x = playerX * tileSize - layout.width / 2
        val y = (playerY + 1) * tileSize + layout.height + 10f
        font.draw(batch, layout, x, y)
        batch.end()
        Gdx.app.log("TextRenderer", "Rendered head message: $message at ($x, $y)")
    }

    fun dispose() {
        font.dispose()
        batch.dispose()
    }
}
