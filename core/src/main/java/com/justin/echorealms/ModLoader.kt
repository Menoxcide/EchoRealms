package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import org.luaj.vm2.lib.OneArgFunction

class ModLoader(private val eventBus: EventBus, private val game: MyGame) {
    private val modsDir = Gdx.files.internal("mods/")  // Or external for Android
    private val scripts = mutableListOf<LuaValue>()

    fun loadMods() {
        if (!modsDir.exists() || !modsDir.isDirectory) {
            Gdx.app.log("ModLoader", "No mods directory found")
            return
        }

        modsDir.list().forEach { modDir ->
            if (modDir.isDirectory) {
                Gdx.app.log("ModLoader", "Loading mod: ${modDir.name()}")
                loadAssets(modDir)
                loadScripts(modDir)
            }
        }
    }

    private fun loadAssets(modDir: FileHandle) {
        // Example: Load new textures or maps
        val newMapFile = modDir.child("new_map.tmx")
        if (newMapFile.exists()) {
            // Integrate into MapManager, e.g., add layers or tiles
            // mapManager.addCustomMap(newMapFile)
        }
        // Similar for textures, sounds, etc.
    }

    private fun loadScripts(modDir: FileHandle) {
        val scriptFile = modDir.child("mod.lua")
        if (scriptFile.exists()) {
            val globals = JsePlatform.standardGlobals()  // Lua environment
            globals.set("showMessage", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    game.showMessage(arg.tojstring())
                    return LuaValue.NIL
                }
            })
            val script = globals.load(scriptFile.readString())
            script.call()  // Execute script

            // Example: Register event listeners from Lua
            val onPlayerMove = globals.get("onPlayerMove")
            if (onPlayerMove.isfunction()) {
                eventBus.subscribe("playerMoved") { args ->
                    try {
                        // Safely convert args to Float for Lua
                        val x = (args[0] as? Int)?.toFloat() ?: (args[0] as? Float) ?: 0f
                        val y = (args[1] as? Int)?.toFloat() ?: (args[1] as? Float) ?: 0f
                        onPlayerMove.call(LuaValue.valueOf(x.toDouble()), LuaValue.valueOf(y.toDouble()))
                        Gdx.app.log("ModLoader", "Processed playerMoved event to ($x, $y)")
                    } catch (e: Exception) {
                        Gdx.app.error("ModLoader", "Error processing playerMoved event: ${e.message}")
                    }
                }
            }

            scripts.add(script)
        }
    }
}
