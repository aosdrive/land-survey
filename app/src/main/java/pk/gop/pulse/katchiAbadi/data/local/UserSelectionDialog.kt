package pk.gop.pulse.katchiAbadi.data.local

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.databinding.ActivityUserSelectionDialogBinding
import pk.gop.pulse.katchiAbadi.domain.model.UserResponse

class UserSelectionDialog(
    context: Context,
    private val users: List<UserResponse>,
    private val onUserSelected: (UserResponse) -> Unit
) : Dialog(context) {

    private var binding: ActivityUserSelectionDialogBinding
    private lateinit var userAdapter: UserAdapter
    private var filteredUsers: MutableList<UserResponse> = users.toMutableList()

    init {
        binding = ActivityUserSelectionDialogBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        setupRecyclerView()
        setupSearchView()
        setupCancelButton()

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(filteredUsers) { selectedUser ->
            onUserSelected(selectedUser)
            dismiss()
        }

        binding.recyclerUsers.layoutManager = LinearLayoutManager(context)
        binding.recyclerUsers.adapter = userAdapter
    }

    private fun setupSearchView() {
        binding.searchOwner.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterUsers(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterUsers(newText)
                return true
            }
        })
    }

    private fun setupCancelButton() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun filterUsers(query: String?) {
        filteredUsers.clear()

        if (query.isNullOrBlank()) {
            // Show all users if search is empty
            filteredUsers.addAll(users)
        } else {
            // Filter users based on search query
            val searchQuery = query.lowercase().trim()
            filteredUsers.addAll(
                users.filter { user ->
                    user.fullName?.lowercase()?.contains(searchQuery) == true ||
                            user.cnic?.contains(searchQuery) == true ||
                            user.roleName?.lowercase()?.contains(searchQuery) == true
                }
            )
        }

        // Notify adapter of data change
        userAdapter.notifyDataSetChanged()
    }

    private inner class UserAdapter(
        private val users: List<UserResponse>,
        private val onItemClick: (UserResponse) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        inner class UserViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
            val tvUserCnic: TextView = itemView.findViewById(R.id.tvUserCnic)

        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_user_selection, parent, false)
            return UserViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            holder.tvUserName.text = user.fullName ?: "N/A"
            holder.tvUserCnic.text = "CNIC: ${user.cnic ?: "N/A"}"

            holder.itemView.setOnClickListener {
                onItemClick(user)
            }
        }

        override fun getItemCount() = users.size
    }
}