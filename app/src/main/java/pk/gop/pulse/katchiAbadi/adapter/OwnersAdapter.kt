package pk.gop.pulse.katchiAbadi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.data.remote.post.Partition
import pk.gop.pulse.katchiAbadi.databinding.CardViewOwnerBinding
import pk.gop.pulse.katchiAbadi.databinding.CardViewPartitionBinding
import pk.gop.pulse.katchiAbadi.domain.model.OwnerInfo

class OwnersAdapter :
    ListAdapter<OwnerInfo, OwnersAdapter.OwnersViewHolder>(OwnersDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OwnersViewHolder {
        val binding =
            CardViewOwnerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return OwnersViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OwnersViewHolder, position: Int) {
        val ownerInfo = getItem(position)
        holder.bind(ownerInfo)
    }

    class OwnersViewHolder(private val binding: CardViewOwnerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ownerInfo: OwnerInfo) {
            binding.apply {

                val relation = when (ownerInfo.relation){
                    "3" -> "S/O"
                    "4" -> "D/O"
                    "5" -> "Wife/O"
                    "6" -> "Widow/O"
                    else -> {}
                }

                val relationBold = "<font color='#e20000'><b>$relation</b></font>"

                tvName.text =
                    HtmlCompat.fromHtml("${ownerInfo.name} $relationBold ${ownerInfo.fname}", HtmlCompat.FROM_HTML_MODE_LEGACY)
                tvCnic.text = ownerInfo.cnic
            }
        }
    }

    class OwnersDiffCallback : DiffUtil.ItemCallback<OwnerInfo>() {
        override fun areItemsTheSame(oldItem: OwnerInfo, newItem: OwnerInfo): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: OwnerInfo, newItem: OwnerInfo): Boolean {
            return oldItem == newItem
        }
    }
}
