package pk.gop.pulse.katchiAbadi.fragments.form

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.devstune.searchablemultiselectspinner.SearchableItem
import com.devstune.searchablemultiselectspinner.SearchableMultiSelectSpinner
import com.devstune.searchablemultiselectspinner.SelectionCompleteListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.FragmentParcelDetailsBinding
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class FragmentParcelDetails : Fragment(), RadioGroup.OnCheckedChangeListener {
    private val viewModel: SharedFormViewModel by activityViewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    private var _binding: FragmentParcelDetailsBinding? = null
    private val binding get() = _binding!!

    private var requestCode: Int = 0

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        Manifest.permission.CAMERA
                    )
                ) {
                    // User has permanently denied the permission, open settings dialog
                    showSettingsDialog()
                } else {
                    // Display a rationale and request permission again
                    showMessageOKCancel(
                        "You need to allow camera permission"
                    ) { _, _ ->
                        requestCameraPermission()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParcelDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("requestCode", requestCode)
    }


    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.let {
            requestCode = it.getInt("requestCode", 0)
        }
    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }


        binding.apply {

            tvDetails.text = "Parcel No: ${viewModel.parcelNo}"

            when (viewModel.parcelOperation) {
                "Same" -> {
                    rbSame.isChecked = true
                    etSplitParcel.setText("")
                    tvMergeParcel.text = ""

                    binding.trSplitParcelCaption.visibility = View.GONE
                    binding.trSplitParcel.visibility = View.GONE
                    binding.trMergeParcelCaption.visibility = View.GONE
                    binding.trMergeParcel.visibility = View.GONE
                    binding.layoutDiscrepancyPicture.visibility = View.GONE
                }

                "Split" -> {
                    rbSplit.isChecked = true
                    etSplitParcel.setText(viewModel.parcelOperationValue)
                    tvMergeParcel.text = ""

                    binding.trSplitParcelCaption.visibility = View.VISIBLE
                    binding.trSplitParcel.visibility = View.VISIBLE
                    binding.trMergeParcelCaption.visibility = View.GONE
                    binding.trMergeParcel.visibility = View.GONE
                    binding.layoutDiscrepancyPicture.visibility = View.VISIBLE
                }

                "Merge" -> {
                    rbMerge.isChecked = true
                    tvMergeParcel.text = viewModel.parcelOperationValue
                    etSplitParcel.setText("")

                    binding.trSplitParcelCaption.visibility = View.GONE
                    binding.trSplitParcel.visibility = View.GONE
                    binding.trMergeParcelCaption.visibility = View.VISIBLE
                    binding.trMergeParcel.visibility = View.VISIBLE
                    binding.layoutDiscrepancyPicture.visibility = View.VISIBLE
                }
            }

            when (viewModel.imageTaken) {
                1 -> {
                    binding.btnLayout.visibility = View.VISIBLE
                }

                else -> {
                    binding.btnLayout.visibility = View.GONE
                }
            }

            rgParcelDivision.setOnCheckedChangeListener(this@FragmentParcelDetails)

            tvMergeParcel.setOnClickListener {
                val items: MutableList<SearchableItem> = ArrayList()

                viewLifecycleOwner.lifecycleScope.launch {
                    val abadiId = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    )
                    val parcels = database.parcelDao().getAllUnSurveyedParcels(abadiId, viewModel.parcelNo)

                    for (parcel in parcels) {
                        if (viewModel.parcelNo != parcel.parcelNo)
                            items.add(SearchableItem("${parcel.parcelNo}", "${parcel.pkId}"))
                    }

                    SearchableMultiSelectSpinner.show(
                        requireContext(),
                        "Select Parcels",
                        "Done",
                        items,
                        object :
                            SelectionCompleteListener {
                            override fun onCompleteSelection(selectedItems: ArrayList<SearchableItem>) {
                                if (selectedItems.size > 0) {
                                    var returnValue = ""
                                    for (item in selectedItems) {
                                        returnValue += "${item.text},"
                                    }

                                    val filteredValue = returnValue.removeLastOccurrence(',')

                                    binding.tvMergeParcel.text = filteredValue
                                    viewModel.parcelOperationValue = filteredValue

                                } else {
                                    binding.tvMergeParcel.text = ""
                                }
                            }

                        })

                }
            }

            imageParcelDiscrepancy.setOnClickListener {
                if (checkCameraPermission()) {
                    startCamera()
                } else {
                    requestCameraPermission()
                }
            }

            viewBtn.setOnClickListener {
                val pictureFile = File(viewModel.discrepancyPicturePath)
                if (pictureFile.exists()) {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        Constants.Package_Provider,
                        pictureFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "image/*")
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                    try {
                        requireContext().startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            requireContext(),
                            "No app can open this image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Image file does not exist",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            btnNext.setOnClickListener {

                val radioButton: RadioButton =
                    binding.root.findViewById(rgParcelDivision.checkedRadioButtonId)

                viewModel.parcelOperation = radioButton.text.toString()

                var action = R.id.action_fragmentParcelDetails_to_fragmentSurveyList

                when (rgParcelDivision.checkedRadioButtonId) {
                    R.id.rb_same -> {
                        viewModel.parcelOperationValue = ""
                        viewModel.imageTaken = 0
                        viewModel.discrepancyPicturePath = ""
                    }

                    R.id.rb_split -> {
                        if (etSplitParcel.text.toString().trim().isEmpty()) {
                            etSplitParcel.apply {
                                setText("")
                                error = "Field cannot be empty"
                                requestFocus()
                            }
                            return@setOnClickListener
                        }

                        if (etSplitParcel.text.toString().trim().toInt() < 2) {
                            etSplitParcel.apply {
                                setText("")
                                error = "Enter valid number of parcels"
                                requestFocus()
                            }
                            return@setOnClickListener
                        }

                        if (viewModel.imageTaken == 0) {
                            Toast.makeText(
                                requireContext(),
                                "Capture discrepancy picture",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        viewModel.parcelOperationValue = etSplitParcel.text.toString().trim()

                        action = R.id.action_fragmentParcelDetails_to_fragmentSubParcelList
                    }

                    R.id.rb_merge -> {
                        if (tvMergeParcel.text.toString().trim().isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                "Merge parcel field is empty",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        if (viewModel.imageTaken == 0) {
                            Toast.makeText(
                                requireContext(),
                                "Capture discrepancy picture",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }

                        viewModel.parcelOperationValue = tvMergeParcel.text.toString().trim()
                    }
                }

                navigateToNextScreen(action)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        startForResult.unregister()
    }

    override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
        when (checkedId) {
            R.id.rb_same -> {
                binding.trSplitParcelCaption.visibility = View.GONE
                binding.trSplitParcel.visibility = View.GONE
                binding.trMergeParcelCaption.visibility = View.GONE
                binding.trMergeParcel.visibility = View.GONE
                binding.layoutDiscrepancyPicture.visibility = View.GONE

                viewModel.imageTaken = 0
                viewModel.discrepancyPicturePath = ""
            }

            R.id.rb_split -> {
                binding.trSplitParcelCaption.visibility = View.VISIBLE
                binding.trSplitParcel.visibility = View.VISIBLE
                binding.trMergeParcelCaption.visibility = View.GONE
                binding.trMergeParcel.visibility = View.GONE
                binding.layoutDiscrepancyPicture.visibility = View.VISIBLE

                binding.btnLayout.visibility = View.GONE
                viewModel.imageTaken = 0
            }

            R.id.rb_merge -> {
                binding.trSplitParcelCaption.visibility = View.GONE
                binding.trSplitParcel.visibility = View.GONE
                binding.trMergeParcelCaption.visibility = View.VISIBLE
                binding.trMergeParcel.visibility = View.VISIBLE
                binding.layoutDiscrepancyPicture.visibility = View.VISIBLE

                binding.btnLayout.visibility = View.GONE
                viewModel.imageTaken = 0

            }
        }
    }

    private fun navigateToNextScreen(action: Int) {
        if (isAdded) {
            findNavController().navigate(action)
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(requireContext())
            .setMessage("You have denied permission permanently. Please go to settings to enable it.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)

        // Create the AlertDialog object
        val dialog = builder.create()
        dialog.show()

        // Get the buttons from the dialog
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        // Set button text size and style
        positiveButton.textSize =
            16f // Change the size according to your preference
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold

        negativeButton.textSize =
            16f // Change the size according to your preference
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
        // Create the AlertDialog object
        val dialog = builder.create()
        dialog.show()

        // Get the buttons from the dialog
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        // Set button text size and style
        positiveButton.textSize =
            16f // Change the size according to your preference
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold

        negativeButton.textSize =
            16f // Change the size according to your preference
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
    }

    private fun startCamera() {
        try {
            if (viewModel.discrepancyPicturePath.isNotEmpty()) {
                val file = File(viewModel.discrepancyPicturePath)
                file.delete()
            }
        } catch (e: Exception) {
        }

        startImageCapture()
    }

    private fun startImageCapture() {
        val photoFile = createImageFile()

        if (photoFile != null) {

            viewModel.discrepancyPicturePath = photoFile.absolutePath

            val photoURI = FileProvider.getUriForFile(
                requireContext(),
                Constants.Package_Provider,
                photoFile
            )

            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            }.let { cameraIntent ->
                startForResult.launch(cameraIntent)
            }
        }
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    try {
                        val path: String = viewModel.discrepancyPicturePath
                        resizeImage(path)

                        binding.btnLayout.visibility = View.VISIBLE
                        viewModel.imageTaken = 1
                    } catch (e: Exception) {
                        showErrorMessage("Problem occurred while capturing image!")
                    }
                } else {
                    binding.btnLayout.visibility = View.GONE
                    viewModel.imageTaken = 0
                    showErrorMessage("You canceled the image!")
                }
        }


    @Throws(IOException::class)
    private fun createImageFile(): File? {
        var fileName = DateFormat.getDateTimeInstance().format(Calendar.getInstance().time)
            .replace(" ", "").replace(":", "").replace(",", "")
        fileName += UUID.randomUUID().toString().substring(0, 4);
        val storageDir = requireContext().cacheDir
        return File.createTempFile(fileName, ".jpg", storageDir)
    }

    private fun resizeImage(inputPath: String) {
        // Load the original bitmap from file
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        var bitmap = BitmapFactory.decodeFile(inputPath, options)

        try {
            bitmap = rotateImage(bitmap, inputPath)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        // Calculate the scale factor to achieve the target size
        val scaleFactor = calculateScaleFactor(bitmap.width, bitmap.height)

        // Calculate the new dimensions
        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        // Create a new bitmap with the desired dimensions
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        // Save the resized bitmap to the original file
        val fileOutputStream = FileOutputStream(File(inputPath))
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        resizedBitmap.recycle()
    }

    private fun calculateScaleFactor(originalWidth: Int, originalHeight: Int): Float {
        val widthScale = Constants.maxImageSize.toFloat() / originalWidth.toFloat()
        val heightScale = Constants.maxImageSize.toFloat() / originalHeight.toFloat()
        return if (widthScale < heightScale) widthScale else heightScale
    }

    @Throws(IOException::class)
    private fun rotateImage(bitmap: Bitmap, path: String): Bitmap {
        var rotate = 0
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90
        }
        val matrix = Matrix()
        matrix.postRotate(rotate.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun showErrorMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

fun String.removeLastOccurrence(char: Char): String {
    val lastIndexOfChar = lastIndexOf(char)
    return if (lastIndexOfChar >= 0) {
        substring(0, lastIndexOfChar) + substring(lastIndexOfChar + 1)
    } else {
        this
    }
}