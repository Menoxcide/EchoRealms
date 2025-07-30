// core/src/com/justin/echorealms/MyGame.kt
package com.justin.echorealms

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import kotlin.math.sign

class MyGame : ApplicationAdapter() {
    lateinit var eventBus: EventBus
    private lateinit var modLoader: ModLoader
    private lateinit var mapManager: MapManager
    private lateinit var player: Player
    lateinit var pathFinder: PathFinder
    lateinit var minimap: Minimap
    private lateinit var cameraManager: CameraManager
    private lateinit var inputHandler: GameInputHandler
    private lateinit var textRenderer: TextRenderer
    private lateinit var monsterManager: MonsterManager
    private lateinit var floatingTextManager: FloatingTextManager
    private lateinit var inventoryManager: InventoryManager
    private lateinit var levelingSystem: LevelingSystem
    private lateinit var combatManager: CombatManager
    private lateinit var movementManager: MovementManager
    lateinit var battleList: BattleList
    private lateinit var fogOfWar: FogOfWar
    private lateinit var worldMap: WorldMap
    private lateinit var chatWindow: ChatWindow
    private lateinit var uiManager: UIManager
    private var lastPlayerX = 0
    private var lastPlayerY = 0
    private var isDeadOverlayVisible = false
    val assetManager = AssetManager()
    private lateinit var gameWorldBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var mainViewport: ScreenViewport
    private lateinit var uiCamera: OrthographicCamera
    var moveDirection = Vector2(0f, 0f)
    private var dpadMoveTimer = 0f
    private val dpadMoveInterval = 0.15f

    override fun create() {
        eventBus = EventBus()
        modLoader = ModLoader(eventBus, this)
        modLoader.loadMods()

        val map = TmxMapLoader().load("map/map.tmx")
        mapManager = MapManager(map)

        floatingTextManager = FloatingTextManager()
        inventoryManager = InventoryManager()
        textRenderer = TextRenderer()
        levelingSystem = LevelingSystem(textRenderer)
        pathFinder = PathFinder(mapManager)

        assetManager.load("icons/inventory.png", Texture::class.java)
        assetManager.load("icons/character.png", Texture::class.java)
        assetManager.load("player/base/human_male.png", Texture::class.java)
        assetManager.load("icons/respawn.png", Texture::class.java)
        assetManager.load("icons/control.png", Texture::class.java)
        assetManager.load("misc/slot.png", Texture::class.java)
        assetManager.load("misc/slot_equipped.png", Texture::class.java)
        assetManager.load("ui/uiskin.json", Skin::class.java)
        assetManager.finishLoading()
        val loadedAssets = assetManager.getAssetNames().joinToString(", ")
        Gdx.app.log("AssetManager", "Loaded assets: $loadedAssets")
        if (!assetManager.isLoaded("ui/uiskin.json")) {
            Gdx.app.error("AssetManager", "Failed to load ui/uiskin.json")
        }

        cameraManager = CameraManager(mapManager.mapPixelWidth, mapManager.mapPixelHeight)
        cameraManager.camera.zoom = cameraManager.minZoom.toFloat() / 1000f

        uiCamera = OrthographicCamera().apply { setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()) }

        val skin = assetManager.get("ui/uiskin.json", Skin::class.java)
        Gdx.app.log("MyGame", "Skin loaded: ${skin != null}")
        player = Player(mapManager.tileSize, (mapManager.mapTileWidth / 2f), (mapManager.mapTileHeight / 2f), floatingTextManager, null, inventoryManager, levelingSystem, assetManager, mapManager, eventBus)
        player.cameraManager = cameraManager
        monsterManager = MonsterManager(mapManager, player, eventBus, pathFinder, floatingTextManager, assetManager, cameraManager, mapManager)
        monsterManager.loadMonsters()
        player.setMonsterManager(monsterManager)

        movementManager = MovementManager(mapManager, pathFinder, monsterManager)
        movementManager.clearPath()

        chatWindow = ChatWindow(skin, player)
        combatManager = CombatManager(player, monsterManager, floatingTextManager, inventoryManager, chatWindow)
        fogOfWar = FogOfWar(mapManager, player)
        minimap = Minimap(mapManager, player, cameraManager, skin, fogOfWar)
        worldMap = WorldMap(mapManager, player, skin, fogOfWar)
        battleList = BattleList(monsterManager, player, skin, mapManager)

        uiManager = UIManager(player, mapManager, monsterManager, cameraManager, fogOfWar, inventoryManager, assetManager, worldMap, battleList, chatWindow, minimap)
        inputHandler = GameInputHandler(cameraManager.camera, mapManager.tileSize, player, movementManager, minimap, mapManager, monsterManager, battleList, chatWindow, pathFinder, worldMap, uiManager)

        val multiplexer = com.badlogic.gdx.InputMultiplexer(uiManager.getStage(), inputHandler.gestureDetector)
        Gdx.input.inputProcessor = multiplexer

        mainViewport = ScreenViewport(cameraManager.camera)
        gameWorldBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()

        lastPlayerX = player.playerTileX
        lastPlayerY = player.playerTileY
    }

    fun clearPlayerPath() {
        movementManager.clearPath()
        Gdx.app.log("MyGame", "Cleared player movement path")
    }

    fun showMessage(text: String) {
        textRenderer.showMessage(text)
    }

    private fun updateDpad(delta: Float) {
        if (!uiManager.isDpadVisible()) return
        val pad = uiManager.getTouchpad()
        if (pad.isTouched) {
            val knobPercentX = pad.knobPercentX
            val knobPercentY = pad.knobPercentY
            moveDirection.set(knobPercentX, knobPercentY).nor()
            if (moveDirection.len() > 0.3f) {
                dpadMoveTimer += delta
                if (dpadMoveTimer >= dpadMoveInterval) {
                    val dx = sign(knobPercentX).toInt()
                    val dy = sign(knobPercentY).toInt()
                    if (dx != 0 || dy != 0) {
                        val moved = movementManager.moveInDirection(player, dx, dy, true)
                        if (moved) {
                            dpadMoveTimer -= dpadMoveInterval
                            Gdx.app.log("MyGame", "DPAD movement: dx=$dx, dy=$dy, success: $moved")
                        } else {
                            Gdx.app.log("MyGame", "DPAD movement failed: dx=$dx, dy=$dy")
                        }
                    }
                }
            }
        } else {
            moveDirection.set(0f, 0f)
            dpadMoveTimer = 0f
        }
        movementManager.updateContinuousDirection(player, delta)
    }

    private fun renderDebugOverlay() {
        if (!uiManager.isDebugOverlayVisible()) return
        shapeRenderer.projectionMatrix = cameraManager.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        val viewportWidth = cameraManager.camera.viewportWidth * cameraManager.camera.zoom
        val viewportHeight = cameraManager.camera.viewportHeight * cameraManager.camera.zoom
        val minX = maxOf(0f, (cameraManager.camera.position.x - viewportWidth / 2) / mapManager.tileSize).toInt()
        val maxX = minOf((mapManager.mapTileWidth - 1).toFloat(), (cameraManager.camera.position.x + viewportWidth / 2) / mapManager.tileSize).toInt()
        val minY = maxOf(0f, (cameraManager.camera.position.y - viewportHeight / 2) / mapManager.tileSize).toInt()
        val maxY = minOf((mapManager.mapTileHeight - 1).toFloat(), (cameraManager.camera.position.y + viewportHeight / 2) / mapManager.tileSize).toInt()
        val monsterSnapshot = monsterManager.getMonsters()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                shapeRenderer.color = if (mapManager.isWalkable(x, y) && !monsterManager.isTileOccupied(x, y, null, null, monsterSnapshot)) {
                    Color.GREEN.cpy().apply { a = 0.3f }
                } else {
                    Color.RED.cpy().apply { a = 0.3f }
                }
                shapeRenderer.rect(x * mapManager.tileSize, y * mapManager.tileSize, mapManager.tileSize, mapManager.tileSize)
            }
        }
        shapeRenderer.end()
    }

    override fun render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        if (!player.isDead()) {
            player.update(Gdx.graphics.deltaTime, movementManager)
            combatManager.update(Gdx.graphics.deltaTime)
            if (player.playerTileX != lastPlayerX || player.playerTileY != lastPlayerY) {
                eventBus.fire("playerMoved", player.playerTileX, player.playerTileY)
                fogOfWar.update(player.playerX, player.playerY)
                lastPlayerX = player.playerTileX
                lastPlayerY = player.playerTileY
            }
            monsterManager.update(Gdx.graphics.deltaTime)
            floatingTextManager.update(Gdx.graphics.deltaTime)
            cameraManager.update(player.playerX, player.playerY, Gdx.graphics.deltaTime)

            mainViewport.apply()
            gameWorldBatch.projectionMatrix = cameraManager.camera.combined
            mapManager.render(cameraManager.camera)
            gameWorldBatch.begin()
            player.render(mapManager.tileSize, cameraManager.camera)
            monsterManager.render(cameraManager.camera, mapManager.tileSize)
            if (movementManager.getCurrentPath().size > 0) pathFinder.renderHighlight(movementManager.getCurrentPath(), mapManager.tileSize, cameraManager.camera)
            textRenderer.update(Gdx.graphics.deltaTime)
            textRenderer.render(cameraManager.camera, player.playerX, player.playerY, mapManager.tileSize)
            floatingTextManager.render(cameraManager.camera, mapManager.tileSize)
            gameWorldBatch.end()
            fogOfWar.render(cameraManager.camera, mapManager.tileSize)
            renderDebugOverlay()

            battleList.update(cameraManager.camera)

            uiManager.draw()

            battleList.renderResize(uiCamera)
            updateDpad(Gdx.graphics.deltaTime)
        }

        if (player.isDead()) {
            isDeadOverlayVisible = true
            mainViewport.apply()
            // Draw semi-transparent black overlay
            shapeRenderer.projectionMatrix = uiCamera.combined
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.color = Color(0f, 0f, 0f, 0.3f) // Reduced alpha for visibility
            shapeRenderer.rect(0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
            shapeRenderer.end()

            gameWorldBatch.projectionMatrix = uiCamera.combined
            gameWorldBatch.begin()
            val font = BitmapFont().apply { color = Color.RED; data.setScale(5f) }
            val layout = GlyphLayout(font, "You are dead")
            val textX = (Gdx.graphics.width.toFloat() - layout.width) / 2f
            val textY = (Gdx.graphics.height.toFloat() + layout.height) / 2f + 50f
            font.draw(gameWorldBatch, layout, textX, textY)
            Gdx.app.log("MyGame", "Rendering 'You are dead' at ($textX, $textY)")

            if (assetManager.isLoaded("icons/respawn.png")) {
                val respawnTexture = assetManager.get("icons/respawn.png", Texture::class.java)
                val buttonWidth = respawnTexture.width.toFloat() * 2f // Scale up for visibility
                val buttonHeight = respawnTexture.height.toFloat() * 2f
                val buttonX = (Gdx.graphics.width.toFloat() - buttonWidth) / 2f
                val buttonY = textY - 100f - buttonHeight / 2f
                gameWorldBatch.draw(respawnTexture, buttonX, buttonY, buttonWidth, buttonHeight)
                Gdx.app.log("MyGame", "Rendering respawn icon at ($buttonX, $buttonY), size ($buttonWidth, $buttonHeight)")

                if (Gdx.input.justTouched()) {
                    val touchX = Gdx.input.x.toFloat()
                    val touchY = Gdx.graphics.height.toFloat() - Gdx.input.y.toFloat()
                    Gdx.app.log("MyGame", "Touch detected at ($touchX, $touchY)")
                    if (touchX in buttonX..(buttonX + buttonWidth) && touchY in buttonY..(buttonY + buttonHeight)) {
                        player.respawn()
                        isDeadOverlayVisible = false
                        Gdx.app.log("MyGame", "Player respawned via icon click")
                    }
                }
            } else {
                Gdx.app.error("MyGame", "Respawn texture not loaded")
                val respawnLayout = GlyphLayout(font, "Respawn (Image Missing)")
                val respawnX = (Gdx.graphics.width.toFloat() - respawnLayout.width) / 2f
                val respawnY = textY - 100f
                font.draw(gameWorldBatch, respawnLayout, respawnX, respawnY)
                Gdx.app.log("MyGame", "Rendering fallback respawn text at ($respawnX, $respawnY)")

                if (Gdx.input.justTouched()) {
                    val touchX = Gdx.input.x.toFloat()
                    val touchY = Gdx.graphics.height.toFloat() - Gdx.input.y.toFloat()
                    Gdx.app.log("MyGame", "Touch detected at ($touchX, $touchY)")
                    if (touchX in respawnX..(respawnX + respawnLayout.width) && touchY in (respawnY - respawnLayout.height)..respawnY) {
                        player.respawn()
                        isDeadOverlayVisible = false
                        Gdx.app.log("MyGame", "Player respawned via text click")
                    }
                }
            }
            gameWorldBatch.end()
            font.dispose()
        } else {
            isDeadOverlayVisible = false
        }
    }

    override fun resize(width: Int, height: Int) {
        mainViewport.update(width, height, true)
        uiManager.resize(width, height)
        minimap.offsetX = minimap.offsetX.coerceIn(0f, width.toFloat() - minimap.size)
        minimap.offsetY = minimap.offsetY.coerceIn(0f, height.toFloat() - minimap.size)
        battleList.offsetX = battleList.offsetX.coerceIn(0f, width.toFloat() - battleList.size)
        battleList.offsetY = battleList.offsetY.coerceIn(0f, height.toFloat() - battleList.size)
        chatWindow.offsetX = chatWindow.offsetX.coerceIn(0f, width.toFloat() - chatWindow.sizeX)
        chatWindow.offsetY = chatWindow.offsetY.coerceIn(0f, height.toFloat() - chatWindow.sizeY)
        chatWindow.updateMinimizedPosition(
            chatWindow.minimizedOffsetX.coerceIn(0f, width.toFloat() - chatWindow.minimizedSize.x),
            chatWindow.minimizedOffsetY.coerceIn(0f, height.toFloat() - chatWindow.minimizedSize.y)
        )
        worldMap.offsetX = worldMap.offsetX.coerceIn(0f, width.toFloat() - worldMap.sizeX)
        worldMap.offsetY = worldMap.offsetY.coerceIn(0f, height.toFloat() - worldMap.sizeY)
        worldMap.window.setPosition(worldMap.offsetX, worldMap.offsetY)
        uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
        uiCamera.update()
        Gdx.app.log("MyGame", "Resized to ($width, $height)")
    }

    override fun dispose() {
        mapManager.dispose()
        player.dispose()
        minimap.dispose()
        battleList.dispose()
        textRenderer.dispose()
        monsterManager.dispose()
        floatingTextManager.dispose()
        pathFinder.dispose()
        fogOfWar.dispose()
        worldMap.dispose()
        chatWindow.dispose()
        uiManager.dispose()
        assetManager.dispose()
        gameWorldBatch.dispose()
        shapeRenderer.dispose()
    }
}
