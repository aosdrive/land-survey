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
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.PersonEntryHelper
import pk.gop.pulse.katchiAbadi.data.local.SurveyFormViewModel
import pk.gop.pulse.katchiAbadi.data.local.SurveyImageAdapter
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
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
    private var tempImagePath: String? = null  // ADD this line
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
        val cropList = listOf(
            "Sugarcane",
            "Wheat",
            "Rice",
            "Cotton",
            "Maize",
            "Plot",
            "Sesame Seeds",
            "Uncultivated Area",
            "Vegetables",
            "Fodder",
            "Orchard",
            "Other"
        )
        val cropTypeList = listOf("Ratoon 1", "Ratoon 2", "Sep", "Feb", "Other")
        val varietyList = listOf(
            "CP-77400",
            "CPF-253",
            "CPF-246",
            "NSG-59",
            "J-16-639",
            "J-16-487",
            "YTFG-236",
            "HSF-240",
            "CPF-247",
            "CPF-249",
            "CPF-250",
            "CPF-251",
            "CPF-252",
            "CPF-237",
            "CPF-236",
            "CSSG-676",
            "Other"
        )

        binding.spinnerOwnershipStatus.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                ownershipStatusList
            )

        binding.spinnerPropertyStatus.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, propertyTypeList)

        binding.spinnerImageType.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, imageTypeList)

        binding.etCrop.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, cropList)

        binding.etCropType.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, cropTypeList)

        // Setup etVariety
        val varietyAdapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_item, varietyList)
        varietyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.etVariety.adapter = varietyAdapter

        // Listener for etVariety
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

        // Listener for etCropType
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

        AlertDialog.Builder(this)
            .setTitle("Enter Variety")
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val customVariety = input.text.toString()
                if (customVariety.isNotBlank()) {
                    selectedVariety = customVariety
                    ToastUtil.showShort(this, "Selected Variety: $customVariety")
                } else {
                    // If empty, set to "Other" and keep spinner on "Other"
                    selectedVariety = "Other"
                    // Keep the spinner on "Other" position
                    isSettingSpinnerProgrammatically = true
                    val varietyAdapter = binding.etVariety.adapter as ArrayAdapter<String>
                    val otherPosition = varietyAdapter.getPosition("Other")
                    binding.etVariety.setSelection(otherPosition)
                    isSettingSpinnerProgrammatically = false
                    ToastUtil.showShort(this, "Selected Variety: Other")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // On cancel, set to "Other" and keep spinner on "Other"
                selectedVariety = "Other"
                isSettingSpinnerProgrammatically = true
                val varietyAdapter = binding.etVariety.adapter as ArrayAdapter<String>
                val otherPosition = varietyAdapter.getPosition("Other")
                binding.etVariety.setSelection(otherPosition)
                isSettingSpinnerProgrammatically = false
                ToastUtil.showShort(this, "Selected Variety: Other")
                dialog.dismiss()
            }
            .show()
    }

    private fun showCustomCropInputDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this)
            .setTitle("Enter Crop Type")
            .setView(input)
            .setPositiveButton("OK") { dialog, _ ->
                val customCrop = input.text.toString().trim()
                if (customCrop.isNotBlank()) {
                    selectedCropType = customCrop
                    Toast.makeText(this, "Custom crop selected: $customCrop", Toast.LENGTH_SHORT)
                        .show()
                } else {
                    // If empty, set to "Other" and keep spinner on "Other"
                    selectedCropType = "Other"
                    // Keep the spinner on "Other" position
                    isSettingSpinnerProgrammatically = true
                    val cropAdapter = binding.etCropType.adapter as ArrayAdapter<String>
                    val otherPosition = cropAdapter.getPosition("Other")
                    binding.etCropType.setSelection(otherPosition)
                    isSettingSpinnerProgrammatically = false
                    ToastUtil.showShort(this, "Selected Crop Type: Other")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // On cancel, set to "Other" and keep spinner on "Other"
                selectedCropType = "Other"
                isSettingSpinnerProgrammatically = true
                val cropAdapter = binding.etCropType.adapter as ArrayAdapter<String>
                val otherPosition = cropAdapter.getPosition("Other")
                binding.etCropType.setSelection(otherPosition)
                isSettingSpinnerProgrammatically = false
                dialog.dismiss()
            }
            .show()
    }

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