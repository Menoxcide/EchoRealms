package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.MathUtils

class CameraManager(val mapPixelWidth: Float, val mapPixelHeight: Float) {
    val camera = OrthographicCamera()
    val minZoom = 333 // Integer scale factor (0.333 * 1000)
    var maxZoom = 1000 // Integer scale factor (1.0 * 1000)
    private val cameraLerpSpeed = 5f

    init {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.zoom = minZoom.toFloat() / 1000f
        maxZoom = Math.max((mapPixelWidth / camera.viewportWidth * 1000).toInt(), (mapPixelHeight / camera.viewportHeight * 1000).toInt())
    }

    fun update(camTargetTileX: Float, camTargetTileY: Float, delta: Float) {
        val tileSize = 32f
        val camTargetX = camTargetTileX * tileSize + tileSize / 2f
        val camTargetY = camTargetTileY * tileSize + tileSize / 2f
        camera.position.x = MathUtils.lerp(camera.position.x, camTargetX, delta * cameraLerpSpeed)
        camera.position.y = MathUtils.lerp(camera.position.y, camTargetY, delta * cameraLerpSpeed)

        // Clamp camera to map boundaries to prevent showing blank space
        val halfWidth = (camera.viewportWidth * (camera.zoom / 1000f)) / 2f
        val halfHeight = (camera.viewportHeight * (camera.zoom / 1000f)) / 2f
        camera.position.x = MathUtils.clamp(camera.position.x, halfWidth, mapPixelWidth - halfWidth)
        camera.position.y = MathUtils.clamp(camera.position.y, halfHeight, mapPixelHeight - halfHeight)

        // Ensure camera doesn't show beyond map edges
        camera.position.x = MathUtils.clamp(camera.position.x, 0f, mapPixelWidth)
        camera.position.y = MathUtils.clamp(camera.position.y, 0f, mapPixelHeight)

        camera.update()
    }
}
