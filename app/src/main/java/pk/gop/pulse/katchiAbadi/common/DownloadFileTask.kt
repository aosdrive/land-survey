package pk.gop.pulse.katchiAbadi.common


import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

class DownloadFileTask(
    private val context: Context,
    private val mauzaId: Long,
    private val abadiId: Long,
    private val onDownloadCompleteListener: OnDownloadCompleteListener
) {

    private var downloadJob: Job? = null

    fun execute() {
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            var inputStream: BufferedInputStream? = null
            var outputStream: FileOutputStream? = null
            var connection: HttpURLConnection? = null

            try {

                val url = URL("${Constants.BASE_URL}Shared/$abadiId.tpk")

                println(url)

                connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    // Handle specific HTTP errors
                    val errorMessage = when (responseCode) {
                        HttpURLConnection.HTTP_NOT_FOUND -> "File not found: The requested file could not be found. Please contact support."
                        HttpURLConnection.HTTP_UNAUTHORIZED,
                        HttpURLConnection.HTTP_FORBIDDEN -> "Access denied: You do not have permission to download this file."
//                        else -> "Download failed with error code $responseCode."
                        else -> "File not found: The requested file could not be found. Please contact support."
                    }
                    throw IOException(errorMessage)
                }

                val contentLength = connection.contentLength
                inputStream = BufferedInputStream(connection.inputStream, 8192)

                val filesDir = context.cacheDir

                val file = File(filesDir, "${mauzaId}_${abadiId}.tpk")

                if (file.exists()) file.delete()

                outputStream = FileOutputStream(file)

                val buffer = ByteArray(1024) // 8 KB buffer size
                var bytesRead: Int
                var totalBytesRead: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {

                    if (!isActive) {
                        throw CancellationException("Download was cancelled")
                    }


                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead.toLong()

                    withContext(Dispatchers.Main) {
                        onDownloadCompleteListener.onProgress((100 * totalBytesRead / contentLength).toInt())
                    }
                }

                // Notify completion on the main thread
                withContext(Dispatchers.Main) {
                    onDownloadCompleteListener.onComplete(file)
                }

            } catch (e: IOException) {
                // Notify failure on the main thread
                withContext(Dispatchers.Main) {
                    onDownloadCompleteListener.onFailure(e)
                }
            } catch (e: CancellationException) {
                // Handle cancellation specifically
                withContext(Dispatchers.Main) {
                    onDownloadCompleteListener.onFailure(e)
                }
            } finally {
                connection?.disconnect()
                inputStream?.close()
                outputStream?.close()
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
    }

    interface OnDownloadCompleteListener {
        fun onComplete(file: File)
        fun onFailure(error: Throwable)
        fun onProgress(progress: Int)
    }
}