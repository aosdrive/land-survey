package pk.gop.pulse.katchiAbadi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.data.local.SurveyWithKhewat

class NewSurveyAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<SurveyWithKhewat, NewSurveyAdapter.NewSurveyViewHolder>(DiffCallback()) {

    interface OnItemClickListener {
        fun onUploadClicked(survey: NewSurveyNewEntity)
        fun onItemClicked(survey: NewSurveyNewEntity)
        fun onDeleteClicked(survey: NewSurveyNewEntity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewSurveyViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_new_survey, parent, false)
        return NewSurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewSurveyViewHolder, position: Int) {
        val surveyWithKhewat = getItem(position)
        holder.bind(surveyWithKhewat)
    }

    inner class NewSurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvParcelNo: TextView = itemView.findViewById(R.id.tvParcelNo)
        private val tvPropertyType: TextView = itemView.findViewById(R.id.tvPropertyType)
        private val tvOwnershipStatus: TextView = itemView.findViewById(R.id.tvOwnershipStatus)
        private val tvKhewatInfo: TextView = itemView.findViewById(R.id.tvKhewatInfo)
        private val btnUpload: Button = itemView.findViewById(R.id.btnUpload)
        private val btnView: Button = itemView.findViewById(R.id.btnView)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(surveyWithKhewat: SurveyWithKhewat) {
            val survey = surveyWithKhewat.survey

            tvParcelNo.text = "Parcel No: ${survey.parcelNo}"
            tvPropertyType.text = "Property Type: ${survey.propertyType}"
            tvOwnershipStatus.text = "Ownership: ${survey.ownershipStatus}"
            tvKhewatInfo.text = "Khewat: ${surveyWithKhewat.khewatInfo}"

            // Click listeners
            btnUpload.setOnClickListener {
                listener.onUploadClicked(survey)
            }

            btnView.setOnClickListener {
                listener.onItemClicked(survey)
            }

            btnDelete.setOnClickListener {
                listener.onDeleteClicked(survey)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SurveyWithKhewat>() {
        override fun areItemsTheSame(
            oldItem: SurveyWithKhewat,
            newItem: SurveyWithKhewat
        ): Boolean =
            oldItem.survey.pkId == newItem.survey.pkId

        override fun areContentsTheSame(
            oldItem: SurveyWithKhewat,
            newItem: SurveyWithKhewat
        ): Boolean =
            oldItem == newItem
    }
}