package pk.gop.pulse.katchiAbadi.fragments.form

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.ExtraPictures
import pk.gop.pulse.katchiAbadi.common.ExtraPicturesInterface
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcel
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.common.Utility.Companion.convertGpsTimeToString
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.response.SubParcelStatus
import pk.gop.pulse.katchiAbadi.databinding.FragmentFormRemarksBinding
import pk.gop.pulse.katchiAbadi.databinding.FragmentNahRemarksBinding
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import pk.gop.pulse.katchiAbadi.presentation.not_at_home.SharedNAHViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class FragmentFormRemarks : Fragment(), ExtraPicturesInterface {

    private val viewModel: SharedFormViewModel by activityViewModels()
    private var _binding: FragmentFormRemarksBinding? = null
    private val binding get() = _binding!!
    private lateinit var extraPicturesList: ArrayList<ExtraPictures>
    private lateinit var pictureInflater: LayoutInflater
    private var requestCode: Int = 0

    private lateinit var customArray: Array<String>

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var sharedPreferences: SharedPreferences


    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                extraPicturesList.getOrNull(requestCode - 1)?.startCamera(requestCode)
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        Manifest.permission.CAMERA
                    )
                ) {
                    // User has permanently denied the permission, open settings dialog
                    showSettingsDialog("Camera")
                } else {
                    // Display a rationale and request permission again
                    showMessageOKCancel(
                        "You need to allow camera permission"
                    ) { _, _ ->
                        requestCameraPermission(requestCode)
                    }
                }
            }
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                navigateToMenuActivity()
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    // User has permanently denied the permission, open settings dialog
                    showSettingsDialog("Location")
                } else {
                    // Display a rationale and request permission again
                    showMessageOKCancel(
                        "You need to allow location permission"
                    ) { _, _ ->
                        requestPermission()
                    }
                }
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                view?.let {
                    handleLocationResult(locationResult)
                } ?: run {
                    Toast.makeText(
                        activity,
                        "View is not available when receiving location result",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showAlertDialog()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFormRemarksBinding.inflate(inflater, container, false)
        pictureInflater =
            requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        extraPicturesList = viewModel.extraPicturesList
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {

            if (viewModel.qrCode.isNotEmpty()) {
                tvQrCode.text = "QR Code Scanned Successfully"
                tvQrCode.setTextColor(ContextCompat.getColor(requireContext(), R.color.DarkGreen))
            } else {
                tvQrCode.text = "Click icon to scan QR Code"
                tvQrCode.setTextColor(ContextCompat.getColor(requireContext(), R.color.Black))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveForm.collect {
                when (it) {
                    is Resource.Loading -> {
//                            Utility.showProgressAlertDialog(
//                                requireContext(),
//                                "Please wait! this may take few moments..."
//                            )
                    }

                    is Resource.Success -> {
//                            Utility.dismissProgressAlertDialog()

                        if (viewModel.parcelOperation == "Split") {

                            if (viewModel.isRevisit == 1 && viewModel.newStatusId != 4) {
                                val rejectedSubParcel =
                                    Gson().fromJson(
                                        viewModel.subParcelsStatusList,
                                        RejectedSubParcel::class.java
                                    )

                                viewModel.rejectedSubParcelsList[rejectedSubParcel.position].isFormFilled =
                                    true

                                val verifyAllFilled =
                                    viewModel.rejectedSubParcelsList.all { subPar -> subPar.isFormFilled }

                                if (!verifyAllFilled) {
                                    viewModel.resetValues(true)
                                }

                                if (isAdded && findNavController().currentDestination?.id == R.id.fragmentFormRemarks) {
                                    findNavController().navigate(R.id.action_fragmentFormRemarks_to_fragmentRejectedSubParcelList)
                                }

                            } else {
                                viewModel.subParcelList[viewModel.surveyFormCounter - 1].isFormFilled =
                                    true

                                val verifyAllFilled =
                                    viewModel.subParcelList.all { subPar -> subPar.isFormFilled }

                                if (!verifyAllFilled) {
                                    viewModel.resetValues(true)
                                }

                                if (isAdded && findNavController().currentDestination?.id == R.id.fragmentFormRemarks) {
                                    findNavController().navigate(R.id.action_fragmentFormRemarks_to_fragmentSubParcelList)
                                }
                            }

                        } else {
                            Intent(requireActivity(), MenuActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(this)
                                requireActivity().finish()
                            }

                            Toast.makeText(
                                context,
                                "Data saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                        }

                    }

                    is Resource.Error -> {
//                            Utility.dismissProgressAlertDialog()
                        Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    }

                    else -> {
//                            Utility.dismissProgressAlertDialog()
                    }

                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

        try {

            extraPicturesList = viewModel.extraPicturesList

            customArray = when (viewModel.interviewStatus) {
                "Respondent Present" -> resources.getStringArray(R.array.picture_options)
                else -> resources.getStringArray(R.array.picture_options_limited)
            }

            viewLifecycleOwner.lifecycleScope.launch {
                binding.apply {

                    if (viewModel.interviewStatus == "Empty Plot") {
                        ivQrCode.visibility = View.GONE
                        tvQrCode.visibility = View.GONE
                    } else {
                        ivQrCode.visibility = View.VISIBLE
                        tvQrCode.visibility = View.VISIBLE
                    }

                    llPictureCaption.visibility = View.VISIBLE
                    llPicture.visibility = View.VISIBLE
                    llQrLayout.visibility = View.VISIBLE
                    llRemarksCaption.visibility = View.VISIBLE
                    llRemarks.visibility = View.VISIBLE

                    if (viewModel.extraPicturesList.isNotEmpty()) {
                        progressBar.visibility = View.VISIBLE
                        llMainLayout.visibility = View.GONE
                        delay(10)
                        extraPicturesContainer.removeAllViews()
                        extraPicturesContainer.visibility = View.VISIBLE
                        extraPicturesList.forEach { extraPictures ->
                            extraPictures.inflate(
                                this@FragmentFormRemarks, this@FragmentFormRemarks,
                                layoutInflater,
                                extraPicturesContainer,
                                extraPicturesList,
                                true,
                                customArray
                            )
                        }
                        makeAddMoreButtonInvisibleIfLimitReached()
                        progressBar.visibility = View.GONE
                        llMainLayout.visibility = View.VISIBLE
                    } else {
                        progressBar.visibility = View.VISIBLE
                        llMainLayout.visibility = View.GONE
                        extraPicture()
                        extraPicture()

                        progressBar.visibility = View.GONE
                        llMainLayout.visibility = View.VISIBLE
                    }

                    if (viewModel.newStatusId == 3 || viewModel.newStatusId == 11) {
//                        progressBar.visibility = View.GONE
//                        llMainLayout.visibility = View.VISIBLE
//                        llPictureCaption.visibility = View.GONE
//                        llPicture.visibility = View.GONE
                        llQrLayout.visibility = View.GONE
//                        llRemarksCaption.visibility = View.VISIBLE
//                        llRemarks.visibility = View.VISIBLE
                    }

                }
            }

            binding.apply {

                if (viewModel.parcelOperation == "Split") {
                    if (viewModel.isRevisit == 1) {
                        tvDetails.text =
                            "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter})"
                    } else {
                        tvDetails.text =
                            "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter}/${viewModel.parcelOperationValue})"
                    }
                } else {
                    tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
                }

                if (viewModel.newStatusId == 3 || viewModel.newStatusId == 11) {
                    tvPageNumber.text = "1/1"
                } else {
                    if (viewModel.interviewStatus == "Respondent Present") {
                        tvPageNumber.text = "4/4"
                    } else if (viewModel.interviewStatus == "Respondent Declined Response" ||
                        viewModel.interviewStatus == "Respondent Not Present"
                    ) {
                        tvPageNumber.text = "4/4"
                    } else if (viewModel.interviewStatus == "Empty Plot") {
                        tvPageNumber.text = "3/3"
                    }
                }

                addImageBtn.setOnClickListener { extraPicture() }

                btnSave.setOnClickListener {
                    if (!Utility.checkTimeZone(requireContext())) return@setOnClickListener

                    if (viewModel.interviewStatus == "Empty Plot" || viewModel.newStatusId == 3 || viewModel.newStatusId == 11) {
                        startLocationAndSaveRecord()
                    } else {
                        if (viewModel.qrCode.isNotEmpty()) {
                            startLocationAndSaveRecord()
                        } else {
                            Toast.makeText(context, "QR Code is not scanned", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }

                btnPrevious.setOnClickListener {
                    if (viewModel.newStatusId == 3 || viewModel.newStatusId == 11) {
                        showAlertDialog()
                    } else {
                        if (isAdded) {
                            findNavController().popBackStack()
                        }
                    }
                }

                etComments.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        charSequence: CharSequence,
                        i: Int,
                        i1: Int,
                        i2: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        i: Int,
                        i1: Int,
                        i2: Int
                    ) {
                        if (s != null) {
                            viewModel.remarks = s.toString().trim()
                        }
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })

                ivQrCode.setOnClickListener {
                    scannerLauncher.launch(
                        ScanOptions().setPrompt("Scan Qr Code")
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    )
                }
            }


        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Remarks Activity:\n${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun startLocationAndSaveRecord() {
        if (checkPermission()) {
            navigateToMenuActivity()
        } else {
            requestPermission()
        }
    }

    private val scannerLauncher = registerForActivityResult(
        ScanContract()
    ) { result ->
        binding.apply {
            if (result.contents == null) {
                tvQrCode.text = "Click icon to scan QR Code"
                viewModel.qrCode = ""
                tvQrCode.setTextColor(ContextCompat.getColor(requireContext(), R.color.Black))
            } else {
                if (result.contents.contains("//") && result.contents.contains("/") && result.contents.split(
                        "/"
                    ).size > 4 && result.contents.split("/").last().length >= 32
                ) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val scannerQR = result.contents.split("/").last()
                        val count = database.surveyFormDao().checkQRCode(scannerQR)

                        if (count < 1) {
                            viewModel.qrCode = result.contents.split("/").last()
                            tvQrCode.text = "QR Code Scanned Successfully"
                            tvQrCode.setTextColor(
                                ContextCompat.getColor(
                                    requireContext(),
                                    R.color.DarkGreen
                                )
                            )
                        } else {

                            val existsInNotAtHome =
                                database.surveyFormDao().checkQRCodeInSurveyForm(
                                    scannerQR,
                                    viewModel.parcelNo,
                                    viewModel.surveyFormCounter
                                ) > 0

                            if (existsInNotAtHome) {
                                viewModel.qrCode = result.contents.split("/").last()
                                tvQrCode.text = "QR Code Scanned Successfully"
                                tvQrCode.setTextColor(
                                    ContextCompat.getColor(
                                        requireContext(),
                                        R.color.DarkGreen
                                    )
                                )
                            } else {
                                tvQrCode.text = when (viewModel.isRevisit) {
                                    1 -> "Scan previously used or new QR Code"
                                    else -> "QR Code is already used, scan new QR code"
                                }
                                viewModel.qrCode = ""
                                tvQrCode.setTextColor(
                                    ContextCompat.getColor(
                                        requireContext(),
                                        R.color.DarkRed
                                    )
                                )
                            }

//                            tvQrCode.text = "QR Code is already used, scan new QR code"
//                            viewModel.qrCode = ""
//                            tvQrCode.setTextColor(
//                                ContextCompat.getColor(
//                                    requireContext(),
//                                    R.color.DarkRed
//                                )
//                            )
                        }
                    }
                } else {
                    tvQrCode.text = "Not a valid QR Code, Scan again"
                    viewModel.qrCode = ""
                    tvQrCode.setTextColor(ContextCompat.getColor(requireContext(), R.color.DarkRed))
                }
            }
        }
    }


    override fun onPause() {
        super.onPause()
        binding.apply {
            if (etComments.text.toString().trim().isNotEmpty()) {
                viewModel.remarks = etComments.text.toString()
            }
        }
        viewModel.extraPicturesList = extraPicturesList

        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        if (this::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        startForResult.unregister()
    }

    override fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun requestCameraPermission(priority: Int) {
        this.requestCode = priority
        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun showSettingsDialog(value: String) {
        val builder = AlertDialog.Builder(requireContext())
            .setMessage("You have denied $value permission permanently. Please go to settings to enable it.")
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

    private fun extraPicture() {
        if (binding.extraPicturesContainer.visibility == View.GONE) {
            binding.extraPicturesContainer.visibility = View.VISIBLE
        }

        val extraPicture = ExtraPictures(extraPicturesList.size)
        extraPicture.inflate(
            this, this,
            pictureInflater,
            binding.extraPicturesContainer,
            extraPicturesList,
            false,
            customArray
        )
        extraPicturesList.add(extraPicture)
        makeAddMoreButtonInvisibleIfLimitReached()

        // Save to ViewModel
        viewModel.extraPicturesList = extraPicturesList
    }

    private fun makeAddMoreButtonInvisibleIfLimitReached() {
        if (binding.extraPicturesContainer.childCount == Constants.maxNumberOfPictures) {
            binding.addImageBtn.visibility = View.GONE
        }
    }

    override fun makeAddMoreButtonVisible() {
        binding.addImageBtn.visibility = View.VISIBLE
    }

    private fun navigateToMenuActivity() {

        var addMoreImagesVerification = false
        var addMoreImagesErrorMessage = ""

        if (extraPicturesList.isNotEmpty()) {
            // Check for at least one "CNIC Front" and validate "CNIC Back"
//            val hasCnicFront =
//                extraPicturesList.any { it.pictureType == "CNIC Front" && it.imageTaken == 1 }
//            if (!hasCnicFront) {
//                addMoreImagesVerification = false
//                addMoreImagesErrorMessage = "You must capture at least one CNIC Front."
//            } else {
            for (i in extraPicturesList.indices) {
                val ep: ExtraPictures = extraPicturesList[i]
                if (ep.imageTaken == 1) {
                    if (ep.descriptionIndex > 0) {
//                            if (ep.spPictureType?.selectedItem.toString() == "Other") { // TODO check whether this is working as Survey app
                        if (ep.pictureType == "Other") {
                            if (ep.otherDescription == "") {
                                addMoreImagesVerification = false
                                ep.setFocus()
                                addMoreImagesErrorMessage =
                                    "Specify other type of Picture " + ep.priority + "."
                                break
                            } else if (ep.otherDescription.length < 4) {
                                addMoreImagesVerification = false
                                ep.setFocus()
                                addMoreImagesErrorMessage =
                                    "Enter valid other type of Picture " + ep.priority + "."
                                break
                            } else {
                                addMoreImagesVerification = true
                            }

                        } else if (ep.pictureType == "CNIC Front") {
                            val hasCnicBack = extraPicturesList.any {
                                it.pictureType == "CNIC Back" && it.imageTaken == 1
                            }
                            if (!hasCnicBack) {
                                addMoreImagesVerification = false
                                ep.setFocus()
                                addMoreImagesErrorMessage =
                                    "You must capture CNIC Back for CNIC Front " + ep.priority + "."
                                break
                            } else {
                                addMoreImagesVerification = true
                            }

                        } else {
                            addMoreImagesVerification = true

                        }
                    } else {
                        addMoreImagesVerification = false
                        addMoreImagesErrorMessage =
                            "Select type of Picture " + ep.priority + "."
                        break
                    }
                } else {
                    addMoreImagesVerification = false
                    addMoreImagesErrorMessage = when (ep.priority) {
                        1 -> "Take Property Picture."
                        else -> "Take Picture ${ep.priority}."
                    }
                    break
                }
            }
//            }
        }

        if (addMoreImagesVerification) {

            if (viewModel.newStatusId == 3 || viewModel.newStatusId == 11) {
                if (binding.etComments.text.toString().trim()
                        .isNotEmpty() && binding.etComments.text.toString().trim().length < 2
                ) {
                    binding.etComments.apply {
                        error = "Enter valid comments"
                        requestFocus()
                    }
                } else {
                    startLocationProcess()
                }
            } else {
                if (viewModel.qrCode.isNotEmpty() || viewModel.interviewStatus == "Empty Plot") {

                    if (binding.etComments.text.toString().trim()
                            .isNotEmpty() && binding.etComments.text.toString().trim().length < 2
                    ) {
                        binding.etComments.apply {
                            error = "Enter valid comments"
                            requestFocus()
                        }
                    } else {
                        startLocationProcess()
                    }

                } else {
                    Toast.makeText(context, "QR Code is not scanned", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, addMoreImagesErrorMessage, Toast.LENGTH_LONG).show()
        }
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

    override fun startImageCapture(code: Int) {
        if (isAdded) {
            this.requestCode = code

            val photoFile = createImageFile()
            val ep = extraPicturesList.getOrNull(code - 1)

            if (photoFile != null && ep != null) {
                ep.picturePath = photoFile.absolutePath

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
    }

    private val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == Activity.RESULT_OK) {

                val ep = extraPicturesList.getOrNull(requestCode - 1)

                ep?.picturePath?.let { path ->
                    try {
                        resizeImage(path)
                        ep.imageTaken = 1
                        ep.makeButtonsVisible()
                        viewModel.extraPicturesList = extraPicturesList
                    } catch (e: Exception) {
                        showErrorMessage("Problem occurred while capturing image!")
                    }
                }
            } else {
                val ep = extraPicturesList.getOrNull(requestCode - 1)
                ep?.apply {
                    imageTaken = 0
                    makeButtonsInvisible()
                }
                showErrorMessage("You canceled the image!")
            }
        }

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        var fileName = DateFormat.getDateTimeInstance().format(Calendar.getInstance().time)
            .replace(" ", "").replace(":", "").replace(",", "")
        fileName += UUID.randomUUID().toString().substring(0, 4)
        val storageDir = requireContext().cacheDir
        return File.createTempFile(fileName, ".jpg", storageDir)
    }

    private fun startLocationProcess() {
        try {
            if (Utility.checkGPS(requireActivity())) {
                Utility.showProgressAlertDialog(
                    requireActivity(),
                    "Please wait, fetching location..."
                )
                getCurrentLocationFromFusedProvider(true)


            } else {
                Utility.buildAlertMessageNoGps(requireActivity())
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location Exception :${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun getCurrentLocationFromFusedProvider(moveToActivity: Boolean) {
        try {
            locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(1500)
                .setMaxUpdateDelayMillis(3000)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Current Location Exception :${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleLocationResult(locationResult: LocationResult) {
        view?.let {
            for (location in locationResult.locations) {
                val locationAccuracy = location.accuracy.roundToInt()
                val meterAccuracy = sharedPreferences.getInt(
                    Constants.SHARED_PREF_METER_ACCURACY,
                    Constants.SHARED_PREF_DEFAULT_ACCURACY
                )
                if (locationAccuracy < meterAccuracy) {
                    if (Build.VERSION.SDK_INT < 31) {
                        @Suppress("DEPRECATION")
                        if (!location.isFromMockProvider) {
                            Utility.dismissProgressAlertDialog()
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                            setLocationAndSaveData(location)
                        } else {
                            Utility.dismissProgressAlertDialog()
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                            Utility.exitApplication(
                                "Warning!",
                                "Please disable mock/fake location. The application will exit now.",
                                requireActivity()
                            )
                        }
                    } else {
                        if (!location.isMock) {
                            Utility.dismissProgressAlertDialog()
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                            setLocationAndSaveData(location)
                        } else {
                            Utility.dismissProgressAlertDialog()
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                            Utility.exitApplication(
                                "Warning!",
                                "Please disable mock/fake location. The application will exit now.",
                                requireActivity()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        startForResult.unregister()
    }

    private fun setLocationAndSaveData(location: Location) {

        val areaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        view?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                val parcel = database.parcelDao().getParcelById(viewModel.parcelNo, areaId)

                parcel?.let {
                    val centroid = if (it.centroidGeom.contains("POINT (")) {
                        it.centroidGeom.removePrefix("POINT (").removeSuffix(")")
                    }else {
                        it.centroidGeom.removePrefix("POINT(").removeSuffix(")")
                    }

                    val splitCentroid = if (centroid.contains(",")) {
                        centroid.split(",")
                    } else {
                        centroid.split(" ")
                    }

                    val parcelLong = splitCentroid[0].toDoubleOrNull()
                    val parcelLat = splitCentroid[1].toDoubleOrNull()

                    val distanceLimit = sharedPreferences.getInt(
                        Constants.SHARED_PREF_METER_DISTANCE,
                        Constants.SHARED_PREF_DEFAULT_DISTANCE
                    )

                    val parcelDistance = it.distance

                    if (parcelLat != null && parcelLong != null) {
                        val pointDistance = Utility.distance(
                            location.latitude,
                            location.longitude,
                            parcelLat,
                            parcelLong
                        )

                        val meterAccuracy = sharedPreferences.getInt(
                            Constants.SHARED_PREF_METER_ACCURACY,
                            Constants.SHARED_PREF_DEFAULT_ACCURACY
                        )

                        val locationAccuracy = meterAccuracy / 2

                        val minusDistance = parcelDistance + locationAccuracy

                        val currentDistance =
                            pointDistance - minusDistance

//                        if (currentDistance > distanceLimit) {
//                            showDistanceAlert(currentDistance, distanceLimit)
//                        } else {
                            updateViewModelWithLocationData(location)
//                        }
                    }
                }
            }
        } ?: run {
            Toast.makeText(
                activity,
                "view is not available to set location data, please save again",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDistanceAlert(currentDistance: Double, distanceLimit: Int) {
        val message =
            "Your distance from parcel must be below ${distanceLimit}m but current distance is ${currentDistance.toInt()}m"
        Utility.dialog(context, message, "Alert!")
    }

    private fun updateViewModelWithLocationData(location: Location) {
        view?.let {
            viewModel.apply {
                gpsAltitude = location.altitude.toString()
                gpsAccuracy = location.accuracy.toString()
                gpsProvider = location.provider.toString()
                gpsTimestamp = convertGpsTimeToString(location.time)

                val currentMobileTimestamp = Constants.dateFormat.format(Date())

                Log.d("TAG", "currentLocation: $currentLocation")
                Log.d("TAG", "gpsTimestamp: $gpsTimestamp")
                Log.d("TAG", "currentMobileTimestamp: $currentMobileTimestamp")

                if (Utility.areDatesSame(
                        currentLocation = currentLocation,
                        gpsTimestamp = gpsTimestamp,
                        currentMobileTimestamp = currentMobileTimestamp
                    )
                ) {

                    if (currentLocation != "") {
                        gpsTimestamp = currentLocation
                    }

                    latitude = location.latitude.toString()
                    longitude = location.longitude.toString()

                    val validationValue = validateData()

                    if (validationValue.isEmpty()) {
                        // Proceed to save data
                        when {
                            parcelOperation == "Split" && isRevisit == 0 -> saveTempData()
                            parcelOperation == "Split" && isRevisit == 1 -> saveTempData()
                            Constants.SAVE_NAH && interviewStatus == "Respondent Not Present" -> saveNotAtHomeData()
                            else -> saveData()
                        }
                    } else {
                        val builder = AlertDialog.Builder(requireActivity())
                            .setTitle("Alert!")
                            .setCancelable(false)
                            .setMessage("Data cannot be saved, \"$validationValue\" while saving the data, restart the application.")
                            .setPositiveButton("Exit") { _, _ ->
                                Intent(context, MenuActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(this)
                                    requireActivity().finish()
                                    Utility.dismissProgressAlertDialog()
                                }
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

                } else {
                    val builder = AlertDialog.Builder(requireActivity())
                        .setTitle("Alert!")
                        .setCancelable(false)
                        .setMessage(
                            "The Mobile time and GPS time of your device are not synchronized. This may lead to " +
                                    "incorrect data being saved. Please restart the mobile to " +
                                    "resolve this issue."
                        )
                        .setPositiveButton("Exit") { _, _ ->
                            Intent(context, MenuActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(this)
                                requireActivity().finish()
                                Utility.dismissProgressAlertDialog()
                            }
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
            }
        }
    }

    private fun validateData(): String {
        viewModel.apply {
            if (parcelStatus != "New") {
                if (parcelNo == 0L) return "Parcel No is missing"
                if (parcelPkId == 0L) return "Parcel PkId is missing"
                if (parcelStatus.isEmpty()) return "Parcel Status is missing"
                if (geom.isEmpty()) return "Parcel Geometry is missing"
                if (centroid.isEmpty()) return "Parcel Centroid is missing"
            }

            if (extraPicturesList.isEmpty()) return "Property picture/s not attached"

            if (gpsTimestamp.isEmpty()) return "GPS timestamp is missing"
            if (gpsAccuracy.isEmpty()) return "GPS accuracy is missing"
            if (latitude.isEmpty()) return "Latitude is missing"
            if (longitude.isEmpty()) return "Longitude is missing"

            if (viewModel.interviewStatus != "Empty Plot" && qrCode.isEmpty() && viewModel.newStatusId != 3 && viewModel.newStatusId != 11) {
                return "QR Code is missing"
            }

            return ""
        }
    }

    private fun showAlertDialog() {
        val builder = AlertDialog.Builder(requireActivity())
            .setTitle("Alert!")
            .setCancelable(false)
            .setMessage("Are you sure you want to go back? Please note that the form will be reset, if you proceed to leave")
            .setPositiveButton("Proceed") { _, _ ->
                viewModel.resetValues(false)
                if (isAdded) {
                    findNavController().popBackStack()
                }
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


}

