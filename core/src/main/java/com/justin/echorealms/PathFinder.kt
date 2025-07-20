package com.justin.echorealms

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer
import kotlin.math.abs
import java.util.PriorityQueue

class PathFinder(private val mapManager: MapManager) {
    private val shapeRenderer = ShapeRenderer()
    private val walkableCache = mutableMapOf<Vector2, Boolean>()
    private val monsterPositionCache = mutableMapOf<Vector2, Boolean>()
    private val costMap = mutableMapOf<Vector2, Float>().withDefault { 1f }
    private var lastUpdateTime = 0L
    private val debounceInterval = 200L

    init {
        val layer = mapManager.map.layers.get(0) as? TiledMapTileLayer
        if (layer != null) {
            for (x in 0 until mapManager.mapTileWidth) {
                for (y in 0 until mapManager.mapTileHeight) {
                    val cell = layer.getCell(x, y)
                    val key = Vector2(x.toFloat(), y.toFloat())
                    costMap[key] = if (cell != null && !cell.tile.properties.get("walkable", true, Boolean::class.java)) Float.MAX_VALUE else 1f
                }
            }
        }
    }

    fun findPath(startX: Int, startY: Int, endX: Int, endY: Int, monsterManager: MonsterManager): Array<Vector2> {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < debounceInterval) return Array()
        lastUpdateTime = currentTime

        walkableCache.clear()
        monsterPositionCache.clear()
        monsterManager.getMonsters().forEach { monster ->
            monsterPositionCache[Vector2(monster.x.toInt().toFloat(), monster.y.toInt().toFloat())] = true
        }

        val path = Array<Vector2>()
        if (!isWalkable(endX, endY, monsterManager) || monsterManager.isTileOccupied(endX, endY)) {
            Gdx.app.log("PathFinder", "Target tile ($endX, $endY) is unwalkable or occupied")
            return path
        }

        if (startX == endX && startY == endY) return path

        data class Node(val pos: Vector2, val fScore: Float) : Comparable<Node> {
            override fun compareTo(other: Node) = fScore.compareTo(other.fScore)
        }

        val openSet = PriorityQueue<Node>()
        val closedSet = mutableSetOf<Vector2>()
        val cameFrom = mutableMapOf<Vector2, Vector2>()
        val gScore = mutableMapOf<Vector2, Float>().withDefault { Float.MAX_VALUE }
        val fScore = mutableMapOf<Vector2, Float>().withDefault { Float.MAX_VALUE }

        val start = Vector2(startX.toFloat(), startY.toFloat())
        val end = Vector2(endX.toFloat(), endY.toFloat())

        gScore[start] = 0f
        fScore[start] = heuristic(start, end)
        openSet.add(Node(start, fScore[start]!!))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll().pos
            if (current == end) {
                reconstructPath(cameFrom, end, path)
                return path
            }

            closedSet.add(current)
            for (neighbor in getNeighbors(current)) {
                if (closedSet.contains(neighbor) || !isWalkable(neighbor.x.toInt(), neighbor.y.toInt(), monsterManager)) continue

                val cost = costMap.getValue(neighbor)
                if (cost == Float.MAX_VALUE) continue

                val tentativeGScore = gScore.getValue(current) + distance(current, neighbor) * cost
                if (tentativeGScore < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + heuristic(neighbor, end)
                    openSet.add(Node(neighbor, fScore[neighbor]!!))
                }
            }
        }
        Gdx.app.log("PathFinder", "No path found from ($startX, $startY) to ($endX, $endY)")
        return path
    }

    private fun isWalkable(x: Int, y: Int, monsterManager: MonsterManager): Boolean {
        val key = Vector2(x.toFloat(), y.toFloat())
        return walkableCache.getOrPut(key) {
            mapManager.isWalkable(x, y) && !monsterManager.isTileOccupied(x, y) && costMap.getValue(key) != Float.MAX_VALUE
        }
    }

    private fun reconstructPath(cameFrom: MutableMap<Vector2, Vector2>, current: Vector2, path: Array<Vector2>) {
        path.add(current)
        var currentNode = current
        while (cameFrom.containsKey(currentNode)) {
            currentNode = cameFrom[currentNode]!!
            path.add(currentNode)
        }
        path.reverse()
    }

    private fun getNeighbors(current: Vector2): List<Vector2> {
        val neighbors = mutableListOf<Vector2>()
        val directions = listOf(
            Vector2(0f, 1f), Vector2(0f, -1f), Vector2(-1f, 0f), Vector2(1f, 0f),
            Vector2(-1f, 1f), Vector2(1f, 1f), Vector2(-1f, -1f), Vector2(1f, -1f)
        )
        for (dir in directions) {
            val newX = current.x + dir.x
            val newY = current.y + dir.y
            if (newX.toInt() in 0 until mapManager.mapTileWidth && newY.toInt() in 0 until mapManager.mapTileHeight) {
                neighbors.add(Vector2(newX, newY))
            }
        }
        return neighbors
    }

    private fun heuristic(a: Vector2, b: Vector2): Float {
        return abs(a.x - b.x) + abs(a.y - b.y)
    }

    private fun distance(a: Vector2, b: Vector2): Float {
        return if (abs(a.x - b.x) + abs(a.y - b.y) == 2f) 1.414f else 1f
    }

    fun renderHighlight(path: Array<Vector2>, tileSize: Float, camera: OrthographicCamera) {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.GREEN.cpy().apply { a = 0.5f }
        for (point in path) {
            shapeRenderer.rect(point.x * tileSize, point.y * tileSize, tileSize, tileSize)
        }
        if (path.size > 0) {
            shapeRenderer.color = Color.YELLOW.cpy().apply { a = 0.5f }
            val dest = path.last()
            shapeRenderer.rect(dest.x * tileSize, dest.y * tileSize, tileSize, tileSize)
        }
        shapeRenderer.end()
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
