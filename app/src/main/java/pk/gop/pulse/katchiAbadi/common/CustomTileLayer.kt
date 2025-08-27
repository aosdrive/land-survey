package pk.gop.pulse.katchiAbadi.common

import com.esri.arcgisruntime.layers.ImageTiledLayer
import com.esri.arcgisruntime.arcgisservices.TileInfo
import com.esri.arcgisruntime.data.TileKey
import com.esri.arcgisruntime.geometry.Envelope
import kotlinx.coroutines.runBlocking
import java.io.IOException

/*class CustomTileLayer(
    tileInfo: TileInfo,
    fullExtent: Envelope,
    private val tileManager: TileManager,
    private val areaId: Long,
) : ImageTiledLayer(tileInfo, fullExtent) {

    // Ensure that combinedGeometry is properly initialized
    private var combinedGeometry: Geometry? = createPolygonFromEnvelope(fullExtent)

    override fun getTile(tileKey: TileKey): ByteArray {
        val level = tileKey.level
        val row = tileKey.row
        val col = tileKey.column

        // Create the tile extent using the tile key
        val tileExtent = calculateTileExtent(tileKey, tileInfo, fullExtent)

        return if (combinedGeometry != null && GeometryEngine.intersects(tileExtent, combinedGeometry)) {
            try {
                // Fetch and cache the tile using the TileManager
                val tileFile = runBlocking(Dispatchers.IO) {
                    tileManager.downloadAndCacheTile(level, row, col, areaId, "files")
                }
                tileFile?.readBytes() ?: ByteArray(0)
            } catch (e: IOException) {
                e.printStackTrace()
                ByteArray(0)
            }
        } else {
            ByteArray(0)
        }
    }

    private fun createPolygonFromEnvelope(envelope: Envelope): Polygon {
        // Get the corner points of the envelope
        val lowerLeft = Point(envelope.xMin, envelope.yMin, envelope.spatialReference)
        val lowerRight = Point(envelope.xMax, envelope.yMin, envelope.spatialReference)
        val upperRight = Point(envelope.xMax, envelope.yMax, envelope.spatialReference)
        val upperLeft = Point(envelope.xMin, envelope.yMax, envelope.spatialReference)

        // Create a PointCollection with the envelope corners
        val pointCollection = PointCollection(envelope.spatialReference)
        pointCollection.add(lowerLeft)
        pointCollection.add(lowerRight)
        pointCollection.add(upperRight)
        pointCollection.add(upperLeft)
        pointCollection.add(lowerLeft) // Close the polygon by adding the first point again

        // Create and return a Polygon from the PointCollection
        return Polygon(pointCollection)
    }

    // Method to calculate the tile extent
    private fun calculateTileExtent(tileKey: TileKey, tileInfo: TileInfo, fullExtent: Envelope): Envelope {
        val zoomLevel = tileKey.level
        val col = tileKey.column
        val row = tileKey.row

        // Get map's full extent and calculate the number of tiles at this zoom level
        val mapWidth = fullExtent.width
        val mapHeight = fullExtent.height
        val numTiles = 2.0.pow(zoomLevel.toDouble()).toInt()

        // Calculate resolution for each tile
        val tileResolutionX = mapWidth / numTiles
        val tileResolutionY = mapHeight / numTiles

        // Calculate the tile extent
        val minX = fullExtent.xMin + col * tileResolutionX
        val minY = fullExtent.yMin + (numTiles - row - 1) * tileResolutionY
        val maxX = minX + tileResolutionX
        val maxY = minY + tileResolutionY

        return Envelope(minX, minY, maxX, maxY, tileInfo.spatialReference)
    }
}*/

class CustomTileLayer(
    tileInfo: TileInfo,
    fullExtent: Envelope,
    private val tileManager: TileManager,
    private val areaId: String,
    private val minZoomLevel: Int,
    private val maxZoomLevel: Int,

    ) : ImageTiledLayer(tileInfo, fullExtent) {

    override fun getTile(tileKey: TileKey): ByteArray {
        return runBlocking {
            try {
                val level = tileKey.level
                val row = tileKey.row
                val col = tileKey.column

                // Fetch and cache the tile using the TileManager
                val tileFile = tileManager.viewCacheTile(level, row, col, areaId, "files", minZoomLevel, maxZoomLevel)
                // Return the tile bytes if found, else return an empty byte array
                tileFile?.readBytes() ?: ByteArray(0)
            } catch (e: IOException) {
                e.printStackTrace()
                ByteArray(0)
            }
        }
    }
}

