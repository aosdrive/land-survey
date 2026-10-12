package pk.gop.pulse.katchiAbadi.adapter

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val COLOR_UNMAPPED = Color.parseColor("#EF4444")
private val COLOR_NO_SURVEY = Color.parseColor("#FACC15")

class PendingDeclineAdapter(
    private val onDelete: (ParcelDeclineEntity) -> Unit
) : ListAdapter<ParcelDeclineEntity, PendingDeclineAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ParcelDeclineEntity>() {
            override fun areItemsTheSame(a: ParcelDeclineEntity, b: ParcelDeclineEntity) =
                a.id == b.id

            override fun areContentsTheSame(a: ParcelDeclineEntity, b: ParcelDeclineEntity) =
                a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.view_dot)
        val parcel: TextView = view.findViewById(R.id.tv_parcel)
        val status: TextView = view.findViewById(R.id.tv_status)
        val remarks: TextView = view.findViewById(R.id.tv_remarks)
        val date: TextView = view.findViewById(R.id.tv_date)
        val delete: ImageView = view.findViewById(R.id.iv_delete)
        val thumbs = listOf<ImageView>(
            view.findViewById(R.id.iv_thumb1),
            view.findViewById(R.id.iv_thumb2),
            view.findViewById(R.id.iv_thumb3)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_decline, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.parcel.text = "Parcel ${item.parcelNo}"
        holder.status.text = buildString {
            append(item.parcelStatus)
            item.calculatedArea?.let {
                append(" · ")
                append(String.format(Locale.US, "%.2f Acres", it))
            }
        }

        holder.dot.setBackgroundColor(
            if (item.parcelStatus.contains("surveyed", true)) COLOR_UNMAPPED   // red: surveyed but not cane
            else COLOR_NO_SURVEY                                                // yellow: cane without survey
        )

        if (item.remarks.isNotBlank()) {
            holder.remarks.visibility = View.VISIBLE
            holder.remarks.text = item.remarks
        } else {
            holder.remarks.visibility = View.GONE
        }

        holder.date.text = "Saved on " + SimpleDateFormat(
            "dd MMM yyyy, hh:mm a", Locale.US
        ).format(Date(item.createdAt))

        listOf(item.pic1Path, item.pic2Path, item.pic3Path)
            .forEachIndexed { i, path ->
                val f = File(path)
                if (f.exists()) {
                    holder.thumbs[i].setImageURI(Uri.fromFile(f))
                } else {
                    holder.thumbs[i].setImageResource(R.drawable.bg_image_placeholder)
                }
            }

        holder.delete.setOnClickListener { onDelete(item) }
    }
}