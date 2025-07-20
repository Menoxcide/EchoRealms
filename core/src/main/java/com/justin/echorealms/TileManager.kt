// core/src/com.justin.echorealms/TileManager.kt
// Kept as is; may be useful later for dynamic tiles or entities
package com.justin.echorealms;

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Array

class TileManager(private val tileset: Texture) {
    private val tileSize = 32 // Tile width/height
    private val columns = tileset.width / tileSize // Number of columns in tileset

    // Example: Get all tiles from a row (e.g., row 0 for ground)
    fun getTilesFromRow(row: Int, startCol: Int, numTiles: Int): Array<TextureRegion> {
        val tiles = Array<TextureRegion>()
        for (col in startCol until startCol + numTiles) {
            tiles.add(TextureRegion(tileset, col * tileSize, row * tileSize, tileSize, tileSize))
        }
        return tiles
    }

    // Random tile from an array (for variation)
    fun getRandomTile(tiles: Array<TextureRegion>): TextureRegion {
        return tiles.random()
    }
}
