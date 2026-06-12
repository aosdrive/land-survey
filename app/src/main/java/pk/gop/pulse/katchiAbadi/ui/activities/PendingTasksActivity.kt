package pk.gop.pulse.katchiAbadi.ui.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.repository.TaskRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivityPendingTasksBinding
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PendingTasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingTasksBinding
    private lateinit var adapter: PendingTaskAdapter

    @Inject lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Pending Tasks"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = PendingTaskAdapter(
            onUploadClick = { task -> uploadSingle(task) },
            onDeleteClick = { task -> confirmDelete(task) }
        )

        binding.recyclerPendingTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerPendingTasks.adapter = adapter

        binding.btnUploadAll.setOnClickListener { uploadAll() }

        observePending()
    }

    private fun observePending() {
        lifecycleScope.launch {
            taskRepository.livePendingTasks().collectLatest { list ->
                adapter.submit(list)
                binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.btnUploadAll.isEnabled = list.isNotEmpty()
                binding.tvCount.text = "Pending: ${list.size}"
            }
        }
    }

    // =============================================================
    //   SINGLE UPLOAD
    // =============================================================
    private fun uploadSingle(task: TaskEntity) {
        // Internet check FIRST — before showing progress
        if (!Utility.checkInternetConnection(this)) {
            showNoInternetDialog()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val result = taskRepository.uploadTask(task)) {
                is Resource.Success -> {
                    ToastUtil.showShort(this@PendingTasksActivity, "Task uploaded ✓")
                }
                is Resource.Error -> {
                    showUploadFailedDialog(
                        title = "Upload Failed",
                        message = friendlyError(result.message),
                        onRetry = { uploadSingle(task) }
                    )
                }
                else -> { /* nothing */ }
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    // =============================================================
    //   UPLOAD ALL
    // =============================================================
    private fun uploadAll() {
        // Internet check BEFORE showing the confirmation dialog
        if (!Utility.checkInternetConnection(this)) {
            showNoInternetDialog()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Upload All")
            .setMessage("Upload all pending tasks now?")
            .setPositiveButton("Upload") { _, _ ->
                binding.progressBar.visibility = View.VISIBLE
                binding.btnUploadAll.isEnabled = false

                lifecycleScope.launch {
                    val summary = taskRepository.uploadAllPendingTasks()
                    binding.progressBar.visibility = View.GONE
                    binding.btnUploadAll.isEnabled = true

                    showUploadAllResult(summary)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUploadAllResult(summary: TaskRepository.UploadSummary) {
        val (title, message) = when {
            summary.total == 0 -> {
                "Nothing to Upload" to "There are no pending tasks."
            }
            summary.failed == 0 -> {
                "Upload Complete ✓" to
                        "Successfully uploaded all ${summary.success} task(s)."
            }
            summary.success == 0 -> {
                // Everything failed — likely the connection dropped mid-upload
                "Upload Failed" to buildString {
                    append("None of the ${summary.total} task(s) could be uploaded.\n\n")
                    append("This usually means:\n")
                    append("• Your internet connection dropped\n")
                    append("• The server is temporarily unavailable\n\n")
                    append("Please check your connection and try again.")
                }
            }
            else -> {
                // Partial success
                "Partially Uploaded" to buildString {
                    append("✅ Uploaded: ${summary.success}\n")
                    append("❌ Failed: ${summary.failed}\n")
                    append("Total: ${summary.total}\n\n")
                    append("Failed tasks will stay in the list — tap Upload All again, or try them one by one.")
                    if (summary.failures.isNotEmpty()) {
                        append("\n\nDetails:\n")
                        append(summary.failures.take(5).joinToString("\n"))
                        if (summary.failures.size > 5) {
                            append("\n…and ${summary.failures.size - 5} more")
                        }
                    }
                }
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)

        // If everything failed, offer a Retry button
        if (summary.failed > 0 && summary.success == 0) {
            builder.setNegativeButton("Retry") { _, _ -> uploadAll() }
        }

        builder.show()
    }

    // =============================================================
    //   DELETE
    // =============================================================
    private fun confirmDelete(task: TaskEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Task")
            .setMessage("Discard this pending task? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    taskRepository.deletePendingTask(task)
                    ToastUtil.showShort(this@PendingTasksActivity, "Task deleted")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // =============================================================
    //   DIALOG HELPERS
    // =============================================================
    private fun showNoInternetDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage(
                "You need an active internet connection to upload tasks.\n\n" +
                        "Please turn on Wi-Fi or mobile data and try again. " +
                        "Your tasks are safely saved on this device and won't be lost."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (e: Exception) {
                    // Fallback if the wireless settings intent is unavailable
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                        ToastUtil.showShort(this, "Couldn't open settings")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUploadFailedDialog(
        title: String,
        message: String,
        onRetry: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ -> onRetry() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Convert raw error messages into something user-friendly. */
    private fun friendlyError(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return "Something went wrong. Please try again."
        }
        val lower = raw.lowercase()
        return when {
            lower.contains("unable to resolve host") ||
                    lower.contains("failed to connect") ||
                    lower.contains("no address associated") ||
                    lower.contains("network is unreachable") ->
                "Couldn't reach the server. Please check your internet connection and try again."

            lower.contains("timeout") || lower.contains("timed out") ->
                "The connection timed out. Your internet may be slow — please try again."

            lower.contains("login again") || lower.contains("401") ||
                    lower.contains("unauthorized") ->
                "Your session has expired. Please log in again."

            lower.contains("500") || lower.contains("502") ||
                    lower.contains("503") || lower.contains("504") ->
                "The server is temporarily unavailable. Please try again in a moment."

            else -> raw
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

// =====================================================================
//   Adapter (unchanged)
// =====================================================================
class PendingTaskAdapter(
    private val onUploadClick: (TaskEntity) -> Unit,
    private val onDeleteClick: (TaskEntity) -> Unit
) : RecyclerView.Adapter<PendingTaskAdapter.VH>() {

    private val items = mutableListOf<TaskEntity>()
    private val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    fun submit(list: List<TaskEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_task, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = items[position]
        holder.tvParcel.text = "Parcel ${task.parcelNo}"
        holder.tvIssue.text = "Issue: ${task.issueType}"
        holder.tvDate.text = "Assigned: ${task.assignDate}"
        holder.tvCreated.text = "Saved: ${dateFmt.format(Date(task.createdOn))}"
        val imgCount = task.picData.split(",").filter { it.isNotBlank() }.size
        holder.tvImages.text = "Images: $imgCount"

        holder.btnUpload.setOnClickListener { onUploadClick(task) }
        holder.btnDelete.setOnClickListener { onDeleteClick(task) }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvParcel: TextView = view.findViewById(R.id.tvParcel)
        val tvIssue: TextView = view.findViewById(R.id.tvIssue)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvCreated: TextView = view.findViewById(R.id.tvCreated)
        val tvImages: TextView = view.findViewById(R.id.tvImages)
        val btnUpload: MaterialButton = view.findViewById(R.id.btnUpload)
        val btnDelete: MaterialButton = view.findViewById(R.id.btnDelete)
    }
}