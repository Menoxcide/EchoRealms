package com.justin.echorealms

import com.badlogic.gdx.maps.tiled.TiledMap
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer
import com.badlogic.gdx.graphics.OrthographicCamera

class MapManager(val map: TiledMap) {
    val tileSize = 32f
    val mapTileWidth = map.properties.get("width", Int::class.java)
    val mapTileHeight = map.properties.get("height", Int::class.java)
    val mapPixelWidth = mapTileWidth * tileSize
    val mapPixelHeight = mapTileHeight * tileSize
    private val renderer = OrthogonalTiledMapRenderer(map)

    fun render(camera: OrthographicCamera) {
        renderer.setView(camera)
        renderer.render()
    }

    fun isWalkable(x: Int, y: Int): Boolean {
        if (x < 0 || x >= mapTileWidth || y < 0 || y >= mapTileHeight) return false
        val layer = map.layers.get(0) as? TiledMapTileLayer
        val cell = layer?.getCell(x, y)
        return cell != null && cell.tile.properties.get("walkable", true, Boolean::class.java)
    }

    fun dispose() {
        map.dispose()
        renderer.dispose()
    }
}
