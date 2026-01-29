// SurveyActivity.kt
package pk.gop.pulse.katchiAbadi.ui.activities

import OwnerSelectionDialog
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.PersonEntryHelper
import pk.gop.pulse.katchiAbadi.data.local.SurveyFormViewModel
import pk.gop.pulse.katchiAbadi.data.local.SurveyImageAdapter
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.repository.DropdownRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivitySurveyNewBinding
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import com.google.android.gms.location.Priority
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@AndroidEntryPoint
class SurveyActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivitySurveyNewBinding
    private lateinit var context: Context
    private lateinit var personEntryHelper: PersonEntryHelper
    private lateinit var imageAdapter: SurveyImageAdapter
    private var tempImageUri: Uri? = null
    private var tempImagePath: String? = null
    private var currentImageType: String = ""

    private val viewModel: SurveyFormViewModel by viewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var serverApi: ServerApi

    @Inject
    lateinit var dropdownRepository: DropdownRepository


    private var cropList = mutableListOf<String>()
    private var cropTypeList = mutableListOf<String>()
    private var varietyList = mutableListOf<String>()

    private var selectedCropType: String? = null
    private var selectedVariety: String? = null
    private var isSettingSpinnerProgrammatically = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 100

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var currentBearing: Float = 0f
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tempImagePath", tempImagePath)
        outState.putString("tempImageUri", tempImageUri?.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        tempImagePath = savedInstanceState.getString("tempImagePath")
        tempImageUri = savedInstanceState.getString("tempImageUri")?.let { Uri.parse(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@SurveyActivity
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupSensors()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        // Set default year
        binding.etYear.setText("2025")
//        setupAreaInputRestrictions()


        if (!sharedPreferences.getBoolean("sample_persons_inserted", false)) {
            //  insertSamplePersonsOnce()
        }


        val parcelId = intent.getLongExtra("parcelId", 0L)
        val parcelNo = intent.getStringExtra("parcelNo") ?: ""
        val subParcelNo = intent.getStringExtra("subParcelNo") ?: ""
        val parcelArea = intent.getStringExtra("parcelArea") ?: ""
        val khewatInfo = intent.getStringExtra("khewatInfo") ?: ""
        val parcelOperation = intent.getStringExtra("parcelOperation") ?: ""
        val parcelOperationValue = intent.getStringExtra("parcelOperationValue") ?: ""

//        val parcelInfoText = "Parcel #$parcelNo-$subParcelNo\nArea: $parcelArea\nID: $parcelId"
        val parcelInfoText = SpannableStringBuilder()

// Bold "Parcel:"
        val parcelLabel = SpannableString("P/N:")
        parcelLabel.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            parcelLabel.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        parcelInfoText.append(parcelLabel)

// Add the rest with tabs (inline)
        parcelInfoText.append("$parcelNo/$subParcelNo\t\t GC= $khewatInfo \t\tArea: $parcelArea\t\tID: $parcelId\t\tOperation: $parcelOperation\t\tparcelOperationValue: $parcelOperationValue")

// Set to TextView
        binding.tvParcelInfo.text = parcelInfoText

        findViewById<TextView>(R.id.tvParcelInfo).text = parcelInfoText


        setupSpinners()
        setupPersonSection()
        setupImageSection()
        setupSubmit(parcelId, parcelNo, subParcelNo)
        loadSharedMouzaData()
        syncUnsyncedData()
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }


    override fun onResume() {
        super.onResume()
        accelerometer?.also { acc ->
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.also { mag ->
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

//    private fun setupAreaInputRestrictions() {
//        binding.etArea.addTextChangedListener(object : android.text.TextWatcher {
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//
//            override fun afterTextChanged(s: android.text.Editable?) {
//                val text = s.toString()
//                if (text.isNotEmpty()) {
//                    val value = text.toDoubleOrNull()
//                    if (value != null && value > 100) {
//                        binding.etArea.error = "Maximum area is 100"
//                    } else {
//                        binding.etArea.error = null
//                    }
//                }
//            }
//        })
//    }

    private fun requestCameraPermissionAndCapture() {
        val cameraPermissionGranted = checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        val locationPermissionGranted =
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

        when {
            cameraPermissionGranted && locationPermissionGranted -> {
                getCurrentLocationAndCaptureImage()
            }

            cameraPermissionGranted && !locationPermissionGranted -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            !cameraPermissionGranted && locationPermissionGranted -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }

            else -> {
                // Request both permissions
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val cameraGranted = grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) {
                getCurrentLocationAndCaptureImage()
            } else {
                ToastUtil.showShort(this, "Camera permission is required")
            }
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

            if (fineLocationGranted || coarseLocationGranted) {
                Log.d("Location", "Location permission granted, getting location...")
                getCurrentLocationAndCaptureImage()
            } else {
                Log.w("Location", "Location permission denied")
                ToastUtil.showShort(
                    this,
                    "Location permission is required to tag images with location"
                )
                // Still allow photo capture without location
                captureImage()
            }
        }

    private fun getCurrentLocationAndCaptureImage() {
        Log.d("Locations", "getCurrentLocationAndCaptureImage() called")

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w("Location", "Location permission not granted, capturing without location")
            captureImage()
            return
        }
        Log.d("Location", "Location permission granted, requesting location...")

        try {
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                Log.d("Location", "Location success callback received")
                currentLocation = location
                if (location != null) {
                    Log.d(
                        "Location",
                        "✅ Location obtained - Lat: ${location.latitude}, Lng: ${location.longitude}"
                    )
                } else {
                    Log.w("Location", "⚠️ Location is null from provider")
                }
                captureImage()
            }.addOnFailureListener { e ->
                Log.e("Location", "Failed to get location: ${e.message}")
                ToastUtil.showShort(this, "Could not get location")
                captureImage() // Still capture image without location
            }
        } catch (e: Exception) {
            Log.e("Location", "Error getting location: ${e.message}")
            captureImage()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("Camera", "Camera permission granted")
                // Check if we also have location permission
                val hasLocationPermission =
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED ||
                            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED

                if (hasLocationPermission) {
                    getCurrentLocationAndCaptureImage()  // ← FIXED
                } else {
                    captureImage()  // No location permission, capture without location
                }
            } else {
                ToastUtil.showShort(this, "Camera permission is required")
            }
        }


    private fun setupSpinners() {
        val ownershipStatusList = listOf("Self", "On Lease")
        val propertyTypeList = listOf("Farm Survey", "Other")
        val imageTypeList = listOf("CNIC", "Property", "Other Document", "Discrepancy Pic")

        // Static spinners - FIXED
        val ownershipAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,  // Changed from simple_spinner_dropdown_item
            ownershipStatusList
        )
        ownershipAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOwnershipStatus.adapter = ownershipAdapter

        val propertyAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,  // Changed from simple_spinner_dropdown_item
            propertyTypeList
        )
        propertyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPropertyStatus.adapter = propertyAdapter

        val imageAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,  // Changed from simple_spinner_dropdown_item
            imageTypeList
        )
        imageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerImageType.adapter = imageAdapter

        // Load dynamic spinners from API
        loadCropsFromLocalDb()
        loadCropTypesFromLocalDb()
        loadVarietiesFromLocalDb()
    }

    private fun loadCropsFromLocalDb() {
        lifecycleScope.launch {
            try {
                val crops = withContext(Dispatchers.IO) {
                    dropdownRepository.getCrops(forceRefresh = false)
                }

                cropList.clear()
                cropList.addAll(crops)

                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,  // Changed
                    cropList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)  // Added
                binding.etCrop.adapter = adapter

                val sugarcanePosition = cropList.indexOf("Sugarcane")
                if (sugarcanePosition != -1) {
                    binding.etCrop.setSelection(sugarcanePosition)
                } else {
                    binding.etCrop.setSelection(0)
                }

                Log.d("SurveyActivity", "Loaded ${crops.size} crops from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading crops: ${e.message}")
                ToastUtil.showShort(context, "Error loading crops")
            }
        }
    }

    private fun loadCropTypesFromLocalDb() {
        lifecycleScope.launch {
            try {
                val cropTypes = withContext(Dispatchers.IO) {
                    dropdownRepository.getCropTypes(forceRefresh = false)
                }

                cropTypeList.clear()
                cropTypeList.addAll(cropTypes)

                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,  // Changed
                    cropTypeList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)  // Added
                binding.etCropType.adapter = adapter
                setupCropTypeListener()

                Log.d("SurveyActivity", "Loaded ${cropTypes.size} crop types from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading crop types: ${e.message}")
                ToastUtil.showShort(context, "Error loading crop types")
            }
        }
    }

    private fun loadVarietiesFromLocalDb() {
        lifecycleScope.launch {
            try {
                val varieties = withContext(Dispatchers.IO) {
                    dropdownRepository.getVarieties(forceRefresh = false)
                }

                varietyList.clear()
                varietyList.addAll(varieties)
                varietyList.add("Other")

                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,  // Changed
                    varietyList
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)  // Added
                binding.etVariety.adapter = adapter
                setupVarietyListener()

                Log.d("SurveyActivity", "Loaded ${varieties.size} varieties from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading varieties: ${e.message}")
                ToastUtil.showShort(context, "Error loading varieties")
            }
        }
    }

    private fun setupVarietyListener() {
        binding.etVariety.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (isSettingSpinnerProgrammatically) return

                val selected = parent.getItemAtPosition(position).toString()

                if (selected == "Other") {
                    showCustomVarietyInputDialog()
                } else {
                    selectedVariety = selected
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupCropTypeListener() {
        binding.etCropType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (isSettingSpinnerProgrammatically) return

                val selected = parent.getItemAtPosition(position).toString()

                if (selected == "Other") {
                    showCustomCropInputDialog()
                } else {
                    selectedCropType = selected
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showCustomVarietyInputDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Enter variety name"

        AlertDialog.Builder(this)
            .setTitle("Enter Variety")
            .setMessage("Please enter the variety name. This field is required.")
            .setView(input)
            .setPositiveButton("OK") { dialogInterface, _ ->
                val customVariety = input.text.toString().trim()
                if (customVariety.isNotBlank()) {
                    // Just set the selected variety, don't add to database
                    selectedVariety = customVariety
                    ToastUtil.showShort(this, "Custom variety selected: $customVariety")
                    dialogInterface.dismiss()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Required Field")
                        .setMessage("You must enter a variety name.")
                        .setPositiveButton("Retry") { _, _ ->
                            showCustomVarietyInputDialog()
                        }
                        .show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                resetVarietySpinner()
            }
            .setCancelable(false)
            .show()
    }

    private fun showCustomCropInputDialog() {
        // You can implement similar logic for crop types if needed
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Enter crop type name"

        AlertDialog.Builder(this)
            .setTitle("Enter Crop Type")
            .setMessage("Please enter the crop type name. This field is required.")
            .setView(input)
            .setPositiveButton("OK") { dialogInterface, _ ->
                val customCrop = input.text.toString().trim()
                if (customCrop.isNotBlank()) {
                    selectedCropType = customCrop
                    ToastUtil.showShort(this, "Custom crop type selected: $customCrop")
                    dialogInterface.dismiss()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Required Field")
                        .setMessage("You must enter a crop type name.")
                        .setPositiveButton("Retry") { _, _ ->
                            showCustomCropInputDialog()
                        }
                        .show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                selectedCropType = null
                isSettingSpinnerProgrammatically = true
                binding.etCropType.setSelection(0)
                isSettingSpinnerProgrammatically = false
            }
            .setCancelable(false)
            .show()
    }

    private fun syncUnsyncedData() {
        lifecycleScope.launch {
            try {
                val syncedCount = withContext(Dispatchers.IO) {
                    dropdownRepository.syncUnsyncedVarieties()
                }
                if (syncedCount > 0) {
                    Log.d("SurveyActivity", "Synced $syncedCount unsynced varieties")
                    // Optionally refresh the varieties list
                    loadVarietiesFromLocalDb()
                }
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error syncing unsynced data: ${e.message}")
            }
        }
    }

    private fun resetVarietySpinner() {
        selectedVariety = null
        isSettingSpinnerProgrammatically = true
        binding.etVariety.setSelection(0)
        isSettingSpinnerProgrammatically = false
    }

    private fun setupPersonSection() {
        personEntryHelper = PersonEntryHelper(context, binding.layoutPersonEntries)

        binding.btnSelectOwner.setOnClickListener {
            // Check current ownership status
            val ownershipStatus = binding.spinnerOwnershipStatus.selectedItem.toString()
            val currentPersons = personEntryHelper.getAllPersons()
            val currentPersonCount = currentPersons.size

            // Validate based on ownership status
            if (ownershipStatus == "Self" && currentPersonCount >= 1) {
                AlertDialog.Builder(this)
                    .setTitle("Limit Reached")
                    .setMessage("You can only add 1 owner for 'Self' ownership status.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            if (ownershipStatus == "On Lease" && currentPersonCount >= 2) {
                AlertDialog.Builder(this)
                    .setTitle("Limit Reached")
                    .setMessage("You can only add 1 owner and 1 lease holder for 'On Lease' status.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val mauzaId =
                    sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
                Toast.makeText(context, "MauzaID: $mauzaId", Toast.LENGTH_SHORT).show()
                val owners = withContext(Dispatchers.IO) {
//                    database.personDao().getPersonsForCurrentMouza(mauzaId) ////to show owner only in a desired mouza
                    database.personDao().getallPersons()
                }

                if (owners.isEmpty()) {
                    ToastUtil.showShort(context, "No owner data found.")
                } else {
                    OwnerSelectionDialog(context, owners) { selectedPerson ->
                        personEntryHelper.addPersonView(selectedPerson, editable = true)
                    }.show()
                }
            }
        }

        binding.btnAddNewPerson.setOnClickListener {
            // Check current ownership status
            val ownershipStatus = binding.spinnerOwnershipStatus.selectedItem.toString()
            val currentPersons = personEntryHelper.getAllPersons()
            val currentPersonCount = currentPersons.size

            // Validate based on ownership status
            if (ownershipStatus == "Self" && currentPersonCount >= 1) {
                AlertDialog.Builder(this)
                    .setTitle("Limit Reached")
                    .setMessage("You can only add 1 owner for 'Self' ownership status.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            if (ownershipStatus == "On Lease" && currentPersonCount >= 2) {
                AlertDialog.Builder(this)
                    .setTitle("Limit Reached")
                    .setMessage("You can only add 1 owner and 1 lease holder for 'On Lease' status.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            personEntryHelper.addPersonView(null, editable = true)
        }
    }

    private fun setupImageSection() {
        imageAdapter = SurveyImageAdapter { imageToRemove ->
            viewModel.removeImage(imageToRemove)
            imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
            updateImageSectionUI()
        }

        binding.recyclerPictures.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = imageAdapter
        }

        binding.btnAddImage.setOnClickListener {
            currentImageType = binding.spinnerImageType.selectedItem.toString()
            imagePickerLauncher.launch("image/*")
        }

        binding.btnTakePhoto.setOnClickListener {
            currentImageType = binding.spinnerImageType.selectedItem.toString()
            requestCameraPermissionAndCapture()
        }

        // Initial UI state
        updateImageSectionUI()
    }

    private fun updateImageSectionUI() {
        val hasImages = viewModel.surveyImages.value?.isNotEmpty() == true

        binding.recyclerPictures.visibility = if (hasImages) View.VISIBLE else View.GONE
        binding.layoutEmptyState.visibility = if (hasImages) View.GONE else View.VISIBLE
        binding.layoutLoadingState.visibility = View.GONE
    }

    private fun showImageLoading() {
        binding.layoutEmptyState.visibility = View.GONE
        binding.recyclerPictures.visibility = View.GONE
        binding.layoutLoadingState.visibility = View.VISIBLE
    }

    private fun hideImageLoading() {
        binding.layoutLoadingState.visibility = View.GONE
        updateImageSectionUI()
    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                showImageLoading()

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val savedPath = savePickedImageToInternalStorage(uri)
                        val image = SurveyImage(uri = savedPath, type = currentImageType)

                        withContext(Dispatchers.Main) {
                            viewModel.addImage(image)
                            imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
                            hideImageLoading()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            hideImageLoading()
                            ToastUtil.showShort(context, "Error loading image: ${e.message}")
                        }
                    }
                }
            }
        }

    private fun savePickedImageToInternalStorage(uri: Uri): String {
        val inputStream =
            contentResolver.openInputStream(uri) ?: throw Exception("Can't open image stream")
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)

        inputStream.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return file.absolutePath
    }

    private fun captureImage() {
        try {
            val timestamp = System.currentTimeMillis()
            val photoFile = File(filesDir, "survey_img_${timestamp}.jpg")

            // Store the file path
            tempImagePath = photoFile.absolutePath

            // Ensure parent directory exists
            photoFile.parentFile?.mkdirs()

            tempImageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileProvider",
                photoFile
            )

            Log.d("Camera", "Created photo file: ${photoFile.absolutePath}")
            Log.d("Camera", "Stored path: $tempImagePath")
            Log.d("Camera", "TempImageUri: $tempImageUri")

            cameraLauncher.launch(tempImageUri!!)
        } catch (e: Exception) {
            Log.e("Camera", "Error setting up camera: ${e.message}", e)
            ToastUtil.showShort(context, "Error setting up camera: ${e.message}")
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            Log.d("Camera", "Camera result: success=$success, path=$tempImagePath, uri=$tempImageUri")

            if (!success) {
                ToastUtil.showShort(context, "Photo capture was cancelled")
                return@registerForActivityResult
            }

            // Show loading indicator
            showImageLoading()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    var finalFile: File? = null

                    // 1. Try the file path first
                    if (!tempImagePath.isNullOrEmpty()) {
                        val f = File(tempImagePath!!)
                        if (f.exists() && f.length() > 0) {
                            finalFile = f
                            Log.d("Camera", "File exists at path: ${f.absolutePath}")
                        } else {
                            Log.w("Camera", "File not found or empty: $f")
                        }
                    }

                    // 2. If file missing, fallback: copy from content Uri
                    if (finalFile == null && tempImageUri != null) {
                        val input = contentResolver.openInputStream(tempImageUri!!)
                        if (input != null) {
                            val fallbackFile = File(filesDir, "fallback_${System.currentTimeMillis()}.jpg")
                            fallbackFile.outputStream().use { output -> input.copyTo(output) }
                            if (fallbackFile.exists() && fallbackFile.length() > 0) {
                                finalFile = fallbackFile
                                Log.d("Camera", "Recovered image from Uri to: ${fallbackFile.absolutePath}")
                            }
                        }
                    }

                    if (finalFile == null) {
                        withContext(Dispatchers.Main) {
                            hideImageLoading()
                            ToastUtil.showShort(context, "Failed to get photo")
                        }
                        return@launch
                    }

                    // Compress image
                    val compressedFile = compressImageFile(finalFile, 300) ?: finalFile

                    // Get current timestamp
                    val timestamp = System.currentTimeMillis()

                    // ===== MODIFIED: Don't get location address, just use coordinates =====
                    val image = SurveyImage(
                        uri = compressedFile.absolutePath,
                        type = currentImageType,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        timestamp = timestamp,
                        locationAddress = null,
                        bearing = currentBearing
                    )

                    // Log the image data for debugging
                    Log.d("Camera", "Image created with:")
                    Log.d("Camera", "  timestamp: ${image.timestamp}")
                    Log.d("Camera", "  latitude: ${image.latitude}")
                    Log.d("Camera", "  longitude: ${image.longitude}")
                    Log.d("Camera", "  bearing: ${image.bearing}")

                    withContext(Dispatchers.Main) {
                        viewModel.addImage(image)
                        database.imageDao().insertImage(image)
                        imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
                        hideImageLoading()

                        // ===== MODIFIED: Show Date, Time, and Coordinates instead of address =====
                        val dateTime = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(
                            Date(timestamp)
                        )

                        val locationInfo = if (currentLocation != null) {
                            val lat = String.format("%.6f", currentLocation!!.latitude)
                            val lng = String.format("%.6f", currentLocation!!.longitude)
                            "\nCoordinates: $lat, $lng"
                        } else {
                            "\nLocation: Not available"
                        }

                        val directionInfo = if (currentBearing != 0f) {
                            val direction = getDirectionFromBearing(currentBearing)
                            "\nDirection: $direction (${currentBearing.toInt()}°)"
                        } else {
                            ""
                        }

                        ToastUtil.showShort(context, "Photo added\n$dateTime$locationInfo$directionInfo")

                        // Reset current location for next image
                        currentLocation = null
                    }
                } catch (e: Exception) {
                    Log.e("Camera", "Error handling image: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        hideImageLoading()
                        ToastUtil.showShort(context, "Error: ${e.message}")
                    }
                }
            }
        }


//    private fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
//        return try {
//            val geocoder = Geocoder(this, Locale.getDefault())
//            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
//            if (!addresses.isNullOrEmpty()) {
//                val address = addresses[0]
//                "${address.getAddressLine(0)}"
//            } else {
//                "Lat: $latitude, Lng: $longitude"
//            }
//        } catch (e: Exception) {
//            Log.e("Geocoder", "Error getting address: ${e.message}")
//            "Lat: $latitude, Lng: $longitude"
//        }
//    }

    // Compress file function compressing bitmap until <= targetKB size
    private fun compressImageFile(inputFile: File, targetKB: Int = 300): File? {
        try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return null

            var compressQuality = 100
            val stream = ByteArrayOutputStream()

            bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, stream)

            while (stream.size() / 1024 > targetKB && compressQuality > 10) {
                stream.reset()
                compressQuality -= 5
                bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, stream)
            }

            // Write compressed bytes to a new file
            val compressedFile = File(filesDir, "compressed_${inputFile.name}")
            compressedFile.writeBytes(stream.toByteArray())

            // Delete the original file only after successful compression
            if (compressedFile.exists()) {
                inputFile.delete()
            }

            return compressedFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun setupSubmit(parcelId: Long, parcelNo: String, subParcelNo: String) {
        binding.btnSubmitSurvey.setOnClickListener {
            // ===== VALIDATION: Check if survey area is filled =====
//            val surveyArea = binding.etArea.text.toString().trim()
//            if (surveyArea.isBlank()) {
//                AlertDialog.Builder(this)
//                    .setTitle("Area Required")
//                    .setMessage("Please enter the survey area before submitting.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }

            // ===== NEW VALIDATION: Check if area exceeds maximum value of 100 =====
//            val areaValue = surveyArea.toDoubleOrNull()
//            if (areaValue == null) {
//                AlertDialog.Builder(this)
//                    .setTitle("Invalid Area")
//                    .setMessage("Please enter a valid numeric value for area.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }

//            if (areaValue > 100) {
//                AlertDialog.Builder(this)
//                    .setTitle("Area Limit Exceeded")
//                    .setMessage("Survey area cannot exceed 100. Please enter a value between 0 and 100.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }
//
//            if (areaValue <= 0) {
//                AlertDialog.Builder(this)
//                    .setTitle("Invalid Area")
//                    .setMessage("Survey area must be greater than 0.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }

            // ===== GET PERSONS FROM HELPER =====
            val rawPersons = personEntryHelper.getAllPersons()

            // ===== VALIDATION: Check if at least one owner/person has been added =====
            if (rawPersons.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Owner Required")
                    .setMessage("Please add at least one owner/person before submitting the survey. Use 'Select Owner' or 'Add New Person' button.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            // ===== VALIDATION: Check that each owner has first name filled =====
            val invalidPersons = rawPersons.filter { person ->
                person.firstName.isNullOrBlank()
            }

            if (invalidPersons.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Missing Required Fields")
                    .setMessage("Please enter the first name for all owners.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            // ===== VALIDATION: Check for valid Grower Code =====
            val growerCodePattern = Regex("""^\d{2}-(\d{2}-\d{5}|\d{5})$""")

            val personsWithoutGrowerCode = rawPersons.filter { person ->
                val growerCode = person.growerCode?.trim()

                Log.d("GrowerCodeValidation", "Person: ${person.firstName}")
                Log.d("GrowerCodeValidation", "Raw code: '${person.growerCode}'")
                Log.d("GrowerCodeValidation", "Cleaned code: '$growerCode'")
                Log.d(
                    "GrowerCodeValidation",
                    "Pattern matches: ${growerCode?.let { growerCodePattern.matches(it) }}"
                )

                growerCode.isNullOrBlank() || !growerCodePattern.matches(growerCode)
            }

            if (personsWithoutGrowerCode.isNotEmpty()) {
                val invalidCodes = mutableListOf<String>()
                personsWithoutGrowerCode.forEachIndexed { index, person ->
                    val name = person.firstName ?: "Person ${index + 1}"
                    val code = person.growerCode?.trim() ?: "Empty"
                    invalidCodes.add("$name: $code")
                }

                AlertDialog.Builder(this)
                    .setTitle("Invalid Grower Code")
                    .setMessage(
                        "Please provide valid Grower Code with dashes (numbers only):\n• 12-34-56789 (11 digits)\n• 12-56789 (8 digits)\n\nInvalid codes:\n${
                            invalidCodes.joinToString("\n")
                        }"
                    )
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            // ===== VALIDATION: Check if at least one image has been added =====
            if (viewModel.surveyImages.value.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Image Required")
                    .setMessage("Please add at least one image before submitting the survey. Use 'Add Image' or 'Take Photo' button.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            val parcelOperation = intent.getStringExtra("parcelOperation") ?: ""
            val parcelOperationValue = if (parcelOperation == "Split") {
                intent.getStringExtra("parcelOperationValue") ?: ""
            } else {
                intent.getStringExtra("parcelOperationValueHi") ?: ""
            }

            val survey = NewSurveyNewEntity(
                parcelId = parcelId,
                parcelNo = parcelNo,
                subParcelNo = subParcelNo,
                propertyType = binding.spinnerPropertyStatus.selectedItem.toString(),
                ownershipStatus = binding.spinnerOwnershipStatus.selectedItem.toString(),
                variety = selectedVariety ?: binding.etVariety.selectedItem.toString(),
                crop = binding.etCrop.selectedItem.toString(),
                cropType = selectedCropType ?: binding.etCropType.selectedItem.toString(),
                year = binding.etYear.text.toString(),
//                area = binding.etArea.text.toString(),
                isGeometryCorrect = binding.cbGeometryCorrect.isChecked,
                remarks = binding.etRemarks.text.toString(),
                parcelOperation = parcelOperation,
                parcelOperationValue = parcelOperationValue,
                mauzaId = sharedPreferences.getLong(
                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                ),
                areaName = sharedPreferences.getString(
                    Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                    Constants.SHARED_PREF_DEFAULT_STRING
                ).orEmpty()
            )

            val mauzaId = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
            val images = viewModel.surveyImages.value!!

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val surveyId = database.newSurveyNewDao().insertSurvey(survey)
                    Log.d("SurveyActivity", "=== SAVING SURVEY $surveyId ===")

                    // ========== SMART PERSON HANDLING: Reuse unchanged, create new if modified ==========
                    rawPersons.forEach { person ->
                        Log.d("SurveyActivity", "Processing person: ${person.firstName} ${person.lastName}")
                        Log.d("SurveyActivity", "  Current personId: ${person.personId}")
                        Log.d("SurveyActivity", "  Database ID: ${person.id}")
                        Log.d("SurveyActivity", "  Grower Code: ${person.growerCode}")

                        // Check if this is an existing person from the database
                        if (person.personId != null && person.personId > 0) {
                            // This person was selected from existing list
                            val originalPerson = database.personDao().getPersonById(person.personId)

                            if (originalPerson != null) {
                                // Compare if any data was changed
                                val hasChanges = hasPersonDataChanged(originalPerson, person)

                                if (hasChanges) {
                                    // ✅ DATA WAS MODIFIED: Create a new person record
                                    Log.d("SurveyActivity", "  ⚠️ Person data was modified - creating new record")

                                    val newPerson = person.copy(
                                        id = 0, // ← Force new database ID (0 for auto-generate)
                                        personId = 0, // ← Clear old personId to create completely new
                                        surveyId = surveyId,
                                        mauzaId = mauzaId
                                    )

                                    val insertedId = database.personDao().insertPerson(newPerson)
                                    Log.d("SurveyActivity", "  ✅ Created NEW person record with ID: $insertedId")

                                } else {
                                    // ✅ NO CHANGES: Create a link to existing person for this survey
                                    Log.d("SurveyActivity", "  ✓ No changes - linking existing person to survey")

                                    val personLink = person.copy(
                                        id = 0, // ← New link record (0 for auto-generate)
                                        personId = originalPerson.personId, // ← Keep reference to original
                                        surveyId = surveyId,
                                        mauzaId = mauzaId
                                    )

                                    val linkId = database.personDao().insertPerson(personLink)
                                    Log.d("SurveyActivity", "  ✅ Created link record with ID: $linkId (points to personId: ${originalPerson.personId})")
                                }
                            } else {
                                // Person ID exists but not found in DB (shouldn't happen)
                                Log.w("SurveyActivity", "  ⚠️ PersonId ${person.personId} not found in DB - creating new")

                                val newPerson = person.copy(
                                    id = 0,
                                    surveyId = surveyId,
                                    mauzaId = mauzaId
                                )
                                database.personDao().insertPerson(newPerson)
                            }
                        } else {
                            // ✅ COMPLETELY NEW PERSON: User clicked "Add New Person"
                            Log.d("SurveyActivity", "  ➕ New person being added")

                            // Check if similar person exists by CNIC to avoid duplicates
                            val similarPerson = if (!person.nic.isNullOrBlank()) {
                                database.personDao().getPersonByCnic(person.nic)
                            } else {
                                null
                            }

                            if (similarPerson != null) {
                                // Similar person found by CNIC, link to existing
                                Log.d("SurveyActivity", "  ⚠️ Found similar person by CNIC - linking")
                                val personLink = person.copy(
                                    id = 0,
                                    personId = similarPerson.personId,
                                    surveyId = surveyId,
                                    mauzaId = mauzaId
                                )
                                database.personDao().insertPerson(personLink)
                            } else {
                                // Completely new person, insert
                                val newPerson = person.copy(
                                    id = 0,
                                    surveyId = surveyId,
                                    mauzaId = mauzaId
                                )

                                val insertedId = database.personDao().insertPerson(newPerson)
                                Log.d("SurveyActivity", "  ✅ Created new person with ID: $insertedId")
                            }
                        }
                    }
                    // ========== END SMART PERSON HANDLING ==========

                    images.forEach {
                        it.surveyId = surveyId
                        database.imageDao().insertImage(it)
                    }

                    // Mark parcel as surveyed
                    database.activeParcelDao().updateParcelSurveyStatus(2, surveyId, parcelId)

                    val log = TempSurveyLogEntity(
                        parcelId = parcelId,
                        parcelNo = parcelNo,
                        subParcelNo = subParcelNo
                    )
                    database.tempSurveyLogDao().insertLog(log)

                    // If operation is "Merge", mark the merged parcels as surveyed
                    if (parcelOperation.equals(
                            "Merge",
                            ignoreCase = true
                        ) && parcelOperationValue.isNotBlank()
                    ) {
                        Log.d("SurveyActivity", "Merge operation detected.")
                        Log.d("SurveyActivity", "Raw merge parcel IDs: $parcelOperationValue")

                        val parcelIdList = parcelOperationValue
                            .split(",")
                            .mapNotNull { it.trim().toLongOrNull() }

                        Log.d("SurveyActivity", "Parsed merge parcel IDs: $parcelIdList")

                        parcelIdList.forEach { id ->
                            Log.d(
                                "SurveyActivity",
                                "Marking parcelId=$id as surveyed with surveyId=$surveyId"
                            )
                            database.activeParcelDao().updateParcelSurveyStatus(2, surveyId, id)
                        }
                    }
                }
                ToastUtil.showShort(context, "Survey saved locally")
                finish()
            }
        }
    }

    private fun hasPersonDataChanged(original: SurveyPersonEntity, current: SurveyPersonEntity): Boolean {
        // Compare all important fields
        return original.firstName.trim() != current.firstName.trim() ||
                original.lastName.trim() != current.lastName.trim() ||
                original.gender.trim() != current.gender.trim() ||
                original.relation.trim() != current.relation.trim() ||
                original.religion.trim() != current.religion.trim() ||
                original.mobile.trim() != current.mobile.trim() ||
                original.nic.trim() != current.nic.trim() ||
                original.growerCode.trim() != current.growerCode.trim() ||
                original.ownershipType.trim() != current.ownershipType.trim() ||
                original.extra1.trim() != current.extra1.trim() ||
                original.extra2.trim() != current.extra2.trim()
    }

    private fun loadSharedMouzaData() {
        val mauzaName = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
        val areaName =
            sharedPreferences.getString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, "")
        ToastUtil.showShort(context, "MauzaID: $mauzaName ($areaName)")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(it.values, 0, accelerometerReading, 0, accelerometerReading.size)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(it.values, 0, magnetometerReading, 0, magnetometerReading.size)
                }
            }
            updateBearing()
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }


    private fun updateBearing() {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        if (SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerReading,
                magnetometerReading
            )) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert radians to degrees and normalize to 0-360
            var degrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (degrees < 0) {
                degrees += 360f
            }
            currentBearing = degrees
        }
    }

    private fun getDirectionFromBearing(bearing: Float): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "North"
            bearing >= 22.5 && bearing < 67.5 -> "North-East"
            bearing >= 67.5 && bearing < 112.5 -> "East"
            bearing >= 112.5 && bearing < 157.5 -> "South-East"
            bearing >= 157.5 && bearing < 202.5 -> "South"
            bearing >= 202.5 && bearing < 247.5 -> "South-West"
            bearing >= 247.5 && bearing < 292.5 -> "West"
            bearing >= 292.5 && bearing < 337.5 -> "North-West"
            else -> "Unknown"
        }
    }


}