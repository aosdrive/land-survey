package pk.gop.pulse.katchiAbadi.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.common.SubParcel
import pk.gop.pulse.katchiAbadi.common.SubParcelItemClickListener
import pk.gop.pulse.katchiAbadi.databinding.CardviewSubParcelListBinding

class SubParcelAdapter(
    private val context: Context,
    private val listener: SubParcelItemClickListener
) :
    ListAdapter<SubParcel, SubParcelAdapter.SurveyViewHolder>(SurveyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val binding =
            CardviewSubParcelListBinding.inflate(
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

    inner class SurveyViewHolder(private val binding: CardviewSubParcelListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(survey: SubParcel) {
            binding.apply {
                tvSubParcel.text = "${survey.id}"

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
                        listener.onSelectItemClicked(item)
                    }
                }

            }
        }
    }

    private class SurveyDiffCallback : DiffUtil.ItemCallback<SubParcel>() {
        override fun areItemsTheSame(oldItem: SubParcel, newItem: SubParcel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SubParcel, newItem: SubParcel): Boolean {
            return oldItem == newItem
        }
    }
}
