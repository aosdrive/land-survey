package pk.gop.pulse.katchiAbadi.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.adapter.PendingAcceptAdapter
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.repository.MapAnalysisRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivityPendingAcceptsBinding
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAcceptEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import javax.inject.Inject

private const val TAG = "PendingAccepts"

/**
 * Survey removals ("accepts") saved from Map Analysis that have not reached the server yet.
 * Same flow as [PendingDeclinesActivity]: a list, Upload All, and delete per row.
 */
@AndroidEntryPoint
class PendingAcceptsActivity : AppCompatActivity() {

    @Inject
    lateinit var mapAnalysisRepository: MapAnalysisRepository

    @Inject
    lateinit var database: AppDatabase

    private lateinit var binding: ActivityPendingAcceptsBinding
    private lateinit var adapter: PendingAcceptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingAcceptsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Pending Survey Removals"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = PendingAcceptAdapter { accept -> confirmDelete(accept) }
        binding.recyclerAccepts.layoutManager = LinearLayoutManager(this)
        binding.recyclerAccepts.adapter = adapter

        binding.btnUploadAll.setOnClickListener { uploadAll() }
    }

    override fun onResume() {
        super.onResume()
        loadAccepts()
    }

    private fun loadAccepts() {
        lifecycleScope.launch {
            val list = mapAnalysisRepository.getPendingAccepts()
            adapter.submitList(list)

            binding.tvCount.text =
                if (list.size == 1) "1 PENDING SURVEY REMOVAL" else "${list.size} PENDING SURVEY REMOVALS"

            val empty = list.isEmpty()
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.recyclerAccepts.visibility = if (empty) View.GONE else View.VISIBLE
            binding.btnUploadAll.isEnabled = !empty
            binding.btnUploadAll.alpha = if (empty) 0.4f else 1f
        }
    }

    private fun confirmDelete(accept: ParcelAcceptEntity) {
        lifecycleScope.launch {
            // A new survey of this parcel may already be waiting on the device. Without the removal
            // it would upload on top of the server's existing survey, so it must go first.
            val hasPendingSurvey = withContext(Dispatchers.IO) {
                accept.serverParcelId > 0 &&
                    database.newSurveyNewDao().getSurveysByParcelId(accept.serverParcelId).isNotEmpty()
            }
            if (hasPendingSurvey) {
                ZdAlert.warn(
                    this@PendingAcceptsActivity, "Survey Waiting",
                    "Parcel ${accept.parcelNo} already has a new survey waiting for upload on this device. " +
                        "Upload both together, or delete that survey from Saved Records first."
                )
                return@launch
            }

            ZdAlert.confirmDanger(
                context = this@PendingAcceptsActivity,
                title = "Delete Survey Removal?",
                message = "The survey removal for Parcel ${accept.parcelNo} has not been uploaded yet. " +
                        "Deleting it keeps the parcel's survey on the server, and the parcel shows as " +
                        "red again in Map Analysis.",
                confirmText = "Delete"
            ) {
                lifecycleScope.launch {
                    val deleted = try {
                        mapAnalysisRepository.deleteAccept(accept)
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "Delete failed: ${e.message}", e)
                        ZdAlert.error(this@PendingAcceptsActivity, "Not Deleted", e.message ?: "Unknown error")
                        false
                    }
                    if (deleted) {
                        // The row is gone either way; a failure here only affects the local colour.
                        try {
                            restoreLocalSurveyStatus(accept)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not restore local survey status: ${e.message}")
                        }
                        ToastUtil.showShort(this@PendingAcceptsActivity, "Survey removal deleted")
                    }
                    loadAccepts()
                }
            }
        }
    }

    /**
     * Saving the removal marked the parcel unsurveyed on this device so it could be surveyed
     * again. Without the removal the server survey stands, so the parcel goes back to surveyed -
     * unless a new survey of it is already waiting on this device, which keeps its own status.
     */
    private suspend fun restoreLocalSurveyStatus(accept: ParcelAcceptEntity) = withContext(Dispatchers.IO) {
        val parcelId = accept.serverParcelId
        if (parcelId <= 0) return@withContext
        if (database.newSurveyNewDao().getSurveysByParcelId(parcelId).isNotEmpty()) return@withContext
        if (accept.parcelStatus.contains("surveyed", ignoreCase = true)) {
            database.activeParcelDao().updateSurveyStatus(parcelId, 2)
        }
    }

    private fun uploadAll() {
        if (!Utility.checkInternetConnection(this)) {
            Utility.dialog(this, "Please connect to the internet and try again.", "No Internet!")
            return
        }

        lifecycleScope.launch {
            binding.progressUpload.visibility = View.VISIBLE
            binding.btnUploadAll.isEnabled = false

            val result: Resource<MapAnalysisRepository.AcceptUploadSummary> = try {
                mapAnalysisRepository.uploadPendingAccepts()
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed: ${e.message}", e)
                Resource.Error(e.message ?: "Upload failed")
            }

            binding.progressUpload.visibility = View.GONE
            binding.btnUploadAll.isEnabled = true
            loadAccepts()

            when (result) {
                is Resource.Success -> {
                    val summary = result.data!!
                    if (summary.uploaded == 0 && summary.failed == 0) {
                        ToastUtil.showShort(this@PendingAcceptsActivity, "No pending survey removals")
                    } else if (summary.failed == 0) {
                        ZdAlert.confirm(
                            context = this@PendingAcceptsActivity,
                            title = "Upload Complete",
                            message = "${summary.uploaded} survey removal(s) uploaded successfully.",
                            confirmText = "OK"
                        ) { }
                    } else {
                        ZdAlert.error(
                            this@PendingAcceptsActivity,
                            "Some Uploads Failed",
                            "${summary.uploaded} uploaded, ${summary.failed} failed.",
                            details = summary.errors.take(5)
                        )
                    }
                }
                is Resource.Error -> ZdAlert.error(
                    this@PendingAcceptsActivity,
                    "Upload Failed",
                    result.message ?: "Upload failed"
                )
                else -> Unit
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
