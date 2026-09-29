package pk.gop.pulse.katchiAbadi.presentation.util

import com.esri.arcgisruntime.geometry.ImmutablePart
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import kotlin.math.hypot

object TraceUtils {

    data class RingHit(
        val polyIndex: Int,
        val ringIndex: Int,
        val segmentIndex: Int,
        val t: Double,
        val point: Point
    )

    fun ringPoints(part: ImmutablePart): List<Point> {
        val pts = part.points.toList()
        if (pts.size > 1 && pts.first().x == pts.last().x && pts.first().y == pts.last().y)
            return pts.dropLast(1)
        return pts
    }

    fun locate(point: Point, polygons: List<Polygon>, tolerance: Double): RingHit? {
        var best: RingHit? = null
        var bestDist = tolerance
        polygons.forEachIndexed { pi, poly ->
            poly.parts.forEachIndexed { ri, part ->
                val pts = ringPoints(part)
                val n = pts.size
                if (n < 3) return@forEachIndexed
                for (i in 0 until n) {
                    val (proj, t, d) = projectOnSegment(point, pts[i], pts[(i + 1) % n])
                    if (d < bestDist) {
                        bestDist = d
                        best = RingHit(pi, ri, i, t, proj)
                    }
                }
            }
        }
        return best
    }

    private fun projectOnSegment(p: Point, a: Point, b: Point): Triple<Point, Double, Double> {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0
        else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        val px = a.x + t * dx
        val py = a.y + t * dy
        return Triple(Point(px, py, a.spatialReference), t, hypot(p.x - px, p.y - py))
    }
}

class TraceGraph(polygons: List<Polygon>, private val mergeTol: Double) {

    private val nodes = mutableListOf<Point>()
    private val adj = mutableListOf<MutableList<Pair<Int, Double>>>()
    private val cell = HashMap<Long, MutableList<Int>>()
    private val ringNodes = HashMap<Long, IntArray>()

    init {
        polygons.forEachIndexed { pi, poly ->
            poly.parts.forEachIndexed { ri, part ->
                val pts = TraceUtils.ringPoints(part)
                if (pts.size < 3) return@forEachIndexed
                val ids = IntArray(pts.size) { nodeFor(pts[it]) }
                for (i in ids.indices) link(ids[i], ids[(i + 1) % ids.size])
                ringNodes[ringKey(pi, ri)] = ids
            }
        }
    }

    private fun ringKey(pi: Int, ri: Int) = (pi.toLong() shl 32) or ri.toLong()
    private fun cellKey(cx: Long, cy: Long) = (cx shl 32) xor (cy and 0xffffffffL)
    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun nodeFor(p: Point): Int {
        val cx = Math.round(p.x / mergeTol)
        val cy = Math.round(p.y / mergeTol)
        for (dx in -1L..1L) for (dy in -1L..1L) {
            cell[cellKey(cx + dx, cy + dy)]?.forEach { id ->
                if (dist(nodes[id], p) <= mergeTol) return id
            }
        }
        nodes.add(p)
        adj.add(mutableListOf())
        val id = nodes.size - 1
        cell.getOrPut(cellKey(cx, cy)) { mutableListOf() }.add(id)
        return id
    }

    private fun link(a: Int, b: Int) {
        if (a == b) return
        val d = dist(nodes[a], nodes[b])
        adj[a].add(b to d)
        adj[b].add(a to d)
    }

    /** from -> to shortest path along parcel edges; beech ke vertices (endpoints exclude). */
    fun path(from: TraceUtils.RingHit, to: TraceUtils.RingHit): List<Point>? {
        val fromRing = ringNodes[ringKey(from.polyIndex, from.ringIndex)] ?: return null
        val toRing = ringNodes[ringKey(to.polyIndex, to.ringIndex)] ?: return null

        if (from.polyIndex == to.polyIndex && from.ringIndex == to.ringIndex &&
            from.segmentIndex == to.segmentIndex
        ) return emptyList()

        val n = nodes.size
        val start = n
        val end = n + 1
        val dStart = DoubleArray(n + 2) { Double.MAX_VALUE }
        val prev = IntArray(n + 2) { -1 }

        fun endpoints(ring: IntArray, hit: TraceUtils.RingHit) =
            listOf(ring[hit.segmentIndex], ring[(hit.segmentIndex + 1) % ring.size])

        val startLinks = endpoints(fromRing, from).map { it to dist(from.point, nodes[it]) }
        val endLinks = endpoints(toRing, to).associateWith { dist(to.point, nodes[it]) }

        val pq = java.util.PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
        dStart[start] = 0.0
        pq.add(start to 0.0)

        while (pq.isNotEmpty()) {
            val (u, du) = pq.poll()
            if (du > dStart[u]) continue
            if (u == end) break
            val neighbours: List<Pair<Int, Double>> =
                if (u == start) startLinks
                else adj[u] + (endLinks[u]?.let { listOf(end to it) } ?: emptyList())
            for ((v, w) in neighbours) {
                val nd = du + w
                if (nd < dStart[v]) {
                    dStart[v] = nd
                    prev[v] = u
                    pq.add(v to nd)
                }
            }
        }

        if (dStart[end] == Double.MAX_VALUE) return null

        val out = ArrayDeque<Point>()
        var cur = prev[end]
        while (cur != -1 && cur != start) {
            out.addFirst(nodes[cur])
            cur = prev[cur]
        }
        return out.toList()
    }
}