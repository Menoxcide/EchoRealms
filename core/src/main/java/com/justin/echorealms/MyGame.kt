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
import com.badlogic.gdx.maps.tiled.TiledMap
import com.badlogic.gdx.maps.tiled.TmxMapLoader
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.utils.Timer
import kotlin.math.sign

class MyGame : ApplicationAdapter() {
    private lateinit var eventBus: EventBus
    private lateinit var modLoader: ModLoader
    private lateinit var mapManager: MapManager
    private lateinit var player: Player
    lateinit var pathFinder: PathFinder
    lateinit var minimap: Minimap
    private lateinit var cameraManager: CameraManager
    private lateinit var inputHandler: GameInputHandler
    private lateinit var mainViewport: ScreenViewport
    private lateinit var textRenderer: TextRenderer
    private lateinit var monsterManager: MonsterManager
    private lateinit var floatingTextManager: FloatingTextManager
    private lateinit var inventoryManager: InventoryManager
    private lateinit var levelingSystem: LevelingSystem
    private lateinit var combatManager: CombatManager
    private lateinit var movementManager: MovementManager
    lateinit var battleList: BattleList
    private var lastPlayerX = 0
    private var lastPlayerY = 0
    val assetManager = AssetManager()
    private lateinit var gameWorldBatch: SpriteBatch
    lateinit var uiBatch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private var isInventoryOpen = false
    private var isCharacterSheetOpen = false
    private var isDeadOverlayVisible = false
    private lateinit var chatWindow: ChatWindow
    private lateinit var gameWorldStage: Stage
    private lateinit var uiStage: Stage
    private lateinit var uiCamera: OrthographicCamera
    private var touchpad: Touchpad? = null
    private var dpadVisible = false
    private var dpadLastTouchTime = 0L
    private val dpadTimeout = 5000L
    private lateinit var skin: Skin
    var moveDirection = Vector2(0f, 0f)
    private lateinit var inventoryWindow: Window
    private lateinit var characterWindow: Window
    private lateinit var chatTable: Table
    private lateinit var inventoryIcon: Image
    private lateinit var characterIcon: Image
    private var dpadMoveTimer = 0f
    private val dpadMoveInterval = 0.25f
    private val uiBoundaryPadding = 10f

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
        assetManager.load("ui/uiskin.json", Skin::class.java)
        assetManager.finishLoading()
        Gdx.app.log("AssetManager", "Loaded assets: ${assetManager.getAssetNames().joinToString(", ")}")

        cameraManager = CameraManager(mapManager.mapPixelWidth, mapManager.mapPixelHeight)
        cameraManager.camera.zoom = cameraManager.minZoom.toFloat() / 1000f

        uiCamera = OrthographicCamera().apply { setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat()) }
        skin = assetManager.get("ui/uiskin.json", Skin::class.java)
        gameWorldStage = Stage(ScreenViewport())
        uiStage = Stage(ScreenViewport())

        inventoryWindow = Window("Inventory", skin)
        inventoryWindow.setSize(300f * 2f, 400f * 2f)
        inventoryWindow.setPosition((Gdx.graphics.width - 300f * 2f) / 2f, (Gdx.graphics.height - 400f * 2f) / 2f)
        inventoryWindow.isMovable = true
        inventoryWindow.color = Color(1f, 1f, 1f, 0.5f)
        inventoryWindow.isVisible = false
        inventoryWindow.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val newX = (inventoryWindow.getX() + x - inventoryWindow.width / 2).coerceIn(0f, Gdx.graphics.width - inventoryWindow.width)
                val newY = (inventoryWindow.getY() + y - inventoryWindow.height / 2).coerceIn(0f, Gdx.graphics.height - inventoryWindow.height)
                inventoryWindow.setPosition(newX, newY)
                Gdx.app.log("MyGame", "Inventory window dragged to ($newX, $newY)")
            }
        })
        uiStage.addActor(inventoryWindow)

        characterWindow = Window("Character Sheet", skin)
        characterWindow.setSize(400f * 2f, 500f * 2f)
        characterWindow.setPosition((Gdx.graphics.width - 400f * 2f) / 2f, (Gdx.graphics.height - 500f * 2f) / 2f)
        characterWindow.isMovable = true
        characterWindow.color = Color(1f, 1f, 1f, 0.5f)
        characterWindow.isVisible = false
        characterWindow.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val newX = (characterWindow.getX() + x - characterWindow.width / 2).coerceIn(0f, Gdx.graphics.width - characterWindow.width)
                val newY = (characterWindow.getY() + y - characterWindow.height / 2).coerceIn(0f, Gdx.graphics.height - characterWindow.height)
                characterWindow.setPosition(newX, newY)
                Gdx.app.log("MyGame", "Character window dragged to ($newX, $newY)")
            }
        })
        uiStage.addActor(characterWindow)

        inventoryIcon = Image(assetManager.get("icons/inventory.png", Texture::class.java))
        inventoryIcon.color = Color(1f, 1f, 1f, 0.5f)
        inventoryIcon.setPosition(10f, Gdx.graphics.height - 100f * 2f - 10f)
        inventoryIcon.setSize(100f * 2f, 100f * 2f)
        inventoryIcon.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isInventoryOpen = !isInventoryOpen
                inventoryWindow.isVisible = isInventoryOpen
                inventoryIcon.color.a = if (isInventoryOpen) 1f else 0.5f
                if (isInventoryOpen) {
                    inventoryWindow.setPosition((Gdx.graphics.width - inventoryWindow.width) / 2f, (Gdx.graphics.height - inventoryWindow.height) / 2f)
                }
                Gdx.app.log("MyGame", "Inventory icon clicked, isInventoryOpen: $isInventoryOpen")
            }
        })
        inventoryIcon.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val newX = (inventoryIcon.getX() + x - inventoryIcon.width / 2).coerceIn(0f, Gdx.graphics.width - inventoryIcon.width)
                val newY = (inventoryIcon.getY() + y - inventoryIcon.height / 2).coerceIn(0f, Gdx.graphics.height - inventoryIcon.height)
                inventoryIcon.setPosition(newX, newY)
                Gdx.app.log("MyGame", "Inventory icon dragged to ($newX, $newY)")
            }
        })
        uiStage.addActor(inventoryIcon)

        characterIcon = Image(assetManager.get("icons/character.png", Texture::class.java))
        characterIcon.color = Color(1f, 1f, 1f, 0.5f)
        characterIcon.setPosition(10f + 100f * 2f + 20f, Gdx.graphics.height - 100f * 2f - 10f)
        characterIcon.setSize(100f * 2f, 100f * 2f)
        characterIcon.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                isCharacterSheetOpen = !isCharacterSheetOpen
                characterWindow.isVisible = isCharacterSheetOpen
                characterIcon.color.a = if (isCharacterSheetOpen) 1f else 0.5f
                if (isCharacterSheetOpen) {
                    characterWindow.setPosition((Gdx.graphics.width - characterWindow.width) / 2f, (Gdx.graphics.height - characterWindow.height) / 2f)
                }
                Gdx.app.log("MyGame", "Character icon clicked, isCharacterSheetOpen: $isCharacterSheetOpen")
            }
        })
        characterIcon.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val newX = (characterIcon.getX() + x - characterIcon.width / 2).coerceIn(0f, Gdx.graphics.width - characterIcon.width)
                val newY = (characterIcon.getY() + y - characterIcon.height / 2).coerceIn(0f, Gdx.graphics.height - characterIcon.height)
                characterIcon.setPosition(newX, newY)
                Gdx.app.log("MyGame", "Character icon dragged to ($newX, $newY)")
            }
        })
        uiStage.addActor(characterIcon)

        player = Player(mapManager.tileSize, (mapManager.mapTileWidth / 2f), (mapManager.mapTileHeight / 2f), floatingTextManager, null, inventoryManager, levelingSystem, assetManager, mapManager)

        monsterManager = MonsterManager(mapManager, player, eventBus, pathFinder, floatingTextManager, assetManager, cameraManager, mapManager)
        monsterManager.loadMonsters()
        player.setMonsterManager(monsterManager)

        movementManager = MovementManager(mapManager, pathFinder, monsterManager)
        movementManager.clearPath()

        chatWindow = ChatWindow(skin, player)
        chatTable = chatWindow.table
        chatTable.setSize(chatWindow.sizeX, chatWindow.sizeY)
        chatTable.setPosition(chatWindow.offsetX, chatWindow.offsetY)
        chatTable.addListener(object : DragListener() {
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                chatWindow.drag(x + chatTable.getX(), y + chatTable.getY())
                Gdx.app.log("MyGame", "Chat table dragged to (${chatWindow.offsetX}, ${chatWindow.offsetY})")
            }
        })
        uiStage.addActor(chatTable)
        combatManager = CombatManager(player, monsterManager, floatingTextManager, inventoryManager, chatWindow)

        minimap = Minimap(mapManager, player)
        battleList = BattleList(monsterManager, player, uiCamera)

        inputHandler = GameInputHandler(cameraManager.camera, mapManager.tileSize, player, movementManager, minimap, mapManager, monsterManager, battleList, chatWindow, pathFinder)
        val multiplexer = com.badlogic.gdx.InputMultiplexer(uiStage, inputHandler.gestureDetector, gameWorldStage)
        Gdx.input.inputProcessor = multiplexer

        mainViewport = ScreenViewport(cameraManager.camera)

        minimap.offsetX = Gdx.graphics.width - minimap.size - 10f
        minimap.offsetY = Gdx.graphics.height - minimap.size - 10f
        battleList.offsetX = Gdx.graphics.width - battleList.size - 10f
        battleList.offsetY = minimap.offsetY - battleList.size - 10f

        gameWorldBatch = SpriteBatch()
        uiBatch = SpriteBatch()
        shapeRenderer = ShapeRenderer()

        lastPlayerX = player.playerTileX
        lastPlayerY = player.playerTileY
    }

    fun clearPlayerPath() {
        movementManager.clearPath()
        Gdx.app.log("MyGame", "Cleared player movement path")
    }

    public fun showDpad(screenX: Float, screenY: Float) {
        if (!dpadVisible && !isOverUI(screenX, Gdx.graphics.height - screenY)) {
            dpadVisible = true
            dpadLastTouchTime = System.currentTimeMillis()
            val touchpadStyle = TouchpadStyle()
            touchpadStyle.background = TextureRegionDrawable(TextureRegion(assetManager.get("icons/control.png", Texture::class.java)))
            touchpadStyle.knob = null
            touchpad = Touchpad(10f, touchpadStyle)
            touchpad!!.setBounds(
                (screenX - 200f).coerceIn(0f, Gdx.graphics.width - 400f),
                (screenY - 200f).coerceIn(0f, Gdx.graphics.height - 400f),
                400f, 400f
            )
            touchpad!!.color = Color(1f, 1f, 1f, 1f)
            uiStage.addActor(touchpad)
            Gdx.app.log("MyGame", "DPAD shown at ($screenX, $screenY), size (400x400)")
        }
    }

    public fun isOverUI(x: Float, y: Float): Boolean {
        return (x in (chatWindow.offsetX - uiBoundaryPadding)..(chatWindow.offsetX + chatWindow.sizeX + uiBoundaryPadding) &&
            y in (chatWindow.offsetY - uiBoundaryPadding)..(chatWindow.offsetY + chatWindow.sizeY + uiBoundaryPadding)) ||
            (::inventoryWindow.isInitialized &&
                x in (inventoryWindow.getX() - uiBoundaryPadding)..(inventoryWindow.getX() + inventoryWindow.width + uiBoundaryPadding) &&
                y in (inventoryWindow.getY() - uiBoundaryPadding)..(inventoryWindow.getY() + inventoryWindow.height + uiBoundaryPadding) &&
                inventoryWindow.isVisible) ||
            (::characterWindow.isInitialized &&
                x in (characterWindow.getX() - uiBoundaryPadding)..(characterWindow.getX() + characterWindow.width + uiBoundaryPadding) &&
                y in (characterWindow.getY() - uiBoundaryPadding)..(characterWindow.getY() + characterWindow.height + uiBoundaryPadding) &&
                characterWindow.isVisible) ||
            (x in (minimap.offsetX - uiBoundaryPadding)..(minimap.offsetX + minimap.size + uiBoundaryPadding) &&
                y in (minimap.offsetY - uiBoundaryPadding)..(minimap.offsetY + minimap.size + uiBoundaryPadding)) ||
            (x in (battleList.offsetX - uiBoundaryPadding)..(battleList.offsetX + battleList.size + uiBoundaryPadding) &&
                y in (battleList.offsetY - uiBoundaryPadding)..(battleList.offsetY + battleList.size + uiBoundaryPadding)) ||
            (::inventoryIcon.isInitialized &&
                x in (inventoryIcon.getX() - uiBoundaryPadding)..(inventoryIcon.getX() + inventoryIcon.width + uiBoundaryPadding) &&
                y in (inventoryIcon.getY() - uiBoundaryPadding)..(inventoryIcon.getY() + inventoryIcon.height + uiBoundaryPadding)) ||
            (::characterIcon.isInitialized &&
                x in (characterIcon.getX() - uiBoundaryPadding)..(characterIcon.getX() + characterIcon.width + uiBoundaryPadding) &&
                y in (characterIcon.getY() - uiBoundaryPadding)..(characterIcon.getY() + characterIcon.height + uiBoundaryPadding)) ||
            (touchpad?.let {
                x in (it.getX() - uiBoundaryPadding)..(it.getX() + it.width + uiBoundaryPadding) &&
                    y in (it.getY() - uiBoundaryPadding)..(it.getY() + it.height + uiBoundaryPadding)
            } ?: false)
    }

    private fun hideDpad() {
        dpadVisible = false
        moveDirection.set(0f, 0f)
        touchpad?.remove()
        touchpad = null
        Gdx.app.log("MyGame", "DPAD hidden")
    }

    private fun updateDpad(delta: Float) {
        if (dpadVisible && touchpad?.isTouched != true && System.currentTimeMillis() - dpadLastTouchTime > dpadTimeout) {
            hideDpad()
        }
        touchpad?.let { pad ->
            if (pad.isTouched) {
                dpadLastTouchTime = System.currentTimeMillis()
                val knobPercentX = pad.knobPercentX
                val knobPercentY = pad.knobPercentY
                moveDirection.set(knobPercentX, knobPercentY).nor()
                dpadMoveTimer += delta
                if (dpadMoveTimer >= dpadMoveInterval) {
                    val dx = when {
                        moveDirection.x > 0.5f -> 1
                        moveDirection.x < -0.5f -> -1
                        else -> 0
                    }
                    val dy = when {
                        moveDirection.y > 0.5f -> 1
                        moveDirection.y < -0.5f -> -1
                        else -> 0
                    }
                    if (dx != 0 || dy != 0) {
                        val moved = movementManager.moveInDirection(player, dx, dy)
                        Gdx.app.log("MyGame", "DPAD movement: dx=$dx, dy=$dy, success: $moved")
                        dpadMoveTimer = 0f
                    }
                }
                uiBatch.begin()
                shapeRenderer.projectionMatrix = uiCamera.combined
                shapeRenderer.begin(ShapeType.Filled)
                shapeRenderer.color = Color.YELLOW
                val centerX = pad.getX() + pad.width / 2
                val centerY = pad.getY() + pad.height / 2
                val radius = 100f
                if (moveDirection.x > 0) shapeRenderer.arc(centerX, centerY, radius, 270f, 90f)
                if (moveDirection.x < 0) shapeRenderer.arc(centerX, centerY, radius, 90f, 90f)
                if (moveDirection.y > 0) shapeRenderer.arc(centerX, centerY, radius, 0f, 90f)
                if (moveDirection.y < 0) shapeRenderer.arc(centerX, centerY, radius, 180f, 90f)
                shapeRenderer.end()
                uiBatch.end()
            } else {
                moveDirection.set(0f, 0f)
                dpadMoveTimer = 0f
            }
        }
    }

    override fun render() {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        if (!player.isDead()) {
            gameWorldStage.act(Gdx.graphics.deltaTime)
            player.update(Gdx.graphics.deltaTime, movementManager)
            combatManager.update(Gdx.graphics.deltaTime)
            if (player.playerTileX != lastPlayerX || player.playerTileY != lastPlayerY) {
                eventBus.fire("playerMoved", player.playerTileX, player.playerTileY)
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
            gameWorldStage.draw()

            uiStage.viewport.apply()
            uiBatch.projectionMatrix = uiCamera.combined
            Gdx.gl.glEnable(GL20.GL_BLEND)
            battleList.render()
            minimap.render(uiCamera)
            chatWindow.draw()
            chatWindow.renderResizeCorners(uiCamera)
            uiStage.act(Gdx.graphics.deltaTime)
            uiStage.draw()
            updateDpad(Gdx.graphics.deltaTime)

            if (isInventoryOpen && ::inventoryWindow.isInitialized) {
                shapeRenderer.begin(ShapeType.Filled)
                shapeRenderer.projectionMatrix = uiCamera.combined
                shapeRenderer.color = Color(0f, 0f, 0f, 0.5f)
                shapeRenderer.rect(inventoryWindow.getX(), inventoryWindow.getY(), inventoryWindow.width, inventoryWindow.height)
                shapeRenderer.end()
                uiBatch.begin()
                if (assetManager.isLoaded("icons/inventory.png")) {
                    uiBatch.draw(assetManager.get("icons/inventory.png", Texture::class.java), inventoryWindow.getX() + 10f, inventoryWindow.getY() + inventoryWindow.height - 60f, 50f * 2f, 50f * 2f)
                }
                val font = BitmapFont().apply { color = Color.WHITE; data.setScale(2f) }
                font.draw(uiBatch, "Items: ${player.getInventoryManager().getItems().joinToString(", ")}", inventoryWindow.getX() + 20f, inventoryWindow.getY() + inventoryWindow.height - 80f)
                font.dispose()
                uiBatch.end()
            }

            if (isCharacterSheetOpen && ::characterWindow.isInitialized) {
                shapeRenderer.begin(ShapeType.Filled)
                shapeRenderer.projectionMatrix = uiCamera.combined
                shapeRenderer.color = Color(0f, 0f, 0f, 0.5f)
                shapeRenderer.rect(characterWindow.getX(), characterWindow.getY(), characterWindow.width, characterWindow.height)
                shapeRenderer.end()
                uiBatch.begin()
                if (assetManager.isLoaded("icons/character.png")) {
                    uiBatch.draw(assetManager.get("icons/character.png", Texture::class.java), characterWindow.getX() + 10f, characterWindow.getY() + characterWindow.height - 60f, 50f * 2f, 50f * 2f)
                }
                val font = BitmapFont().apply { color = Color.WHITE; data.setScale(3f) }
                var y = characterWindow.getY() + characterWindow.height - 80f
                font.draw(uiBatch, "Stats:", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "HP: ${player.currentHp}/${player.maxHp} (Lvl ${player.getLevelingSystem().getLevel("HP")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Str: ${player.strength} (Lvl ${player.getLevelingSystem().getLevel("strength")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Mag: ${player.magic} (Lvl ${player.getLevelingSystem().getLevel("magic")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Rng: ${player.ranged} (Lvl ${player.getLevelingSystem().getLevel("ranged")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Pry: ${player.prayer} (Lvl ${player.getLevelingSystem().getLevel("prayer")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Wdc: ${player.woodcutting} (Lvl ${player.getLevelingSystem().getLevel("woodcutting")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Min: ${player.mining} (Lvl ${player.getLevelingSystem().getLevel("mining")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Agl: ${player.agility} (Lvl ${player.getLevelingSystem().getLevel("agility")})", characterWindow.getX() + 10f, y); y -= 60f
                font.draw(uiBatch, "Def: ${player.defense} (Lvl ${player.getLevelingSystem().getLevel("defense")})", characterWindow.getX() + 10f, y)
                font.dispose()
                uiBatch.end()
            }

            uiBatch.begin()
            if (::inventoryIcon.isInitialized && assetManager.isLoaded("icons/inventory.png")) {
                val inventoryTexture = assetManager.get("icons/inventory.png", Texture::class.java)
                uiBatch.draw(inventoryTexture, inventoryIcon.getX(), inventoryIcon.getY(), inventoryIcon.width, inventoryIcon.height)
            }
            if (::characterIcon.isInitialized && assetManager.isLoaded("icons/character.png")) {
                val characterTexture = assetManager.get("icons/character.png", Texture::class.java)
                uiBatch.draw(characterTexture, characterIcon.getX(), characterIcon.getY(), characterIcon.width, characterIcon.height)
            }
            uiBatch.end()
        }

        if (player.isDead()) {
            isDeadOverlayVisible = true
            mainViewport.apply()
            shapeRenderer.begin(ShapeType.Filled)
            shapeRenderer.color = Color(0f, 0f, 0f, 0.5f)
            shapeRenderer.rect(0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
            shapeRenderer.end()

            uiBatch.begin()
            val font = BitmapFont().apply { color = Color.RED; data.setScale(5f) }
            val layout = GlyphLayout(font, "You are dead")
            val textX = (Gdx.graphics.width - layout.width) / 2f
            val textY = (Gdx.graphics.height + layout.height) / 2f + 50f
            font.draw(uiBatch, layout, textX, textY)
            if (assetManager.isLoaded("icons/respawn.png")) {
                val respawnTexture = assetManager.get("icons/respawn.png", Texture::class.java)
                val buttonX = (Gdx.graphics.width.toFloat() - respawnTexture.width.toFloat()) / 2f
                val buttonY = textY - 100f - respawnTexture.height / 2f
                uiBatch.draw(respawnTexture, buttonX, buttonY, respawnTexture.width.toFloat(), respawnTexture.height.toFloat())
                if (Gdx.input.justTouched()) {
                    val touchX = Gdx.input.x.toFloat()
                    val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()
                    if (touchX in buttonX..(buttonX + respawnTexture.width) && touchY in buttonY..(buttonY + respawnTexture.height)) {
                        player.respawn()
                        isDeadOverlayVisible = false
                        Gdx.app.log("MyGame", "Player respawned")
                    }
                }
            } else {
                val respawnLayout = GlyphLayout(font, "Respawn (Image Missing)")
                val respawnX = (Gdx.graphics.width - respawnLayout.width) / 2f
                val respawnY = textY - 100f
                font.draw(uiBatch, respawnLayout, respawnX, respawnY)
                if (Gdx.input.justTouched()) {
                    val touchX = Gdx.input.x.toFloat()
                    val touchY = Gdx.graphics.height - Gdx.input.y.toFloat()
                    if (touchX in respawnX..(respawnX + respawnLayout.width) && touchY in (respawnY - respawnLayout.height)..respawnY) {
                        player.respawn()
                        isDeadOverlayVisible = false
                        Gdx.app.log("MyGame", "Player respawned")
                    }
                }
            }
            font.dispose()
            uiBatch.end()
        } else {
            isDeadOverlayVisible = false
        }
    }

    fun showMessage(text: String) {
        textRenderer.showMessage(text)
    }

    override fun resize(width: Int, height: Int) {
        mainViewport.update(width, height, true)
        if (::gameWorldStage.isInitialized) gameWorldStage.viewport.update(width, height, true)
        if (::uiStage.isInitialized) uiStage.viewport.update(width, height, true)
        if (::minimap.isInitialized) {
            minimap.offsetX = minimap.offsetX.coerceIn(0f, width.toFloat() - minimap.size)
            minimap.offsetY = minimap.offsetY.coerceIn(0f, height.toFloat() - minimap.size)
        }
        if (::battleList.isInitialized) {
            battleList.offsetX = battleList.offsetX.coerceIn(0f, width.toFloat() - battleList.size)
            battleList.offsetY = battleList.offsetY.coerceIn(0f, height.toFloat() - battleList.size)
        }
        if (::chatWindow.isInitialized) {
            chatWindow.offsetX = chatWindow.offsetX.coerceIn(0f, width.toFloat() - chatWindow.sizeX)
            chatWindow.offsetY = chatWindow.offsetY.coerceIn(0f, height.toFloat() - chatWindow.sizeY)
            chatTable.setPosition(chatWindow.offsetX, chatWindow.offsetY)
            chatWindow.updateMinimizedPosition(
                chatWindow.minimizedOffsetX.coerceIn(0f, width.toFloat() - chatWindow.minimizedSize.x),
                chatWindow.minimizedOffsetY.coerceIn(0f, height.toFloat() - chatWindow.minimizedSize.y)
            )
        }
        if (::inventoryWindow.isInitialized) {
            inventoryWindow.setPosition(
                inventoryWindow.getX().coerceIn(0f, width.toFloat() - inventoryWindow.width),
                inventoryWindow.getY().coerceIn(0f, height.toFloat() - inventoryWindow.height)
            )
        }
        if (::characterWindow.isInitialized) {
            characterWindow.setPosition(
                characterWindow.getX().coerceIn(0f, width.toFloat() - characterWindow.width),
                characterWindow.getY().coerceIn(0f, height.toFloat() - characterWindow.height)
            )
        }
        if (::inventoryIcon.isInitialized) {
            inventoryIcon.setPosition(
                inventoryIcon.getX().coerceIn(0f, width.toFloat() - inventoryIcon.width),
                inventoryIcon.getY().coerceIn(0f, height.toFloat() - inventoryIcon.height)
            )
        }
        if (::characterIcon.isInitialized) {
            characterIcon.setPosition(
                characterIcon.getX().coerceIn(0f, width.toFloat() - characterIcon.width),
                characterIcon.getY().coerceIn(0f, height.toFloat() - characterIcon.height)
            )
        }
        if (dpadVisible && touchpad != null) {
            touchpad!!.setPosition(
                touchpad!!.getX().coerceIn(0f, width.toFloat() - touchpad!!.width),
                touchpad!!.getY().coerceIn(0f, height.toFloat() - touchpad!!.height)
            )
        }
        if (::uiCamera.isInitialized) {
            uiCamera.setToOrtho(false, width.toFloat(), height.toFloat())
            uiCamera.update()
        }
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
        assetManager.dispose()
        gameWorldBatch.dispose()
        uiBatch.dispose()
        shapeRenderer.dispose()
        gameWorldStage.dispose()
        uiStage.dispose()
    }
}
