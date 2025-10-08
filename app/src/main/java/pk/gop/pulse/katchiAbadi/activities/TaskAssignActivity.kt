package pk.gop.pulse.katchiAbadi.activities

import OwnerSelectionDialog
import android.app.DatePickerDialog
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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import pk.gop.pulse.katchiAbadi.data.local.TaskSubmitDto
import pk.gop.pulse.katchiAbadi.data.local.UserSelectionDialog
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.databinding.ActivitySurveyNewBinding
import pk.gop.pulse.katchiAbadi.databinding.ActivityTaskAssignBinding
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.TaskEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyLogEntity
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
    private lateinit var personEntryHelper: PersonEntryHelper
    private lateinit var imageAdapter: SurveyImageAdapter
    private var tempImageUri: Uri? = null
    private var tempImagePath: String? = null
    private var currentImageType: String = ""

    private val viewModel: SurveyFormViewModel by viewModels()

    private var selectedUser: UserResponse? = null

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
        binding = ActivityTaskAssignBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@TaskAssignActivity

        if (!sharedPreferences.getBoolean("sample_persons_inserted", false)) {
            //  insertSamplePersonsOnce()
        }

        binding.btnTakePhoto.setOnClickListener {
            requestCameraPermissionAndCapture()
        }

        val parcelId = intent.getLongExtra("parcelId", 0L)
        val parcelNo = intent.getStringExtra("parcelNo") ?: ""
        val subParcelNo = intent.getStringExtra("subParcelNo") ?: ""
        val parcelArea = intent.getStringExtra("parcelArea") ?: ""
        val khewatInfo = intent.getStringExtra("khewatInfo") ?: ""
        val parcelOperation = intent.getStringExtra("parcelOperation") ?: ""
        val parcelOperationValue = intent.getStringExtra("parcelOperationValue") ?: ""

        val parcelInfoText = SpannableStringBuilder()

        val parcelLabel = SpannableString("P/N:")
        parcelLabel.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            parcelLabel.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        parcelInfoText.append(parcelLabel)

        parcelInfoText.append("$parcelNo/$subParcelNo\t\t GC= $khewatInfo \t\tArea: $parcelArea\t\tID: $parcelId\t\tOperation: $parcelOperation\t\tparcelOperationValue: $parcelOperationValue")

        binding.tvParcelInfo.text = parcelInfoText

        findViewById<TextView>(R.id.tvParcelInfo).text = parcelInfoText


        setupSpinners()
        setupUserListSection()
        setupImageSection()
        setupSubmit(parcelId, parcelNo.toString(), subParcelNo)
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

    private fun setupSpinners() {
        val propertyTypeList = listOf("Pest Attack","Drone Spray","Excess Water","Water Shortage", "Other")
        binding.spinnerPropertyStatus.adapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, propertyTypeList)

    }
    private fun setupUserListSection() {
        personEntryHelper = PersonEntryHelper(context, binding.layoutPersonEntries)

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
                                // Store the selected user
                                selectedUser = selectedUserFromDialog

                                // Display the selected user
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
        // Show the selected user card
        binding.cardSelectedUser.visibility = View.VISIBLE

        // Update the card content
        binding.tvSelectedUserName.text = user.fullName ?: "N/A"
        binding.tvSelectedUserCnic.text = "CNIC: ${user.cnic ?: "N/A"}"
        binding.tvSelectedUserRole.text = "Role: ${user.roleName ?: "Officer"}"

        // Update button text
        binding.btnSelectOwner.text = "Change Officer"

        // Add remove button functionality
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

            // More aggressive: max 600x600 pixels
            options.inSampleSize = calculateInSampleSize(options, 600, 600)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options) ?: return null

            Log.d("ImageCompression", "🖼️ Decoded bitmap: ${bitmap.width}x${bitmap.height}")

            var compressQuality = 75 // Start lower
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
            val compressionRatio = ((inputFile.length() - compressedFile.length()) * 100.0 / inputFile.length())

            Log.d("ImageCompression", "✅ Final: $finalSizeKB KB (${String.format("%.1f", compressionRatio)}% reduction)")

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

    // Helper function to calculate sample size
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

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
    private fun setupSubmit(parcelId: Long, parcelNo: String, subParcelNo: String) {

        // Setup date picker for etDate
        binding.etDate.setOnClickListener {
            showDatePicker()
        }

        // Make EditText non-editable but clickable
        binding.etDate.isFocusable = false
        binding.etDate.isClickable = true
        binding.etDate.inputType = InputType.TYPE_NULL


        binding.btnSubmitSurvey.setOnClickListener {
            val assignDate = binding.etDate.text.toString()
            val issueType = binding.spinnerPropertyStatus.selectedItem.toString()
            val details = binding.etDetail.text.toString()



            if (selectedUser == null) {
                ToastUtil.showShort(context, "Please select a user to assign the task")
                return@setOnClickListener
            }

            val assignedByUserId = sharedPreferences.getLong(Constants.SHARED_PREF_USER_ID, 0L)
            val mauzaID = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L)

            binding.btnSubmitSurvey.isEnabled = false


            lifecycleScope.launch {
                try {
                    // Convert images to base64
                    val images = viewModel.surveyImages.value ?: emptyList()
                    val base64Images = withContext(Dispatchers.IO) {
                        images.mapNotNull { image ->
                            convertImageToBase64(image.uri)
                        }
                    }

                    // Create DTO for API
                    val taskDto = TaskSubmitDto(
                        assignDate = assignDate,
                        issueType = issueType,
                        detail = details,
                        images = base64Images,
                        parcelId = parcelId,
                        parcelNo = parcelNo,
                        mauzaId = mauzaID,
                        assignedByUserId = assignedByUserId,
                        assignedToUserId = selectedUser!!.id ?: 0L
                    )

                    Log.d("TaskAssign", "========== TASK DTO INFO ==========")
                    Log.d("TaskAssign", "Assign Date: $assignDate")
                    Log.d("TaskAssign", "Issue Type: $issueType")
                    Log.d("TaskAssign", "Details: $details")
                    Log.d("TaskAssign", "Parcel ID: $parcelId")
                    Log.d("TaskAssign", "Parcel No: $parcelNo")
                    Log.d("TaskAssign", "Mauza ID: $mauzaID")
                    Log.d("TaskAssign", "Assigned By User ID: $assignedByUserId")
                    Log.d("TaskAssign", "Assigned To User ID: ${selectedUser!!.id}")
                    Log.d("TaskAssign", "Number of Images: ${base64Images.size}")
                    Log.d("TaskAssign", "Full DTO: $taskDto")
                    Log.d("TaskAssign", "===================================")

                    // Submit to server
                    val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                    if (token.isEmpty()) {
                        ToastUtil.showShort(context, "Please login again")
                        binding.btnSubmitSurvey.isEnabled = true
                        return@launch
                    }

                    val response = withContext(Dispatchers.IO) {
                        serverApi.submitTask("Bearer $token", taskDto)
                    }

//                    Log.d("TaskAssign", "Response code: ${response.code()}")
//                    Log.d("TaskAssign", "Response success: ${response.isSuccessful}")
//                    Log.d("TaskAssign", "Response body: ${response.body()}")
//                    Log.d("TaskAssign", "Error body: ${response.errorBody()?.string()}")

                    if (response.isSuccessful && response.body()?.success == true) {
                        // Save to Room DB for offline reference
                        val picData = images.joinToString(",") { it.uri }
                        val taskEntity = TaskEntity(
                            assignDate = assignDate,
                            issueType = issueType,
                            details = details,
                            picData = picData,
                            parcelId = parcelId,
                            parcelNo = parcelNo,
                            mauzaId = mauzaID,
                            assignedByUserId = assignedByUserId,
                            assignedToUserId = selectedUser!!.id ?: 0L,
                            createdOn = System.currentTimeMillis(),
                            isSynced = true
                        )

                        withContext(Dispatchers.IO) {
                            database.taskDao().insertTask(taskEntity)
                        }

                        ToastUtil.showShort(context, "Task assigned successfully!")
                        finish()
                    } else {
                        val errorMsg = response.body()?.message ?: "Failed to assign task"
                        Log.e("TaskAssign", "Full error: $errorMsg")
                        ToastUtil.showShort(context, errorMsg)
                    }

                } catch (e: Exception) {
                    Log.e("TaskAssign", "Error: ${e.message}", e)
                    ToastUtil.showShort(context, "Error: ${e.localizedMessage}")
                } finally {
                    binding.btnSubmitSurvey.isEnabled = true
                }
            }
        }
    }

    // Add this function to show the date picker
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            context,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Format date as yyyy-MM-dd
                val formattedDate = String.format(
                    "%04d-%02d-%02d",
                    selectedYear,
                    selectedMonth + 1, // Month is 0-indexed
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

    // 4. Add helper function to convert image to base64
    private fun convertImageToBase64(imagePath: String, maxSizeKB: Int = 60): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e("ImageConversion", "❌ File not found: $imagePath")
                return null
            }

            val fileSizeKB = file.length() / 1024
            Log.d("ImageConversion", "📁 Input file: $fileSizeKB KB")

            // If file is already small enough, just encode it
            if (fileSizeKB < maxSizeKB) {
                val bytes = file.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                Log.d("ImageConversion", "✅ Base64 size: ${base64.length / 1024} KB")
                return base64
            }

            // Further compress for Base64
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

            val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            val base64SizeKB = base64.length / 1024

            Log.d("ImageConversion", "✅ Final Base64: $base64SizeKB KB")

            return base64

        } catch (e: Exception) {
            Log.e("ImageConversion", "❌ Error: ${e.message}", e)
            null
        }
    }
    private fun loadSharedMouzaData() {
        val mauzaName = sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0)
        val areaName =
            sharedPreferences.getString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, "")
        ToastUtil.showShort(context, "MauzaID: $mauzaName ($areaName)")
    }

}