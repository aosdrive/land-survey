package pk.gop.pulse.katchiAbadi.common

import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import pk.gop.pulse.katchiAbadi.R

@Parcelize
data class ExtraPartitions(
    var counter: Int = 0,
    var priority: Int = 0,
    var occupancyStatus: String = "Self",
    var landuseType: String = "Residential",
    var commercialActivityType: String = ""
) : Parcelable {

    var fragment: Fragment? = null
    var view: View? = null
    private var priorityTView: TextView? = null

    private var adapter: ArrayAdapter<String>? = null

    constructor(fragment: Fragment, index: Int) : this() {
        this.fragment = fragment
        this.counter = index
        this.priority = index + 1
    }

    // Inflate the view and set up UI elements
    fun inflate(
        adapter: ArrayAdapter<String>,
        inflater: LayoutInflater,
        parent: LinearLayout,
        extraPartitionsList: ArrayList<ExtraPartitions>,
        restored: Boolean
    ): View {
        this.adapter = adapter
        view = inflater.inflate(R.layout.card_add_floor_partition, null)
        initializeViews()
        setupLanduseTypeRadioButtons(restored)
        setupOccupancyStatusRadioGroup(restored)
        setupDeletePartitionButton(parent, extraPartitionsList)
        return view!!
    }

    // Initialize views within the layout
    private fun initializeViews() {
        priorityTView = view?.findViewById(R.id.priorityTView)
        priorityTView?.text = "Division $priority"
    }

    private fun setupLanduseTypeRadioButtons(restored: Boolean) {
        val rgLanduseType = view?.findViewById<RadioGroup>(R.id.rg_landuse_type)
        val radioButtonCount = rgLanduseType?.childCount ?: 0

        for (i in 0 until radioButtonCount) {
            val radioButton = rgLanduseType?.getChildAt(i) as RadioButton
            val dynamicId = View.generateViewId()
            radioButton.id = dynamicId
            val tagValue = "${radioButton.text}_${counter}_${i + 1}"
            radioButton.tag = tagValue
            radioButton.setOnClickListener {
                landuseType = it.tag.toString()
                rgLanduseType?.check(radioButton.id)

                if (landuseType.contains("Residential")) {
                    commercialActivityLayoutVisibility(false, restored)
                } else {
                    commercialActivityLayoutVisibility(true, restored)
                }
            }
            if (restored) {
                if (landuseType == tagValue) {
                    radioButton.isChecked = true
                } else {
                    if (radioButton.tag.toString().contains("Residential")) {
                        radioButton.isChecked = true
                    }
                }
            } else {
                if (radioButton.tag.toString().contains("Residential")) {
                    radioButton.isChecked = true
                }
            }

            if (landuseType.contains("Residential")) {
                commercialActivityLayoutVisibility(false, restored)
            } else {
                commercialActivityLayoutVisibility(true, restored)
            }
        }

        rgLanduseType?.setOnCheckedChangeListener { group, _ ->
            group.tag?.let {
                landuseType = it.toString()
            }
        }
    }

    private fun setupOccupancyStatusRadioGroup(restored: Boolean) {
        val rgOccupancyStatus = view?.findViewById<RadioGroup>(R.id.rg_occupancy_status)
        val radioButtonCount = rgOccupancyStatus?.childCount ?: 0

        for (i in 0 until radioButtonCount) {
            val radioButton = rgOccupancyStatus?.getChildAt(i) as RadioButton
            val dynamicId = View.generateViewId()
            radioButton.id = dynamicId
            val tagValue = "${radioButton.text}_${counter}_${i + 1}"
            radioButton.tag = tagValue
            radioButton.setOnClickListener {
                occupancyStatus = it.tag.toString()
                rgOccupancyStatus?.check(radioButton.id)
            }

            if (restored) {
                if (occupancyStatus == tagValue) {
                    radioButton.isChecked = true
                } else {
                    if (radioButton.tag.toString().contains("Self")) {
                        radioButton.isChecked = true
                    }
                }
            } else {
                if (radioButton.tag.toString().contains("Self")) {
                    radioButton.isChecked = true
                }
            }
        }

        rgOccupancyStatus?.setOnCheckedChangeListener { group, _ ->
            group.tag?.let {
                occupancyStatus = it.toString()
            }
        }
    }

    private fun commercialActivityLayoutVisibility(visible: Boolean, restored: Boolean) {

        val tableLayout = view?.findViewById<TableLayout>(R.id.tlayout)

        val trCommercialActivityTypeCaption = tableLayout?.getChildAt(2) as TableRow
        val dynamicId = View.generateViewId()
        trCommercialActivityTypeCaption.id = dynamicId
        val tagValue = "CommercialActivityTypeCaption_${counter}"
        trCommercialActivityTypeCaption.tag = tagValue

        val trCommercialActivityType = tableLayout?.getChildAt(3) as TableRow
        val dynamicId2 = View.generateViewId()
        trCommercialActivityType.id = dynamicId2
        val tagValue2 = "CommercialActivityType_${counter}"
        trCommercialActivityType.tag = tagValue2

        if (visible) {
            trCommercialActivityTypeCaption.visibility = View.VISIBLE
            trCommercialActivityType.visibility = View.VISIBLE
            setupCommercialActivityUIElements(trCommercialActivityType, restored)
        } else {
            trCommercialActivityTypeCaption.visibility = View.GONE
            trCommercialActivityType.visibility = View.GONE
        }
    }

    private fun setupCommercialActivityUIElements(tRow: TableRow, restored: Boolean) {

        Log.d("ExtraPartitions", "Restored commercialActivityType1: $commercialActivityType")

            Log.d("ExtraPartitions", "Restored commercialActivityType2: $commercialActivityType")

            for (a in 0 until tRow.childCount) {
                if (tRow.getChildAt(a) is LinearLayout) {
                    val lLayout = tRow.getChildAt(a) as LinearLayout
                    lLayout.id = View.generateViewId()

                    val et = lLayout.getChildAt(0) as AutoCompleteTextView
                    val dynamicId = View.generateViewId()
                    et.id = dynamicId
                    val tagValue = "Commercial_${counter}_$a"
                    et.tag = tagValue

                    et.setAdapter(adapter)

                    et.setOnItemClickListener { parent, _, position, _ ->
                        val selectedItem = parent.getItemAtPosition(position).toString()
                        handleAutoCompleteAction(selectedItem)
                        et.clearFocus()
                    }

                    Log.d("ExtraPartitions", "Restored commercialActivityType3: $commercialActivityType")
                    // Always update the AutoCompleteTextView with the restored or current value
                    et.setText(commercialActivityType)

                    // Add TextWatcher to update the value dynamically
                    et.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(
                            charSequence: CharSequence, start: Int, count: Int, after: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) {
                            s?.let {
                                handleAutoCompleteAction(it.toString())
                            }
                        }

                        override fun afterTextChanged(editable: Editable) {}
                    })
                }
            }


    }


    private fun handleAutoCompleteAction(enteredText: String) {
        commercialActivityType = enteredText
    }

    private fun setupDeletePartitionButton(
        parent: LinearLayout,
        extraPartitionsList: ArrayList<ExtraPartitions>
    ) {
        val imgDeletePartition = view?.findViewById<ImageView>(R.id.img_delete_partition)
        imgDeletePartition?.setOnClickListener {
            showDeleteConfirmationDialog(parent, extraPartitionsList)
        }
        val viewIndex: Int = parent.childCount

        imgDeletePartition?.visibility = if (viewIndex == 0) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmationDialog(
        parent: LinearLayout,
        extraPartitionsList: ArrayList<ExtraPartitions>
    ) {
        val builder = AlertDialog.Builder(fragment?.requireContext() ?: return)
        builder.setMessage("Do you want to delete this Division?")
            .setTitle("Confirm Deletion!")
        builder.setPositiveButton("Yes") { dialog, _ ->
            dialog.dismiss()
            try {
                parent.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extraPartitionsList.remove(this)
                var p = 1
                extraPartitionsList.forEach {
                    it.correctPriority(p)
                    p++
                }
            }
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun correctPriority(priority: Int) {
        this.priority = priority
        priorityTView?.text = "Division $priority"
    }

    companion object : Parceler<ExtraPartitions> {

        override fun create(parcel: Parcel): ExtraPartitions {
            return ExtraPartitions(
                counter = parcel.readInt(),
                priority = parcel.readInt(),
                occupancyStatus = parcel.readString() ?: "Self",
                landuseType = parcel.readString() ?: "Residential",
                commercialActivityType = parcel.readString() ?: ""
            )
        }

        override fun ExtraPartitions.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(counter)
            parcel.writeInt(priority)
            parcel.writeString(occupancyStatus)
            parcel.writeString(landuseType)
            parcel.writeString(commercialActivityType)
        }
    }

}


