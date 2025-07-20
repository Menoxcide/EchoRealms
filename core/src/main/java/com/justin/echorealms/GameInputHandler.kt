package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
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
    private val pathFinder: PathFinder
) : GestureAdapter() {
    val gestureDetector = GestureDetector(this)
    private var isDraggingMinimap = false
    private var isDraggingBattleList = false
    private var isDraggingChatWindow = false
    private var isResizingMinimap = false
    private var isResizingBattleList = false
    private var isResizingChatWindow = false
    private var minimapResizeCorner = ""
    private var battleListResizeCorner = ""
    private var chatWindowResizeCorner = ""
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
        Gdx.app.log("GameInputHandler", "Tap detected at screen ($x, $y), count: $count, button: $button")
        if (isDraggingMinimap || isDraggingBattleList || isDraggingChatWindow || isResizingMinimap || isResizingBattleList || isResizingChatWindow) {
            Gdx.app.log("GameInputHandler", "Tap ignored: dragging or resizing in progress")
            return false
        }
        val screenY = Gdx.graphics.height - y
        if ((Gdx.app.applicationListener as? MyGame)?.isOverUI(x, screenY) == true) {
            Gdx.app.log("GameInputHandler", "Tap ignored: over UI")
            return false
        }
        if (minimap.isInMinimap(x, screenY)) {
            // Minimap click-to-move
            val tileSize = minimap.size / minOf(mapManager.mapTileWidth, mapManager.mapTileHeight)
            val tileX = floor((x - minimap.offsetX) / tileSize).toInt()
            val tileY = floor((screenY - minimap.offsetY) / tileSize).toInt()
            if (mapManager.isWalkable(tileX, tileY) && !monsterManager.isTileOccupied(tileX, tileY)) {
                movementManager.clearPath()
                val path = pathFinder.findPath(player.playerTileX, player.playerTileY, tileX, tileY, monsterManager)
                if (path.size > 0) {
                    movementManager.getCurrentPath().clear()
                    movementManager.getCurrentPath().addAll(path)
                    player.setTargetTile(tileX, tileY)
                    player.moveProgress = 0f // Reset interpolation
                    (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f) // Clear DPAD
                    Gdx.app.log("GameInputHandler", "Minimap tap initiated movement to tile ($tileX, $tileY)")
                    return true
                } else {
                    Gdx.app.log("GameInputHandler", "No valid path to minimap tile ($tileX, $tileY)")
                }
            }
            return true
        }
        val touch = Vector3(x, screenY, 0f)
        camera.unproject(touch)
        val tileX = floor(touch.x / tileSize).toInt()
        val tileY = floor((mapManager.mapPixelHeight - touch.y) / tileSize).toInt()
        Gdx.app.log("GameInputHandler", "Tap mapped to tile ($tileX, $tileY)")

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

        if (mapManager.isWalkable(tileX, tileY) && !monsterManager.isTileOccupied(tileX, tileY) && camera.frustum.pointInFrustum(touch.x, touch.y, 0f)) {
            movementManager.clearPath()
            val path = pathFinder.findPath(player.playerTileX, player.playerTileY, tileX, tileY, monsterManager)
            if (path.size > 0) {
                movementManager.getCurrentPath().clear()
                movementManager.getCurrentPath().addAll(path)
                player.setTargetTile(tileX, tileY)
                player.moveProgress = 0f // Reset interpolation
                (Gdx.app.applicationListener as? MyGame)?.moveDirection?.set(0f, 0f) // Clear DPAD
                Gdx.app.log("GameInputHandler", "Initiated movement to tile ($tileX, $tileY)")
                return true
            } else {
                Gdx.app.log("GameInputHandler", "No valid path to tile ($tileX, $tileY)")
            }
        } else {
            Gdx.app.log("GameInputHandler", "Tap ignored: tile ($tileX, $tileY) is unwalkable, occupied, or out of viewport")
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
        } else {
            touchDownTime = System.currentTimeMillis()
            touchDownX = x
            touchDownY = screenY
            lastPanX = x
            lastPanY = screenY
            longPressTask = object : Timer.Task() {
                override fun run() {
                    if (System.currentTimeMillis() - touchDownTime >= longPressDuration * 1000 && !isDraggingMinimap && !isDraggingBattleList && !isDraggingChatWindow && !isResizingMinimap && !isResizingBattleList && !isResizingChatWindow && !(Gdx.app.applicationListener as? MyGame)?.isOverUI(touchDownX, Gdx.graphics.height - touchDownY)!!) {
                        (Gdx.app.applicationListener as? MyGame)?.showDpad(touchDownX, touchDownY)
                        Gdx.app.log("GameInputHandler", "Long press detected, showing DPAD at ($touchDownX, $touchDownY)")
                    }
                    longPressTask = null
                }
            }
            Timer.schedule(longPressTask, longPressDuration)
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
        } else if (isResizingMinimap) {
            minimap.resize(minimapResizeCorner, deltaX, -deltaY)
            return true
        } else if (isResizingBattleList) {
            battleList.resize(battleListResizeCorner, deltaX, -deltaY)
            return true
        } else if (isResizingChatWindow) {
            chatWindow.resize(chatWindowResizeCorner, deltaX, -deltaY)
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
        longPressTask?.cancel()
        longPressTask = null
        Gdx.app.log("GameInputHandler", "Canceled long press timer on pan stop")
        return false
    }
}
