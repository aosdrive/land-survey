package pk.gop.pulse.katchiAbadi.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcel
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcelItemClickListener
import pk.gop.pulse.katchiAbadi.databinding.CardviewRejectedSubParcelListBinding
import pk.gop.pulse.katchiAbadi.databinding.CardviewSubParcelListBinding

class RejectedSubParcelAdapter(
    private val context: Context,
    private val listener: RejectedSubParcelItemClickListener
) :
    ListAdapter<RejectedSubParcel, RejectedSubParcelAdapter.SurveyViewHolder>(SurveyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val binding =
            CardviewRejectedSubParcelListBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        return SurveyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        val survey = getItem(position)
        holder.bind(survey)
    }

    inner class SurveyViewHolder(private val binding: CardviewRejectedSubParcelListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(survey: RejectedSubParcel) {
            binding.apply {
                tvSubParcel.text = "${survey.id}"
                tvReason.text = "${survey.subParcelNoAction}"

                if (survey.isFormFilled) {
                    llSyncData.setBackgroundColor(
                        Color.GREEN
                    )

                    tvSelect.visibility = View.INVISIBLE
                    tvUpdate.visibility = View.GONE
                } else {
                    llSyncData.setBackgroundColor(
                        Color.WHITE
                    )

                    tvSelect.visibility = View.VISIBLE
                    tvUpdate.visibility = View.GONE
                }

                tvSelect.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = getItem(position)
                        item.position = position
                        listener.onSelectItemClicked(item)
                    }
                }

            }
        }
    }

    private class SurveyDiffCallback : DiffUtil.ItemCallback<RejectedSubParcel>() {
        override fun areItemsTheSame(oldItem: RejectedSubParcel, newItem: RejectedSubParcel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: RejectedSubParcel, newItem: RejectedSubParcel): Boolean {
            return oldItem == newItem
        }
    }
}
