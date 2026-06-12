package pk.gop.pulse.katchiAbadi.ui.activities

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.SurveyFormViewModel
import pk.gop.pulse.katchiAbadi.data.local.SurveyImageAdapter
import pk.gop.pulse.katchiAbadi.data.local.UserSelectionDialog
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.repository.LookupRepository
import pk.gop.pulse.katchiAbadi.data.repository.OfficerRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivityTaskAssignBinding
import pk.gop.pulse.katchiAbadi.domain.model.DiseaseTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.IssueTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.PestTypeEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import pk.gop.pulse.katchiAbadi.domain.model.UserResponse
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class TaskAssignActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTaskAssignBinding
    private lateinit var context: Context
    private lateinit var imageAdapter: SurveyImageAdapter
    private var tempImageUri: Uri? = null
    private var tempImagePath: String? = null
    private var currentImageType: String = ""

    private val viewModel: SurveyFormViewModel by viewModels()

    private var selectedUser: UserResponse? = null
    private lateinit var selectedParcels: List<ParcelData>
    private var isMultipleParcel: Boolean = false

    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var serverApi: ServerApi
    @Inject lateinit var lookupRepository: LookupRepository

    private var issueTypes: List<IssueTypeEntity> = emptyList()
    private var pestTypes: List<PestTypeEntity> = emptyList()
    private var diseaseTypes: List<DiseaseTypeEntity> = emptyList()

    private val selectedPestIds = mutableSetOf<Int>()
    private val selectedDiseaseIds = mutableSetOf<Int>()
    private var selectedIssueTypeId: Int? = null
    @Inject lateinit var officerRepository: OfficerRepository


    data class ParcelData(
        val parcelId: Long,
        val parcelNo: String,
        val subParcelNo: String,
        val area: String,
        val khewatInfo: String,
        val unitId: Long,
        val groupId: Long
    )

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
        binding = ActivityTaskAssignBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@TaskAssignActivity

        binding.btnTakePhoto.setOnClickListener { requestCameraPermissionAndCapture() }

        isMultipleParcel = intent.getBooleanExtra("isMultipleParcel", false)
        if (isMultipleParcel) loadMultipleParcels() else loadSingleParcel()

        setupSpinners()
        setupUserListSection()
        setupImageSection()
        setupSubmit()
    }

    private fun loadSingleParcel() {
        val parcelData = ParcelData(
            parcelId = intent.getLongExtra("parcelId", 0L),
            parcelNo = intent.getStringExtra("parcelNo") ?: "",
            subParcelNo = intent.getStringExtra("subParcelNo") ?: "",
            area = intent.getStringExtra("parcelArea") ?: "",
            khewatInfo = intent.getStringExtra("khewatInfo") ?: "",
            unitId = intent.getLongExtra("unitId", 0L),
            groupId = intent.getLongExtra("groupId", 0L)
        )
        selectedParcels = listOf(parcelData)
    }

    private fun loadMultipleParcels() {
        val parcelIds = intent.getLongArrayExtra("parcelIds") ?: longArrayOf()
        val parcelNos = intent.getStringArrayExtra("parcelNos") ?: arrayOf()
        val subParcelNos = intent.getStringArrayExtra("subParcelNos") ?: arrayOf()
        val areas = intent.getStringArrayExtra("areas") ?: arrayOf()
        val khewatInfos = intent.getStringArrayExtra("khewatInfos") ?: arrayOf()
        val unitIds = intent.getLongArrayExtra("unitIds") ?: longArrayOf()
        val groupIds = intent.getLongArrayExtra("groupIds") ?: longArrayOf()

        selectedParcels = parcelIds.mapIndexed { index, id ->
            ParcelData(
                parcelId = id,
                parcelNo = parcelNos.getOrNull(index) ?: "",
                subParcelNo = subParcelNos.getOrNull(index) ?: "",
                area = areas.getOrNull(index) ?: "",
                khewatInfo = khewatInfos.getOrNull(index) ?: "",
                unitId = unitIds.getOrNull(index) ?: 0L,
                groupId = groupIds.getOrNull(index) ?: 0L
            )
        }
        Log.d("TaskAssign", "Loaded ${selectedParcels.size} parcels for task assignment")
    }

    private fun requestCameraPermissionAndCapture() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) captureImage()
        else cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) captureImage()
            else ToastUtil.showShort(this, "Camera permission is required")
        }

    // ============================================================
    //   SPINNERS — issue / pest / disease
    // ============================================================
    private fun setupSpinners() {
        lifecycleScope.launch {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
            if (token.isEmpty()) {
                ToastUtil.showShort(context, "Please login again")
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    issueTypes = lookupRepository.getIssueTypes()
                    pestTypes = lookupRepository.getPestTypes()
                    diseaseTypes = lookupRepository.getDiseaseTypes()
                }
                setupIssueTypeSpinner()
                setupConditionalDropdowns()
            } catch (e: Exception) {
                Log.e("DROPDOWN_DEBUG", "Exception in setupSpinners: ${e.message}", e)
                ToastUtil.showShort(context, "Failed to load dropdown values: ${e.message}")
            }
        }
    }

    private fun setupIssueTypeSpinner() {
        if (issueTypes.isEmpty()) {
            ToastUtil.showShort(context, "No issue types available")
            return
        }
        val displayList = mutableListOf("-- Select Issue Type --")
        displayList.addAll(issueTypes.map { it.name })

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, displayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPropertyStatus.adapter = adapter

        binding.spinnerPropertyStatus.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
                ) {
                    if (position == 0) {
                        selectedIssueTypeId = null
                        binding.layoutPestType.visibility = View.GONE
                        binding.layoutDiseaseType.visibility = View.GONE
                        return
                    }
                    val selected = issueTypes[position - 1]
                    selectedIssueTypeId = selected.id

                    selectedPestIds.clear()
                    selectedDiseaseIds.clear()
                    binding.btnSelectPestType.text = "Select pest type(s)"
                    binding.btnSelectDiseaseType.text = "Select disease type(s)"

                    when (selected.name.lowercase()) {
                        "pest attack" -> {
                            binding.layoutPestType.visibility = View.VISIBLE
                            binding.layoutDiseaseType.visibility = View.GONE
                        }
                        "diseases" -> {
                            binding.layoutPestType.visibility = View.GONE
                            binding.layoutDiseaseType.visibility = View.VISIBLE
                        }
                        else -> {
                            binding.layoutPestType.visibility = View.GONE
                            binding.layoutDiseaseType.visibility = View.GONE
                        }
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun setupConditionalDropdowns() {
        binding.btnSelectPestType.setOnClickListener {
            showMultiSelectDialog(
                title = "Select Pest Type(s)",
                items = pestTypes.map { it.name },
                preSelectedIds = selectedPestIds,
                allIds = pestTypes.map { it.id }
            ) { chosenIds ->
                selectedPestIds.clear()
                selectedPestIds.addAll(chosenIds)
                val names = pestTypes.filter { it.id in selectedPestIds }
                    .joinToString(", ") { it.name }
                binding.btnSelectPestType.text = names.ifEmpty { "Select pest type(s)" }
            }
        }
        binding.btnSelectDiseaseType.setOnClickListener {
            showMultiSelectDialog(
                title = "Select Disease Type(s)",
                items = diseaseTypes.map { it.name },
                preSelectedIds = selectedDiseaseIds,
                allIds = diseaseTypes.map { it.id }
            ) { chosenIds ->
                selectedDiseaseIds.clear()
                selectedDiseaseIds.addAll(chosenIds)
                val names = diseaseTypes.filter { it.id in selectedDiseaseIds }
                    .joinToString(", ") { it.name }
                binding.btnSelectDiseaseType.text = names.ifEmpty { "Select disease type(s)" }
            }
        }
    }

    private fun showMultiSelectDialog(
        title: String, items: List<String>, preSelectedIds: Set<Int>,
        allIds: List<Int>, onConfirm: (Set<Int>) -> Unit
    ) {
        if (items.isEmpty()) {
            ToastUtil.showShort(context, "No options available")
            return
        }
        val checkedItems = BooleanArray(items.size) { allIds[it] in preSelectedIds }
        val tempSelection = checkedItems.copyOf()

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(title)
            .setMultiChoiceItems(items.toTypedArray(), checkedItems) { _, which, isChecked ->
                tempSelection[which] = isChecked
            }
            .setPositiveButton("OK") { dialog, _ ->
                val chosen = mutableSetOf<Int>()
                tempSelection.forEachIndexed { idx, picked -> if (picked) chosen.add(allIds[idx]) }
                onConfirm(chosen)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ============================================================
    //   USER LIST  (still needs network — see note at bottom of file)
    // ============================================================
    private fun setupUserListSection() {
        binding.btnSelectOwner.setOnClickListener {
            lifecycleScope.launch {
                try {
                    binding.btnSelectOwner.isEnabled = false

                    // 1) Try to refresh from network in the background — fine if it fails
                    if (Utility.checkInternetConnection(context)) {
                        withContext(Dispatchers.IO) {
                            officerRepository.refreshFromServer()
                        }
                    }

                    // 2) Always read from local cache (offline-first)
                    val users = withContext(Dispatchers.IO) {
                        officerRepository.getOfficers()
                    }

                    binding.btnSelectOwner.isEnabled = true

                    if (users.isEmpty()) {
                        ToastUtil.showShort(
                            context,
                            "No officers cached. Please connect to internet once to download the list."
                        )
                        return@launch
                    }

                    UserSelectionDialog(context, users) { selectedUserFromDialog ->
                        selectedUser = selectedUserFromDialog
                        displaySelectedUserInCard(selectedUserFromDialog)
                        Toast.makeText(
                            context,
                            "Selected: ${selectedUserFromDialog.fullName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.show()

                } catch (e: Exception) {
                    binding.btnSelectOwner.isEnabled = true
                    Log.e("OfficerLoad", "Error: ${e.message}", e)
                    ToastUtil.showShort(context, "Error loading officers: ${e.localizedMessage}")
                }
            }
        }
    }


    private fun displaySelectedUserInCard(user: UserResponse) {
        binding.cardSelectedUser.visibility = View.VISIBLE
        binding.tvSelectedUserName.text = user.fullName ?: "N/A"
        binding.tvSelectedUserCnic.text = "CNIC: ${user.cnic ?: "N/A"}"
        binding.tvSelectedUserRole.text = "Role: ${user.roleName ?: "Officer"}"
        binding.btnSelectOwner.text = "Change Officer"
        binding.btnRemoveSelectedUser.setOnClickListener {
            selectedUser = null
            binding.cardSelectedUser.visibility = View.GONE
            binding.btnSelectOwner.text = "Select from List"
        }
    }

    // ============================================================
    //   IMAGE SECTION
    // ============================================================
    private fun setupImageSection() {
        imageAdapter = SurveyImageAdapter { imageToRemove ->
            viewModel.removeImage(imageToRemove)
            imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
        }
        binding.recyclerPictures.apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = imageAdapter
        }
        binding.btnAddImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnTakePhoto.setOnClickListener { captureImage() }
    }

    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val savedPath = savePickedImageToInternalStorage(uri)
                val image = SurveyImage(uri = savedPath, type = currentImageType)
                viewModel.addImage(image)
                imageAdapter.submitList(viewModel.surveyImages.value!!.toList())
            }
        }

    private fun savePickedImageToInternalStorage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("Can't open image stream")
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)
        inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        return file.absolutePath
    }

    private fun captureImage() {
        try {
            val timestamp = System.currentTimeMillis()
            val photoFile = File(filesDir, "survey_img_${timestamp}.jpg")
            tempImagePath = photoFile.absolutePath
            photoFile.parentFile?.mkdirs()
            tempImageUri = FileProvider.getUriForFile(
                this, "${packageName}.fileProvider", photoFile
            )
            cameraLauncher.launch(tempImageUri!!)
        } catch (e: Exception) {
            Log.e("Camera", "Error setting up camera: ${e.message}", e)
            ToastUtil.showShort(context, "Error setting up camera: ${e.message}")
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success) {
                ToastUtil.showShort(context, "Photo capture was cancelled")
                return@registerForActivityResult
            }
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    var finalFile: File? = null
                    if (!tempImagePath.isNullOrEmpty()) {
                        val f = File(tempImagePath!!)
                        if (f.exists() && f.length() > 0) finalFile = f
                    }
                    if (finalFile == null && tempImageUri != null) {
                        val input = contentResolver.openInputStream(tempImageUri!!)
                        if (input != null) {
                            val fallbackFile =
                                File(filesDir, "fallback_${System.currentTimeMillis()}.jpg")
                            fallbackFile.outputStream().use { output -> input.copyTo(output) }
                            if (fallbackFile.exists() && fallbackFile.length() > 0)
                                finalFile = fallbackFile
                        }
                    }
                    if (finalFile == null) {
                        withContext(Dispatchers.Main) {
                            ToastUtil.showShort(context, "Failed to get photo")
                        }
                        return@launch
                    }

                    val compressedFile = compressImageFile(finalFile, 300) ?: finalFile

                    withContext(Dispatchers.Main) {
                        val image = SurveyImage(
                            uri = compressedFile.absolutePath, type = currentImageType
                        )
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

    private fun compressImageFile(inputFile: File, maxSizeKB: Int = 60): File? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)

            options.inSampleSize = calculateInSampleSize(options, 600, 600)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options) ?: return null
            var compressQuality = 75
            val stream = ByteArrayOutputStream()
            do {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, stream)
                if (stream.size() / 1024 <= maxSizeKB) break
                compressQuality -= 5
            } while (compressQuality > 20)

            val compressedFile = File(filesDir, "compressed_${System.currentTimeMillis()}.jpg")
            compressedFile.writeBytes(stream.toByteArray())
            bitmap.recycle()

            if (compressedFile.exists() && compressedFile.length() > 0) {
                inputFile.delete()
                return compressedFile
            }
            return null
        } catch (e: Exception) {
            Log.e("ImageCompression", "Error: ${e.message}", e)
            return null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) inSampleSize *= 2
        }
        return inSampleSize
    }

    // ============================================================
    //   SUBMIT  — OFFLINE FIRST: save to Room only, NO network call
    // ============================================================
    private fun setupSubmit() {
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.etDate.isFocusable = false
        binding.etDate.isClickable = true
        binding.etDate.inputType = InputType.TYPE_NULL

        binding.btnSubmitSurvey.setOnClickListener {
            val assignDate = binding.etDate.text.toString()
            val details = binding.etDetail.text.toString()
            val daysToComplete = binding.etDaysToComplete.text.toString()

            // ---- VALIDATION ----
            if (selectedIssueTypeId == null) {
                ToastUtil.showShort(context, "Please select an issue type")
                return@setOnClickListener
            }
            val selectedIssueName = issueTypes.find { it.id == selectedIssueTypeId }?.name ?: ""
            when (selectedIssueName.lowercase()) {
                "pest attack" -> if (selectedPestIds.isEmpty()) {
                    ToastUtil.showShort(context, "Please select at least one pest type")
                    return@setOnClickListener
                }
                "diseases" -> if (selectedDiseaseIds.isEmpty()) {
                    ToastUtil.showShort(context, "Please select at least one disease type")
                    return@setOnClickListener
                }
            }
            if (selectedUser == null) {
                ToastUtil.showShort(context, "Please select a user to assign the task")
                return@setOnClickListener
            }
            if (assignDate.isEmpty()) {
                ToastUtil.showShort(context, "Please select assign date")
                return@setOnClickListener
            }
            if (daysToComplete.isEmpty()) {
                ToastUtil.showShort(context, "Please enter days to complete")
                return@setOnClickListener
            }
            val daysToCompleteInt = daysToComplete.toIntOrNull()
            if (daysToCompleteInt == null || daysToCompleteInt <= 0) {
                ToastUtil.showShort(context, "Please enter a valid number of days")
                return@setOnClickListener
            }

            binding.btnSubmitSurvey.isEnabled = false

            // ---- LOCAL SAVE ONLY (no network call) ----
            lifecycleScope.launch {
                try {
                    val assignedByUserId =
                        sharedPreferences.getLong(Constants.SHARED_PREF_USER_ID, 0L)
                    val mauzaID = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L
                    )

                    val images = viewModel.surveyImages.value ?: emptyList()
                    // Store ONLY file paths locally. Base64 conversion is deferred to upload time.
                    val picData = images.joinToString(",") { it.uri }

                    var insertedCount = 0
                    withContext(Dispatchers.IO) {
                        for (parcel in selectedParcels) {
                            val taskEntity = TaskEntity(
                                assignDate = assignDate,
                                issueType = selectedIssueName,
                                details = details,
                                picData = picData,
                                parcelId = parcel.parcelId,
                                parcelNo = parcel.parcelNo,
                                mauzaId = mauzaID,
                                assignedByUserId = assignedByUserId,
                                assignedToUserId = selectedUser!!.id ?: 0L,
                                khewatInfo = parcel.khewatInfo,
                                createdOn = System.currentTimeMillis(),
                                isSynced = false,     // ⚠️ Key change — never synced at insert time
                                daysToComplete = daysToCompleteInt,
                                issueTypeId = selectedIssueTypeId,
                                pestTypeIds = if (selectedPestIds.isNotEmpty())
                                    selectedPestIds.joinToString(",") else null,
                                diseaseTypeIds = if (selectedDiseaseIds.isNotEmpty())
                                    selectedDiseaseIds.joinToString(",") else null,
                            )
                            database.taskDao().insertTask(taskEntity)
                            insertedCount++
                        }
                    }

                    ToastUtil.showShort(
                        context,
                        "Saved $insertedCount task(s) locally. Upload when online."
                    )
                    finish()
                } catch (e: Exception) {
                    Log.e("TaskAssign", "Error saving task: ${e.message}", e)
                    ToastUtil.showShort(context, "Error: ${e.localizedMessage}")
                    binding.btnSubmitSurvey.isEnabled = true
                }
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            context,
            { _, y, m, d ->
                val formattedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                binding.etDate.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }
}