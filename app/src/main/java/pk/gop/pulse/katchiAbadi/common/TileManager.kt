package pk.gop.pulse.katchiAbadi.common

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.io.File
import java.io.FileOutputStream


class TileManager(private val context: Context) {

    fun viewCacheTile(
        level: Int,
        row: Int,
        column: Int,
        areaId: String,
        sourceFolder: String,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? {

        // Check if the zoom level is within the specified bounds
        if (level < minZoomLevel || level > maxZoomLevel) {
            return null // Return null if the requested zoom level is out of range
        }

        val cacheDir = when (sourceFolder) {
            "files" -> {
                File(context.filesDir, "MapTiles/$areaId").apply {
                    if (!exists()) mkdirs()
                }
            }

            else -> {
                File(context.cacheDir, "MapTiles/$areaId").apply {
                    if (!exists()) mkdirs()
                }
            }
        }

        val tileFile = File(cacheDir, "tile_${level}_${row}_${column}.png")
        return if (tileFile.exists()) tileFile else null

    }

    suspend fun downloadAndCacheTileNew(
        level: Int,
        row: Int,
        column: Int,
        areaId: String,
        sourceFolder: String,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? {

        val cacheDir = when (sourceFolder) {
            "files" -> {
                File(context.filesDir, "MapTiles/$areaId").apply {
                    if (!exists()) mkdirs()
                }
            }

            else -> {
                File(context.cacheDir, "MapTiles/$areaId").apply {
                    if (!exists()) mkdirs()
                }
            }
        }

        val tileFile = File(cacheDir, "tile_${level}_${row}_${column}.png")
        if (tileFile.exists()) return tileFile

        if (sourceFolder != "files") {
            if (level > maxZoomLevel || level < minZoomLevel) return null // Don't download tiles at high zoom levels'
        }

        val tileService = createTileService()
        val url = "vt/lyrs=s&x=$column&y=$row&z=$level"

        Log.d("TAG", "$url")

        return withContext(Dispatchers.IO) {
            try {
                val responseBody: ResponseBody = tileService.getTile(url)
                val bytes = responseBody.bytes()

                FileOutputStream(tileFile).use { outputStream ->
                    outputStream.write(bytes)
                }

                tileFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun downloadAndCacheTile(
        level: Int,
        row: Int,
        column: Int,
        areaId: Long,
        sourceFolder: String,
        minZoomLevel: Int,
        maxZoomLevel: Int,
    ): File? {

        val cacheDir = when (sourceFolder) {
            "files" -> {
                File(context.filesDir, "MapTiles/$areaId").apply {
                    if (!exists()) mkdirs()
                }
            }

            else -> {
                File(context.cacheDir, "MapTiles/$areaId").apply {
                    if (!exists()) mkdirs()
                }
            }
        }

        val tileFile = File(cacheDir, "tile_${level}_${row}_${column}.png")
        if (tileFile.exists()) return tileFile

        if (sourceFolder != "files") {
            if (level > maxZoomLevel || level < minZoomLevel) return null // Don't download tiles at high zoom levels'
        }

        val tileService = createTileService()
        val url = "vt/lyrs=s&x=$column&y=$row&z=$level"

        Log.d("TAG", "$url")

        return withContext(Dispatchers.IO) {
            try {
                val responseBody: ResponseBody = tileService.getTile(url)
                val bytes = responseBody.bytes()

                FileOutputStream(tileFile).use { outputStream ->
                    outputStream.write(bytes)
                }

                tileFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /*    suspend fun manageCacheSize(maxCacheSize: Long) {
            withContext(Dispatchers.IO) {
                val files = cacheDir.listFiles() ?: return@withContext
                var totalSize = files.sumOf { it.length() }

                if (totalSize > maxCacheSize) {
                    files.sortedBy { it.lastModified() }.forEach { file ->
                        if (totalSize > maxCacheSize) {
                            totalSize -= file.length()
                            file.delete()
                        }
                    }
                }
            }
        }*/
}

interface TileService {
    @GET
    suspend fun getTile(@Url url: String): ResponseBody
}

fun createTileService(): TileService {
    val retrofit = Retrofit.Builder()
        .baseUrl("https://mt1.google.com/") // Base URL is required by Retrofit
        .build()

    return retrofit.create(TileService::class.java)
}


//class TileManager(private val context: Context) {
//
//    private val cacheDir: File = File(context.cacheDir, "mapTiles").apply {
//        if (!exists()) mkdirs()
//    }
//
//    suspend fun downloadAndCacheTile(level: Int, row: Int, column: Int): File? {
//        val tileFile = File(cacheDir, "tile_${level}_${row}_${column}.png")
//        if (tileFile.exists()) return tileFile
//
//        val tileService = createTileService()
//        val url = "vt/lyrs=s&x=$column&y=$row&z=$level"
//
//        return withContext(Dispatchers.IO) {
//            try {
//                val responseBody: ResponseBody = tileService.getTile(url)
//                val bytes = responseBody.bytes()
//
//                FileOutputStream(tileFile).use { outputStream ->
//                    outputStream.write(bytes)
//                }
//
//                tileFile
//            } catch (e: Exception) {
//                e.printStackTrace()
//                null
//            }
//        }
//    }
//
//    suspend fun manageCacheSize(maxCacheSize: Long) {
//        withContext(Dispatchers.IO) {
//            val files = cacheDir.listFiles() ?: return@withContext
//            var totalSize = files.sumOf { it.length() }
//
//            if (totalSize > maxCacheSize) {
//                files.sortedBy { it.lastModified() }.forEach { file ->
//                    if (totalSize > maxCacheSize) {
//                        totalSize -= file.length()
//                        file.delete()
//                    }
//                }
//            }
//        }
//    }
//}
//
//interface TileService {
//    @GET
//    suspend fun getTile(@Url url: String): ResponseBody
//}
//
//fun createTileService(): TileService {
//    val retrofit = Retrofit.Builder()
//        .baseUrl("https://mt1.google.com/") // Base URL is required by Retrofit
//        .build()
//
//    return retrofit.create(TileService::class.java)
//}

