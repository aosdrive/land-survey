package pk.gop.pulse.katchiAbadi.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAcceptEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Red: a survey removal always belongs to a "Surveyed, but not cane" parcel. */
private val COLOR_SURVEYED_NOT_CANE = Color.parseColor("#EF4444")

/**
 * One card per survey removal ("accept") saved from Map Analysis and not uploaded yet.
 * Same shape as [PendingDeclineAdapter], without pictures: a removal has none.
 */
class PendingAcceptAdapter(
    private val onDelete: (ParcelAcceptEntity) -> Unit
) : ListAdapter<ParcelAcceptEntity, PendingAcceptAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ParcelAcceptEntity>() {
            override fun areItemsTheSame(a: ParcelAcceptEntity, b: ParcelAcceptEntity) =
                a.id == b.id

            override fun areContentsTheSame(a: ParcelAcceptEntity, b: ParcelAcceptEntity) =
                a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.view_dot)
        val parcel: TextView = view.findViewById(R.id.tv_parcel)
        val status: TextView = view.findViewById(R.id.tv_status)
        val remarks: TextView = view.findViewById(R.id.tv_remarks)
        val error: TextView = view.findViewById(R.id.tv_error)
        val date: TextView = view.findViewById(R.id.tv_date)
        val delete: ImageView = view.findViewById(R.id.iv_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_accept, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.parcel.text = "Parcel ${item.parcelNo}"
        holder.status.text = buildString {
            append(item.parcelStatus.ifBlank { "Survey removal" })
            if (item.areaName.isNotBlank()) append(" · Area ${item.areaName}")
            if (item.khewatInfo.isNotBlank()) append(" · Khewat ${item.khewatInfo}")
        }
        holder.dot.setBackgroundColor(COLOR_SURVEYED_NOT_CANE)

        if (item.remarks.isNotBlank()) {
            holder.remarks.visibility = View.VISIBLE
            holder.remarks.text = item.remarks
        } else {
            holder.remarks.visibility = View.GONE
        }

        // Why the last upload attempt did not go through, so the surveyor can act on it.
        val error = item.lastError?.trim().orEmpty()
        if (error.isNotBlank()) {
            holder.error.visibility = View.VISIBLE
            holder.error.text = "Last upload failed: $error"
        } else {
            holder.error.visibility = View.GONE
        }

        holder.date.text = "Saved on " + SimpleDateFormat(
            "dd MMM yyyy, hh:mm a", Locale.US
        ).format(Date(item.createdAt))

        holder.delete.setOnClickListener { onDelete(item) }
    }
}
