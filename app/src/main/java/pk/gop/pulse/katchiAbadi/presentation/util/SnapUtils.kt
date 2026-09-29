package pk.gop.pulse.katchiAbadi.presentation.util

import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.ProximityResult

object SnapUtils {

    fun snap(
        raw: Point,
        targets: List<Polygon>,
        tolerance: Double
    ): Point {
        var bestVertex: ProximityResult? = null
        var bestEdge: ProximityResult? = null

        targets.forEach { poly ->
            // Quick reject: extent se door hai to skip
            val ext = poly.extent
            if (raw.x < ext.xMin - tolerance || raw.x > ext.xMax + tolerance ||
                raw.y < ext.yMin - tolerance || raw.y > ext.yMax + tolerance
            ) return@forEach

            GeometryEngine.nearestVertex(poly, raw)?.let { pr ->
                if (pr.distance <= tolerance &&
                    (bestVertex == null || pr.distance < bestVertex!!.distance)
                ) bestVertex = pr
            }

            GeometryEngine.nearestCoordinate(poly, raw)?.let { pr ->
                if (pr.distance <= tolerance &&
                    (bestEdge == null || pr.distance < bestEdge!!.distance)
                ) bestEdge = pr
            }
        }

        return bestVertex?.coordinate ?: bestEdge?.coordinate ?: raw
    }
}