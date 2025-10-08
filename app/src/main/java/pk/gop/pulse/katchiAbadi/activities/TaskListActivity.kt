package pk.gop.pulse.katchiAbadi.activities

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.target.Target
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.TaskUpdateDto
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.response.TaskItem
import pk.gop.pulse.katchiAbadi.databinding.ActivityTaskListBinding
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import javax.inject.Inject

@AndroidEntryPoint
class TaskListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskListBinding
    private lateinit var context: Context
    private lateinit var taskAdapter: TaskAdapter

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var serverApi: ServerApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this

        supportActionBar?.title = "My Tasks"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        loadTasks()
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(emptyList()) { task ->
            // Handle task item click - show update dialog
            showTaskUpdateDialog(task)
        }
        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
        }
    }

    private fun showTaskUpdateDialog(task: TaskItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_task_update, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Get views from dialog
        val tvTaskInfo = dialogView.findViewById<TextView>(R.id.tvTaskInfo)
        val spinnerStatus = dialogView.findViewById<Spinner>(R.id.spinnerStatus)
        val etFeedback = dialogView.findViewById<EditText>(R.id.etFeedback)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmit)

        // Set task info
        tvTaskInfo.text = "${task.issue_Type} - Parcel: ${task.parcelNo}"

        // Setup status spinner
        val statusList = listOf("Pending", "In Progress", "Completed")
        val statusAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, statusList)
        spinnerStatus.adapter = statusAdapter

        // Set current status as selected
        val currentStatusIndex = statusList.indexOfFirst {
            it.equals(task.status, ignoreCase = true)
        }
        if (currentStatusIndex >= 0) {
            spinnerStatus.setSelection(currentStatusIndex)
        }

        // Cancel button
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Submit button
        btnSubmit.setOnClickListener {
            val selectedStatus = spinnerStatus.selectedItem.toString()
            val feedback = etFeedback.text.toString().trim()

            if (feedback.isEmpty()) {
                ToastUtil.showShort(context, "Please enter remarks")
                return@setOnClickListener
            }

            // Disable button to prevent double submission
            btnSubmit.isEnabled = false

            updateTaskStatus(task.id, selectedStatus, feedback) { success ->
                btnSubmit.isEnabled = true
                if (success) {
                    dialog.dismiss()

                    // Show different message based on status
                    when (selectedStatus.lowercase()) {
                        "completed" -> {
                            ToastUtil.showShort(context, "Task completed and removed from list!")
                        }
                        "in progress" -> {
                            ToastUtil.showShort(context, "Task status updated to In Progress")
                        }
                        "pending" -> {
                            ToastUtil.showShort(context, "Task status updated to Pending")
                        }
                    }

                    loadTasks() // Reload tasks - completed tasks will be filtered out
                }
            }
        }

        dialog.show()
    }

    private fun updateTaskStatus(taskId: Int, status: String, feedback: String, callback: (Boolean) -> Unit) {
        val userId = sharedPreferences.getLong(Constants.SHARED_PREF_USER_ID, 0L)
        val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

        if (token.isEmpty()) {
            ToastUtil.showShort(context, "Please login again")
            callback(false)
            return
        }

        lifecycleScope.launch {
            try {
                val updateDto = TaskUpdateDto(
                    taskId = taskId,
                    status = status,
                    feedback = feedback,
                    updatedByUserId = userId
                )

                Log.d("TaskUpdate", "Updating task $taskId to status: $status")
                Log.d("TaskUpdate", "Remarks: $feedback")

                val response = withContext(Dispatchers.IO) {
                    serverApi.updateTaskStatus("Bearer $token", updateDto)
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d("TaskUpdate", "✅ Task updated successfully")
                    callback(true)
                } else {
                    val errorMsg = response.body()?.message ?: "Failed to update task"
                    ToastUtil.showShort(context, errorMsg)
                    Log.e("TaskUpdate", "Error: ${response.code()} - $errorMsg")
                    callback(false)
                }
            } catch (e: Exception) {
                Log.e("TaskUpdate", "Exception: ${e.message}", e)
                ToastUtil.showShort(context, "Error: ${e.localizedMessage}")
                callback(false)
            }
        }
    }
    private fun loadTasks() {
        val userId = sharedPreferences.getLong(Constants.SHARED_PREF_USER_ID, 0L)
        val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

        if (userId == 0L || token.isEmpty()) {
            ToastUtil.showShort(context, "Please login again")
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    serverApi.getTasksForUser(userId, "Bearer $token")
                }

                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val taskResponse = response.body()!!

                    Log.d("TaskList", "Received ${taskResponse.data.size} tasks")
                    taskResponse.data.forEach { task ->
                        Log.d("TaskList", "Task ${task.id}: picData = ${task.picData}")
                    }

                    if (taskResponse.data.isEmpty()) {
                        binding.tvNoTasks.visibility = View.VISIBLE
                        binding.recyclerViewTasks.visibility = View.GONE
                    } else {
                        binding.tvNoTasks.visibility = View.GONE
                        binding.recyclerViewTasks.visibility = View.VISIBLE
                        taskAdapter.updateTasks(taskResponse.data)
                    }
                } else {
                    ToastUtil.showShort(context, "Failed to load tasks: ${response.code()}")
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("TaskList", "Error loading tasks: ${e.message}", e)
                ToastUtil.showShort(context, "Error: ${e.localizedMessage}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

class TaskAdapter(
    private var tasks: List<TaskItem>,
    private val onTaskClick: (TaskItem) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIssueType: TextView = itemView.findViewById(R.id.tvIssueType)
        val tvParcelNo: TextView = itemView.findViewById(R.id.tvParcelNo)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvAssignDate: TextView = itemView.findViewById(R.id.tvAssignDate)
        val tvDetail: TextView = itemView.findViewById(R.id.tvDetail)
        val tvAssignedBy: TextView = itemView.findViewById(R.id.tvAssignedBy)
        val ivTaskImage: ImageView? = itemView.findViewById(R.id.ivTaskImage)
        val tvImageCount: TextView? = itemView.findViewById(R.id.tvImageCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.tvIssueType.text = task.issue_Type
        holder.tvParcelNo.text = "Parcel No: ${task.parcelNo}"
        holder.tvStatus.text = "Status: ${task.status}"
        holder.tvAssignDate.text = "Date: ${task.assign_Date}"
        holder.tvDetail.text = "Detail: ${task.detail ?: "No details"}"
        holder.tvAssignedBy.text = "Assigned by: ${task.assignedByUser ?: "Unknown"}"

        // Set status color
        val statusColor = when (task.status.lowercase()) {
            "pending" -> android.graphics.Color.parseColor("#FFA500")
            "completed" -> android.graphics.Color.parseColor("#4CAF50")
            "in progress" -> android.graphics.Color.parseColor("#2196F3")
            else -> android.graphics.Color.parseColor("#757575")
        }
        holder.tvStatus.setTextColor(statusColor)

        // Handle Base64 images
        if (!task.picData.isNullOrEmpty()) {
            Log.d("TaskAdapter", "picData length: ${task.picData.length}")

            // Split by delimiter (use same delimiter as backend)
            val base64Images = task.picData
                .split("|||")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            Log.d("TaskAdapter", "Found ${base64Images.size} images")

            if (base64Images.isNotEmpty()) {
                holder.ivTaskImage?.visibility = View.VISIBLE

                // Load first image from Base64
                val firstBase64 = base64Images[0]

                // Decode Base64 to byte array
                try {
                    val imageBytes = android.util.Base64.decode(firstBase64, android.util.Base64.DEFAULT)

                    holder.ivTaskImage?.let {
                        Glide.with(holder.itemView.context)
                            .load(imageBytes)
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // Don't cache Base64
                            .placeholder(R.drawable.ic_launcher_background)
                            .error(R.drawable.ic_launcher_foreground)
                            .centerCrop()
                            .listener(object : com.bumptech.glide.request.RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Log.e("TaskAdapter", "Failed to load Base64 image", e)
                                    e?.logRootCauses("TaskAdapter")
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: Drawable,
                                    model: Any,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    Log.d("TaskAdapter", "Successfully loaded Base64 image")
                                    return false
                                }
                            })
                            .into(it)
                    }

                    // Show count for multiple images
                    if (base64Images.size > 1) {
                        holder.tvImageCount?.visibility = View.VISIBLE
                        holder.tvImageCount?.text = "+${base64Images.size - 1}"
                    } else {
                        holder.tvImageCount?.visibility = View.GONE
                    }

                    // Click to view all images
                    holder.ivTaskImage?.setOnClickListener {
                        Log.d("TaskAdapter", "Image clicked. Total images: ${base64Images.size}")
                    }

                } catch (e: Exception) {
                    Log.e("TaskAdapter", "Error decoding Base64: ${e.message}")
                    holder.ivTaskImage?.visibility = View.GONE
                }
            } else {
                holder.ivTaskImage?.visibility = View.GONE
                holder.tvImageCount?.visibility = View.GONE
            }
        } else {
            Log.d("TaskAdapter", "No images for task ${task.id}")
            holder.ivTaskImage?.visibility = View.GONE
            holder.tvImageCount?.visibility = View.GONE
        }

        // Click listener for the entire item
        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }


    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<TaskItem>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}