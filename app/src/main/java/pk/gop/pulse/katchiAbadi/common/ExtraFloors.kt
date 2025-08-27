package pk.gop.pulse.katchiAbadi.common

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parceler
import pk.gop.pulse.katchiAbadi.R
import kotlinx.parcelize.Parcelize

@Parcelize
data class ExtraFloors(
    var counter: Int = 0,
    var priority: Int = 0,
    var extraPartitionsList: ArrayList<ExtraPartitions> = arrayListOf()
) : Parcelable {

    var fragment: Fragment? = null
    private var extraFloorsInterface: ExtraFloorsInterface? = null
    var view: View? = null
    private var priorityTView: TextView? = null
    private var tvDeleteFloor: TextView? = null
    private var extraPartitionContainer: LinearLayout? = null
    private var btnPartition: Button? = null
    private var partitionInflater: LayoutInflater? = null

    private val commercialActivityOptions = arrayOf(
        "Shop", "Departmental Store", "Factory", "Workshop", "Warehouse",
        "Service Station", "Cattle Farm", "School", "Medical Store"
    )

    constructor(index: Int) : this(
        counter = index,
        priority = index + 1
    )

    fun inflate(
        fragment: Fragment,
        extraFloorsInterface: ExtraFloorsInterface,
        inflater: LayoutInflater,
        parent: LinearLayout,
        extraFloorsList: ArrayList<ExtraFloors>,
        restored: Boolean
    ) {

        val adapter = ArrayAdapter(
            fragment.requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            commercialActivityOptions
        )

        this.fragment = fragment
        this.extraFloorsInterface = extraFloorsInterface
        partitionInflater = fragment.requireContext()
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(R.layout.card_add_floor, null)
        priorityTView = view?.findViewById(R.id.tv_floor_number)
        tvDeleteFloor = view?.findViewById(R.id.tv_delete_floor)
        btnPartition = view?.findViewById(R.id.btn_partition)
        extraPartitionContainer = view?.findViewById(R.id.extraPartitionContainer)

        extraPartitionContainer?.visibility = View.VISIBLE
        priorityTView?.text = Utility.convertFloorNumber(priority)

        if (restored && extraPartitionsList.isNotEmpty()) {
            for (extraPart in extraPartitionsList) {
                val partitionView = extraPart.inflate(
                    adapter,
                    inflater,
                    extraPartitionContainer!!,
                    extraPartitionsList,
                    true
                )
                extraPartitionContainer?.addView(partitionView)
            }
        } else {
            addExtraPartition(adapter)
        }

        tvDeleteFloor?.setOnClickListener {
            val builder = AlertDialog.Builder(fragment.requireContext())
            builder.setMessage("Do You want to Delete this Floor?")
                .setTitle("Confirm Deletion!")
            builder.setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                try {
                    parent.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    extraFloorsList.remove(this)
                    var p = 1
                    val item = extraFloorsList.iterator()
                    while (item.hasNext()) {
                        item.next().correctPriority(p)
                        p++
                    }
                    extraFloorsInterface.makeAddMoreButtonVisible()
                }
            }
            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            builder.create().show()
        }

        tvDeleteFloor?.visibility = if (priority < 2) View.GONE else View.VISIBLE

        btnPartition?.setOnClickListener {
            addExtraPartition(adapter)
        }

        parent.addView(view)
    }

    private fun addExtraPartition(adapter: ArrayAdapter<String>) {
        extraPartitionContainer?.visibility = View.VISIBLE
        val extraPartitions = ExtraPartitions(fragment!!, extraPartitionsList.size)
        val partitionView = extraPartitions.inflate(
            adapter,
            partitionInflater!!,
            extraPartitionContainer!!,
            extraPartitionsList,
            false
        )
        extraPartitionsList.add(extraPartitions)
        extraPartitionContainer?.addView(partitionView)
    }

    private fun correctPriority(priority: Int) {
        this.priority = priority
        priorityTView?.text = Utility.convertFloorNumber(priority)
    }

    companion object : Parceler<ExtraFloors> {

        override fun create(parcel: Parcel): ExtraFloors {
            val counter = parcel.readInt()
            val priority = parcel.readInt()

            val size = parcel.readInt()
            val extraPartitionsList = ArrayList<ExtraPartitions>(size)
            for (i in 0 until size) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    extraPartitionsList.add(
                        parcel.readParcelable(
                            ExtraPartitions::class.java.classLoader,
                            ExtraPartitions::class.java
                        )!!
                    )
                } else {
                    @Suppress("DEPRECATION")
                    extraPartitionsList.add(parcel.readParcelable(ExtraPartitions::class.java.classLoader)!!)
                }
            }

            return ExtraFloors(counter, priority, extraPartitionsList)
        }

        override fun ExtraFloors.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(counter)
            parcel.writeInt(priority)

            parcel.writeInt(extraPartitionsList.size)
            extraPartitionsList.forEach {
                parcel.writeParcelable(it, flags)
            }
        }
    }

}



