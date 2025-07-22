package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.DragListener
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.badlogic.gdx.utils.Align

class BattleList(
    private val monsterManager: MonsterManager,
    private val player: Player,
    private val skin: Skin?,
    private val mapManager: MapManager
) {
    val table = Table()
    var offsetX = Gdx.graphics.width - 200f - 10f
    var offsetY = Gdx.graphics.height - 200f - 210f
    var size = 200f
    private var dragOffset = Vector2()
    private val resizeCornerSize = 20f
    private val minSize = 150f
    private val maxSize = minOf(Gdx.graphics.width * 0.5f, Gdx.graphics.height * 0.5f)
    private val sidePadding = 12f // Increased padding to accommodate larger elements
    private val shapeRenderer = ShapeRenderer()

    init {
        table.setSize(size, size)
        table.setPosition(offsetX, offsetY)
        if (skin != null) {
            table.background = skin.getDrawable("window")
        } else {
            Gdx.app.log("BattleList", "Skin is null, skipping table background")
        }
        table.isVisible = true
        table.top() // Align entries to the top
        table.addListener(object : DragListener() {
            override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                dragOffset.set(x, y)
                Gdx.app.log("BattleList", "Drag started at screen ($x, $y)")
            }
            override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                if (!isInResizeCorner(x + offsetX, y + offsetY)) {
                    val newX = (table.x + (x - dragOffset.x)).coerceIn(0f, Gdx.graphics.width.toFloat() - size)
                    val newY = (table.y + (y - dragOffset.y)).coerceIn(0f, Gdx.graphics.height.toFloat() - size)
                    offsetX = newX
                    offsetY = newY
                    table.setPosition(offsetX, offsetY)
                    Gdx.app.log("BattleList", "Dragged to ($offsetX, $offsetY)")
                }
            }
            override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                Gdx.app.log("BattleList", "Drag stopped")
            }
        })
        Gdx.app.log("BattleList", "Initialized at ($offsetX, $offsetY), size ($size, $size)")
    }

    fun isInBounds(x: Float, y: Float): Boolean {
        val inBounds = x in offsetX..(offsetX + size) && y in offsetY..(offsetY + size)
        Gdx.app.log("BattleList", "Checking bounds at ($x, $y): $inBounds")
        return inBounds
    }

    fun isInResizeCorner(x: Float, y: Float): Boolean {
        val corner = getResizeCorner(x, y)
        Gdx.app.log("BattleList", "Checking resize corner at ($x, $y): $corner")
        return corner.isNotEmpty()
    }

    fun getResizeCorner(x: Float, y: Float): String {
        val left = offsetX
        val right = offsetX + size
        val bottom = offsetY
        val top = offsetY + size
        return when {
            x in left..(left + resizeCornerSize) && y in (top - resizeCornerSize)..top -> "top-left"
            x in (right - resizeCornerSize)..right && y in (top - resizeCornerSize)..top -> "top-right"
            x in left..(left + resizeCornerSize) && y in bottom..(bottom + resizeCornerSize) -> "bottom-left"
            x in (right - resizeCornerSize)..right && y in bottom..(bottom + resizeCornerSize) -> "bottom-right"
            else -> ""
        }
    }

    fun startDragging(x: Float, y: Float) {
        dragOffset.set(x - offsetX, y - offsetY)
        Gdx.app.log("BattleList", "Started dragging at ($x, $y), offset ($offsetX, $offsetY)")
    }

    fun drag(x: Float, y: Float) {
        val newX = (x - dragOffset.x).coerceIn(0f, Gdx.graphics.width.toFloat() - size)
        val newY = (y - dragOffset.y).coerceIn(0f, Gdx.graphics.height.toFloat() - size)
        offsetX = newX
        offsetY = newY
        table.setPosition(offsetX, offsetY)
        Gdx.app.log("BattleList", "Dragged to ($offsetX, $offsetY)")
    }

    fun stopDragging() {
        Gdx.app.log("BattleList", "Stopped dragging")
    }

    fun resize(corner: String, deltaX: Float, deltaY: Float) {
        val delta = (kotlin.math.abs(deltaX) + kotlin.math.abs(deltaY)) / 2
        when (corner) {
            "top-left" -> {
                val newSize = size - delta
                if (newSize in minSize..maxSize) {
                    offsetX += delta
                    offsetY += delta
                    size = newSize
                }
            }
            "top-right" -> {
                val newSize = size + delta
                if (newSize in minSize..maxSize) {
                    size = newSize
                }
            }
            "bottom-left" -> {
                val newSize = size + delta
                if (newSize in minSize..maxSize) {
                    offsetX += delta
                    size = newSize
                }
            }
            "bottom-right" -> {
                val newSize = size + delta
                if (newSize in minSize..maxSize) {
                    size = newSize
                }
            }
        }
        size = size.coerceIn(minSize, maxSize)
        offsetX = offsetX.coerceIn(0f, Gdx.graphics.width.toFloat() - size)
        offsetY = offsetY.coerceIn(0f, Gdx.graphics.height.toFloat() - size)
        table.setSize(size, size)
        table.setPosition(offsetX, offsetY)
        Gdx.app.log("BattleList", "Resized to size ($size, $size), offset ($offsetX, $offsetY)")
    }

    fun update(camera: OrthographicCamera) {
        if (!table.isVisible) {
            Gdx.app.log("BattleList", "Table is not visible")
            return
        }
        table.clear()
        Gdx.app.log("BattleList", "Clearing table for update")

        // Calculate viewport in world coordinates
        val viewport = Rectangle(
            camera.position.x - (camera.viewportWidth * camera.zoom) / 2,
            camera.position.y - (camera.viewportHeight * camera.zoom) / 2,
            camera.viewportWidth * camera.zoom,
            camera.viewportHeight * camera.zoom
        )
        Gdx.app.log("BattleList", "Viewport: x=${viewport.x}, y=${viewport.y}, width=${viewport.width}, height=${viewport.height}")

        // Calculate dynamic widths for elements (scaled up 20%)
        val availableWidth = size - 2 * sidePadding
        val spriteWidth = 48f // 40 * 1.2
        val hpBarWidth = availableWidth * 0.4f // 40% of available width
        val labelWidth = availableWidth - spriteWidth - hpBarWidth - 2 * sidePadding

        // Create scaled label style
        val labelStyle = skin?.let { LabelStyle(it.get("default", LabelStyle::class.java)) }?.apply {
            font.data.setScale(1.2f) // Scale font by 20%
        } ?: LabelStyle().apply {
            Gdx.app.log("BattleList", "Skin is null, using default LabelStyle")
        }

        // Add monsters in viewport to the table
        val monsters = monsterManager.getMonsters()
        Gdx.app.log("BattleList", "Found ${monsters.size} monsters in MonsterManager")
        var addedMonsters = 0
        monsters.forEach { monster ->
            val worldX = monster.x * mapManager.tileSize
            val worldY = monster.y * mapManager.tileSize
            Gdx.app.log("BattleList", "Checking monster ${monster.stats.name} at world ($worldX, $worldY), dead=${monster.isDead()}")
            if (viewport.contains(worldX, worldY) && !monster.isDead()) {
                // Filter out "(old)" and "(new)" from the monster name
                val filteredName = monster.stats.name.replace(Regex("\\s*\\((?i)(old|new)\\)", RegexOption.IGNORE_CASE), "").trim()
                // Create a nested table for the row
                val rowTable = Table().apply {
                    top() // Align contents to top
                    val spriteImage = Image(monster.sprite.texture).apply {
                        setSize(spriteWidth, spriteWidth)
                    }
                    val hpBarActor = object : Actor() {
                        override fun draw(batch: Batch?, parentAlpha: Float) {
                            batch?.end()
                            shapeRenderer.projectionMatrix = stage.camera.combined
                            shapeRenderer.begin(ShapeType.Filled)
                            val hpPercentage = monster.currentHp.toFloat() / monster.stats.hp.toFloat()
                            val hpBarColor = when {
                                hpPercentage > 0.5f -> Color.GREEN
                                hpPercentage > 0.2f -> Color.YELLOW
                                else -> Color.RED
                            }
                            val barHeight = 6f // 5 * 1.2
                            val hpWidth = width * hpPercentage
                            shapeRenderer.color = Color.BLACK
                            shapeRenderer.rect(x, y, width, barHeight)
                            shapeRenderer.color = hpBarColor
                            shapeRenderer.rect(x, y, hpWidth, barHeight)
                            shapeRenderer.end()
                            batch?.begin()
                            Gdx.app.log("BattleList", "Rendered HP bar for ${monster.stats.name} at ($x, $y), width=$width")
                        }
                    }.apply {
                        setSize(hpBarWidth, 6f)
                    }
                    val label = Label("$filteredName (${monster.currentHp}/${monster.stats.hp})", labelStyle).apply {
                        setAlignment(Align.left)
                    }
                    val indicatorActor = object : Actor() {
                        override fun draw(batch: Batch?, parentAlpha: Float) {
                            if (monster == player.targetedMonster) {
                                batch?.end()
                                shapeRenderer.projectionMatrix = stage.camera.combined
                                shapeRenderer.begin(ShapeType.Line)
                                shapeRenderer.color = Color.RED.cpy().apply { a = (MathUtils.sin(Gdx.graphics.getDeltaTime() * 15f) + 1f) / 2f }
                                shapeRenderer.rect(x, y, width, height)
                                shapeRenderer.end()
                                batch?.begin()
                                Gdx.app.log("BattleList", "Rendered red indicator for ${monster.stats.name} at ($x, $y), size=($width, $height)")
                            }
                        }
                    }.apply {
                        setSize(availableWidth, spriteWidth) // Match row height
                    }
                    add(spriteImage).size(spriteWidth, spriteWidth).pad(sidePadding)
                    add(hpBarActor).width(hpBarWidth).height(6f).pad(sidePadding)
                    add(label).width(labelWidth).pad(sidePadding)
                    addListener(object : ClickListener() {
                        override fun clicked(event: InputEvent?, x: Float, y: Float) {
                            if (player.targetedMonster == monster) {
                                player.targetedMonster = null
                                Gdx.app.log("BattleList", "Untargeted monster: ${monster.stats.name}")
                            } else {
                                player.targetedMonster = monster
                                Gdx.app.log("BattleList", "Targeted monster: ${monster.stats.name}")
                            }
                        }
                    })
                    setSize(availableWidth, spriteWidth) // Match row width and height
                    if (skin != null) {
                        background = skin.getDrawable("window") // Line 253
                    } else {
                        Gdx.app.log("BattleList", "Skin is null, skipping rowTable background for ${monster.stats.name}")
                    }
                }
                table.add(rowTable).width(availableWidth).pad(sidePadding).top()
                table.row()
                addedMonsters++
                Gdx.app.log("BattleList", "Added monster $filteredName to table, spriteWidth=$spriteWidth, hpBarWidth=$hpBarWidth, labelWidth=$labelWidth")
            }
        }
        if (addedMonsters == 0) {
            table.add(Label("No monsters visible", labelStyle)).grow().pad(sidePadding).top()
            table.row()
            Gdx.app.log("BattleList", "No monsters added to table")
        }
    }

    fun renderResize(uiCamera: OrthographicCamera) {
        shapeRenderer.projectionMatrix = uiCamera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.RED
        shapeRenderer.rect(offsetX, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY + size - resizeCornerSize, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.rect(offsetX + size - resizeCornerSize, offsetY, resizeCornerSize, resizeCornerSize)
        shapeRenderer.end()
        Gdx.app.log("BattleList", "Rendered resize corners at ($offsetX, $offsetY)")
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
