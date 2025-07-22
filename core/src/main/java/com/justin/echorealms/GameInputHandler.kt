package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.input.GestureDetector.GestureListener
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.floor

class GameInputHandler(
    private val camera: OrthographicCamera,
    private val tileSize: Float,
    private val player: Player,
    private val movementManager: MovementManager,
    private val minimap: Minimap,
    private val mapManager: MapManager,
    private val monsterManager: MonsterManager,
    private val battleList: BattleList,
    private val chatWindow: ChatWindow,
    private val pathFinder: PathFinder,
    private val worldMap: WorldMap,
    private val uiManager: UIManager
) : GestureListener {

    val gestureDetector = GestureDetector(this)
    private var initialDistance = 0f
    private var initialZoom = 0f
    private var draggingMap = false
    private var draggingWindow: Any? = null
    private var draggingCorner: String? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var screenDragStartX = 0f
    private var screenDragStartY = 0f

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        if (player.isDead()) return false

        val screenX = x
        val screenY = Gdx.graphics.height - y

        if (uiManager.isOverUI(screenX, screenY)) {
            if (minimap.isInMinimap(screenX, screenY)) {
                draggingWindow = minimap
                if (minimap.isInResizeCorner(screenX, screenY)) {
                    draggingCorner = minimap.getResizeCorner(screenX, screenY)
                } else {
                    minimap.startDragging(screenX, screenY)
                }
                return true
            }
            if (battleList.isInBounds(screenX, screenY)) {
                draggingWindow = battleList
                if (battleList.isInResizeCorner(screenX, screenY)) {
                    draggingCorner = battleList.getResizeCorner(screenX, screenY)
                } else {
                    battleList.startDragging(screenX, screenY)
                }
                return true
            }
            if (chatWindow.isInBounds(screenX, screenY)) {
                draggingWindow = chatWindow
                if (chatWindow.isInResizeCorner(screenX, screenY)) {
                    draggingCorner = chatWindow.getResizeCorner(screenX, screenY)
                } else {
                    chatWindow.startDragging(screenX, screenY)
                }
                return true
            }
            if (worldMap.isInBounds(screenX, screenY)) {
                draggingWindow = worldMap
                if (worldMap.isInResizeCorner(screenX, screenY)) {
                    draggingCorner = worldMap.getResizeCorner(screenX, screenY)
                } else {
                    worldMap.startDragging(screenX, screenY)
                }
                return true
            }
            return false
        }

        val worldPos = camera.unproject(Vector3(x, y, 0f))
        val tileX = floor(worldPos.x / tileSize).toInt()
        val tileY = floor(worldPos.y / tileSize).toInt()

        val monsterSnapshot = monsterManager.getMonsters()
        for (i in 0 until monsterSnapshot.size) {
            val monster = monsterSnapshot[i]
            if (abs(monster.x - tileX) <= 0.5f && abs(monster.y - tileY) <= 0.5f && !monster.isDead()) {
                player.targetedMonster = monster
                Gdx.app.log("GameInputHandler", "Targeted monster: ${monster.stats.name} at ($tileX, $tileY)")
                return true
            }
        }

        if (tileX in 0 until mapManager.mapTileWidth && tileY in 0 until mapManager.mapTileHeight) {
            if (!uiManager.isWorldMapOpen()) {
                movementManager.moveToTile(player, tileX, tileY, Gdx.graphics.deltaTime)
                Gdx.app.log("GameInputHandler", "Initiated movement to ($tileX, $tileY)")
            } else {
                val path = pathFinder.findPath(
                    player.playerTileX, player.playerTileY,
                    tileX, tileY, monsterManager, monsterSnapshot
                )
                if (path.size > 0) {
                    uiManager.setWorldMapOpen(false)
                    movementManager.moveToTile(player, tileX, tileY, Gdx.graphics.deltaTime)
                    Gdx.app.log("GameInputHandler", "Initiated movement to ($tileX, $tileY) from world map")
                } else {
                    Gdx.app.log("GameInputHandler", "No valid path found to ($tileX, $tileY) from world map")
                }
            }
        }

        return true
    }

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (uiManager.isOverUI(x, Gdx.graphics.height - y)) {
            return false
        }
        return false
    }

    override fun longPress(x: Float, y: Float): Boolean {
        return false
    }

    override fun fling(velocityX: Float, velocityY: Float, button: Int): Boolean {
        return false
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        if (player.isDead()) return false

        val screenX = x
        val screenY = Gdx.graphics.height - y

        if (uiManager.isOverUI(screenX, screenY) && draggingWindow == null) {
            return false
        }

        if (draggingWindow != null) {
            when (draggingWindow) {
                is WorldMap -> {
                    if (draggingCorner != null) {
                        worldMap.resize(draggingCorner!!, deltaX, -deltaY)
                    } else {
                        worldMap.drag(screenX, screenY)
                    }
                }
                is BattleList -> {
                    if (draggingCorner != null) {
                        battleList.resize(draggingCorner!!, deltaX, -deltaY)
                    } else {
                        battleList.drag(screenX, screenY)
                    }
                }
                is ChatWindow -> {
                    if (draggingCorner != null) {
                        chatWindow.resize(draggingCorner!!, deltaX, -deltaY)
                    } else {
                        chatWindow.drag(screenX, screenY)
                    }
                }
                is Minimap -> {
                    if (draggingCorner != null) {
                        minimap.resize(draggingCorner!!, deltaX, -deltaY)
                    } else {
                        minimap.drag(screenX, screenY)
                    }
                }
            }
            return true
        }

        if (!draggingMap) {
            draggingMap = true
            screenDragStartX = x
            screenDragStartY = y
            dragStartX = camera.position.x
            dragStartY = camera.position.y
        }

        camera.position.x = dragStartX - (x - screenDragStartX) * camera.zoom
        camera.position.y = dragStartY + (y - screenDragStartY) * camera.zoom
        camera.position.x = camera.position.x.coerceIn(0f, mapManager.mapPixelWidth.toFloat())
        camera.position.y = camera.position.y.coerceIn(0f, mapManager.mapPixelHeight.toFloat())
        camera.update()
        Gdx.app.log("GameInputHandler", "Camera panned to (${camera.position.x}, ${camera.position.y})")
        return true
    }

    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        draggingMap = false
        draggingWindow = null
        draggingCorner = null
        if (draggingWindow is WorldMap) worldMap.stopDragging()
        if (draggingWindow is BattleList) battleList.stopDragging()
        if (draggingWindow is ChatWindow) chatWindow.stopDragging()
        if (draggingWindow is Minimap) minimap.stopDragging()
        Gdx.app.log("GameInputHandler", "Pan stopped")
        return true
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        if (initialDistance == 0f) {
            this.initialDistance = initialDistance
            initialZoom = camera.zoom
        }
        val ratio = initialDistance / distance
        camera.zoom = (initialZoom * ratio).coerceIn(0.5f, 2f)
        camera.update()
        Gdx.app.log("GameInputHandler", "Zoomed to ${camera.zoom}")
        return true
    }

    override fun pinch(initialPointer1: Vector2, initialPointer2: Vector2, pointer1: Vector2, pointer2: Vector2): Boolean {
        return false
    }

    override fun pinchStop() {}
}
