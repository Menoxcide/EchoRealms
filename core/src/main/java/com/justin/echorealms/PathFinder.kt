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
import kotlin.math.min
import kotlin.math.sqrt
import java.util.PriorityQueue

class PathFinder(
    private val mapManager: MapManager
) {
    private val shapeRenderer = ShapeRenderer()
    private val costMap = mutableMapOf<Vector2, Float>().withDefault { 1f }
    private val clusterSize = 10
    private val clusters = Array<Cluster>()
    private val maxSearchRadius = 40

    data class Cluster(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val entrances: MutableList<Vector2>)

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
        initializeClusters()
    }

    private fun initializeClusters() {
        val emptyMonsters = Array<Monster>()
        for (x in 0 until mapManager.mapTileWidth step clusterSize) {
            for (y in 0 until mapManager.mapTileHeight step clusterSize) {
                val maxX = min(x + clusterSize - 1, mapManager.mapTileWidth - 1)
                val maxY = min(y + clusterSize - 1, mapManager.mapTileHeight - 1)
                val entrances = mutableListOf<Vector2>()
                for (i in x..maxX) {
                    if (isWalkable(i, y, null, emptyMonsters)) entrances.add(Vector2(i.toFloat(), y.toFloat()))
                    if (isWalkable(i, maxY, null, emptyMonsters)) entrances.add(Vector2(i.toFloat(), maxY.toFloat()))
                }
                for (j in y..maxY) {
                    if (isWalkable(x, j, null, emptyMonsters)) entrances.add(Vector2(x.toFloat(), j.toFloat()))
                    if (isWalkable(maxX, j, null, emptyMonsters)) entrances.add(Vector2(maxX.toFloat(), j.toFloat()))
                }
                clusters.add(Cluster(x, y, maxX, maxY, entrances))
            }
        }
        Gdx.app.log("PathFinder", "Initialized ${clusters.size} clusters")
    }

    fun findPath(startX: Int, startY: Int, endX: Int, endY: Int, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>): Array<Vector2> {
        val path = Array<Vector2>()
        var targetX = endX
        var targetY = endY
        if (!isWalkable(endX, endY, monsterManager, monsterSnapshot)) {
            Gdx.app.log("PathFinder", "Target tile ($endX, $endY) is unwalkable or occupied - finding nearest alternative")
            val nearest = findNearestWalkable(startX, startY, endX, endY, monsterManager, monsterSnapshot)
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

        val startCluster = getCluster(startX, startY)
        val endCluster = getCluster(targetX, targetY)
        if (startCluster != endCluster && (abs(startX - targetX) > maxSearchRadius || abs(startY - targetY) > maxSearchRadius)) {
            return findHierarchicalPath(startX, startY, targetX, targetY, monsterManager, monsterSnapshot)
        }

        return findAStarPath(startX, startY, targetX, targetY, monsterManager, monsterSnapshot)
    }

    private fun findAStarPath(startX: Int, startY: Int, endX: Int, endY: Int, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>): Array<Vector2> {
        val path = Array<Vector2>()
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
                smoothPath(path, monsterManager, monsterSnapshot)
                Gdx.app.log("PathFinder", "Path found: ${path.joinToString()}")
                return path
            }

            closedSet.add(current)
            for (i in 0 until getNeighbors(current).size) {
                val neighbor = getNeighbors(current)[i]
                if (closedSet.contains(neighbor) || !isWalkable(neighbor.x.toInt(), neighbor.y.toInt(), monsterManager, monsterSnapshot)) {
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
        Gdx.app.log("PathFinder", "No path found from ($startX, $startY) to ($endX, $endY)")
        return path
    }

    private fun findHierarchicalPath(startX: Int, startY: Int, endX: Int, endY: Int, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>): Array<Vector2> {
        val path = Array<Vector2>()
        val startCluster = getCluster(startX, startY)
        val endCluster = getCluster(endX, endY)

        val clusterPath = findClusterPath(startCluster, endCluster)
        if (clusterPath.isEmpty()) {
            Gdx.app.log("PathFinder", "No cluster path found")
            return path
        }

        var prevPoint = Vector2(startX.toFloat(), startY.toFloat())
        path.add(prevPoint)
        for (i in 0 until clusterPath.size) {
            val entrance = clusterPath[i]
            val intraPath = findAStarPath(prevPoint.x.toInt(), prevPoint.y.toInt(), entrance.x.toInt(), entrance.y.toInt(), monsterManager, monsterSnapshot)
            if (intraPath.size > 0) {
                path.addAll(intraPath)
                prevPoint = intraPath[intraPath.size - 1]
            }
        }
        val finalPath = findAStarPath(prevPoint.x.toInt(), prevPoint.y.toInt(), endX, endY, monsterManager, monsterSnapshot)
        if (finalPath.size > 0) {
            path.addAll(finalPath)
        }

        smoothPath(path, monsterManager, monsterSnapshot)
        Gdx.app.log("PathFinder", "Hierarchical path found: ${path.joinToString()}")
        return path
    }

    private fun findClusterPath(startCluster: Cluster, endCluster: Cluster): Array<Vector2> {
        val path = Array<Vector2>()
        if (startCluster.entrances.isNotEmpty() && endCluster.entrances.isNotEmpty()) {
            path.add(startCluster.entrances[0])
            path.add(endCluster.entrances[0])
        }
        return path
    }

    private fun getCluster(x: Int, y: Int): Cluster {
        val clusterX = (x / clusterSize) * clusterSize
        val clusterY = (y / clusterSize) * clusterSize
        for (i in 0 until clusters.size) {
            val cluster = clusters[i]
            if (cluster.minX == clusterX && cluster.minY == clusterY) {
                return cluster
            }
        }
        return clusters[0]
    }

    private fun smoothPath(path: Array<Vector2>, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>) {
        if (path.size < 2) return
        val smoothed = Array<Vector2>()
        smoothed.add(path[0])
        var i = 1
        while (i < path.size - 1) {
            val p0 = path[i - 1]
            val p1 = path[i]
            val p2 = path[i + 1]
            if (canSmooth(p0, p2, monsterManager, monsterSnapshot)) {
                i++
            } else {
                smoothed.add(p1)
            }
            i++
        }
        smoothed.add(path[path.size - 1])
        path.clear()
        path.addAll(smoothed)
    }

    private fun canSmooth(start: Vector2, end: Vector2, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>): Boolean {
        val x0 = start.x.toInt()
        val y0 = start.y.toInt()
        val x1 = end.x.toInt()
        val y1 = end.y.toInt()
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var x = x0
        var y = y0

        while (true) {
            if (!isWalkable(x, y, monsterManager, monsterSnapshot)) return false
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return true
    }

    private fun findNearestWalkable(startX: Int, startY: Int, endX: Int, endY: Int, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>): Vector2? {
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
            for (i in 0 until size) {
                val current = queue[0]
                queue.removeIndex(0)
                val x = current.x.toInt()
                val y = current.y.toInt()
                if (x in 0 until mapManager.mapTileWidth && y in 0 until mapManager.mapTileHeight &&
                    mapManager.isWalkable(x, y) && (monsterManager == null || !monsterManager.isTileOccupied(x, y, null, null, monsterSnapshot))) {
                    Gdx.app.log("PathFinder", "Found nearest walkable tile at ($x, $y) after searching radius $radius")
                    return current
                }
                for (j in 0 until directions.size) {
                    val dir = directions[j]
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

    private fun isWalkable(x: Int, y: Int, monsterManager: MonsterManager?, monsterSnapshot: Array<Monster>): Boolean {
        if (x !in 0 until mapManager.mapTileWidth || y !in 0 until mapManager.mapTileHeight) {
            Gdx.app.log("PathFinder", "Tile ($x, $y): out of bounds")
            return false
        }
        val isMapWalkable = mapManager.isWalkable(x, y)
        val isOccupied = monsterManager?.isTileOccupied(x, y, null, null, monsterSnapshot) ?: false
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
            Vector2(0f, 1f), Vector2(0f, -1f), Vector2(-1f, 0f), Vector2(1f, 0f)
        )
        for (i in 0 until directions.size) {
            val dir = directions[i]
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
        return 1f
    }

    fun renderHighlight(path: Array<Vector2>, tileSize: Float, camera: OrthographicCamera) {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeType.Filled)
        shapeRenderer.color = Color.GREEN.cpy().apply { a = 0.5f }
        for (i in 0 until path.size) {
            val point = path[i]
            shapeRenderer.rect(point.x * tileSize, point.y * tileSize, tileSize, tileSize)
        }
        if (path.size > 0) {
            shapeRenderer.color = Color.YELLOW.cpy().apply { a = 0.5f }
            val dest = path[path.size - 1]
            shapeRenderer.rect(dest.x * tileSize, dest.y * tileSize, tileSize, tileSize)
        }
        shapeRenderer.end()
    }

    fun dispose() {
        shapeRenderer.dispose()
    }
}
