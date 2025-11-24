// SurveyActivity.kt
package pk.gop.pulse.katchiAbadi.activities

import OwnerSelectionDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.AddVarietyRequest
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.PersonEntryHelper
import pk.gop.pulse.katchiAbadi.data.local.SurveyFormViewModel
import pk.gop.pulse.katchiAbadi.data.local.SurveyImageAdapter
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.repository.AddVarietyResult
import pk.gop.pulse.katchiAbadi.data.repository.DropdownRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivitySurveyNewBinding
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class SurveyActivity : AppCompatActivity() {

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

    @Inject
    lateinit var dropdownRepository: DropdownRepository


    private var cropList = mutableListOf<String>()
    private var cropTypeList = mutableListOf<String>()
    private var varietyList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyNewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@SurveyActivity

        // Set default year
        binding.etYear.setText("2025")
        if (!sharedPreferences.getBoolean("sample_persons_inserted", false)) {
            //  insertSamplePersonsOnce()
        }


        binding.btnTakePhoto.setOnClickListener {
            currentImageType = binding.spinnerImageType.selectedItem.toString()
            requestCameraPermissionAndCapture()
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

    private fun requestCameraPermissionAndCapture() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            captureImage()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                captureImage()
            } else {
                ToastUtil.showShort(this, "Camera permission is required")
            }
        }

    private var selectedCropType: String? = null
    private var selectedVariety: String? = null

    // Add these as class-level variables
    private var isSettingSpinnerProgrammatically = false

    private fun setupSpinners() {
        val ownershipStatusList = listOf("Self", "On Lease")
        val propertyTypeList = listOf("Farm Survey", "Other")
        val imageTypeList = listOf("CNIC", "Property", "Other Document", "Discrepancy Pic")

        // Static spinners
        binding.spinnerOwnershipStatus.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ownershipStatusList)

        binding.spinnerPropertyStatus.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, propertyTypeList)

        binding.spinnerImageType.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, imageTypeList)

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
                cropList.add("Other")

                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    cropList
                )
                binding.etCrop.adapter = adapter

                val sugarcanePosition = cropList.indexOf("Sugarcane")
                if (sugarcanePosition != -1) {
                    binding.etCrop.setSelection(sugarcanePosition)
                } else {
                    // If "Sugarcane" not found, default to first item
                    binding.etCrop.setSelection(0)
                }

                Log.d("SurveyActivity", "Loaded ${crops.size} crops from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading crops: ${e.message}")
                ToastUtil.showShort(context, "Error loading crops")
                // ❌ REMOVE: setDefaultCropList() - No fallback
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
                    android.R.layout.simple_spinner_dropdown_item,
                    cropTypeList
                )
                binding.etCropType.adapter = adapter
                setupCropTypeListener()

                Log.d("SurveyActivity", "Loaded ${cropTypes.size} crop types from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading crop types: ${e.message}")
                ToastUtil.showShort(context, "Error loading crop types")
                // ❌ REMOVE: setDefaultCropTypeList() - No fallback
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
                    android.R.layout.simple_spinner_dropdown_item,
                    varietyList
                )
                binding.etVariety.adapter = adapter
                setupVarietyListener()

                Log.d("SurveyActivity", "Loaded ${varieties.size} varieties from local DB")
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error loading varieties: ${e.message}")
                ToastUtil.showShort(context, "Error loading varieties")
                // REMOVE: setDefaultVarietyList() - No fallback
            }
        }
    }



//    private fun loadCropsFromServer() {
//        lifecycleScope.launch {
//            try {
//                val response = withContext(Dispatchers.IO) {
//                    serverApi.getCrops()
//                }
//
//                if (response.isSuccessful && response.body() != null) {
//                    cropList.clear()
//                    cropList.addAll(response.body()!!.map { it.value })
//                    cropList.add("Other") // Add "Other" option at the end
//
//                    val adapter = ArrayAdapter(
//                        context,
//                        android.R.layout.simple_spinner_dropdown_item,
//                        cropList
//                    )
//                    binding.etCrop.adapter = adapter
//                } else {
//                    ToastUtil.showShort(context, "Failed to load crops")
//                    setDefaultCropList() // Fallback to hardcoded list
//                }
//            } catch (e: Exception) {
//                Log.e("SurveyActivity", "Error loading crops: ${e.message}")
//                ToastUtil.showShort(context, "Error loading crops: ${e.message}")
//                setDefaultCropList() // Fallback to hardcoded list
//            }
//        }
//    }
//
//    private fun loadCropTypesFromServer() {
//        lifecycleScope.launch {
//            try {
//                val response = withContext(Dispatchers.IO) {
//                    serverApi.getCropTypes()
//                }
//
//                if (response.isSuccessful && response.body() != null) {
//                    cropTypeList.clear()
//                    cropTypeList.addAll(response.body()!!.map { it.value })
//
//                    val adapter = ArrayAdapter(
//                        context,
//                        android.R.layout.simple_spinner_dropdown_item,
//                        cropTypeList
//                    )
//                    binding.etCropType.adapter = adapter
//
//                    // Setup listener after adapter is set
//                    setupCropTypeListener()
//                } else {
//                    ToastUtil.showShort(context, "Failed to load crop types")
//                    setDefaultCropTypeList()
//                }
//            } catch (e: Exception) {
//                Log.e("SurveyActivity", "Error loading crop types: ${e.message}")
//                ToastUtil.showShort(context, "Error loading crop types: ${e.message}")
//                setDefaultCropTypeList()
//            }
//        }
//    }
//
//    private fun loadVarietiesFromServer() {
//        lifecycleScope.launch {
//            try {
//                val response = withContext(Dispatchers.IO) {
//                    serverApi.getCropVarieties()
//                }
//
//                if (response.isSuccessful && response.body() != null) {
//                    varietyList.clear()
//                    varietyList.addAll(response.body()!!.map { it.value })
//                    varietyList.add("Other") // Add "Other" option at the end
//
//                    val adapter = ArrayAdapter(
//                        context,
//                        android.R.layout.simple_spinner_dropdown_item,
//                        varietyList
//                    )
//                    binding.etVariety.adapter = adapter
//
//                    // Setup listener after adapter is set
//                    setupVarietyListener()
//                } else {
//                    ToastUtil.showShort(context, "Failed to load varieties")
//                    setDefaultVarietyList()
//                }
//            } catch (e: Exception) {
//                Log.e("SurveyActivity", "Error loading varieties: ${e.message}")
//                ToastUtil.showShort(context, "Error loading varieties: ${e.message}")
//                setDefaultVarietyList()
//            }
//        }
//    }

    private fun setupVarietyListener() {
        binding.etVariety.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
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
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
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

    private fun addVarietyToRepository(varietyName: String) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    dropdownRepository.addVariety(varietyName)
                }

                when (result) {
                    is AddVarietyResult.Success -> {
                        // Successfully added to server and local DB
                        selectedVariety = varietyName
                        addVarietyToSpinner(varietyName)
                        ToastUtil.showShort(context, "Variety added successfully: $varietyName")
                    }
                    is AddVarietyResult.AlreadyExists -> {
                        // Variety already exists
                        AlertDialog.Builder(this@SurveyActivity)
                            .setTitle("Variety Already Exists")
                            .setMessage("This variety already exists. Do you want to select it?")
                            .setPositiveButton("Yes") { _, _ ->
                                selectedVariety = varietyName
                                addVarietyToSpinner(varietyName)
                                ToastUtil.showShort(context, "Selected existing variety: $varietyName")
                            }
                            .setNegativeButton("No") { _, _ ->
                                resetVarietySpinner()
                            }
                            .show()
                    }
                    is AddVarietyResult.SavedOffline -> {
                        // Saved locally, will sync later
                        selectedVariety = varietyName
                        addVarietyToSpinner(varietyName)
                        ToastUtil.showShort(context, "Variety saved offline (will sync when online): $varietyName")
                    }
                    is AddVarietyResult.Error -> {
                        ToastUtil.showShort(context, "Error: ${result.message}")
                        resetVarietySpinner()
                    }
                }
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error adding variety: ${e.message}")
                ToastUtil.showShort(context, "Error adding variety")
                resetVarietySpinner()
            }
        }
    }

    private fun addVarietyToSpinner(varietyName: String) {
        if (!varietyList.contains(varietyName)) {
            varietyList.add(varietyList.size - 1, varietyName) // Add before "Other"
        }

        isSettingSpinnerProgrammatically = true
        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            varietyList
        )
        binding.etVariety.adapter = adapter

        val newPosition = varietyList.indexOf(varietyName)
        binding.etVariety.setSelection(newPosition)
        isSettingSpinnerProgrammatically = false

        setupVarietyListener()
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

    private fun addVarietyToServer(varietyName: String) {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    serverApi.addCropVariety(AddVarietyRequest(varietyName))
                }

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!

                    if (result.alreadyExists == true) {
                        // Variety already exists
                        AlertDialog.Builder(this@SurveyActivity)
                            .setTitle("Variety Already Exists")
                            .setMessage("This variety already exists in the database. Do you want to select it?")
                            .setPositiveButton("Yes") { _, _ ->
                                selectedVariety = varietyName
                                ToastUtil.showShort(context, "Selected existing variety: $varietyName")
                            }
                            .setNegativeButton("No") { _, _ ->
                                // Reset spinner
                                isSettingSpinnerProgrammatically = true
                                binding.etVariety.setSelection(0)
                                isSettingSpinnerProgrammatically = false
                            }
                            .show()
                    } else {
                        // Successfully added
                        selectedVariety = varietyName

                        // Add to local list and refresh spinner
                        varietyList.add(varietyList.size - 1, varietyName) // Add before "Other"

                        isSettingSpinnerProgrammatically = true
                        val adapter = ArrayAdapter(
                            context,
                            android.R.layout.simple_spinner_dropdown_item,
                            varietyList
                        )
                        binding.etVariety.adapter = adapter

                        // Select the newly added variety
                        val newPosition = varietyList.indexOf(varietyName)
                        binding.etVariety.setSelection(newPosition)
                        isSettingSpinnerProgrammatically = false

                        setupVarietyListener() // Re-setup listener

                        ToastUtil.showShort(context, "Variety added successfully: $varietyName")
                    }
                } else {
                    ToastUtil.showShort(context, "Failed to add variety")
                    resetVarietySpinner()
                }
            } catch (e: Exception) {
                Log.e("SurveyActivity", "Error adding variety: ${e.message}")
                ToastUtil.showShort(context, "Error adding variety: ${e.message}")
                resetVarietySpinner()
            }
        }
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

//    private fun setDefaultCropList() {
//        cropList = mutableListOf(
//            "Sugarcane", "Wheat", "Rice", "Cotton", "Maize", "Plot",
//            "Sesame Seeds", "Uncultivated Area", "Vegetables", "Fodder", "Orchard", "Other"
//        )
//        binding.etCrop.adapter = ArrayAdapter(
//            context,
//            android.R.layout.simple_spinner_dropdown_item,
//            cropList
//        )
//    }

//    private fun setDefaultCropTypeList() {
//        cropTypeList = mutableListOf("Ratoon 1", "Ratoon 2", "Sep", "Feb", "May")
//        val adapter = ArrayAdapter(
//            context,
//            android.R.layout.simple_spinner_dropdown_item,
//            cropTypeList
//        )
//        binding.etCropType.adapter = adapter
//        setupCropTypeListener()
//    }

//    private fun setDefaultVarietyList() {
//        varietyList = mutableListOf(
//            "CP-77400", "CPF-253", "CPF-246", "NSG-59", "J-16-639", "J-16-487",
//            "YTFG-236", "HSF-240", "CPF-247", "CPF-249", "CPF-250", "CPF-251",
//            "CPF-252", "CPF-237", "CPF-236", "CSSG-676", "Other"
//        )
//        val adapter = ArrayAdapter(
//            context,
//            android.R.layout.simple_spinner_dropdown_item,
//            varietyList
//        )
//        binding.etVariety.adapter = adapter
//        setupVarietyListener()
//    }

    private fun setupPersonSection() {
        personEntryHelper = PersonEntryHelper(context, binding.layoutPersonEntries)

        binding.btnSelectOwner.setOnClickListener {
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
            personEntryHelper.addPersonView(null, editable = true)
        }
    }

    private fun setupImageSection() {
        imageAdapter = SurveyImageAdapter { imageToRemove ->
            viewModel.removeImage(imageToRemove)
            imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
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
            captureImage()
        }
    }

//    private fun setupImageSection() {
//        imageAdapter = SurveyImageAdapter()
//        binding.recyclerPictures.apply {
//            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
//            adapter = imageAdapter
//        }
//
//        binding.btnAddImage.setOnClickListener {
//            currentImageType = binding.spinnerImageType.selectedItem.toString()
//            imagePickerLauncher.launch("image/*")
//        }
//
//        binding.btnTakePhoto.setOnClickListener {
//            currentImageType = binding.spinnerImageType.selectedItem.toString()
//            captureImage()
//        }
//    }

//    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
//        uri?.let {
//            val image = SurveyImage(uri = it.toString(), type = currentImageType)
//            viewModel.addImage(image)
//            imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
//        }
//    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val savedPath = savePickedImageToInternalStorage(uri)
                val image =
                    SurveyImage(uri = savedPath, type = currentImageType)  // Store path here
                viewModel.addImage(image)
                imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
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
                            ToastUtil.showShort(context, "Failed to get photo")
                        }
                        return@launch
                    }

                    // Compress image
                    val compressedFile = compressImageFile(finalFile, 300) ?: finalFile

                    withContext(Dispatchers.Main) {
                        val image =
                            SurveyImage(uri = compressedFile.absolutePath, type = currentImageType)
                        viewModel.addImage(image)
                        database.imageDao().insertImage(image)
                        imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
                        ToastUtil.showShort(context, "Photo added successfully")
                    }
                } catch (e: Exception) {
                    Log.e("Camera", "Error handling image: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        ToastUtil.showShort(context, "Error: ${e.message}")
                    }
                }
            }
        }


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
            val surveyArea = binding.etArea.text.toString().trim()
            if (surveyArea.isBlank()) {
                AlertDialog.Builder(this)
                    .setTitle("Area Required")
                    .setMessage("Please enter the survey area before submitting.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            // ===== END VALIDATION =====

            // ===== VALIDATION: Check if at least one owner/person has been added =====
            val rawPersons = personEntryHelper.getAllPersons()
            if (rawPersons.isEmpty()) {
                // Show error message and prevent submission
                AlertDialog.Builder(this)
                    .setTitle("Owner Required")
                    .setMessage("Please add at least one owner/person before submitting the survey. Use 'Select Owner' or 'Add New Person' button.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            // ===== VALIDATION: Check that each owner has first name and area filled =====
            val invalidPersons = rawPersons.filter { person ->
                person.firstName.isNullOrBlank() || person.personArea.isNullOrBlank()
            }

            if (invalidPersons.isNotEmpty()) {
                val missingFields = mutableListOf<String>()
                invalidPersons.forEachIndexed { index, person ->
                    val issues = mutableListOf<String>()
                    if (person.firstName.isNullOrBlank()) issues.add("First Name")
                    if (person.personArea.isNullOrBlank()) issues.add("Area")
                    missingFields.add("Owner ${index + 1}: ${issues.joinToString(", ")}")
                }

                AlertDialog.Builder(this)
                    .setTitle("Missing Required Fields")
                    .setMessage("Please fill in the following required fields:\n\n${missingFields.joinToString("\n")}")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }
            // ===== END VALIDATION =====

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
                area = binding.etArea.text.toString(),
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

//need to update this for
            val images = viewModel.surveyImages.value!!

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val surveyId = database.newSurveyNewDao().insertSurvey(survey)

//                    persons.forEach {
//                        it.surveyId = surveyId
//                        database.personDao().insertPerson(it)
//                    }
                    val persons = rawPersons.map {
                        it.copy(surveyId = surveyId, mauzaId = mauzaId)
                    }

                    database.personDao()
                        .insertAll(persons) // Or use insertPerson in loop if insertAll not available


                    images.forEach {
                        it.surveyId = surveyId
                        database.imageDao().insertImage(it)
                    }


                    //   database.activeParcelDao().updateParcelSurveyStatus(2, surveyId,parcelId, )


                    val parcelOperation = intent.getStringExtra("parcelOperation") ?: ""
                    val commaseparatedparcelids =
                        intent.getStringExtra("parcelOperationValueHi") ?: ""

// mark the main parcel as surveyed
                    database.activeParcelDao().updateParcelSurveyStatus(2, surveyId, parcelId)

                    val log = TempSurveyLogEntity(
                        parcelId = parcelId,
                        parcelNo = parcelNo,
                        subParcelNo = subParcelNo
                    )
                    database.tempSurveyLogDao().insertLog(log)

// If operation is "Merge", mark the merged parcels as surveyed too
                    if (parcelOperation.equals(
                            "Merge",
                            ignoreCase = true
                        ) && commaseparatedparcelids.isNotBlank()
                    ) {
                        Log.d("SurveyActivity", "Merge operation detected.")
                        Log.d("SurveyActivity", "Raw merge parcel IDs: $commaseparatedparcelids")

                        val parcelIdList = commaseparatedparcelids
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

    private fun loadSharedMouzaData() {
        val mauzaName = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
        val areaName =
            sharedPreferences.getString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, "")
        ToastUtil.showShort(context, "MauzaID: $mauzaName ($areaName)")
    }


}