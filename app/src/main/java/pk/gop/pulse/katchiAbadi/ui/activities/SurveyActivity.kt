// SurveyActivity.kt
package pk.gop.pulse.katchiAbadi.ui.activities

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
import com.esri.arcgisruntime.geometry.AngularUnit
import com.esri.arcgisruntime.geometry.AngularUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.LinearUnit
import com.esri.arcgisruntime.geometry.LinearUnitId
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
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
import com.google.android.material.textfield.TextInputEditText
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.repository.JKGrowerRepositoryImpl
import pk.gop.pulse.katchiAbadi.domain.model.JKGrowerEntity
import pk.gop.pulse.katchiAbadi.domain.model.SowingPersonEntity
import pk.gop.pulse.katchiAbadi.domain.model.SowingPersonEntry
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.presentation.util.JKGrowerSelectionDialog
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import pk.gop.pulse.katchiAbadi.data.repository.ParcelActivityLogger
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAction
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.repository.CorporateRepository
import pk.gop.pulse.katchiAbadi.data.repository.LessorRepository
import pk.gop.pulse.katchiAbadi.ui.dialogs.LessorSelectionDialog
import pk.gop.pulse.katchiAbadi.domain.model.BlockEntity
import pk.gop.pulse.katchiAbadi.domain.model.DivisionEntity
import pk.gop.pulse.katchiAbadi.domain.model.FarmEntity
import pk.gop.pulse.katchiAbadi.domain.model.LessorEntity
import pk.gop.pulse.katchiAbadi.domain.model.PlotEntity
import pk.gop.pulse.katchiAbadi.domain.model.SectionEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyLessorEntity
import pk.gop.pulse.katchiAbadi.domain.model.ZoneEntity



@AndroidEntryPoint
class SurveyActivity : AppCompatActivity(), SensorEventListener {
    private var parcelMauzaName: String = ""
    private lateinit var binding: ActivitySurveyNewBinding
    private lateinit var context: Context
    private lateinit var personEntryHelper: PersonEntryHelper
    private lateinit var imageAdapter: SurveyImageAdapter
    private var tempImageUri: Uri? = null
    private var tempImagePath: String? = null
    private var currentImageType: String = ""
    private var farmerProfilePath: String? = null

    private val viewModel: SurveyFormViewModel by viewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var serverApi: ServerApi

    @Inject
    lateinit var dropdownRepository: DropdownRepository
    @Inject
    lateinit var jkGrowerRepository: JKGrowerRepositoryImpl

    @Inject
    lateinit var corporateRepository: CorporateRepository

    @Inject
    lateinit var lessorRepository: LessorRepository

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

//    private val sowingPersonViews = mutableListOf<View>()
    private var selectedSowingDate: String? = null
    private var editSurveyId: Long = 0L
    private val isEditMode get() = editSurveyId > 0L
    private var parcelPolygon: Polygon? = null
    @Inject
    lateinit var logger: ParcelActivityLogger


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
        setupSowingSection()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        // Set default year
        binding.etYear.setText(Constants.SURVEY_YEAR)
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
//        parcelMauzaName = intent.getStringExtra("mauzaName") ?: ""
//        val parcelInfoText = "Parcel #$parcelNo-$subParcelNo\nArea: $parcelArea\nID: $parcelId"

        editSurveyId = intent.getLongExtra("editSurveyId", 0L)

        parcelMauzaName = intent.getStringExtra("mauzaName")
            ?: sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty()

        if (isEditMode) {
            supportActionBar?.title = "Edit Survey"
            binding.btnSubmitSurvey.text = "Update Survey"
        }
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

//        parcelInfoText.append("$parcelNo/$subParcelNo\t\tArea: $parcelArea\t\tID: $parcelId\t\tOperation: $parcelOperation\t\tparcelOperationValue: $parcelOperationValue")


// Set to TextView
        binding.tvParcelInfo.text = parcelInfoText

        findViewById<TextView>(R.id.tvParcelInfo).text = parcelInfoText


        setupSpinners()
        setupPersonSection()
        setupImageSection()
        setupFarmerProfile()
        setupCorporateSection()
        setupSubmit(parcelId, parcelNo, subParcelNo)
        loadParcelGeometry(parcelId)
        loadSharedMouzaData()
//        syncUnsyncedData()
        loadJKGrowers()
        if (!isEditMode) syncUnsyncedData()
    }

    private fun loadParcelGeometry(parcelId: Long) {
        lifecycleScope.launch {
            parcelPolygon = withContext(Dispatchers.IO) {
                try {
                    val wkt = database.activeParcelDao().getParcelById(parcelId)?.geomWKT
                    if (wkt.isNullOrBlank()) return@withContext null
                    val wgs84 = SpatialReferences.getWgs84()
                    when {
                        wkt.contains("MULTIPOLYGON") ->
                            Utility.getMultiPolygonFromString(wkt, wgs84)?.firstOrNull()
                        else -> Utility.getPolygonFromString(wkt, wgs84)
                    }
                } catch (e: Exception) {
                    Log.e("CapturePos", "Geometry load failed: ${e.message}")
                    null
                }
            }
            Log.d("CapturePos", "Parcel polygon loaded: ${parcelPolygon != null}")
        }
    }

    private fun describeCapturePosition(location: Location?, cameraBearing: Float): String? {
        val polygon = parcelPolygon ?: return null
        val loc = location ?: return null

        return try {
            val wgs84 = SpatialReferences.getWgs84()
            val photographer = Point(loc.longitude, loc.latitude, wgs84)
            val centre = GeometryEngine.labelPoint(polygon) ?: polygon.extent.center

            val inside = GeometryEngine.contains(polygon, photographer)

            val geo = GeometryEngine.distanceGeodetic(
                centre, photographer,
                LinearUnit(LinearUnitId.METERS),
                AngularUnit(AngularUnitId.DEGREES),
                GeodeticCurveType.GEODESIC
            )
            val distanceM = geo.distance
            val centreToPhotographer = ((geo.azimuth1 % 360) + 360) % 360   // parcel se surveyor ki taraf
            val photographerToCentre = (centreToPhotographer + 180) % 360     // surveyor se parcel ki taraf

            val side = getDirectionFromBearing(centreToPhotographer.toFloat())
            val facing = getDirectionFromBearing(cameraBearing)

            // camera parcel ki taraf hai? (±45° tolerance)
            var diff = Math.abs(cameraBearing - photographerToCentre)
            if (diff > 180) diff = 360 - diff
            val facingNote = when {
                inside -> "inside parcel"
                diff <= 45 -> "towards parcel"
                diff >= 135 -> "away from parcel"
                else -> "sideways"
            }

            val standing = if (inside) {
                "Standing: inside parcel, ~${distanceM.toInt()} m from centre"
            } else {
                "Standing: $side side, ~${distanceM.toInt()} m from centre (outside parcel)"
            }

            "$standing | Facing: $facing ($facingNote)"
        } catch (e: Exception) {
            Log.e("CapturePos", "describe failed: ${e.message}")
            null
        }
    }

    private fun loadJKGrowers() {
        lifecycleScope.launch {
            try {
                val mauzaId = sharedPreferences.getLong(
                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                )

                val growers = withContext(Dispatchers.IO) {
                    jkGrowerRepository.syncGrowersForMouza(mauzaId, forceRefresh = false)
                }

                Log.d("SurveyActivity", "Loaded ${growers.size} JK growers for mouza $mauzaId")
                // populate your UI / selection dialog here
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading JK growers: ${e.message}")
            }
        }
    }

    private fun jkGrowerToPerson(grower: JKGrowerEntity): SurveyPersonEntity {
        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()

        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0
        )
        return SurveyPersonEntity(
            id = 0,
            personId = 0,
            surveyId = 0,
            firstName = grower.growerName,
            lastName = grower.fatherName,   // no father field; using lastName. Move to extra1 if you prefer
            gender = "",
            relation = "",
            religion = "",
            mobile = grower.mobileNo,
            nic = grower.cnicNo,
            growerCode = grower.passbookNo,                // JK_Growers has no grower code; leave blank for surveyor to fill
            personArea = "",
            ownershipType = "",
            address = "",
            extra1 = "",     // stash PassbookNo somewhere; extra1 is a reasonable spot
            extra2 = "",
            mauzaId = mauzaId,
            mauzaName = mauzaName
        )
    }

    private fun setupSowingSection() {
        binding.etSowingDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedSowingDate = String.format("%02d/%02d/%04d", day, month + 1, year)
                    binding.etSowingDate.setText(selectedSowingDate)
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

//    private fun addSowingPersonEntry() {
//        val entryView = layoutInflater.inflate(
//            R.layout.item_sowing_person,
//            binding.layoutSowingPersonEntries,
//            false
//        )
//
//        val index = sowingPersonViews.size + 1
//        entryView.findViewById<TextView>(R.id.tvSowingPersonIndex).text = "Person $index"
//
//        // CNIC auto-formatter: 12345-1234567-1
//        entryView.findViewById<com.google.android.material.textfield.TextInputEditText>(
//            R.id.etSowingCnic
//        ).addTextChangedListener(object : android.text.TextWatcher {
//            private var isFormatting = false
//            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//            override fun afterTextChanged(s: android.text.Editable?) {
//                if (isFormatting) return
//                isFormatting = true
//                val digits = s.toString().replace("-", "")
//                val formatted = buildString {
//                    digits.forEachIndexed { i, c ->
//                        if (i == 5 || i == 12) append('-')
//                        append(c)
//                    }
//                }
//                s?.replace(0, s.length, formatted)
//                isFormatting = false
//            }
//        })
//
//        // Remove button
//        entryView.findViewById<com.google.android.material.button.MaterialButton>(
//            R.id.btnRemoveSowingPerson
//        ).setOnClickListener {
//            binding.layoutSowingPersonEntries.removeView(entryView)
//            sowingPersonViews.remove(entryView)
//            // Re-index remaining
//            sowingPersonViews.forEachIndexed { i, v ->
//                v.findViewById<TextView>(R.id.tvSowingPersonIndex).text = "Person ${i + 1}"
//            }
//        }
//
//        binding.layoutSowingPersonEntries.addView(entryView)
//        sowingPersonViews.add(entryView)
//    }

//    private fun getAllSowingPersons(): List<SowingPersonEntry> {
//        return sowingPersonViews.map { view ->
//            SowingPersonEntry(
//                name = view.findViewById<TextInputEditText>(R.id.etSowingName)
//                    .text.toString().trim(),
//                cnic = view.findViewById<TextInputEditText>(R.id.etSowingCnic)
//                    .text.toString().trim()
//            )
//        }.filter { it.name.isNotBlank() }   // optional: skip completely empty rows
//    }


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

    private val farmerCameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && tempImagePath != null) {
                val f = File(tempImagePath!!)
                if (f.exists() && f.length() > 0) {
                    val compressed = compressImageFile(f, 300) ?: f
                    farmerProfilePath = compressed.absolutePath
                    binding.ivFarmerProfileImage.setImageURI(Uri.fromFile(compressed))
                    // Show image, hide placeholder
                    binding.ivFarmerProfileImage.visibility = View.VISIBLE
                    binding.layoutFarmerEmptyState.visibility = View.GONE
                }
            } else {
                ToastUtil.showShort(context, "Profile photo cancelled")
            }
        }

    private val farmerGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val saved = savePickedImageToInternalStorage(it)
                        val compressed = compressImageFile(File(saved), 300) ?: File(saved)
                        withContext(Dispatchers.Main) {
                            farmerProfilePath = compressed.absolutePath
                            binding.ivFarmerProfileImage.setImageURI(Uri.fromFile(compressed))
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            ToastUtil.showShort(context, "Error: ${e.message}")
                        }
                    }
                }
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

    private fun setupFarmerProfile() {
        binding.btnFarmerCapture.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
                return@setOnClickListener
            }
            captureFarmerPhoto()
        }
    }

    private fun captureFarmerPhoto() {
        try {
            val photoFile = File(filesDir, "farmer_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            tempImagePath = photoFile.absolutePath
            tempImageUri = FileProvider.getUriForFile(this, "${packageName}.fileProvider", photoFile)
            farmerCameraLauncher.launch(tempImageUri!!)
        } catch (e: Exception) {
            ToastUtil.showShort(context, "Camera error: ${e.message}")
        }
    }

    private fun setupSpinners() {
        val ownershipStatusList = listOf("Self", "On Lease")
        val propertyTypeList = listOf("Farm Survey", "Other")
        val imageTypeList = listOf("Property", "CNIC", "Other Document", "Discrepancy Pic")

        binding.spinnerOwnershipStatus.adapter = zdAdapter(ownershipStatusList)
        binding.spinnerPropertyStatus.adapter = zdAdapter(propertyTypeList)
        binding.spinnerImageType.adapter = zdAdapter(imageTypeList)

        // Load dynamic spinners from API
        lifecycleScope.launch {
            loadCropsFromLocalDb()
            loadCropTypesFromLocalDb()
            loadVarietiesFromLocalDb()
            if (isEditMode) loadSurveyForEdit()
        }
    }

    private suspend fun loadSurveyForEdit() {
        val survey = withContext(Dispatchers.IO) {
            database.newSurveyNewDao().getSurveyById(editSurveyId)
        }
        if (survey == null) {
            ToastUtil.showShort(context, "Record not found")
            finish()
            return
        }

        val persons = withContext(Dispatchers.IO) {
            database.personDao().getPersonsForSurvey(editSurveyId)
        }
        val images = withContext(Dispatchers.IO) {
            database.imageDao().getImagesBySurvey(editSurveyId)
        }

        binding.etYear.setText(survey.year)
        binding.etRemarks.setText(survey.remarks)

        selectedSowingDate = survey.sowingDate
        binding.etSowingDate.setText(survey.sowingDate.orEmpty())

        farmerProfilePath = survey.farmerProfilePath
        farmerProfilePath?.let {
            val f = File(it)
            if (f.exists()) {
                binding.ivFarmerProfileImage.setImageURI(Uri.fromFile(f))
                binding.ivFarmerProfileImage.visibility = View.VISIBLE
                binding.layoutFarmerEmptyState.visibility = View.GONE
            }
        }

        selectSpinnerValue(binding.spinnerOwnershipStatus, survey.ownershipStatus)
        selectSpinnerValue(binding.spinnerPropertyStatus, survey.propertyType)

        isSettingSpinnerProgrammatically = true
        selectSpinnerValue(binding.etCrop, survey.crop)
        selectSpinnerValue(binding.etCropType, survey.cropType)
        selectSpinnerValue(binding.etVariety, survey.variety)
        isSettingSpinnerProgrammatically = false

        // custom values (jo list me nahi) ke liye zaroori
        selectedCropType = survey.cropType
        selectedVariety = survey.variety

        persons.forEach { personEntryHelper.addPersonView(it, editable = true) }

        images.forEach { img ->
            viewModel.addImage(img.copy(id = 0))
        }
        imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
        updateImageSectionUI()

        Log.d("SurveyActivity", "Edit prefill done: pkId=$editSurveyId, persons=${persons.size}, images=${images.size}")
    }

    private fun selectSpinnerValue(spinner: android.widget.Spinner, value: String?) {
        if (value.isNullOrBlank()) return
        val adapter = spinner.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString().equals(value, ignoreCase = true)) {
                spinner.setSelection(i)
                return
            }
        }
    }

    private fun zdAdapter(items: List<String>): ArrayAdapter<String> {
        val adapter = ArrayAdapter(context, R.layout.spinner_item_drop_down, items)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        return adapter
    }

    private suspend fun loadCropsFromLocalDb() {
        try {
            val crops = withContext(Dispatchers.IO) {
                if (isCorporateFarm) corporateRepository.getCrops()
                else dropdownRepository.getCrops(forceRefresh = false)
            }
            cropList.clear()
            cropList.addAll(crops)
            binding.etCrop.adapter = zdAdapter(cropList)

            val pos = cropList.indexOf("Sugarcane")
            binding.etCrop.setSelection(if (pos != -1) pos else 0)
        } catch (e: Exception) {
            Log.e("SurveyActivity", "Error loading crops: ${e.message}")
            ToastUtil.showShort(context, "Error loading crops")
        }
    }

    private suspend fun loadCropTypesFromLocalDb() {
            try {
                val cropTypes = withContext(Dispatchers.IO) {
                    if (isCorporateFarm) corporateRepository.getCropTypes()
                    else dropdownRepository.getCropTypes(forceRefresh = false)
                }

                cropTypeList.clear()
                cropTypeList.add("Select Crop Type")
                cropTypeList.addAll(cropTypes)

                binding.etCropType.adapter = zdAdapter(cropTypeList)
                setupCropTypeListener()

                Log.d("SurveyActivity", "Loaded ${cropTypes.size} crop types from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading crop types: ${e.message}")
                ToastUtil.showShort(context, "Error loading crop types")
            }

    }

    private suspend fun loadVarietiesFromLocalDb() {
            try {
                val varieties = withContext(Dispatchers.IO) {
                    if (isCorporateFarm) corporateRepository.getVarieties()
                    else dropdownRepository.getVarieties(forceRefresh = false)
                }
                varietyList.clear()
                varietyList.add("Select Variety")
                varietyList.addAll(varieties)
                varietyList.add("Other")

                binding.etVariety.adapter = zdAdapter(varietyList)
                setupVarietyListener()

                Log.d("SurveyActivity", "Loaded ${varieties.size} varieties from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading varieties: ${e.message}")
                ToastUtil.showShort(context, "Error loading varieties")
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
                if (position == 0) {
                    selectedVariety = null
                    return
                }
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
                if (position == 0) {
                    selectedCropType = null
                    return
                }
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

        ZdAlert.custom(
            context = this,
            title = "Enter Variety",
            message = "Please enter the variety name. This field is required.",
            view = input,
            confirmText = "OK",
            cancelText = "Cancel",
            onCancel = { resetVarietySpinner() },
            onConfirm = {
                val customVariety = input.text.toString().trim()
                if (customVariety.isNotBlank()) {
                    // Just set the selected variety, don't add to database
                    selectedVariety = customVariety
                    ToastUtil.showShort(this, "Custom variety selected: $customVariety")
                } else {
                    ZdAlert.error(
                        this,
                        "Required Field",
                        "You must enter a variety name.",
                        buttonText = "Retry",
                        onDismiss = { showCustomVarietyInputDialog() }
                    )
                }
            }
        )
            .show()
    }

    private fun showCustomCropInputDialog() {
        // You can implement similar logic for crop types if needed
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Enter crop type name"

        ZdAlert.custom(
            context = this,
            title = "Enter Crop Type",
            message = "Please enter the crop type name. This field is required.",
            view = input,
            confirmText = "OK",
            cancelText = "Cancel",
            onCancel = {
                selectedCropType = null
                isSettingSpinnerProgrammatically = true
                binding.etCropType.setSelection(0)
                isSettingSpinnerProgrammatically = false
            },
            onConfirm = {
                val customCrop = input.text.toString().trim()
                if (customCrop.isNotBlank()) {
                    selectedCropType = customCrop
                    ToastUtil.showShort(this, "Custom crop type selected: $customCrop")
                } else {
                    ZdAlert.error(
                        this,
                        "Required Field",
                        "You must enter a crop type name.",
                        buttonText = "Retry",
                        onDismiss = { showCustomCropInputDialog() }
                    )
                }
            }
        )
            .show()
    }

    private fun syncUnsyncedData() {
        // Locally added varieties are pushed to the Katchi Abadi endpoint. Corporate
        // never adds varieties to the DB, so there is nothing for it to sync here.
        if (isCorporateFarm) return

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
        val groupId = sharedPreferences.getInt(Constants.SHARED_PREF_USER_GROUP_ID, 0)
        personEntryHelper = PersonEntryHelper(context, binding.layoutPersonEntries, groupId)

        binding.btnSelectOwner.setOnClickListener {
            val ownershipStatus = binding.spinnerOwnershipStatus.selectedItem.toString()
            val currentPersonCount = personEntryHelper.getAllPersons().size

            if (ownershipStatus == "Self" && currentPersonCount >= 1) {
//                AlertDialog.Builder(this)
//                    .setTitle("Limit Reached")
//                    .setMessage("You can only add 1 owner for 'Self' ownership status.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Limit Reached",
                    "You can only add 1 owner for 'Self' ownership status."
                )
                return@setOnClickListener
            }
            if (ownershipStatus == "On Lease" && currentPersonCount >= 2) {
//                AlertDialog.Builder(this)
//                    .setTitle("Limit Reached")
//                    .setMessage("You can only add 1 owner and 1 lease holder for 'On Lease' status.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Limit Reached",
                    "You can only add 1 owner and 1 lease holder for 'On Lease' status."
                )
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val mauzaId = sharedPreferences.getLong(
                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                )

                // syncGrowersForMouza: cache hai to cache se, warna server se laa kar Room me save
                val growers = withContext(Dispatchers.IO) {
                    jkGrowerRepository.syncGrowersForMouza(mauzaId, forceRefresh = false)
                }

                if (growers.isEmpty()) {
                    ToastUtil.showShort(context, "No grower data found for this mouza.")
                } else {
                    JKGrowerSelectionDialog(context, growers) { selectedGrower ->
                        val person = jkGrowerToPerson(selectedGrower)
                        personEntryHelper.addPersonView(person, editable = true)
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
//                AlertDialog.Builder(this)
//                    .setTitle("Limit Reached")
//                    .setMessage("You can only add 1 owner for 'Self' ownership status.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Limit Reached",
                    "You can only add 1 owner for 'Self' ownership status."
                )
                return@setOnClickListener
            }

            if (ownershipStatus == "On Lease" && currentPersonCount >= 2) {
//                AlertDialog.Builder(this)
//                    .setTitle("Limit Reached")
//                    .setMessage("You can only add 1 owner and 1 lease holder for 'On Lease' status.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Limit Reached",
                    "You can only add 1 owner and 1 lease holder for 'On Lease' status."
                )
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
            Log.d(
                "Camera",
                "Camera result: success=$success, path=$tempImagePath, uri=$tempImageUri"
            )

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
                            val fallbackFile =
                                File(filesDir, "fallback_${System.currentTimeMillis()}.jpg")
                            fallbackFile.outputStream().use { output -> input.copyTo(output) }
                            if (fallbackFile.exists() && fallbackFile.length() > 0) {
                                finalFile = fallbackFile
                                Log.d(
                                    "Camera",
                                    "Recovered image from Uri to: ${fallbackFile.absolutePath}"
                                )
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
                    val capturePosition = describeCapturePosition(currentLocation, currentBearing)
                    Log.d("CapturePos", "Photo position: $capturePosition")
                    // ===== MODIFIED: Don't get location address, just use coordinates =====
                    val image = SurveyImage(
                        uri = compressedFile.absolutePath,
                        type = currentImageType,
                        latitude = currentLocation?.latitude,
                        longitude = currentLocation?.longitude,
                        timestamp = timestamp,
                        locationAddress = capturePosition,
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
//                        database.imageDao().insertImage(image)
                        imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
                        hideImageLoading()

                        // ===== MODIFIED: Show Date, Time, and Coordinates instead of address =====
                        val dateTime =
                            SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault()).format(
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

                        val positionInfo = capturePosition?.let { "\n$it" } ?: ""
                        ToastUtil.showShort(context, "Photo added\n$dateTime$locationInfo$directionInfo$positionInfo")

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

            // ===== PREVENT DOUBLE SUBMISSION =====
            // Disable button immediately - re-enable only on validation failure
            binding.btnSubmitSurvey.isEnabled = false

            // ===== VALIDATION: If sowing is Yes, date must be selected =====
//            val isSowing = binding.spinnerSowing.selectedItem.toString()
//            if (isSowing == "Yes" && selectedSowingDate.isNullOrBlank()) {
//                binding.btnSubmitSurvey.isEnabled = true   // re-enable
//                AlertDialog.Builder(this)
//                    .setTitle("Sowing Date Required")
//                    .setMessage("Please select a sowing date.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }

            // ===== GET PERSONS FROM HELPER =====
            val rawPersons = personEntryHelper.getAllPersons()

            // ===== VALIDATION: Check if at least one owner/person has been added =====
            // Corporate captures lessors instead of owners, so the owner requirement is
            // replaced by a lessor requirement for those users.
            if (isCorporateFarm) {
                if (selectedLessors.isEmpty()) {
                    binding.btnSubmitSurvey.isEnabled = true
                    ZdAlert.warn(
                        this,
                        "Lessor Required",
                        "Please select at least one lessor before submitting the survey."
                    )
                    return@setOnClickListener
                }
            } else if (rawPersons.isEmpty()) {
                binding.btnSubmitSurvey.isEnabled = true
                ZdAlert.warn(
                    this,
                    "Owner Required",
                    "Please add at least one owner/person before submitting the survey. Use 'Select Owner' or 'Add New Person' button."
                )
                return@setOnClickListener
            }

            // ===== VALIDATION: Check that each owner has first name filled =====
            val invalidPersons = rawPersons.filter { person ->
                person.firstName.isNullOrBlank()
            }

            if (invalidPersons.isNotEmpty()) {
                binding.btnSubmitSurvey.isEnabled = true
                ZdAlert.error(
                    context = this,
                    title = "Missing Required Fields",
                    message = "Please enter the first name for all owners.",
                    buttonText = "OK"
                )
                return@setOnClickListener
            }

            // ===== VALIDATION: Check for valid Grower Code (strict 12-34-56789 format) =====
//            val growerCodePattern = Regex("""^\d{2}-\d{2}-\d{5}$""")

//            val personsWithoutGrowerCode = rawPersons.filter { person ->
//                val growerCode = person.growerCode?.replace("\\s".toRegex(), "")?.trim()
//                growerCode.isNullOrBlank() || !growerCodePattern.matches(growerCode)
//            }

//            if (personsWithoutGrowerCode.isNotEmpty()) {
//                binding.btnSubmitSurvey.isEnabled = true
//                val invalidCodes = mutableListOf<String>()
//                personsWithoutGrowerCode.forEachIndexed { index, person ->
//                    val name = person.firstName ?: "Person ${index + 1}"
//                    val code = person.growerCode?.trim() ?: "Empty"
//                    invalidCodes.add("$name: $code")
//                }
//
//                AlertDialog.Builder(this)
//                    .setTitle("Invalid Grower Code")
//                    .setMessage(
//                        "Please Enter Valid Grower Code — format: 12-34-56789 (2-2-5 digits, total 9 numbers).\n\nInvalid codes:\n${
//                            invalidCodes.joinToString("\n")
//                        }"
//                    )
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//            }

            // ===== VALIDATION: Check for valid CNIC (strict 12345-1234567-1 format) =====
            val cnicPattern = Regex("""^\d{5}-\d{7}-\d{1}$""")

            val personsWithInvalidCnic = rawPersons.filter { person ->
                val cnic = person.nic?.trim()
                cnic.isNullOrBlank() || !cnicPattern.matches(cnic)
            }

//            if (personsWithInvalidCnic.isNotEmpty()) {
//                binding.btnSubmitSurvey.isEnabled = true
//                val invalidCnics = mutableListOf<String>()
//                personsWithInvalidCnic.forEachIndexed { index, person ->
//                    val name = person.firstName ?: "Person ${index + 1}"
//                    val cnic = person.nic?.trim().orEmpty().ifBlank { "Empty" }
//                    invalidCnics.add("$name: $cnic")
//                }
//
//                AlertDialog.Builder(this)
//                    .setTitle("Invalid CNIC")
//                    .setMessage(
//                        "Please provide valid CNIC in format 12345-1234567-1.\n\nInvalid CNICs:\n${
//                            invalidCnics.joinToString("\n")
//                        }"
//                    )
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener
//
//                ZdAlert.error(
//                    this,
//                    "Invalid CNIC",
//                    "CNIC is format me chahiye:  12345-1234567-1",
//                    details = invalidCnics
//                )
//                return@setOnClickListener
//            }

            if (personsWithInvalidCnic.isNotEmpty()) {
                binding.btnSubmitSurvey.isEnabled = true

                val invalidCnics = personsWithInvalidCnic.mapIndexed { index, person ->
                    val name = person.firstName?.takeIf { it.isNotBlank() } ?: "Person ${index + 1}"
                    val cnic = person.nic?.trim().orEmpty().ifBlank { "Empty" }
                    "$name  ->  $cnic"
                }

                ZdAlert.error(
                    this,
                    "Invalid CNIC",
                    "Please provide valid CNIC in format:  12345-1234567-1",
                    details = invalidCnics
                )
                return@setOnClickListener
            }


            // ===== VALIDATION: Grower Code (required for ASM / Etihad, format group ke hisab se) =====
            if (personEntryHelper.isGrowerCodeRequired) {
                val pattern = personEntryHelper.growerCodePattern

                val personsWithInvalidGrowerCode = rawPersons.filter { person ->
                    val code = person.growerCode?.replace("\\s".toRegex(), "")?.trim()
                    code.isNullOrBlank() || !pattern.matches(code)
                }

                if (personsWithInvalidGrowerCode.isNotEmpty()) {
                    binding.btnSubmitSurvey.isEnabled = true

                    val invalidCodes = personsWithInvalidGrowerCode.mapIndexed { index, person ->
                        val name = person.firstName?.takeIf { it.isNotBlank() } ?: "Person ${index + 1}"
                        val code = person.growerCode?.trim().orEmpty().ifBlank { "Empty" }
                        "$name  ->  $code"
                    }

                    ZdAlert.error(
                        this,
                        "Grower Code Required",
                        "Grower code is mandatory. Please enter it in format:  ${personEntryHelper.growerCodeHint}",
                        details = invalidCodes
                    )
                    return@setOnClickListener
                }
            }

            // ===== VALIDATION: Crop Type must be selected =====
            // Corporate hides the crop block for non "Crop Survey" property types, so
            // those fields must not be required when they are not on screen.
            val cropFieldsVisible =
                binding.layoutCropSurveyFields.visibility == View.VISIBLE

            if (cropFieldsVisible && selectedCropType.isNullOrBlank()) {
                binding.btnSubmitSurvey.isEnabled = true
//                AlertDialog.Builder(this)
//                    .setTitle("Crop Type Required")
//                    .setMessage("Please select a crop type before submitting.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Crop Type Required",
                    "Please select a crop type before submitting."
                )
                return@setOnClickListener
            }

// ===== VALIDATION: Variety must be selected =====
            if (cropFieldsVisible && selectedVariety.isNullOrBlank()) {
                binding.btnSubmitSurvey.isEnabled = true
//                AlertDialog.Builder(this)
//                    .setTitle("Variety Required")
//                    .setMessage("Please select a variety before submitting.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Variety Required",
                    "Please select a variety before submitting."
                )
                return@setOnClickListener

            }



            // ===== VALIDATION: Check if at least one image has been added =====
            if (viewModel.surveyImages.value.isEmpty()) {
                binding.btnSubmitSurvey.isEnabled = true
//                AlertDialog.Builder(this)
//                    .setTitle("Image Required")
//                    .setMessage("Please add at least one image before submitting the survey. Use 'Add Image' or 'Take Photo' button.")
//                    .setPositiveButton("OK", null)
//                    .show()
//                return@setOnClickListener

                ZdAlert.warn(
                    this,
                    "Image Required",
                    "Please add at least one image before submitting the survey. Use Capture Image button."
                )
                return@setOnClickListener
            }

            // ===== ALL VALIDATIONS PASSED — proceed with insert =====
            // Button stays disabled. We don't re-enable it because finish() will close the activity.

            val parcelOperation = intent.getStringExtra("parcelOperation") ?: ""
            val parcelOperationValue = when {
                isEditMode -> intent.getStringExtra("parcelOperationValue") ?: ""
                parcelOperation == "Split" -> intent.getStringExtra("parcelOperationValue") ?: ""
                else -> intent.getStringExtra("parcelOperationValueHi") ?: ""
            }

            val survey = NewSurveyNewEntity(
                parcelId = parcelId,
                parcelNo = parcelNo,
                subParcelNo = subParcelNo,
                // Spinners inside a hidden section can have no selection, and crop
                // type / variety are cleared when the crop block is hidden. Reading
                // them with !! crashed the submit and lost the survey.
                propertyType = binding.spinnerPropertyStatus.selectedItem?.toString().orEmpty(),
                ownershipStatus = binding.spinnerOwnershipStatus.selectedItem?.toString().orEmpty(),
                variety = selectedVariety.orEmpty(),
                crop = binding.etCrop.selectedItem?.toString().orEmpty(),
                cropType = selectedCropType.orEmpty(),
                year = binding.etYear.text.toString(),
//                isGeometryCorrect = binding.cbGeometryCorrect.isChecked,
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
                ).orEmpty(),
                sowingStatus = if (selectedSowingDate.isNullOrBlank()) "No" else "Yes",
                sowingDate = selectedSowingDate,   // optional, may be null
                farmerProfilePath = farmerProfilePath,
                // These stay null in every flow except Corporate Farms
                zone = if (isCorporateFarm) selectedZone?.zoneName else null,
                division = if (isCorporateFarm) selectedDivision?.divisionName else null,
                section = if (isCorporateFarm) selectedSection?.sectionName else null,
                farm = if (isCorporateFarm) selectedFarm?.farmName else null,
                block = if (isCorporateFarm) selectedBlock?.blockName else null,
                plot = if (isCorporateFarm) selectedPlot?.plotName else null,
                irrigationSource = if (isCorporateFarm) selectedIrrigationSource() else null,
                irrigationSourceQuantity = if (isCorporateFarm)
                    binding.etIrrigationQuantity.text?.toString()?.takeIf { it.isNotBlank() }
                else null
            )

            val mauzaId = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
            val images = viewModel.surveyImages.value!!

            lifecycleScope.launch {
                try {
                    val (surveyId, growerCodesText) = withContext(Dispatchers.IO) {
                        val surveyId = if (isEditMode) {
                            database.newSurveyNewDao().updateSurvey(survey.copy(pkId = editSurveyId))

                            // purane children pehle hataayein — warna duplicate banenge
                            // aur getPersonByCnic() purane row se galat link kar dega
                            val oldPersons = database.personDao().getPersonsForSurvey(editSurveyId)
                            if (oldPersons.isNotEmpty()) database.personDao().deletePersons(oldPersons)

                            val oldImages = database.imageDao().getImagesBySurvey(editSurveyId)
                            if (oldImages.isNotEmpty()) database.imageDao().deleteImages(oldImages)

                            editSurveyId
                        } else {
                            database.newSurveyNewDao().insertSurvey(survey)
                        }
                        Log.d("SurveyActivity", "=== SAVING SURVEY $surveyId (edit=$isEditMode) ===")

                        // Corporate Farms: link the selected lessors to this survey.
                        // In edit mode the old rows are removed first, otherwise they duplicate.
                        if (isCorporateFarm) {
                            database.surveyLessorDao().deleteForSurvey(surveyId)
                            selectedLessors.forEach { lessor ->
                                database.surveyLessorDao().insert(
                                    SurveyLessorEntity(
                                        surveyId = surveyId,
                                        parcelId = parcelId,
                                        parcelNo = parcelNo,
                                        subParcelNo = subParcelNo,
                                        lessorId = lessor.id,
                                        lessorName = lessor.name,
                                        lessorCode = lessor.code.orEmpty(),
                                        lessorType = binding.spinnerLessorType
                                            .selectedItem?.toString() ?: "Single"
                                    )
                                )
                            }
                        }

                        // ========== SMART PERSON HANDLING ==========
                        rawPersons.forEach { person ->
                            Log.d(
                                "SurveyActivity",
                                "Processing person: ${person.firstName} ${person.lastName}"
                            )

                            if (person.personId != null && person.personId > 0) {
                                val originalPerson =
                                    database.personDao().getPersonById(person.personId)

                                if (originalPerson != null) {
                                    val hasChanges = hasPersonDataChanged(originalPerson, person)

                                    if (hasChanges) {
                                        val newPerson = person.copy(
                                            id = 0,
                                            personId = 0,
                                            surveyId = surveyId,
                                            mauzaId = mauzaId,
                                            mauzaName = parcelMauzaName
                                        )
                                        database.personDao().insertPerson(newPerson)
                                        Log.d("SurveyActivity", "  ✅ Created NEW person (modified)")

                                    } else {
                                        val personLink = person.copy(
                                            id = 0,
                                            personId = originalPerson.personId,
                                            surveyId = surveyId,
                                            mauzaId = mauzaId,
                                            mauzaName = parcelMauzaName
                                        )
                                        database.personDao().insertPerson(personLink)
                                        Log.d("SurveyActivity", "  ✅ Linked existing person")
                                    }
                                } else {
                                    val newPerson = person.copy(
                                        id = 0,
                                        surveyId = surveyId,
                                        mauzaId = mauzaId,
                                        mauzaName = parcelMauzaName
                                    )
                                    database.personDao().insertPerson(newPerson)
                                }
                            } else {
                                val similarPerson = if (!person.nic.isNullOrBlank()) {
                                    database.personDao().getPersonByCnic(person.nic)
                                } else {
                                    null
                                }

                                if (similarPerson != null) {
                                    val personLink = person.copy(
                                        id = 0,
                                        personId = similarPerson.personId,
                                        surveyId = surveyId,
                                        mauzaId = mauzaId,
                                        mauzaName = parcelMauzaName
                                    )
                                    database.personDao().insertPerson(personLink)
                                } else {
                                    val newPerson = person.copy(
                                        id = 0,
                                        surveyId = surveyId,
                                        mauzaId = mauzaId,
                                        mauzaName = parcelMauzaName
                                    )
                                    database.personDao().insertPerson(newPerson)
                                    Log.d("SurveyActivity", "  ✅ Created brand new person")
                                }
                            }
                        }

                        images.forEach {
                            it.surveyId = surveyId
                            database.imageDao().insertImage(it)
                        }

                        database.activeParcelDao().updateParcelSurveyStatus(2, surveyId, parcelId)

                        val growerCodesText = rawPersons
                            .mapNotNull { it.growerCode?.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .joinToString(", ")

                        if (growerCodesText.isNotEmpty()) {
                            database.activeParcelDao().updateParcelGrowerCodes(parcelId, growerCodesText)
                            Log.d("SurveyActivity", "Saved grower codes to parcel $parcelId: $growerCodesText")
                        }

                        if (!isEditMode) {
                            val log = TempSurveyLogEntity(
                                parcelId = parcelId,
                                parcelNo = parcelNo,
                                subParcelNo = subParcelNo
                            )
                            database.tempSurveyLogDao().insertLog(log)
                        }

                        if (parcelOperation.equals("Merge", ignoreCase = true) &&
                            parcelOperationValue.isNotBlank()
                        ) {
                            val parcelIdList = parcelOperationValue
                                .split(",")
                                .mapNotNull { it.trim().toLongOrNull() }

                            parcelIdList.forEach { id ->
                                database.activeParcelDao().updateParcelSurveyStatus(2, surveyId, id)
                                if (growerCodesText.isNotEmpty()) {
                                    database.activeParcelDao().updateParcelGrowerCodes(id, growerCodesText)
                                }
                            }
                        }

                        // withContext ka return value
                        surveyId to growerCodesText
                    }

                    // ===== ACTIVITY LOG =====
                    logger.log(
                        parcelId, parcelNo,
                        if (isEditMode) ParcelAction.SURVEY_UPDATED else ParcelAction.SURVEY_SAVED,
                        subParcelNo,
                        surveyPkId = surveyId,
                        details = "op=$parcelOperation, opValue=$parcelOperationValue, " +
                                "variety=$selectedVariety, persons=${rawPersons.size}, " +
                                "images=${images.size}, growers=$growerCodesText"
                    )

                    ToastUtil.showShort(context, if (isEditMode) "Survey updated" else "Survey saved locally")
                    finish()
                } catch (e: Exception) {
                    // If insertion fails, re-enable button so user can retry
                    Log.e("SurveyActivity", "Error saving survey: ${e.message}", e)
                    binding.btnSubmitSurvey.isEnabled = true
                    ToastUtil.showShort(context, "Error saving survey: ${e.message}")
                }
            }
        }
    }

    private fun hasPersonDataChanged(
        original: SurveyPersonEntity,
        current: SurveyPersonEntity
    ): Boolean {
        // Compare all important fields
        return original.firstName.trim() != current.firstName.trim() ||
                original.lastName.trim() != current.lastName.trim() ||
                original.gender.trim() != current.gender.trim() ||
                original.relation.trim() != current.relation.trim() ||
                original.religion.trim() != current.religion.trim() ||
                original.mobile.trim() != current.mobile.trim() ||
                original.nic.trim() != current.nic.trim() ||
                original.address.trim() != current.address.trim() ||
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
                    System.arraycopy(
                        it.values,
                        0,
                        accelerometerReading,
                        0,
                        accelerometerReading.size
                    )
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
            )
        ) {
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



    // =====================================================================
    //  Corporate Farms
    //
    //  The whole section lives inside cardCorporate and stays gone by default.
    //  The gate is the server's Users.isCorperateUser flag - never group or unit
    //  id, because corporate and non-corporate users share the same group and the
    //  same unit.
    // =====================================================================

    private val isCorporateFarm: Boolean
        get() = sharedPreferences.getBoolean(Constants.SHARED_PREF_IS_CORPORATE_USER, false)

    private var allZones: List<ZoneEntity> = emptyList()
    private var allDivisions: List<DivisionEntity> = emptyList()
    private var allSections: List<SectionEntity> = emptyList()
    private var allFarms: List<FarmEntity> = emptyList()
    private var allBlocks: List<BlockEntity> = emptyList()
    private var allPlots: List<PlotEntity> = emptyList()

    private var selectedZone: ZoneEntity? = null
    private var selectedDivision: DivisionEntity? = null
    private var selectedSection: SectionEntity? = null
    private var selectedFarm: FarmEntity? = null
    private var selectedBlock: BlockEntity? = null
    private var selectedPlot: PlotEntity? = null

    private var allLessors: List<LessorEntity> = emptyList()
    private val selectedLessors = mutableListOf<LessorEntity>()

    private fun setupCorporateSection() {
        if (!isCorporateFarm) return

        binding.cardCorporate.visibility = View.VISIBLE

        // Corporate captures lessors, not owners/growers, and has no farmer profile -
        // those sections belong to the Katchi Abadi form only.
        binding.cardOwners.visibility = View.GONE
        binding.cardFarmerProfile.visibility = View.GONE

        // Corporate property types come from GetCorporatePropertyTypes, not the
        // hardcoded Katchi Abadi pair set in setupSpinners().
        lifecycleScope.launch {
            val types = corporateRepository.getPropertyTypes()
            if (types.isNotEmpty()) {
                binding.spinnerPropertyStatus.adapter = zdAdapter(types)

                // Listener must be attached after the adapter, because the list
                // arrives asynchronously from the API.
                binding.spinnerPropertyStatus.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>, view: View?, position: Int, id: Long
                        ) {
                            toggleCropSurveyFields(
                                parent.getItemAtPosition(position).toString() == "Crop Survey"
                            )
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {}
                    }

                val cropSurveyIndex = types.indexOf("Crop Survey")
                if (cropSurveyIndex != -1) {
                    binding.spinnerPropertyStatus.setSelection(cropSurveyIndex)
                }
            }
        }

        binding.spinnerLessorType.adapter = zdAdapter(listOf("Single", "Multiple"))
        binding.btnSelectLessor.setOnClickListener { showLessorPicker() }
        refreshSelectedLessorsUi()

        loadIrrigationSources()
        loadLessors()
        loadZones()
    }

    /**
     *One step of the cascade. Binds the list to the spinner and runs [onPick] on
     *selection. An empty list shows a placeholder and [onPick] never fires, because
     *the position is then outside the items range.
     */
    private fun <T> bindCascade(
        spinner: android.widget.Spinner,
        items: List<T>,
        label: (T) -> String,
        emptyText: String,
        onPick: (T) -> Unit
    ) {
        spinner.adapter =
            zdAdapter(if (items.isEmpty()) listOf(emptyText) else items.map(label))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position < items.size) onPick(items[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    /** When a parent changes, clear every spinner below it - otherwise the old selection lingers. */
    private fun clearCascadeBelow(level: Int) {
        if (level <= 1) {
            selectedDivision = null; allDivisions = emptyList()
            bindCascade(binding.spinnerDivision, emptyList<DivisionEntity>(), { "" }, "Select zone first") {}
        }
        if (level <= 2) {
            selectedSection = null; allSections = emptyList()
            bindCascade(binding.spinnerSection, emptyList<SectionEntity>(), { "" }, "Select division first") {}
        }
        if (level <= 3) {
            selectedFarm = null; allFarms = emptyList()
            bindCascade(binding.spinnerFarm, emptyList<FarmEntity>(), { "" }, "Select section first") {}
        }
        if (level <= 4) {
            selectedBlock = null; allBlocks = emptyList()
            bindCascade(binding.spinnerBlock, emptyList<BlockEntity>(), { "" }, "Select farm first") {}
        }
        if (level <= 5) {
            selectedPlot = null; allPlots = emptyList()
            bindCascade(binding.spinnerPlot, emptyList<PlotEntity>(), { "" }, "Select block first") {}
        }
    }

    private fun <T> unwrap(result: Resource<List<T>>, what: String): List<T> = when (result) {
        is Resource.Success -> result.data.orEmpty()
        is Resource.Error -> {
            ToastUtil.showShort(context, "$what could not be loaded: ${result.message}")
            emptyList()
        }
        else -> emptyList()
    }

    private fun loadZones() {
        lifecycleScope.launch {
            allZones = unwrap(corporateRepository.getZones(), "Zones")
            clearCascadeBelow(1)
            bindCascade(binding.spinnerZone, allZones, { it.zoneName }, "No zones available") { zone ->
                selectedZone = zone
                clearCascadeBelow(1)
                loadDivisions(zone.zoneId)
            }
        }
    }

    private fun loadDivisions(zoneId: Long) {
        lifecycleScope.launch {
            allDivisions = unwrap(corporateRepository.getDivisions(zoneId), "Divisions")
            bindCascade(binding.spinnerDivision, allDivisions, { it.divisionName }, "No divisions") { division ->
                selectedDivision = division
                clearCascadeBelow(2)
                loadSections(division.divisionId)
            }
        }
    }

    private fun loadSections(divisionId: Long) {
        lifecycleScope.launch {
            allSections = unwrap(corporateRepository.getSections(divisionId), "Sections")
            bindCascade(binding.spinnerSection, allSections, { it.sectionName }, "No sections") { section ->
                selectedSection = section
                clearCascadeBelow(3)
                loadFarms(section.sectionId)
            }
        }
    }

    private fun loadFarms(sectionId: Long) {
        lifecycleScope.launch {
            allFarms = unwrap(corporateRepository.getFarms(sectionId), "Farms")
            bindCascade(binding.spinnerFarm, allFarms, { it.farmName }, "No farms") { farm ->
                selectedFarm = farm
                clearCascadeBelow(4)
                loadBlocks(farm.farmId)
            }
        }
    }

    private fun loadBlocks(farmId: Long) {
        lifecycleScope.launch {
            allBlocks = unwrap(corporateRepository.getBlocks(farmId), "Blocks")
            bindCascade(binding.spinnerBlock, allBlocks, { it.blockName }, "No blocks") { block ->
                selectedBlock = block
                clearCascadeBelow(5)
                loadPlots(block.blockId)
            }
        }
    }

    private fun loadPlots(blockId: Long) {
        lifecycleScope.launch {
            allPlots = unwrap(corporateRepository.getPlots(blockId), "Plots")
            bindCascade(binding.spinnerPlot, allPlots, { it.plotName }, "No plots") { plot ->
                selectedPlot = plot
            }
        }
    }

    private fun loadIrrigationSources() {
        lifecycleScope.launch {
            val sources = corporateRepository.getIrrigationSources()
            binding.spinnerIrrigationSource.adapter =
                zdAdapter(if (sources.isEmpty()) listOf("No sources available") else sources)
        }
    }

    private fun selectedIrrigationSource(): String? =
        binding.spinnerIrrigationSource.selectedItem
            ?.toString()
            ?.takeIf { it.isNotBlank() && !it.startsWith("No ") }

    private fun loadLessors() {
        lifecycleScope.launch {
            allLessors = when (val r = lessorRepository.fetchAndCacheLessors()) {
                is Resource.Success -> r.data.orEmpty()
                else -> emptyList()
            }
        }
    }

    private fun showLessorPicker() {
        val multiple = binding.spinnerLessorType.selectedItem?.toString() == "Multiple"
        LessorSelectionDialog(
            context = context,
            lessors = allLessors,
            currentSelectedId = selectedLessors.firstOrNull()?.id
        ) { picked ->
            if (multiple) {
                if (selectedLessors.none { it.id == picked.id }) selectedLessors.add(picked)
            } else {
                selectedLessors.clear()
                selectedLessors.add(picked)
            }
            refreshSelectedLessorsUi()
        }.show()
    }

    private fun refreshSelectedLessorsUi() {
        val container = binding.layoutSelectedLessors
        container.removeAllViews()
        binding.tvNoLessors.visibility =
            if (selectedLessors.isEmpty()) View.VISIBLE else View.GONE

        selectedLessors.forEach { lessor ->
            val row = TextView(context).apply {
                text = if (lessor.code.isNullOrBlank()) lessor.name
                else "${lessor.name}  (${lessor.code})"
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#1B5E20"))
                setPadding(0, 10, 0, 10)
                setOnClickListener {
                    ZdAlert.confirmDanger(
                        context = context,
                        title = "Remove lessor?",
                        message = "${lessor.name} will be removed from this survey.",
                        confirmText = "Remove"
                    ) {
                        selectedLessors.removeAll { it.id == lessor.id }
                        refreshSelectedLessorsUi()
                    }
                }
            }
            container.addView(row)
        }
    }

    /**
     * Corporate only: the crop details and irrigation belong to a "Crop Survey" and are
     * hidden for every other property type. Hidden fields are also cleared, so a value
     * picked before switching type cannot leak into the saved survey.
     */
    private fun toggleCropSurveyFields(show: Boolean) {
        binding.layoutCropSurveyFields.visibility = if (show) View.VISIBLE else View.GONE
        binding.layoutCorporateIrrigation.visibility = if (show) View.VISIBLE else View.GONE

        if (!show) {
            selectedCropType = null
            selectedVariety = null
            selectedSowingDate = null
            binding.etSowingDate.setText("")
            binding.etIrrigationQuantity.setText("")

            isSettingSpinnerProgrammatically = true
            if (binding.etCropType.adapter != null) binding.etCropType.setSelection(0)
            if (binding.etVariety.adapter != null) binding.etVariety.setSelection(0)
            isSettingSpinnerProgrammatically = false
        }
    }
}
