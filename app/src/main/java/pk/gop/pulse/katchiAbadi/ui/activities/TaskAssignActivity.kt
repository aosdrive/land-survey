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
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.local.SurveyFormViewModel
import pk.gop.pulse.katchiAbadi.data.local.SurveyImageAdapter
import pk.gop.pulse.katchiAbadi.data.local.TaskSubmitDto
import pk.gop.pulse.katchiAbadi.data.local.UserSelectionDialog
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.databinding.ActivityTaskAssignBinding
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

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var serverApi: ServerApi

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

        binding.btnTakePhoto.setOnClickListener {
            requestCameraPermissionAndCapture()
        }

        // Check if multiple parcels
        isMultipleParcel = intent.getBooleanExtra("isMultipleParcel", false)

        if (isMultipleParcel) {
            loadMultipleParcels()
        } else {
            loadSingleParcel()
        }

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

        // Update UI to show single parcel info
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

        // Update UI to show multiple parcels

        Log.d("TaskAssign", "Loaded ${selectedParcels.size} parcels for task assignment")
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

    private fun setupSpinners() {
        val propertyTypeList = listOf("Pest Attack", "Drone Spray", "Excess Water", "Water Shortage", "Other")

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,  // Layout for the closed spinner
            propertyTypeList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)  // Layout for dropdown list

        binding.spinnerPropertyStatus.adapter = adapter
    }

    private fun setupUserListSection() {
        binding.btnSelectOwner.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

                    if (token.isEmpty()) {
                        ToastUtil.showShort(context, "Please login again.")
                        return@launch
                    }

                    binding.btnSelectOwner.isEnabled = false

                    Log.d("API_CALL", "Token: Bearer ${token.take(20)}...")

                    val response = withContext(Dispatchers.IO) {
                        serverApi.getAllUsers(
                            token = "Bearer $token",
                            roleId = 8
                        )
                    }

                    binding.btnSelectOwner.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val users = response.body()!!

                        if (users.isEmpty()) {
                            ToastUtil.showShort(context, "No users found.")
                        } else {
                            UserSelectionDialog(context, users) { selectedUserFromDialog ->
                                selectedUser = selectedUserFromDialog

                                displaySelectedUserInCard(selectedUserFromDialog)

                                Toast.makeText(
                                    context,
                                    "Selected: ${selectedUserFromDialog.fullName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("API_ERROR", "Code: ${response.code()}, Body: $errorBody")
                        ToastUtil.showShort(context, "Error: ${response.code()}")
                    }

                } catch (e: Exception) {
                    binding.btnSelectOwner.isEnabled = true
                    Log.e("API_EXCEPTION", "Error: ${e.message}", e)
                    ToastUtil.showShort(context, "Error: ${e.localizedMessage}")
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
            imagePickerLauncher.launch("image/*")
        }

        binding.btnTakePhoto.setOnClickListener {
            captureImage()
        }
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

            tempImagePath = photoFile.absolutePath

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

                    if (!tempImagePath.isNullOrEmpty()) {
                        val f = File(tempImagePath!!)
                        if (f.exists() && f.length() > 0) {
                            finalFile = f
                            Log.d("Camera", "File exists at path: ${f.absolutePath}")
                        } else {
                            Log.w("Camera", "File not found or empty: $f")
                        }
                    }

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

    private fun compressImageFile(inputFile: File, maxSizeKB: Int = 60): File? {
        try {
            Log.d("ImageCompression", "📷 Original file size: ${inputFile.length() / 1024} KB")

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            Log.d("ImageCompression", "📐 Original dimensions: ${originalWidth}x${originalHeight}")

            options.inSampleSize = calculateInSampleSize(options, 600, 600)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options) ?: return null

            Log.d("ImageCompression", "🖼️ Decoded bitmap: ${bitmap.width}x${bitmap.height}")

            var compressQuality = 75
            val stream = ByteArrayOutputStream()

            do {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, stream)
                val currentSizeKB = stream.size() / 1024

                Log.d("ImageCompression", "🔧 Quality: $compressQuality%, Size: $currentSizeKB KB")

                if (currentSizeKB <= maxSizeKB) break

                compressQuality -= 5
            } while (compressQuality > 20)

            val compressedFile = File(filesDir, "compressed_${System.currentTimeMillis()}.jpg")
            compressedFile.writeBytes(stream.toByteArray())

            bitmap.recycle()

            val finalSizeKB = compressedFile.length() / 1024
            val compressionRatio =
                ((inputFile.length() - compressedFile.length()) * 100.0 / inputFile.length())

            Log.d(
                "ImageCompression",
                "✅ Final: $finalSizeKB KB (${String.format("%.1f", compressionRatio)}% reduction)"
            )

            if (compressedFile.exists() && compressedFile.length() > 0) {
                inputFile.delete()
                return compressedFile
            }

            return null
        } catch (e: Exception) {
            Log.e("ImageCompression", "❌ Error: ${e.message}", e)
            return null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun setupSubmit() {
        binding.etDate.setOnClickListener {
            showDatePicker()
        }

        binding.etDate.isFocusable = false
        binding.etDate.isClickable = true
        binding.etDate.inputType = InputType.TYPE_NULL

        binding.btnSubmitSurvey.setOnClickListener {
            val assignDate = binding.etDate.text.toString()
            val issueType = binding.spinnerPropertyStatus.selectedItem.toString()
            val details = binding.etDetail.text.toString()
            val daysToComplete = binding.etDaysToComplete.text.toString()

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

            lifecycleScope.launch {
                try {
                    val images = viewModel.surveyImages.value ?: emptyList()
                    val base64Images = withContext(Dispatchers.IO) {
                        images.mapNotNull { convertImageToBase64(it.uri) }
                    }

                    val assignedByUserId =
                        sharedPreferences.getLong(Constants.SHARED_PREF_USER_ID, 0L)
                    val mauzaID =
                        sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L)
                    val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

                    if (token.isEmpty()) {
                        ToastUtil.showShort(context, "Please login again")
                        binding.btnSubmitSurvey.isEnabled = true
                        return@launch
                    }

                    var successCount = 0

                    for (parcel in selectedParcels) {
                        val taskDto = TaskSubmitDto(
                            assignDate = assignDate,
                            issueType = issueType,
                            detail = details,
                            images = base64Images,
                            parcelId = parcel.parcelId,
                            parcelNo = parcel.parcelNo,
                            mauzaId = mauzaID,
                            assignedByUserId = assignedByUserId,
                            assignedToUserId = selectedUser!!.id ?: 0L,
                            khewatInfo = parcel.khewatInfo,
                            daysToComplete = daysToCompleteInt
                        )

                        Log.d("TaskAssign", "Submitting task for parcel ${parcel.parcelNo}")

                        val response = withContext(Dispatchers.IO) {
                            serverApi.submitTask("Bearer $token", taskDto)
                        }

                        if (response.isSuccessful && response.body()?.success == true) {
                            val picData = images.joinToString(",") { it.uri }
                            val taskEntity = TaskEntity(
                                assignDate = assignDate,
                                issueType = issueType,
                                details = details,
                                picData = picData,
                                parcelId = parcel.parcelId,
                                parcelNo = parcel.parcelNo,
                                mauzaId = mauzaID,
                                assignedByUserId = assignedByUserId,
                                assignedToUserId = selectedUser!!.id ?: 0L,
                                khewatInfo = parcel.khewatInfo,
                                createdOn = System.currentTimeMillis(),
                                isSynced = true,
                                daysToComplete = daysToCompleteInt
                            )

                            withContext(Dispatchers.IO) {
                                database.taskDao().insertTask(taskEntity)
                            }

                            successCount++
                        } else {
                            Log.e(
                                "TaskAssign",
                                "Failed for parcel ${parcel.parcelNo}: ${response.body()?.message}"
                            )
                        }
                    }

                    ToastUtil.showShort(
                        context,
                        "Successfully assigned $successCount of ${selectedParcels.size} tasks"
                    )
                    finish()

                } catch (e: Exception) {
                    Log.e("TaskAssign", "Error: ${e.message}", e)
                    ToastUtil.showShort(context, "Error: ${e.localizedMessage}")
                } finally {
                    binding.btnSubmitSurvey.isEnabled = true
                }
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format(
                    "%04d-%02d-%02d",
                    selectedYear,
                    selectedMonth + 1,
                    selectedDay
                )
                binding.etDate.setText(formattedDate)
            },
            year,
            month,
            day
        )

        datePickerDialog.show()
    }

    private fun convertImageToBase64(imagePath: String, maxSizeKB: Int = 60): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e("ImageConversion", "❌ File not found: $imagePath")
                return null
            }

            val fileSizeKB = file.length() / 1024
            Log.d("ImageConversion", "📁 Input file: $fileSizeKB KB")

            if (fileSizeKB < maxSizeKB) {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                Log.d("ImageConversion", "✅ Base64 size: ${base64.length / 1024} KB")
                return base64
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

            Log.d("ImageConversion", "🖼️ Bitmap for Base64: ${bitmap.width}x${bitmap.height}")

            val stream = ByteArrayOutputStream()
            var quality = 70

            do {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val currentSizeKB = stream.size() / 1024
                Log.d("ImageConversion", "🔧 Quality: $quality%, Size: $currentSizeKB KB")
                quality -= 5
            } while (stream.size() / 1024 > maxSizeKB && quality > 20)

            bitmap.recycle()

            val base64 =
                android.util.Base64.encodeToString(
                    stream.toByteArray(),
                    android.util.Base64.NO_WRAP
                )
            val base64SizeKB = base64.length / 1024

            Log.d("ImageConversion", "✅ Final Base64: $base64SizeKB KB")

            return base64

        } catch (e: Exception) {
            Log.e("ImageConversion", "❌ Error: ${e.message}", e)
            null
        }
    }
}