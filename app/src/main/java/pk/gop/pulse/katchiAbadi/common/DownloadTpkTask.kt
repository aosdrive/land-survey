package pk.gop.pulse.katchiAbadi.common

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Log
import com.esri.arcgisruntime.concurrent.Job
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.layers.ArcGISTiledLayer
import com.esri.arcgisruntime.tasks.tilecache.ExportTileCacheJob
import com.esri.arcgisruntime.tasks.tilecache.ExportTileCacheTask
import pk.gop.pulse.katchiAbadi.R
import java.io.File
import java.util.concurrent.ExecutionException

class DownloadTpkTask(private val context: Context) {

    private val TAG = "MapDownloadHelper"
    private lateinit var mExportTileCacheTask: ExportTileCacheTask
    private lateinit var mExportTileCacheJob: ExportTileCacheJob

    interface OnDownloadCompleteListener {
        fun onComplete(file: File)
        fun onFailure(error: Throwable)
        fun onProgress(progress: Int)
    }

    fun execute(tpkName: String, envelope: Envelope, listener: OnDownloadCompleteListener) {
//        var minScale = 1000000.0
        var minScale = 100000.0
        val maxScale = 500.0

        val tileLayer = createTiledLayer()

        // map view's current scale as the minScale and tiled layer's max scale as maxScale
//      var minScale: Double = tileLayer.defaultMinScale ?: 1.0
//        var maxScale: Double = tileLayer.maxScale

        // minScale must always be larger than maxScale
//        if (minScale <= maxScale) {
//            minScale = maxScale + 1
//        }


        mExportTileCacheTask = ExportTileCacheTask(tileLayer.uri)
        val parametersFuture = mExportTileCacheTask.createDefaultExportTileCacheParametersAsync(envelope, minScale, maxScale)

        parametersFuture.addDoneListener {
            val tileCacheDir = File(context.filesDir, context.getString(R.string.tile_cache_folder))
            if (!tileCacheDir.exists()) {
                if (!tileCacheDir.mkdirs()) {
                    Log.e(TAG, "Error creating local TileCache directory.")
                    listener.onFailure(Exception("Error creating local TileCache directory."))
                    return@addDoneListener
                }
                Log.i(TAG, "Local TileCache directory created.")
            } else {
                Log.i(TAG, "Local TileCache directory already exists.")
            }

            try {
                val parameters = parametersFuture.get()
                mExportTileCacheJob = mExportTileCacheTask.exportTileCache(
                    parameters,
                    "${context.filesDir}/${context.getString(R.string.tile_cache_folder)}/$tpkName.tpk"
                )

                mExportTileCacheJob.start()
                createProgressDialog(mExportTileCacheJob, listener)

                mExportTileCacheJob.addJobDoneListener {
                    if (mExportTileCacheJob.status == Job.Status.SUCCEEDED) {
                        val outputFile = File(context.filesDir, "${context.getString(R.string.tile_cache_folder)}/$tpkName.tpk")
                        listener.onComplete(outputFile)
                    } else {
                        val exception = mExportTileCacheJob.error
                        if (exception != null) {
                            Log.e(TAG, "Job failed with error: ${exception.message}")
                            listener.onFailure(exception)
                        } else {
                            Log.e(TAG, "Unknown error occurred during tile cache export.")
                            listener.onFailure(Exception("Unknown error occurred during tile cache export."))
                        }
                    }
                }

            } catch (e: InterruptedException) {
                Log.e(TAG, "InterruptedException: ${e.message}")
                listener.onFailure(e)
            } catch (e: ExecutionException) {
                Log.e(TAG, "ExecutionException: ${e.message}")
                listener.onFailure(e)
            }
        }
    }

    private fun createProgressDialog(exportTileCacheJob: ExportTileCacheJob, listener: OnDownloadCompleteListener) {
        val progressDialog = ProgressDialog(context)
        progressDialog.setTitle("Export Map")
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel") { _, _ ->
            exportTileCacheJob.cancel()
            listener.onFailure(Exception("Export cancelled by user."))
        }
        progressDialog.show()

        exportTileCacheJob.addProgressChangedListener {
            progressDialog.progress = exportTileCacheJob.progress
            Log.d(TAG, "Progress: ${exportTileCacheJob.progress}%")
            listener.onProgress(exportTileCacheJob.progress)
        }
        exportTileCacheJob.addJobDoneListener {
            progressDialog.dismiss()
            if (exportTileCacheJob.status != Job.Status.SUCCEEDED) {
                listener.onFailure(Exception("Job did not succeed. Current status: ${exportTileCacheJob.status}"))
            }
        }
    }

    private fun createTiledLayer(): ArcGISTiledLayer {
        return ArcGISTiledLayer("MapServer")
    }
}
