package pk.gop.pulse.katchiAbadi.ui.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.request.OnboardingUploadDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import javax.inject.Inject

@AndroidEntryPoint
class FarmerActivity : AppCompatActivity() {

    @Inject
    lateinit var api: ServerApi

    // UI Components
    private lateinit var etCnic: TextInputEditText
    private lateinit var etContact: TextInputEditText
//    private lateinit var etAddress: TextInputEditText
    private lateinit var btnCaptureImage: MaterialButton
    private lateinit var btnCaptureThumbprint: MaterialButton
    private lateinit var btnSave: MaterialButton

    private lateinit var ivCapturedImage: ImageView
    private lateinit var layoutImageEmptyState: LinearLayout

    private lateinit var layoutThumbprintEmptyState: LinearLayout
    private lateinit var layoutThumbprintStatus: LinearLayout
    private lateinit var tvThumbprintStatus: TextView
    private lateinit var viewThumbprintStatusDot: View

    // Data Variables
    private var capturedImageBitmap: Bitmap? = null
    private var capturedImageUri: Uri? = null
    private var isThumbprintVerified: Boolean = false

    // Biometric
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // Constants
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val REQUEST_IMAGE_TYPE = 1
    }

    // Activity Result Launchers
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                handleCapturedImage(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_farmer)

        initializeViews()
        setupBiometric()
        checkBiometricSupport()
        setupListeners()
        setupTextWatchers()
    }

    private fun initializeViews() {
        // Input Fields
        etCnic = findViewById(R.id.etCnic)
        etContact = findViewById(R.id.etContact)
//        etAddress = findViewById(R.id.etAddress)

        // Buttons
        btnCaptureImage = findViewById(R.id.btnCaptureImage)
        btnCaptureThumbprint = findViewById(R.id.btnCaptureThumbprint)
        btnSave = findViewById(R.id.btnSave)

        // Image Views and Layouts
        ivCapturedImage = findViewById(R.id.ivCapturedImage)
        layoutImageEmptyState = findViewById(R.id.layoutImageEmptyState)

        layoutThumbprintEmptyState = findViewById(R.id.layoutThumbprintEmptyState)
        layoutThumbprintStatus = findViewById(R.id.layoutThumbprintStatus)
        tvThumbprintStatus = findViewById(R.id.tvThumbprintStatus)
        viewThumbprintStatusDot = findViewById(R.id.viewThumbprintStatusDot)
    }

    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(
                        applicationContext,
                        "Authentication error: $errString",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateThumbprintStatus(false, "Failed")
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(
                        applicationContext,
                        "Fingerprint verified successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    isThumbprintVerified = true
                    updateThumbprintStatus(true, "Verified")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        applicationContext,
                        "Authentication failed. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Verification")
            .setSubtitle("Place your finger on the sensor")
            .setDescription("Touch the fingerprint sensor to verify your identity")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun checkBiometricSupport() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Biometric features are available
                btnCaptureThumbprint.isEnabled = true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(
                    this,
                    "No biometric hardware available on this device",
                    Toast.LENGTH_LONG
                ).show()
                btnCaptureThumbprint.isEnabled = false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(
                    this,
                    "Biometric hardware is currently unavailable",
                    Toast.LENGTH_LONG
                ).show()
                btnCaptureThumbprint.isEnabled = false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // User hasn't enrolled any biometrics
                AlertDialog.Builder(this)
                    .setTitle("No Fingerprint Enrolled")
                    .setMessage("You need to enroll at least one fingerprint in your device settings to use this feature.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        // Open fingerprint enrollment settings
                        startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                btnCaptureThumbprint.isEnabled = false
            }
        }
    }

    private fun updateThumbprintStatus(verified: Boolean, statusText: String) {
        layoutThumbprintEmptyState.visibility = View.GONE
        layoutThumbprintStatus.visibility = View.VISIBLE
        tvThumbprintStatus.text = statusText

        if (verified) {
            tvThumbprintStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            // Update status dot color if needed
        } else {
            tvThumbprintStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
    }

    private fun setupListeners() {
        btnCaptureImage.setOnClickListener {
            checkCameraPermissionAndCapture(REQUEST_IMAGE_TYPE)
        }

        btnCaptureThumbprint.setOnClickListener {
            // Launch biometric prompt
            biometricPrompt.authenticate(promptInfo)
        }

        btnSave.setOnClickListener {
            validateAndSave()
        }
    }

    private fun setupTextWatchers() {
        // CNIC Formatter: Auto-format as XXXXX-XXXXXXX-X
        etCnic.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            private var deletingHyphen = false
            private var hyphenStart = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (count == 1 && after == 0) {
                    val text = s.toString()
                    if (start < text.length && (text[start] == '-')) {
                        deletingHyphen = true
                        hyphenStart = start
                    }
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                val input = s.toString().replace("-", "")
                val formatted = StringBuilder()

                for (i in input.indices) {
                    if (i == 5 || i == 12) {
                        formatted.append("-")
                    }
                    formatted.append(input[i])
                }

                etCnic.removeTextChangedListener(this)
                etCnic.setText(formatted.toString())

                if (deletingHyphen) {
                    etCnic.setSelection(hyphenStart)
                    deletingHyphen = false
                } else {
                    etCnic.setSelection(formatted.length)
                }

                etCnic.addTextChangedListener(this)
                isFormatting = false
            }
        })

        // Contact Number: Limit to 10 digits (after +92)
        etContact.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Just ensure it doesn't exceed 11 characters
                if (s != null && s.length > 11) {
                    s.delete(11, s.length)
                }
            }
        })
    }

    private fun checkCameraPermissionAndCapture(requestType: Int) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                openCamera(requestType)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> {
                // Show rationale and request permission
                showPermissionRationale(requestType)
            }
            else -> {
                // Request permission directly
                requestCameraPermission(requestType)
            }
        }
    }

    private fun showPermissionRationale(requestType: Int) {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app needs camera access to capture images and thumbprints. Please grant the permission.")
            .setPositiveButton("OK") { _, _ ->
                requestCameraPermission(requestType)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestCameraPermission(requestType: Int) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE + requestType
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE + REQUEST_IMAGE_TYPE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera(REQUEST_IMAGE_TYPE)
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openCamera(requestType: Int) {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        when (requestType) {
            REQUEST_IMAGE_TYPE -> cameraLauncher.launch(cameraIntent)
        }
    }

    private fun handleCapturedImage(bitmap: Bitmap) {
        capturedImageBitmap = bitmap

        // Show image and hide empty state
        ivCapturedImage.setImageBitmap(bitmap)
        ivCapturedImage.visibility = View.VISIBLE
        layoutImageEmptyState.visibility = View.GONE

        // Optional: Save to file
        capturedImageUri = saveBitmapToFile(bitmap, "captured_image_${System.currentTimeMillis()}.jpg")

        Toast.makeText(this, "Image captured successfully", Toast.LENGTH_SHORT).show()
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val file = File(getExternalFilesDir(null), fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun validateAndSave() {
        val cnic = etCnic.text.toString().trim()
        val contact = etContact.text.toString().trim()
//        val address = etAddress.text.toString().trim()

        // Validation
        var isValid = true
        val errors = mutableListOf<String>()

        if (cnic.isEmpty()) {
            etCnic.error = "CNIC is required"
            isValid = false
            errors.add("CNIC")
        } else if (cnic.replace("-", "").length != 13) {
            etCnic.error = "CNIC must be 13 digits"
            isValid = false
            errors.add("Valid CNIC")
        }

        if (contact.isEmpty()) {
            etContact.error = "Contact number is required"
            isValid = false
            errors.add("Contact Number")
        } else if (contact.length < 10) {
            etContact.error = "Contact number must be at least 10 digits"
            isValid = false
            errors.add("Valid Contact Number")
        }

//        if (address.isEmpty()) {
//            etAddress.error = "Address is required"
//            isValid = false
//            errors.add("Address")
//        } else if (address.length < 10) {
//            etAddress.error = "Address must be at least 10 characters"
//            isValid = false
//            errors.add("Valid Address")
//        }

        if (capturedImageBitmap == null) {
            isValid = false
            errors.add("Photo")
        }

        if (!isThumbprintVerified) {
            isValid = false
            errors.add("Fingerprint Verification")
        }

        if (!isValid) {
            val errorMessage = "Please provide: ${errors.joinToString(", ")}"
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            return
        }

        // All validation passed - proceed with save
        saveRegistration(cnic, contact)
    }

    private fun saveRegistration(cnic: String, contact: String) {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Saving").setMessage("Please wait...")
            .setCancelable(false).create()
        loadingDialog.show()

        val imageBase64 = capturedImageBitmap?.let {
            val stream = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        val dto = OnboardingUploadDto(
            cnic = cnic,
            contact = contact,
            imageBase64 = imageBase64,
            fingerprintStatus = if (isThumbprintVerified) "VERIFIED" else "NOT_VERIFIED"
        )

        lifecycleScope.launch {
            try {
                val response = api.uploadOnboarding(dto)
                loadingDialog.dismiss()

                if (response.isSuccessful) {
                    AlertDialog.Builder(this@FarmerActivity)
                        .setTitle("Success")
                        .setMessage("Registered successfully!")
                        .setPositiveButton("OK") { _, _ -> clearForm() }
                        .show()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API_ERROR", "Code: ${response.code()}, Body: $errorBody")
                    val msg = when (response.code()) {
                        409 -> "CNIC already registered"
                        400 -> "Invalid data"
                        else -> "Server error: ${response.code()} - $errorBody"
                    }
                    Toast.makeText(this@FarmerActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(this@FarmerActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun clearForm() {
        // Clear input fields
        etCnic.text?.clear()
        etContact.text?.clear()
//        etAddress.text?.clear()

        // Reset image capture
        capturedImageBitmap = null
        capturedImageUri = null
        ivCapturedImage.visibility = View.GONE
        layoutImageEmptyState.visibility = View.VISIBLE

        // Reset fingerprint verification
        isThumbprintVerified = false
        layoutThumbprintEmptyState.visibility = View.VISIBLE
        layoutThumbprintStatus.visibility = View.GONE

        Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show()
    }

    // Optional: Data class for storing registration data
    data class RegistrationData(
        val cnic: String,
        val contact: String,
        val imagePath: String?,
        val fingerprintVerified: Boolean,
        val timestamp: Long
    )
}