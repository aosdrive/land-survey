package pk.gop.pulse.katchiAbadi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.common.SavedItemClickListener
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.CardviewSentRecordsBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails

class SentAdapter(private val listener: SavedItemClickListener) :
    ListAdapter<SurveyMergeDetails, SentAdapter.SurveyViewHolder>(SurveyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val binding =
            CardviewSentRecordsBinding.inflate(
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

    inner class SurveyViewHolder(private val binding: CardviewSentRecordsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(survey: SurveyMergeDetails) {
            binding.apply {
                tvParcelId.text = survey.parcelNo.toString()
                tvParcelOperation.text = survey.parcelOperation
                tvAbadi.text = survey.kachiAbadiId.toString()
                tvCentroid.text = survey.centroidGeom
                tvRevisit.text = if(survey.isRevisit == 1){
                    "True"
                }else{
                    "False"
                }
                when(survey.parcelOperation){
                    "Split" -> {
                        tvParcelIdsCaption.text = "Split Parcel Nos: "
                        tvParcelIds.text = survey.subParcel
                        tvParcelIds.visibility = View.VISIBLE
                        tvParcelIdsCaption.visibility = View.VISIBLE
                    }
                    "Merge" -> {
                        tvParcelIdsCaption.text = "Merged Parcel Nos: "
                        tvParcelIds.text = survey.parcelOperationValue
                        tvParcelIds.visibility = View.VISIBLE
                        tvParcelIdsCaption.visibility = View.VISIBLE
                    }
                    else -> {
                        tvParcelIds.visibility = View.GONE
                        tvParcelIdsCaption.visibility = View.GONE
                    }
                }

                if(survey.gpsTimestamp.contains(",")){
                    val values = survey.gpsTimestamp.split(",")

                    for (i in values.indices) {
                           tvDateTime.append(Utility.convertStringToDateOnly(values[i]))

                        // Append newline only if it's not the last value
                        if (i < values.size - 1) {
                            tvDateTime.append("\n")
                        }
                    }

                }else{
                    tvDateTime.text = Utility.convertStringToDateOnly(survey.gpsTimestamp)
                }

//                btnUploadRecord.setOnClickListener {
//                    val position = adapterPosition
//                    if (position != RecyclerView.NO_POSITION) {
//                        val item = getItem(position)
//                        listener.onUploadItemClicked(item)
//                    }
//                }

                btnViewRecord.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item = getItem(position)
                        listener.onViewItemClicked(item)
                    }
                }
            }
        }
    }

    private class SurveyDiffCallback : DiffUtil.ItemCallback<SurveyMergeDetails>() {
        override fun areItemsTheSame(
            oldItem: SurveyMergeDetails,
            newItem: SurveyMergeDetails
        ): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(
            oldItem: SurveyMergeDetails,
            newItem: SurveyMergeDetails
        ): Boolean {
            return oldItem == newItem
        }
    }
}
