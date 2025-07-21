// core/src/main/java/com/justin/echorealms/PathFinder.kt
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
    private val costMap = mutableMapOf<Vector2, Float>().withDefault { 1f }

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
        val path = Array<Vector2>()
        var targetX = endX
        var targetY = endY
        if (!isWalkable(endX, endY, monsterManager)) {
            Gdx.app.log("PathFinder", "Target tile ($endX, $endY) is unwalkable or occupied - finding nearest alternative")
            val nearest = findNearestWalkable(startX, startY, endX, endY, monsterManager)
            if (nearest == null) {
                Gdx.app.log("PathFinder", "No nearest walkable tile found")
                return path
            }
            targetX = nearest.x.toInt()
            targetY = nearest.y.toInt()
            Gdx.app.log("PathFinder", "Adjusted target to ($targetX, $targetY)")
        }

        if (startX == targetX && startY == targetY) {
            Gdx.app.log("PathFinder", "Start and target are the same ($startX, $startY)")
            return path
        }

        data class Node(val pos: Vector2, val fScore: Float) : Comparable<Node> {
            override fun compareTo(other: Node) = fScore.compareTo(other.fScore)
        }

        val openSet = PriorityQueue<Node>()
        val closedSet = mutableSetOf<Vector2>()
        val cameFrom = mutableMapOf<Vector2, Vector2>()
        val gScore = mutableMapOf<Vector2, Float>().withDefault { Float.MAX_VALUE }
        val fScore = mutableMapOf<Vector2, Float>().withDefault { Float.MAX_VALUE }

        val start = Vector2(startX.toFloat(), startY.toFloat())
        val end = Vector2(targetX.toFloat(), targetY.toFloat())

        gScore[start] = 0f
        fScore[start] = heuristic(start, end)
        openSet.add(Node(start, fScore[start]!!))

        while (openSet.isNotEmpty()) {
            val current = openSet.poll().pos
            if (current == end) {
                reconstructPath(cameFrom, end, path)
                Gdx.app.log("PathFinder", "Path found: ${path.joinToString()}")
                return path
            }

            closedSet.add(current)
            for (neighbor in getNeighbors(current)) {
                if (closedSet.contains(neighbor) || !isWalkable(neighbor.x.toInt(), neighbor.y.toInt(), monsterManager)) {
                    Gdx.app.log("PathFinder", "Skipping neighbor ($neighbor): in closed set or unwalkable")
                    continue
                }

                val cost = costMap.getValue(neighbor)
                if (cost == Float.MAX_VALUE) {
                    Gdx.app.log("PathFinder", "Skipping neighbor ($neighbor): infinite cost")
                    continue
                }

                val tentativeGScore = gScore.getValue(current) + distance(current, neighbor) * cost
                if (tentativeGScore < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + heuristic(neighbor, end)
                    openSet.add(Node(neighbor, fScore[neighbor]!!))
                    Gdx.app.log("PathFinder", "Updated neighbor ($neighbor): gScore=$tentativeGScore, fScore=${fScore[neighbor]}")
                }
            }
        }
        Gdx.app.log("PathFinder", "No path found from ($startX, $startY) to ($targetX, $targetY)")
        return path
    }

    private fun findNearestWalkable(startX: Int, startY: Int, endX: Int, endY: Int, monsterManager: MonsterManager): Vector2? {
        val queue = Array<Vector2>()
        val visited = Array(mapManager.mapTileWidth) { BooleanArray(mapManager.mapTileHeight) { false } }
        queue.add(Vector2(endX.toFloat(), endY.toFloat()))
        visited[endX][endY] = true

        val directions = arrayOf(
            Vector2(1f, 0f), Vector2(-1f, 0f), Vector2(0f, 1f), Vector2(0f, -1f),
            Vector2(1f, 1f), Vector2(-1f, 1f), Vector2(1f, -1f), Vector2(-1f, -1f)
        )

        var radius = 0
        while (queue.size > 0 && radius < 10) {
            val size = queue.size
            repeat(size) {
                val current = queue.removeIndex(0)
                val x = current.x.toInt()
                val y = current.y.toInt()
                if (x in 0 until mapManager.mapTileWidth && y in 0 until mapManager.mapTileHeight &&
                    mapManager.isWalkable(x, y) && !monsterManager.isTileOccupied(x, y)) {
                    Gdx.app.log("PathFinder", "Found nearest walkable tile at ($x, $y) after searching radius $radius")
                    return current
                }
                for (dir in directions) {
                    val nx = (x + dir.x).toInt()
                    val ny = (y + dir.y).toInt()
                    if (nx in 0 until mapManager.mapTileWidth && ny in 0 until mapManager.mapTileHeight && !visited[nx][ny]) {
                        queue.add(Vector2(nx.toFloat(), ny.toFloat()))
                        visited[nx][ny] = true
                        Gdx.app.log("PathFinder", "Added tile ($nx, $ny) to BFS queue")
                    }
                }
            }
            radius++
        }
        Gdx.app.log("PathFinder", "No walkable tile found near ($endX, $endY) after searching radius $radius")
        return null
    }

    private fun isWalkable(x: Int, y: Int, monsterManager: MonsterManager): Boolean {
        if (x !in 0 until mapManager.mapTileWidth || y !in 0 until mapManager.mapTileHeight) {
            Gdx.app.log("PathFinder", "Tile ($x, $y): out of bounds")
            return false
        }
        val isMapWalkable = mapManager.isWalkable(x, y)
        val isOccupied = monsterManager.isTileOccupied(x, y)
        val cost = costMap.getValue(Vector2(x.toFloat(), y.toFloat()))
        val walkable = isMapWalkable && !isOccupied && cost != Float.MAX_VALUE
        Gdx.app.log("PathFinder", "Tile ($x, $y): walkable=$isMapWalkable, occupied=$isOccupied, cost=$cost, result=$walkable")
        return walkable
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
