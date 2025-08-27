

// =============================
// DownloadedAreasAdapter.kt
// =============================

package pk.gop.pulse.katchiAbadi.adapter

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.activities.DownloadedAreasItemClickListener
import pk.gop.pulse.katchiAbadi.activities.MauzaAreaEntry
import pk.gop.pulse.katchiAbadi.common.Constants

class DownloadedAreasAdapter(
    private val clickListener: DownloadedAreasItemClickListener
) : RecyclerView.Adapter<DownloadedAreasAdapter.ViewHolder>() {

    private var data = listOf<MauzaAreaEntry>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount() = data.size

    fun setData(newData: List<MauzaAreaEntry>) {
        data = newData
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radioButton)
        private val mauzaName: TextView = itemView.findViewById(R.id.mauzaName)
        private val areaName: TextView = itemView.findViewById(R.id.areaName)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.imageButtonDelete)

        fun bind(entry: MauzaAreaEntry) {
            mauzaName.text = entry.mauzaName
            areaName.text = entry.areaAssigned
            radioButton.isChecked = entry.isSelected

//            radioButton.setOnClickListener {
//                for (item in data) {
//                    item.isSelected = item.mauzaId == entry.mauzaId &&
//                            item.areaAssigned == entry.areaAssigned
//                }
//                notifyDataSetChanged()
//                clickListener.onSelectItemClicked(entry)
//            }

            radioButton.setOnClickListener {
                val updatedList = data.map {
                    it.copy(isSelected = it.mauzaId == entry.mauzaId && it.areaAssigned == entry.areaAssigned)
                }
                setData(updatedList)
                clickListener.onSelectItemClicked(entry)
            }


            deleteButton.setOnClickListener {
                clickListener.onDeleteItemClicked(entry)
            }
        }
    }

    fun deleteItem(entry: MauzaAreaEntry, sharedPreferences: SharedPreferences) {
        val position = data.indexOfFirst {
            it.mauzaId == entry.mauzaId && it.areaAssigned == entry.areaAssigned
        }

        if (position != -1) {
            val mutableData = data.toMutableList()
            val wasSelected = mutableData[position].isSelected
            mutableData.removeAt(position)
            data = mutableData

            if (wasSelected && mutableData.isNotEmpty()) {
                val newSelected = mutableData.last().copy(isSelected = true)
                mutableData[mutableData.lastIndex] = newSelected
                sharedPreferences.edit()
                    .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, newSelected.mauzaId)
                    .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, newSelected.mauzaName)
                    .putLong(Constants.SHARED_PREF_USER_SELECTED_AREA_ID, newSelected.mauzaId)
                    .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, newSelected.areaAssigned)
                    .apply()
            }

            notifyDataSetChanged()
        }
    }
}
