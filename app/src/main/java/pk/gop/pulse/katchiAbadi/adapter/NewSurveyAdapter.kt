package pk.gop.pulse.katchiAbadi.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.data.local.SurveyWithKhewat
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewSurveyAdapter(
    private val listener: OnItemClickListener
) : ListAdapter<SurveyWithKhewat, NewSurveyAdapter.NewSurveyViewHolder>(DiffCallback()) {

    interface OnItemClickListener {
        fun onUploadClicked(survey: NewSurveyNewEntity)
        fun onItemClicked(survey: NewSurveyNewEntity)
        fun onDeleteClicked(survey: NewSurveyNewEntity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewSurveyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.cardview_saved_records, parent, false)
        return NewSurveyViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewSurveyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NewSurveyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvParcelNo: TextView = itemView.findViewById(R.id.tv_parcel_id)
        private val tvOperation: TextView = itemView.findViewById(R.id.tv_parcel_operation)
        private val tvGrowerCode: TextView = itemView.findViewById(R.id.tv_parcel_ids)
        private val tvGrowerCodeCaption: TextView =
            itemView.findViewById(R.id.tv_parcel_ids_caption)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)

        private val llGrowerStatus: LinearLayout = itemView.findViewById(R.id.ll_grower_status)
        private val ivGrowerStatus: ImageView = itemView.findViewById(R.id.iv_grower_status)
        private val tvGrowerStatus: TextView = itemView.findViewById(R.id.tv_grower_status)

        private val btnUpload: Button = itemView.findViewById(R.id.btn_upload_record)
        private val btnView: Button = itemView.findViewById(R.id.btn_view_record)
        private val btnDelete: Button = itemView.findViewById(R.id.btn_delete_record)

        fun bind(surveyWithKhewat: SurveyWithKhewat) {
            val survey = surveyWithKhewat.survey

            // ---- parcel no / sub parcel ----
            val sub = survey.subParcelNo.trim()
            tvParcelNo.text = if (sub.isBlank() || sub == "0" || sub == "null") {
                survey.parcelNo
            } else {
                "${survey.parcelNo}/$sub"
            }

            // ---- operation chip ----
            tvOperation.text = survey.parcelOperation.ifBlank { "Same" }

            // ---- grower code + validation ----
            // NOTE: SurveyWithKhewat me filhal growerCodes field nahi hai. Jab tak
            // DAO query me wo column add na ho, khewatInfo dikhaya ja raha hai.
            // growerCodes add karne ke baad neeche ki do lines badal dein:
            //     val code = surveyWithKhewat.growerCodes?.trim()
            //     tvGrowerCodeCaption.text = "GROWER CODE"
            val code = surveyWithKhewat.growerCodes?.trim()
            tvGrowerCodeCaption.text = "GROWER CODE"

            val hasCode = !code.isNullOrBlank() && code != "null"
            tvGrowerCode.text = if (hasCode) code else "—"

            // RecyclerView views recycle karta hai, is liye dono taraf set karna zaroori
            if (hasCode) {
                tvGrowerStatus.text = "Grower added"
                tvGrowerStatus.setTextColor(Color.parseColor("#1B5E20"))
                ivGrowerStatus.setImageResource(R.drawable.ic_check_14)
                llGrowerStatus.setBackgroundResource(R.drawable.zd_chip_ok)
            } else {
                tvGrowerStatus.text = "No grower"
                tvGrowerStatus.setTextColor(Color.parseColor("#C62828"))
                ivGrowerStatus.setImageResource(R.drawable.ic_warn_14)
                llGrowerStatus.setBackgroundResource(R.drawable.zd_chip_missing)
            }

            // ---- when ----
            // NewSurveyNewEntity me date field jo bhi ho wo yahan aayega. Filhal
            // property type + ownership dikhaya ja raha hai kyunki entity me
            // timestamp confirm nahi hai. Date field ka naam bata dein to swap kar dun.
            tvDateTime.text = if (survey.createdAt > 0) {
                SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
                    .format(Date(survey.createdAt))
            } else {
                "—"
            }

            // ---- actions ----
            btnUpload.setOnClickListener { listener.onUploadClicked(survey) }
            btnView.setOnClickListener { listener.onItemClicked(survey) }
            btnDelete.setOnClickListener { listener.onDeleteClicked(survey) }
            itemView.setOnClickListener { listener.onItemClicked(survey) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SurveyWithKhewat>() {
        override fun areItemsTheSame(
            oldItem: SurveyWithKhewat,
            newItem: SurveyWithKhewat
        ): Boolean = oldItem.survey.pkId == newItem.survey.pkId

        override fun areContentsTheSame(
            oldItem: SurveyWithKhewat,
            newItem: SurveyWithKhewat
        ): Boolean = oldItem == newItem
    }
}