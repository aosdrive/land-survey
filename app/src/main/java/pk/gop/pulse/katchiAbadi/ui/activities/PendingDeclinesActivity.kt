package pk.gop.pulse.katchiAbadi.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.adapter.PendingDeclineAdapter
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.repository.DeclineRepositoryImpl
import pk.gop.pulse.katchiAbadi.databinding.ActivityPendingDeclinesBinding
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import javax.inject.Inject

private const val TAG = "PendingDeclines"

@AndroidEntryPoint
class PendingDeclinesActivity : AppCompatActivity() {

    @Inject
    lateinit var declineRepository: DeclineRepositoryImpl

    private lateinit var binding: ActivityPendingDeclinesBinding
    private lateinit var adapter: PendingDeclineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingDeclinesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Pending Declines"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = PendingDeclineAdapter { decline -> confirmDelete(decline) }
        binding.recyclerDeclines.layoutManager = LinearLayoutManager(this)
        binding.recyclerDeclines.adapter = adapter

        binding.btnUploadAll.setOnClickListener { uploadAll() }
    }

    override fun onResume() {
        super.onResume()
        loadDeclines()
    }

    private fun loadDeclines() {
        lifecycleScope.launch {
            val list = declineRepository.getPending()
            adapter.submitList(list)

            binding.tvCount.text =
                if (list.size == 1) "1 PENDING DECLINE" else "${list.size} PENDING DECLINES"

            val empty = list.isEmpty()
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.recyclerDeclines.visibility = if (empty) View.GONE else View.VISIBLE
            binding.btnUploadAll.isEnabled = !empty
            binding.btnUploadAll.alpha = if (empty) 0.4f else 1f
        }
    }

    private fun confirmDelete(decline: ParcelDeclineEntity) {
        ZdAlert.confirmDanger(
            context = this,
            title = "Delete Decline?",
            message = "This decline for Parcel ${decline.parcelNo} has not been uploaded yet. " +
                    "Deleting it will remove the pictures permanently.",
            confirmText = "Delete"
        ) {
            lifecycleScope.launch {
                declineRepository.deleteDecline(decline)
                ToastUtil.showShort(this@PendingDeclinesActivity, "Decline deleted")
                loadDeclines()
            }
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

            var success = 0
            val failures = mutableListOf<String>()

            try {
                val pending = declineRepository.getPending()
                for (decline in pending) {
                    when (val result = declineRepository.uploadDecline(decline)) {
                        is Resource.Success -> {
                            success++
                            Log.d(TAG, "Uploaded decline ${decline.id}")
                        }
                        is Resource.Error -> {
                            failures.add("Parcel ${decline.parcelNo}: ${result.message}")
                            Log.e(TAG, "Failed decline ${decline.id}: ${result.message}")
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload loop failed: ${e.message}", e)
                failures.add(e.message ?: "Unexpected error")
            } finally {
                binding.progressUpload.visibility = View.GONE
                binding.btnUploadAll.isEnabled = true
                loadDeclines()
            }

            when {
                failures.isEmpty() && success > 0 ->
                    ZdAlert.confirm(
                        context = this@PendingDeclinesActivity,
                        title = "Upload Complete",
                        message = "$success decline(s) uploaded successfully.",
                        confirmText = "OK"
                    ) { }

                failures.isNotEmpty() ->
                    ZdAlert.error(
                        this@PendingDeclinesActivity,
                        "Some Uploads Failed",
                        "$success uploaded, ${failures.size} failed.",
                        details = failures.take(5)
                    )
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}