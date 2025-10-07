package pk.gop.pulse.katchiAbadi.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
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
        taskAdapter = TaskAdapter(emptyList())
        binding.recyclerViewTasks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = taskAdapter
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
class TaskAdapter(private var tasks: List<TaskItem>) :
    RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIssueType: TextView = itemView.findViewById(R.id.tvIssueType)
        val tvParcelNo: TextView = itemView.findViewById(R.id.tvParcelNo)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvAssignDate: TextView = itemView.findViewById(R.id.tvAssignDate)
        val tvDetail: TextView = itemView.findViewById(R.id.tvDetail)
        val tvAssignedBy: TextView = itemView.findViewById(R.id.tvAssignedBy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.tvIssueType.text = task.issue_Type
        holder.tvParcelNo.text = "Parcel: ${task.parcelNo}"
        holder.tvStatus.text = task.status
        holder.tvAssignDate.text = task.assign_Date
        holder.tvDetail.text = task.detail ?: "No details"
        holder.tvAssignedBy.text = "Assigned by: ${task.assignedByUser ?: "Unknown"}"

        // Set status color
        val statusColor = when (task.status.lowercase()) {
            "pending" -> android.graphics.Color.parseColor("#FFA500")
            "completed" -> android.graphics.Color.parseColor("#4CAF50")
            "in progress" -> android.graphics.Color.parseColor("#2196F3")
            else -> android.graphics.Color.parseColor("#757575")
        }
        holder.tvStatus.setTextColor(statusColor)
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<TaskItem>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}