package pk.gop.pulse.katchiAbadi.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parceler
import pk.gop.pulse.katchiAbadi.R
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class ExtraPictures(
    var counter: Int = 0,
    var priority: Int = 0,
    var imageTaken: Int = 0,
    var picturePath: String? = null,
    var descriptionIndex: Int = 0,
    var pictureType: String = "",
    var otherDescription: String = ""
) : Parcelable {

    var spPictureType: Spinner? = null
    var layoutOtherPictureDescription: LinearLayout? = null
    var etPictureOtherType: AppCompatEditText? = null
    var viewBtn: Button? = null
    var deleteBtn: Button? = null
    var btnLayout: LinearLayout? = null
    var layoutPictureType: TableLayout? = null
    var priorityTView: TextView? = null
    var extraPicturesInterface: ExtraPicturesInterface? = null

    constructor(index: Int) : this(
        counter = index,
        priority = index + 1
    )

    fun inflate(
        fragment: Fragment,
        extraPicturesInterface: ExtraPicturesInterface,
        inflater: LayoutInflater,
        parent: LinearLayout,
        extraPicturesList: ArrayList<ExtraPictures>,
        restored: Boolean,
        customArray: Array<String>
    ) {
        this.extraPicturesInterface = extraPicturesInterface
        val view = inflater.inflate(R.layout.card_picture, null)
        val image: ImageView = view.findViewById(R.id.image)
        priorityTView = view.findViewById(R.id.priorityTView)
        priorityTView?.text = when (priority) {
            1 -> "Property Picture"
            else -> "Picture $priority"
        }
        spPictureType = view.findViewById(R.id.sp_picture_type)
        layoutPictureType = view.findViewById(R.id.layout_picture_type)
        layoutOtherPictureDescription = view.findViewById(R.id.layout_other_picture_description)
        etPictureOtherType = view.findViewById(R.id.et_picture_other_type)
        viewBtn = view.findViewById(R.id.viewBtn)
        deleteBtn = view.findViewById(R.id.deleteBtn)
        btnLayout = view.findViewById(R.id.btn_layout)

        val adapter = ArrayAdapter(
            fragment.requireContext(),
            android.R.layout.simple_spinner_item,
            customArray
        )

        setupPictureType(adapter, restored)

        if (imageTaken == 1) {
            makeButtonsVisible()
        } else {
            makeButtonsInvisible()
        }

        viewBtn?.setOnClickListener {
            val pictureFile = File(picturePath ?: "")
            if (pictureFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    fragment.requireContext(),
                    Constants.Package_Provider,
                    pictureFile
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "image/*")
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                try {
                    fragment.requireContext().startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        fragment.requireContext(),
                        "No app can open this image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    fragment.requireContext(),
                    "Image file does not exist",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        deleteBtn?.setOnClickListener {
            val builder = AlertDialog.Builder(fragment.requireContext())
            builder.setMessage("Do you want to delete this image?")
                .setTitle("Confirm Deletion!")
            builder.setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                try {
                    parent.removeView(view)
                    imageTaken = 1
                    picturePath?.let {
                        val file = File(it)
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    extraPicturesList.remove(this)
                    var p = 1
                    val extraPicture = extraPicturesList.iterator()
                    while (extraPicture.hasNext()) {
                        extraPicture.next().correctPriority(p)
                        p++
                    }
                    extraPicturesInterface.makeAddMoreButtonVisible()
                }
            }
            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            builder.create().show()
        }

        etPictureOtherType?.setText(otherDescription)
        etPictureOtherType?.setSelection(etPictureOtherType?.length() ?: 0)

        etPictureOtherType?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.let {
                    otherDescription = it.toString()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        image.setOnClickListener {
            if (spPictureType == null || etPictureOtherType == null) {
                if (extraPicturesInterface.checkCameraPermission()) {
                    startCamera(priority)
                } else {
                    extraPicturesInterface.requestCameraPermission(priority)
                }
            } else {
                if (spPictureType?.selectedItemPosition!! > 0) {
                    if(spPictureType?.selectedItem == "Other" && etPictureOtherType?.text!!.isEmpty()){
                        Toast.makeText(
                            fragment.requireContext(),
                            "Specify other type of Picture $priority.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }else{
                        if (extraPicturesInterface.checkCameraPermission()) {
                            startCamera(priority)
                        } else {
                            extraPicturesInterface.requestCameraPermission(priority)
                        }
                    }

                } else {
                    Toast.makeText(
                        fragment.requireContext(),
                        "Select type of Picture $priority",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

        image.setOnClickListener {
            val context = fragment.requireContext()

            // Early return if views are missing
            if (spPictureType == null || etPictureOtherType == null) {
                handleCameraPermission(priority, extraPicturesInterface)
                return@setOnClickListener
            }

            val pictureTypeSelected = spPictureType?.selectedItemPosition ?: 0
            val isOtherTypeSelected = spPictureType?.selectedItem == "Other"
            val otherTypeText = etPictureOtherType?.text?.toString().orEmpty()

            // Check if a valid picture type is selected
            when {
                pictureTypeSelected <= 0 -> {
                    Toast.makeText(context, "Select type of Picture $priority", Toast.LENGTH_SHORT).show()
                }
                isOtherTypeSelected && otherTypeText.isBlank() -> {
                    Toast.makeText(context, "Specify other type of Picture $priority.", Toast.LENGTH_SHORT).show()
                }
                isOtherTypeSelected && otherTypeText.length < 4 -> {
                    Toast.makeText(context, "Enter valid other type of Picture $priority.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    handleCameraPermission(priority,extraPicturesInterface)
                }
            }
        }

        parent.addView(view)
    }

    // Function to check and request camera permission
    private fun handleCameraPermission(priority: Int, extraPicturesInterface: ExtraPicturesInterface) {
        if (extraPicturesInterface.checkCameraPermission()) {
            startCamera(priority)
        } else {
            extraPicturesInterface.requestCameraPermission(priority)
        }
    }

    private fun setupPictureType(adapter: ArrayAdapter<String>, restored: Boolean) {

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spPictureType?.adapter = adapter

        spPictureType?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                pos: Int,
                l: Long
            ) {
                try {
                    if (view != null) {
                        descriptionIndex = pos
                        pictureType = spPictureType?.selectedItem.toString()
                        if (spPictureType?.selectedItem.toString() == "Other") {
                            setFocus()
                            layoutOtherPictureDescription?.visibility = View.VISIBLE
                            etPictureOtherType?.setText(otherDescription)
                            etPictureOtherType?.setSelection(etPictureOtherType?.length() ?: 0)
                        } else {
                            layoutOtherPictureDescription?.visibility = View.GONE
                            etPictureOtherType?.setText("")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {}
        }

//            if (restored) {
//                spPictureType?.setSelection(descriptionIndex)
//            }

        if (priority == 1) {
            spPictureType?.setSelection(priority)
            spPictureType?.isEnabled = false
            layoutPictureType?.visibility = View.GONE
            descriptionIndex = priority
            pictureType = spPictureType?.selectedItem.toString()
        } else {
            spPictureType?.setSelection(descriptionIndex)
            spPictureType?.isEnabled = true
            layoutPictureType?.visibility = View.VISIBLE
            pictureType = spPictureType?.selectedItem.toString()
        }
    }

    private fun correctPriority(priority: Int) {
        this.priority = priority
        priorityTView?.text = when (priority) {
            1 -> "Property Picture"
            else -> "Picture $priority"
        }
    }

    fun setFocus() {
        etPictureOtherType?.requestFocus()
    }

    fun makeButtonsVisible() {
        viewBtn?.visibility = View.VISIBLE
        if (priority < 3) {
            deleteBtn?.visibility = View.GONE
            btnLayout?.visibility = View.VISIBLE
        } else {
            deleteBtn?.visibility = View.VISIBLE
            btnLayout?.visibility = View.VISIBLE
        }
    }

    fun makeButtonsInvisible() {
        viewBtn?.visibility = View.GONE
        if (priority < 3) {
            deleteBtn?.visibility = View.GONE
            btnLayout?.visibility = View.GONE
        } else {
            deleteBtn?.visibility = View.VISIBLE
            btnLayout?.visibility = View.VISIBLE
        }
    }

    companion object : Parceler<ExtraPictures> {

        override fun create(parcel: Parcel): ExtraPictures {
            return ExtraPictures(
                counter = parcel.readInt(),
                priority = parcel.readInt(),
                imageTaken = parcel.readInt(),
                picturePath = parcel.readString(),
                descriptionIndex = parcel.readInt(),
                pictureType = parcel.readString() ?: "",
                otherDescription = parcel.readString() ?: ""
            )
        }

        override fun ExtraPictures.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(counter)
            parcel.writeInt(priority)
            parcel.writeInt(imageTaken)
            parcel.writeString(picturePath)
            parcel.writeInt(descriptionIndex)
            parcel.writeString(pictureType)
            parcel.writeString(otherDescription)
        }
    }

    fun startCamera(priority: Int) {
        try {
            picturePath?.let {
                val file = File(it)
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        extraPicturesInterface?.startImageCapture(priority)
    }
}
