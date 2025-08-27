package pk.gop.pulse.katchiAbadi.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.remote.post.Floors
import pk.gop.pulse.katchiAbadi.databinding.CardViewFloorBinding

class FloorAdapter : ListAdapter<Floors, FloorAdapter.FloorViewHolder>(FloorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FloorViewHolder {
        val binding =
            CardViewFloorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return FloorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FloorViewHolder, position: Int) {
        val floor = getItem(position)
        holder.bind(floor)
    }

    class FloorViewHolder(private val binding: CardViewFloorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(floor: Floors) {

            binding.apply {

                tvFloorNumber.text = Utility.convertFloorNumber(floor.FloorNumber)

                val partitionAdapter = PartitionAdapter()
                recyclerViewPartitions.layoutManager = LinearLayoutManager(itemView.context)
                recyclerViewPartitions.adapter = partitionAdapter
                partitionAdapter.submitList(floor.Partitions)
            }

        }
    }

    class FloorDiffCallback : DiffUtil.ItemCallback<Floors>() {
        override fun areItemsTheSame(oldItem: Floors, newItem: Floors): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Floors, newItem: Floors): Boolean {
            return oldItem == newItem
        }
    }
}
