package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

class UIManager(
    private val player: Player,
    private val mapManager: MapManager,
    private val monsterManager: MonsterManager,
    private val cameraManager: CameraManager,
    private val fogOfWar: FogOfWar,
    private val inventoryManager: InventoryManager,
    private val assetManager: AssetManager,
    private val worldMap: WorldMap,
    private val battleList: BattleList,
    private val chatWindow: ChatWindow,
    private val minimap: Minimap
) {
    private val skin = assetManager.get("ui/uiskin.json", Skin::class.java)
    private val stage = Stage(ScreenViewport())
    private lateinit var inventoryWindow: Window
    private lateinit var characterWindow: Window
    private lateinit var worldMapButton: TextButton
    private lateinit var inventoryIcon: Image
    private lateinit var characterIcon: Image
    private lateinit var debugToggleButton: TextButton
    private lateinit var dpadToggleButton: TextButton
    private lateinit var dpadLockButton: TextButton
    private lateinit var touchpad: Touchpad
    private var isInventoryOpen = false
    private var isCharacterSheetOpen = false
    private var isWorldMapOpen = false
    private var isDebugOverlayVisible = false
    private var dpadVisible = true
    private var dpadLocked = false
    private val uiBoundaryPadding = 10f

    init {
        initializeUI()
    }

    private fun initializeUI() {
        // Add WorldMap, BattleList, ChatWindow, and Minimap to UIManager's stage
        stage.addActor(worldMap.window)
        stage.addActor(battleList.table)
        stage.addActor(chatWindow.table)
        stage.addActor(chatWindow.minimizedTable)
        stage.addActor(minimap.table)

        // Inventory Window
        inventoryWindow = Window("Inventory", skin, "default").apply {
            setSize(600f, 800f)
            setPosition((Gdx.graphics.width - width) / 2f, (Gdx.graphics.height - height) / 2f)
            isMovable = true
            color = Color(1f, 1f, 1f, 0.5f)
            isVisible = false
            addListener(object : DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val newX = (x + this@apply.x - width / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - width)
                    val newY = (y + this@apply.y - height / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - height)
                    setPosition(newX, newY)
                    Gdx.app.log("UIManager", "Inventory window dragged to ($newX, $newY)")
                }
            })
            add(buildInventoryTable()).grow().pad(10f)
        }
        stage.addActor(inventoryWindow)

        // Character Window
        characterWindow = Window("Character Sheet", skin, "default").apply {
            setSize(800f, 1000f)
            setPosition((Gdx.graphics.width - width) / 2f, (Gdx.graphics.height - height) / 2f)
            isMovable = true
            color = Color(1f, 1f, 1f, 0.5f)
            isVisible = false
            addListener(object : DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val newX = (x + this@apply.x - width / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - width)
                    val newY = (y + this@apply.y - height / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - height)
                    setPosition(newX, newY)
                    Gdx.app.log("UIManager", "Character window dragged to ($newX, $newY)")
                }
            })
            add(buildCharacterTable()).grow().pad(10f)
        }
        stage.addActor(characterWindow)

        // Inventory Icon
        inventoryIcon = Image(assetManager.get("icons/inventory.png", Texture::class.java)).apply {
            color = Color(1f, 1f, 1f, 0.5f)
            setPosition(10f, Gdx.graphics.height.toFloat() - 200f)
            setSize(200f, 200f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    isInventoryOpen = !isInventoryOpen
                    inventoryWindow.isVisible = isInventoryOpen
                    color.a = if (isInventoryOpen) 1f else 0.5f
                    if (isInventoryOpen) {
                        inventoryWindow.setPosition((Gdx.graphics.width.toFloat() - inventoryWindow.width) / 2f, (Gdx.graphics.height.toFloat() - inventoryWindow.height) / 2f)
                    }
                    Gdx.app.log("UIManager", "Inventory icon clicked, isInventoryOpen: $isInventoryOpen")
                }
            })
            addListener(object : DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val newX = (x + this@apply.x - width / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - width)
                    val newY = (y + this@apply.y - height / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - height)
                    setPosition(newX, newY)
                    Gdx.app.log("UIManager", "Inventory icon dragged to ($newX, $newY)")
                }
            })
        }
        stage.addActor(inventoryIcon)

        // Character Icon
        characterIcon = Image(assetManager.get("icons/character.png", Texture::class.java)).apply {
            color = Color(1f, 1f, 1f, 0.5f)
            setPosition(230f, Gdx.graphics.height.toFloat() - 200f)
            setSize(200f, 200f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    isCharacterSheetOpen = !isCharacterSheetOpen
                    characterWindow.isVisible = isCharacterSheetOpen
                    color.a = if (isCharacterSheetOpen) 1f else 0.5f
                    if (isCharacterSheetOpen) {
                        characterWindow.setPosition((Gdx.graphics.width.toFloat() - characterWindow.width) / 2f, (Gdx.graphics.height.toFloat() - characterWindow.height) / 2f)
                    }
                    Gdx.app.log("UIManager", "Character icon clicked, isCharacterSheetOpen: $isCharacterSheetOpen")
                }
            })
            addListener(object : DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val newX = (x + this@apply.x - width / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - width)
                    val newY = (y + this@apply.y - height / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - height)
                    setPosition(newX, newY)
                    Gdx.app.log("UIManager", "Character icon dragged to ($newX, $newY)")
                }
            })
        }
        stage.addActor(characterIcon)

        // World Map Button
        worldMapButton = TextButton("World Map", skin, "default").apply {
            color = Color(1f, 1f, 1f, 0.5f)
            setPosition(450f, Gdx.graphics.height.toFloat() - 200f)
            setSize(200f, 200f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    isWorldMapOpen = !isWorldMapOpen
                    worldMap.window.isVisible = isWorldMapOpen
                    color.a = if (isWorldMapOpen) 1f else 0.5f
                    Gdx.app.log("UIManager", "World map button clicked, isWorldMapOpen: $isWorldMapOpen")
                }
            })
        }
        stage.addActor(worldMapButton)

        // Debug Toggle Button
        debugToggleButton = TextButton("Toggle Debug", skin, "default").apply {
            setPosition(20f, Gdx.graphics.height.toFloat() - 150f)
            setSize(200f, 60f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    isDebugOverlayVisible = !isDebugOverlayVisible
                    label.setText(if (isDebugOverlayVisible) "Hide Debug" else "Show Debug")
                    Gdx.app.log("UIManager", "Debug overlay toggled: $isDebugOverlayVisible")
                }
            })
        }
        stage.addActor(debugToggleButton)

        // Custom Touchpad Style with icons/control.png
        val controlTexture = assetManager.get("icons/control.png", Texture::class.java)
        val touchpadStyle = Touchpad.TouchpadStyle().apply {
            background = TextureRegionDrawable(controlTexture)
            knob = TextureRegionDrawable(controlTexture).apply { setMinWidth(50f); setMinHeight(50f) }
        }
        touchpad = Touchpad(10f, touchpadStyle).apply {
            setBounds(20f, 20f, 300f, 300f)
            color = Color(1f, 1f, 1f, 0.8f)
            isVisible = dpadVisible
            addListener(object : DragListener() {
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    if (dpadLocked) return
                    if (event?.type == InputEvent.Type.touchDragged && pointer == 0) {
                        val newX = (x + this@apply.x - width / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - width)
                        val newY = (y + this@apply.y - height / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - height)
                        setPosition(newX, newY)
                        Gdx.app.log("UIManager", "DPAD dragged to ($newX, $newY)")
                    }
                }
            })
        }
        stage.addActor(touchpad)

        // Dpad Toggle Button
        dpadToggleButton = TextButton("Toggle DPAD", skin, "default").apply {
            setPosition(20f, Gdx.graphics.height.toFloat() - 50f)
            setSize(200f, 60f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    dpadVisible = !dpadVisible
                    touchpad.isVisible = dpadVisible
                    Gdx.app.log("UIManager", "DPAD toggled: $dpadVisible")
                }
            })
        }
        stage.addActor(dpadToggleButton)

        // Dpad Lock Button
        dpadLockButton = TextButton("Lock DPAD", skin, "default").apply {
            setPosition(20f, Gdx.graphics.height.toFloat() - 100f)
            setSize(200f, 60f)
            addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    dpadLocked = !dpadLocked
                    label.setText(if (dpadLocked) "Unlock DPAD" else "Lock DPAD")
                    Gdx.app.log("UIManager", "DPAD lock toggled: $dpadLocked")
                }
            })
        }
        stage.addActor(dpadLockButton)
    }

    private fun buildInventoryTable(): Table {
        val table = Table(skin)
        table.add(Image(assetManager.get("icons/inventory.png", Texture::class.java))).size(100f).pad(10f)
        table.row()
        val itemsLabel = Label("Items: ${inventoryManager.getItems().joinToString(", ")}", skin, "default")
        table.add(itemsLabel).growX().pad(10f)
        return table
    }

    private fun buildCharacterTable(): Table {
        val table = Table(skin)
        table.add(Image(assetManager.get("icons/character.png", Texture::class.java))).size(100f).pad(10f)
        table.row()
        table.add(Label("Stats:", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("HP: ${player.currentHp}/${player.maxHp} (Lvl ${player.getLevelingSystem().getLevel("HP")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Str: ${player.strength} (Lvl ${player.getLevelingSystem().getLevel("strength")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Mag: ${player.magic} (Lvl ${player.getLevelingSystem().getLevel("magic")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Rng: ${player.ranged} (Lvl ${player.getLevelingSystem().getLevel("ranged")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Pry: ${player.prayer} (Lvl ${player.getLevelingSystem().getLevel("prayer")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Wdc: ${player.woodcutting} (Lvl ${player.getLevelingSystem().getLevel("woodcutting")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Min: ${player.mining} (Lvl ${player.getLevelingSystem().getLevel("mining")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Agl: ${player.agility} (Lvl ${player.getLevelingSystem().getLevel("agility")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Def: ${player.defense} (Lvl ${player.getLevelingSystem().getLevel("defense")})", skin, "default")).pad(10f).left()
        return table
    }

    fun isOverUI(x: Float, y: Float): Boolean {
        return (x in (inventoryIcon.x - uiBoundaryPadding)..(inventoryIcon.x + inventoryIcon.width + uiBoundaryPadding) &&
            y in (inventoryIcon.y - uiBoundaryPadding)..(inventoryIcon.y + inventoryIcon.height + uiBoundaryPadding)) ||
            (x in (characterIcon.x - uiBoundaryPadding)..(characterIcon.x + characterIcon.width + uiBoundaryPadding) &&
                y in (characterIcon.y - uiBoundaryPadding)..(characterIcon.y + characterIcon.height + uiBoundaryPadding)) ||
            (x in (worldMapButton.x - uiBoundaryPadding)..(worldMapButton.x + worldMapButton.width + uiBoundaryPadding) &&
                y in (worldMapButton.y - uiBoundaryPadding)..(worldMapButton.y + worldMapButton.height + uiBoundaryPadding)) ||
            (x in (debugToggleButton.x - uiBoundaryPadding)..(debugToggleButton.x + debugToggleButton.width + uiBoundaryPadding) &&
                y in (debugToggleButton.y - uiBoundaryPadding)..(debugToggleButton.y + debugToggleButton.height + uiBoundaryPadding)) ||
            (x in (touchpad.x - uiBoundaryPadding)..(touchpad.x + touchpad.width + uiBoundaryPadding) &&
                y in (touchpad.y - uiBoundaryPadding)..(touchpad.y + touchpad.height + uiBoundaryPadding)) ||
            (isInventoryOpen && x in (inventoryWindow.x - uiBoundaryPadding)..(inventoryWindow.x + inventoryWindow.width + uiBoundaryPadding) &&
                y in (inventoryWindow.y - uiBoundaryPadding)..(inventoryWindow.y + inventoryWindow.height + uiBoundaryPadding)) ||
            (isCharacterSheetOpen && x in (characterWindow.x - uiBoundaryPadding)..(characterWindow.x + characterWindow.width + uiBoundaryPadding) &&
                y in (characterWindow.y - uiBoundaryPadding)..(characterWindow.y + characterWindow.height + uiBoundaryPadding)) ||
            (worldMap.window.isVisible && x in (worldMap.window.x - uiBoundaryPadding)..(worldMap.window.x + worldMap.window.width + uiBoundaryPadding) &&
                y in (worldMap.window.y - uiBoundaryPadding)..(worldMap.window.y + worldMap.window.height + uiBoundaryPadding)) ||
            (battleList.table.isVisible && x in (battleList.table.x - uiBoundaryPadding)..(battleList.table.x + battleList.table.width + uiBoundaryPadding) &&
                y in (battleList.table.y - uiBoundaryPadding)..(battleList.table.y + battleList.table.height + uiBoundaryPadding)) ||
            (x in (chatWindow.table.x - uiBoundaryPadding)..(chatWindow.table.x + chatWindow.table.width + uiBoundaryPadding) &&
                y in (chatWindow.table.y - uiBoundaryPadding)..(chatWindow.table.y + chatWindow.table.height + uiBoundaryPadding)) ||
            (chatWindow.minimizedTable.isVisible && x in (chatWindow.minimizedTable.x - uiBoundaryPadding)..(chatWindow.minimizedTable.x + chatWindow.minimizedTable.width + uiBoundaryPadding) &&
                y in (chatWindow.minimizedTable.y - uiBoundaryPadding)..(chatWindow.minimizedTable.y + chatWindow.minimizedTable.height + uiBoundaryPadding)) ||
            (minimap.table.isVisible && x in (minimap.offsetX - uiBoundaryPadding)..(minimap.offsetX + minimap.size + uiBoundaryPadding) &&
                y in (minimap.offsetY - uiBoundaryPadding)..(minimap.offsetY + minimap.size + uiBoundaryPadding))
    }

    fun getStage(): Stage = stage
    fun getTouchpad(): Touchpad = touchpad
    fun isDebugOverlayVisible(): Boolean = isDebugOverlayVisible
    fun isDpadVisible(): Boolean = dpadVisible
    fun isDpadLocked(): Boolean = dpadLocked
    fun isWorldMapOpen(): Boolean = isWorldMapOpen
    fun setWorldMapOpen(open: Boolean) {
        isWorldMapOpen = open
        worldMap.window.isVisible = open
    }

    fun draw() {
        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        inventoryWindow.setPosition(
            inventoryWindow.x.coerceIn(0f, width.toFloat() - inventoryWindow.width),
            inventoryWindow.y.coerceIn(0f, height.toFloat() - inventoryWindow.height)
        )
        characterWindow.setPosition(
            characterWindow.x.coerceIn(0f, width.toFloat() - characterWindow.width),
            characterWindow.y.coerceIn(0f, height.toFloat() - characterWindow.height)
        )
        inventoryIcon.setPosition(
            inventoryIcon.x.coerceIn(0f, width.toFloat() - inventoryIcon.width),
            inventoryIcon.y.coerceIn(0f, height.toFloat() - inventoryIcon.height)
        )
        characterIcon.setPosition(
            characterIcon.x.coerceIn(0f, width.toFloat() - characterIcon.width),
            characterIcon.y.coerceIn(0f, height.toFloat() - characterIcon.height)
        )
        worldMapButton.setPosition(
            worldMapButton.x.coerceIn(0f, width.toFloat() - worldMapButton.width),
            worldMapButton.y.coerceIn(0f, height.toFloat() - worldMapButton.height)
        )
        touchpad.setPosition(
            touchpad.x.coerceIn(0f, width.toFloat() - touchpad.width),
            touchpad.y.coerceIn(0f, height.toFloat() - touchpad.height)
        )
        dpadToggleButton.setPosition(
            dpadToggleButton.x.coerceIn(0f, width.toFloat() - dpadToggleButton.width),
            dpadToggleButton.y.coerceIn(0f, height.toFloat() - dpadToggleButton.height)
        )
        dpadLockButton.setPosition(
            dpadLockButton.x.coerceIn(0f, width.toFloat() - dpadLockButton.width),
            dpadLockButton.y.coerceIn(0f, height.toFloat() - dpadLockButton.height)
        )
        debugToggleButton.setPosition(
            debugToggleButton.x.coerceIn(0f, width.toFloat() - debugToggleButton.width),
            debugToggleButton.y.coerceIn(0f, height.toFloat() - debugToggleButton.height)
        )
        worldMap.window.setPosition(
            worldMap.window.x.coerceIn(0f, width.toFloat() - worldMap.window.width),
            worldMap.window.y.coerceIn(0f, height.toFloat() - worldMap.window.height)
        )
        battleList.table.setPosition(
            battleList.table.x.coerceIn(0f, width.toFloat() - battleList.table.width),
            battleList.table.y.coerceIn(0f, height.toFloat() - battleList.table.height)
        )
        chatWindow.table.setPosition(
            chatWindow.table.x.coerceIn(0f, width.toFloat() - chatWindow.table.width),
            chatWindow.table.y.coerceIn(0f, height.toFloat() - chatWindow.table.height)
        )
        chatWindow.minimizedTable.setPosition(
            chatWindow.minimizedTable.x.coerceIn(0f, width.toFloat() - chatWindow.minimizedTable.width),
            chatWindow.minimizedTable.y.coerceIn(0f, height.toFloat() - chatWindow.minimizedTable.height)
        )
        minimap.table.setPosition(
            minimap.offsetX.coerceIn(0f, width.toFloat() - minimap.size),
            minimap.offsetY.coerceIn(0f, height.toFloat() - minimap.size)
        )
        Gdx.app.log("UIManager", "Resized to ($width, $height)")
    }

    fun dispose() {
        stage.dispose()
    }
}
