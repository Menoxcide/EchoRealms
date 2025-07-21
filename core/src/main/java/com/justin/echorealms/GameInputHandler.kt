// core/src/main/java/com/justin/echorealms/GameInputHandler.kt
package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.Timer
import com.badlogic.gdx.input.GestureDetector.GestureAdapter
import kotlin.math.abs
import kotlin.math.max
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
    private val worldMap: WorldMap
) : GestureAdapter() {
    val gestureDetector = GestureDetector(this)
    private var isDraggingMinimap = false
    private var isDraggingBattleList = false
    private var isDraggingChatWindow = false
    private var isDraggingWorldMap = false
    private var isResizingMinimap = false
    private var isResizingBattleList = false
    private var isResizingChatWindow = false
    private var isResizingWorldMap = false
    private var minimapResizeCorner = ""
    private var battleListResizeCorner = ""
    private var chatWindowResizeCorner = ""
    private var worldMapResizeCorner = ""
    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val longPressDuration = 1.0f
    private var longPressTask: Timer.Task? = null
    private val clickForgiveness = 1f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private val panDeltaThreshold = 5f

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (player.isDead()) {
            Gdx.app.log("GameInputHandler", "Tap ignored: player is dead")
            return false
        }
        val screenY = Gdx.graphics.height - y
        Gdx.app.log("GameInputHandler", "Tap detected at screen ($x, $screenY), count: $count, button: $button")
        if (isDraggingMinimap || isDraggingBattleList || isDraggingChatWindow || isDraggingWorldMap || isResizingMinimap || isResizingBattleList || isResizingChatWindow || isResizingWorldMap) {
            Gdx.app.log("GameInputHandler", "Tap ignored: dragging or resizing in progress")
            return false
        }
        if ((Gdx.app.applicationListener as? MyGame)?.isOverUI(x, screenY) == true) {
            Gdx.app.log("GameInputHandler", "Tap ignored: over UI")
            return false
        }
        if (minimap.isInMinimap(x, screenY)) {
            val minimapTileSize = minimap.getTileSize()
            val tileX = floor((x - minimap.offsetX) / minimapTileSize + minimap.minTileX).toInt()
            val tileY = floor((screenY - minimap.offsetY) / minimapTileSize + minimap.minTileY).toInt()
            if (tileX in 0 until mapManager.mapTileWidth && tileY in 0 until mapManager.mapTileHeight) {
                movementManager.clearPath()
                val (validTileX, validTileY) = movementManager.findNearestWalkableTile(tileX, tileY)
                if (validTileX == -1 || validTileY == -1) {
                    Gdx.app.log("GameInputHandler", "No walkable tile found near minimap tile ($tileX, $tileY)")
                    chatWindow.addMessage("No walkable tile near ($tileX, $tileY)", "General")
                    return true
                }
                val path = pathFinder.findPath(player.playerTileX, player.playerTileY, validTileX, validTileY, monsterManager)
                if (path.size > 0) {
                    movementManager.getCurrentPath().clear()
                    movementManager.getCurrentPath().addAll(path)
                    player.setTargetTile(validTileX, validTileY)
                    player.moveProgress = 0f
                    (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f)
                    Gdx.app.log("GameInputHandler", "Minimap tap initiated movement to tile ($validTileX, $validTileY)")
                    chatWindow.addMessage("Moving to tile ($validTileX, $validTileY)", "General")
                    return true
                } else {
                    Gdx.app.log("GameInputHandler", "No valid path to minimap tile ($validTileX, $validTileY)")
                    chatWindow.addMessage("No path to tile ($validTileX, $validTileY)", "General")
                }
            } else {
                Gdx.app.log("GameInputHandler", "Minimap tap ignored: tile ($tileX, $tileY) out of bounds")
                chatWindow.addMessage("Tile ($tileX, $tileY) out of bounds", "General")
            }
            return true
        }
        val touch = Vector3(x, screenY, 0f)
        camera.unproject(touch)
        val tileX = floor(touch.x / tileSize).toInt()
        val tileY = floor((mapManager.mapPixelHeight - touch.y) / tileSize).toInt()
        Gdx.app.log("GameInputHandler", "Tap mapped to tile ($tileX, $tileY), world coords (${touch.x}, ${touch.y})")

        var monsterClicked: Monster? = null
        monsterManager.getMonsters().forEach { monster ->
            val dx = abs((monster.x * tileSize) - touch.x)
            val dy = abs((monster.y * tileSize) - (mapManager.mapPixelHeight - touch.y))
            val dist = max(dx, dy) / tileSize
            if (dist <= clickForgiveness) {
                monsterClicked = monster
            }
        }
        if (monsterClicked != null) {
            if (count == 2) {
                val description = "A hostile ${monsterClicked!!.stats.name} ready to fight."
                chatWindow.addMessage("Examine: $description", "General")
                Gdx.app.log("GameInputHandler", "Examined monster: ${monsterClicked!!.stats.name}")
                return true
            } else {
                if (player.targetedMonster == monsterClicked) {
                    player.targetedMonster = null
                    Gdx.app.log("GameInputHandler", "Untargeted monster: ${monsterClicked!!.stats.name}")
                } else {
                    player.targetedMonster = monsterClicked
                    Gdx.app.log("GameInputHandler", "Targeted monster: ${monsterClicked!!.stats.name}")
                }
                return true
            }
        }

        if (count == 2) {
            val description = "A standard game tile."
            chatWindow.addMessage("Examine: $description", "General")
            Gdx.app.log("GameInputHandler", "Examined tile ($tileX, $tileY)")
            return true
        }

        if (tileX in 0 until mapManager.mapTileWidth && tileY in 0 until mapManager.mapTileHeight &&
            camera.frustum.pointInFrustum(touch.x, touch.y, 0f)) {
            movementManager.clearPath()
            val (validTileX, validTileY) = movementManager.findNearestWalkableTile(tileX, tileY)
            if (validTileX == -1 || validTileY == -1) {
                Gdx.app.log("GameInputHandler", "No walkable tile found near ($tileX, $tileY)")
                chatWindow.addMessage("No walkable tile near ($tileX, $tileY)", "General")
                return true
            }
            val path = pathFinder.findPath(player.playerTileX, player.playerTileY, validTileX, validTileY, monsterManager)
            if (path.size > 0) {
                movementManager.getCurrentPath().clear()
                movementManager.getCurrentPath().addAll(path)
                player.setTargetTile(validTileX, validTileY)
                player.moveProgress = 0f
                (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f)
                Gdx.app.log("GameInputHandler", "Initiated movement to tile ($validTileX, $validTileY)")
                chatWindow.addMessage("Moving to tile ($validTileX, $validTileY)", "General")
                return true
            } else {
                Gdx.app.log("GameInputHandler", "No valid path to tile ($validTileX, $validTileY)")
                chatWindow.addMessage("No path to tile ($validTileX, $validTileY)", "General")
            }
        } else {
            Gdx.app.log("GameInputHandler", "Tap ignored: tile ($tileX, $tileY) is out of bounds or not in frustum")
            chatWindow.addMessage("Cannot move to tile ($tileX, $tileY): out of bounds or not in frustum", "General")
        }
        longPressTask?.cancel()
        longPressTask = null
        Gdx.app.log("GameInputHandler", "Canceled long press timer on tap")
        return true
    }

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        if (player.isDead()) {
            Gdx.app.log("GameInputHandler", "Touch down ignored: player is dead")
            return false
        }
        Gdx.app.log("GameInputHandler", "Touch down at ($x, ${Gdx.graphics.height - y}), pointer: $pointer, button: $button")
        val screenY = Gdx.graphics.height - y
        if (minimap.isInMinimap(x, screenY)) {
            if (minimap.isInResizeCorner(x, screenY)) {
                isResizingMinimap = true
                minimapResizeCorner = minimap.getResizeCorner(x, screenY)
                Gdx.app.log("GameInputHandler", "Started resizing minimap at corner: $minimapResizeCorner")
            } else {
                isDraggingMinimap = true
                minimap.startDragging(x, screenY)
                Gdx.app.log("GameInputHandler", "Started dragging minimap")
            }
            return true
        } else if (battleList.isInBounds(x, screenY)) {
            if (battleList.isInResizeCorner(x, screenY)) {
                isResizingBattleList = true
                battleListResizeCorner = battleList.getResizeCorner(x, screenY)
                Gdx.app.log("GameInputHandler", "Started resizing battle list at corner: $battleListResizeCorner")
            } else {
                isDraggingBattleList = true
                battleList.startDragging(x, screenY)
                Gdx.app.log("GameInputHandler", "Started dragging battle list")
            }
            return true
        } else if (chatWindow.isInBounds(x, screenY)) {
            if (chatWindow.isInResizeCorner(x, screenY)) {
                isResizingChatWindow = true
                chatWindowResizeCorner = chatWindow.getResizeCorner(x, screenY)
                Gdx.app.log("GameInputHandler", "Started resizing chat window at corner: $chatWindowResizeCorner")
            } else {
                isDraggingChatWindow = true
                chatWindow.startDragging(x, screenY)
                Gdx.app.log("GameInputHandler", "Started dragging chat window")
            }
            return true
        } else if (worldMap.isInBounds(x, screenY)) {
            if (worldMap.isInResizeCorner(x, screenY)) {
                isResizingWorldMap = true
                worldMapResizeCorner = worldMap.getResizeCorner(x, screenY)
                Gdx.app.log("GameInputHandler", "Started resizing world map at corner: $worldMapResizeCorner")
            } else {
                isDraggingWorldMap = true
                worldMap.startDragging(x, screenY)
                Gdx.app.log("GameInputHandler", "Started dragging world map")
            }
            return true
        } else {
            touchDownTime = System.currentTimeMillis()
            touchDownX = x
            touchDownY = screenY
            lastPanX = x
            lastPanY = screenY
            return false
        }
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        if (player.isDead()) {
            Gdx.app.log("GameInputHandler", "Pan ignored: player is dead")
            return false
        }
        val screenY = Gdx.graphics.height - y
        if (abs(x - lastPanX) > panDeltaThreshold || abs(screenY - lastPanY) > panDeltaThreshold) {
            Gdx.app.log("GameInputHandler", "Pan at ($x, ${Gdx.graphics.height - y}), delta: ($deltaX, $deltaY)")
            lastPanX = x
            lastPanY = screenY
        }
        if (isDraggingMinimap) {
            minimap.drag(x, screenY)
            return true
        } else if (isDraggingBattleList) {
            battleList.drag(x, screenY)
            return true
        } else if (isDraggingChatWindow) {
            chatWindow.drag(x, screenY)
            return true
        } else if (isDraggingWorldMap) {
            worldMap.drag(x, screenY)
            return true
        } else if (isResizingMinimap) {
            minimap.resize(minimapResizeCorner, deltaX, -deltaY)
            return true
        } else if (isResizingBattleList) {
            battleList.resize(battleListResizeCorner, deltaX, -deltaY)
            return true
        } else if (isResizingChatWindow) {
            chatWindow.resize(chatWindowResizeCorner, deltaX, -deltaY)
            return true
        } else if (isResizingWorldMap) {
            worldMap.resize(worldMapResizeCorner, deltaX, -deltaY)
            return true
        }
        return false
    }

    override fun panStop(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        if (player.isDead()) {
            Gdx.app.log("GameInputHandler", "Pan stop ignored: player is dead")
            return false
        }
        Gdx.app.log("GameInputHandler", "Pan stop at ($x, ${Gdx.graphics.height - y}), pointer: $pointer, button: $button")
        if (isDraggingMinimap) {
            isDraggingMinimap = false
            minimap.stopDragging()
            Gdx.app.log("GameInputHandler", "Stopped dragging minimap")
        }
        if (isDraggingBattleList) {
            isDraggingBattleList = false
            battleList.stopDragging()
            Gdx.app.log("GameInputHandler", "Stopped dragging battle list")
        }
        if (isDraggingChatWindow) {
            isDraggingChatWindow = false
            chatWindow.stopDragging()
            Gdx.app.log("GameInputHandler", "Stopped dragging chat window")
        }
        if (isDraggingWorldMap) {
            isDraggingWorldMap = false
            worldMap.stopDragging()
            Gdx.app.log("GameInputHandler", "Stopped dragging world map")
        }
        if (isResizingMinimap) {
            isResizingMinimap = false
            Gdx.app.log("GameInputHandler", "Stopped resizing minimap")
        }
        if (isResizingBattleList) {
            isResizingBattleList = false
            Gdx.app.log("GameInputHandler", "Stopped resizing battle list")
        }
        if (isResizingChatWindow) {
            isResizingChatWindow = false
            Gdx.app.log("GameInputHandler", "Stopped resizing chat window")
        }
        if (isResizingWorldMap) {
            isResizingWorldMap = false
            Gdx.app.log("GameInputHandler", "Stopped resizing world map")
        }
        return false
    }
}
