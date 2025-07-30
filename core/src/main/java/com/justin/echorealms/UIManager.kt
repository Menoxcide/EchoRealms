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
    private lateinit var slotTexture: Texture
    private lateinit var slotEquippedTexture: Texture
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
        // Load slot textures
        slotTexture = assetManager.get("misc/slot.png", Texture::class.java)
        slotEquippedTexture = assetManager.get("misc/slot_equipped.png", Texture::class.java)

        stage.addActor(worldMap.window)
        stage.addActor(battleList.table)
        stage.addActor(chatWindow.table)
        stage.addActor(chatWindow.minimizedTable)
        stage.addActor(minimap.table)

        inventoryWindow = Window("Inventory", skin, "default").apply {
            setSize(600f, 800f)
            setPosition((Gdx.graphics.width - width) / 2f, (Gdx.graphics.height - height) / 2f)
            isMovable = true
            color = Color(1f, 1f, 1f, 1f) // Fully opaque
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

        characterWindow = Window("Character Sheet", skin, "default").apply {
            setSize(800f, 1000f)
            setPosition((Gdx.graphics.width - width) / 2f, (Gdx.graphics.height - height) / 2f)
            isMovable = true // Ensure movable
            color = Color(1f, 1f, 1f, 1f) // Fully opaque
            isVisible = false
            addListener(object : DragListener() {
                override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    Gdx.app.log("UIManager", "Started dragging character window at ($x, $y)")
                }
                override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    val newX = (x + this@apply.x - width / 2).coerceIn(0f, Gdx.graphics.width.toFloat() - width)
                    val newY = (y + this@apply.y - height / 2).coerceIn(0f, Gdx.graphics.height.toFloat() - height)
                    setPosition(newX, newY)
                    Gdx.app.log("UIManager", "Character window dragged to ($newX, $newY)")
                }
                override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                    Gdx.app.log("UIManager", "Stopped dragging character window at ($x, $y)")
                }
            })
            add(buildCharacterTable()).grow().pad(10f)
        }
        stage.addActor(characterWindow)

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
                        characterWindow.clear()
                        characterWindow.add(buildCharacterTable()).grow().pad(10f)
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
        val items = inventoryManager.getItems()
        for (item in items) {
            val itemTable = Table(skin)
            itemTable.add(Label(item.name, skin, "default")).pad(5f).left()
            if (item.isEquippable) {
                val equipButton = TextButton("Equip", skin, "default")
                equipButton.addListener(object : ClickListener() {
                    override fun clicked(event: InputEvent?, x: Float, y: Float) {
                        inventoryManager.equipItem(item)
                        inventoryWindow.clear()
                        inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                        characterWindow.clear()
                        characterWindow.add(buildCharacterTable()).grow().pad(10f)
                        Gdx.app.log("UIManager", "Equipped ${item.name} to ${item.slotType}")
                    }
                })
                itemTable.add(equipButton).pad(5f).right()
            }
            table.add(itemTable).growX().pad(5f)
            table.row()
        }
        val equippedItems = inventoryManager.getEffectiveModifiers()
        if (equippedItems.isNotEmpty()) {
            table.add(Label("Equipped:", skin, "default")).growX().pad(10f)
            table.row()
            listOf("weapon", "left_hand", "helmet", "chest", "legs", "feet", "ring", "amulet").forEach { slot ->
                val equipped = inventoryManager.getEquippedItem(slot)
                if (equipped != null) {
                    table.add(Label("$slot: ${equipped.name}", skin, "default")).growX().pad(5f).left()
                    table.row()
                }
            }
        }
        return table
    }

    private fun buildCharacterTable(): Table {
        val table = Table(skin)
        // Character icon and equipment slots
        val equipmentTable = Table(skin)
        equipmentTable.add(Label("Equipment:", skin, "default")).colspan(3).pad(10f).center()
        equipmentTable.row()
        // Helmet slot
        equipmentTable.add(Label("Helmet", skin, "default")).colspan(3).padBottom(5f)
        equipmentTable.row()
        val helmetSlot = Image(if (inventoryManager.getEquippedItem("helmet") != null) slotEquippedTexture else slotTexture)
        helmetSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("helmet") != null) {
                    inventoryManager.unequipItem("helmet")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped helmet")
                }
            }
        })
        equipmentTable.add(helmetSlot).size(50f).pad(10f).colspan(3)
        equipmentTable.row()
        // Character icon with left and right hand slots
        equipmentTable.add(Label("Left Hand", skin, "default")).padBottom(5f)
        equipmentTable.add().size(100f).pad(10f) // Spacer for character icon
        equipmentTable.add(Label("Right Hand", skin, "default")).padBottom(5f)
        equipmentTable.row()
        val leftHandSlot = Image(if (inventoryManager.getEquippedItem("left_hand") != null) slotEquippedTexture else slotTexture)
        leftHandSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("left_hand") != null) {
                    inventoryManager.unequipItem("left_hand")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped left hand")
                }
            }
        })
        equipmentTable.add(leftHandSlot).size(50f).pad(10f)
        equipmentTable.add(Image(assetManager.get("icons/character.png", Texture::class.java))).size(100f).pad(10f)
        val weaponSlot = Image(if (inventoryManager.getEquippedItem("weapon") != null) slotEquippedTexture else slotTexture)
        weaponSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("weapon") != null) {
                    inventoryManager.unequipItem("weapon")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped weapon")
                }
            }
        })
        equipmentTable.add(weaponSlot).size(50f).pad(10f)
        equipmentTable.row()
        // Amulet and ring slots
        equipmentTable.add(Label("Amulet", skin, "default")).padBottom(5f)
        equipmentTable.add(Label("Ring", skin, "default")).padBottom(5f).colspan(2)
        equipmentTable.row()
        val amuletSlot = Image(if (inventoryManager.getEquippedItem("amulet") != null) slotEquippedTexture else slotTexture)
        amuletSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("amulet") != null) {
                    inventoryManager.unequipItem("amulet")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped amulet")
                }
            }
        })
        equipmentTable.add(amuletSlot).size(50f).pad(10f)
        val ringSlot = Image(if (inventoryManager.getEquippedItem("ring") != null) slotEquippedTexture else slotTexture)
        ringSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("ring") != null) {
                    inventoryManager.unequipItem("ring")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped ring")
                }
            }
        })
        equipmentTable.add(ringSlot).size(50f).pad(10f).colspan(2)
        equipmentTable.row()
        // Chest slot
        equipmentTable.add(Label("Chest", skin, "default")).colspan(3).padBottom(5f)
        equipmentTable.row()
        val chestSlot = Image(if (inventoryManager.getEquippedItem("chest") != null) slotEquippedTexture else slotTexture)
        chestSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("chest") != null) {
                    inventoryManager.unequipItem("chest")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped chest")
                }
            }
        })
        equipmentTable.add(chestSlot).size(50f).pad(10f).colspan(3)
        equipmentTable.row()
        // Legs and feet slots
        equipmentTable.add(Label("Legs", skin, "default")).colspan(3).padBottom(5f)
        equipmentTable.row()
        val legsSlot = Image(if (inventoryManager.getEquippedItem("legs") != null) slotEquippedTexture else slotTexture)
        legsSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("legs") != null) {
                    inventoryManager.unequipItem("legs")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped legs")
                }
            }
        })
        equipmentTable.add(legsSlot).size(50f).pad(10f).colspan(3)
        equipmentTable.row()
        equipmentTable.add(Label("Feet", skin, "default")).colspan(3).padBottom(5f)
        equipmentTable.row()
        val feetSlot = Image(if (inventoryManager.getEquippedItem("feet") != null) slotEquippedTexture else slotTexture)
        feetSlot.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (inventoryManager.getEquippedItem("feet") != null) {
                    inventoryManager.unequipItem("feet")
                    characterWindow.clear()
                    characterWindow.add(buildCharacterTable()).grow().pad(10f)
                    inventoryWindow.clear()
                    inventoryWindow.add(buildInventoryTable()).grow().pad(10f)
                    Gdx.app.log("UIManager", "Unequipped feet")
                }
            }
        })
        equipmentTable.add(feetSlot).size(50f).pad(10f).colspan(3)

        table.add(equipmentTable).pad(10f)
        table.row()

        // Stats (using effective stats where applicable)
        table.add(Label("Stats:", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("HP: ${player.currentHp}/${player.maxHp} (Lvl ${player.getLevelingSystem().getLevel("HP")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Att: ${player.getEffectiveAttack()} (Lvl ${player.getLevelingSystem().getLevel("attack")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Str: ${player.getEffectiveStrength()} (Lvl ${player.getLevelingSystem().getLevel("strength")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Mag: ${player.getEffectiveMagic()} (Lvl ${player.getLevelingSystem().getLevel("magic")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Rng: ${player.getEffectiveRanged()} (Lvl ${player.getLevelingSystem().getLevel("ranged")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Pry: ${player.getEffectivePrayer()} (Lvl ${player.getLevelingSystem().getLevel("prayer")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Wdc: ${player.getEffectiveWoodcutting()} (Lvl ${player.getLevelingSystem().getLevel("woodcutting")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Min: ${player.getEffectiveMining()} (Lvl ${player.getLevelingSystem().getLevel("mining")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Agl: ${player.getEffectiveAgility()} (Lvl ${player.getLevelingSystem().getLevel("agility")})", skin, "default")).pad(10f).left()
        table.row()
        table.add(Label("Def: ${player.getEffectiveDefense()} (Lvl ${player.getLevelingSystem().getLevel("defense")})", skin, "default")).pad(10f).left()
        table.row()
        Gdx.app.log("UIManager", "Built character table with HP: ${player.currentHp}/${player.maxHp}, Att: ${player.getEffectiveAttack()}, Str: ${player.getEffectiveStrength()}, Def: ${player.getEffectiveDefense()}")
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
