package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.R
import android.Manifest
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.repository.DeclineRepositoryImpl
import pk.gop.pulse.katchiAbadi.databinding.ActivityDeclineCaptureBinding
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class DeclineCaptureActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var declineRepository: DeclineRepositoryImpl
    private lateinit var binding: ActivityDeclineCaptureBinding
    private val picturePaths = mutableListOf<String>()
    private var tempImagePath: String? = null
    private var tempImageUri: Uri? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    companion object { const val REQUIRED_PICTURES = 3 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeclineCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Decline Parcel"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val parcelNo = intent.getStringExtra("parcelNo").orEmpty()
        val status   = intent.getStringExtra("status").orEmpty()
        val acres    = intent.getDoubleExtra("acres", 0.0)

        binding.tvDeclineHeader.text = "Parcel $parcelNo"
        binding.tvDeclineSub.text =
            "$status · ${String.format(Locale.US, "%.2f Acres", acres)}"

        binding.btnCapture.setOnClickListener { requestPermissionsAndCapture() }
        binding.btnSaveDecline.setOnClickListener { saveDecline() }

        updateUi()
    }

    private fun updateUi() {
        binding.tvPictureCount.text =
            "${picturePaths.size} of $REQUIRED_PICTURES pictures captured"
        binding.btnCapture.isEnabled = picturePaths.size < REQUIRED_PICTURES
        binding.btnSaveDecline.isEnabled = picturePaths.size == REQUIRED_PICTURES
        binding.btnSaveDecline.alpha = if (picturePaths.size == REQUIRED_PICTURES) 1f else 0.4f

        // teen thumbnails
        listOf(binding.ivPic1, binding.ivPic2, binding.ivPic3)
            .forEachIndexed { i, iv ->
                if (i < picturePaths.size) {
                    iv.setImageURI(Uri.fromFile(File(picturePaths[i])))
                    iv.alpha = 1f
                } else {
                    iv.setImageResource(R.drawable.bg_image_placeholder)
                    iv.alpha = 0.4f
                }
            }
    }

    private fun requestPermissionsAndCapture() {
        val camOk = checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        val locOk = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        when {
            camOk && locOk -> fetchLocationThenCapture()
            !camOk -> permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            )
            else -> permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.CAMERA] == false) {
            ToastUtil.showShort(this, "Camera permission is required")
            return@registerForActivityResult
        }
        fetchLocationThenCapture()
    }

    private fun fetchLocationThenCapture() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) { capture(); return }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
        ).addOnSuccessListener { loc ->
            currentLocation = loc
            capture()
        }.addOnFailureListener { capture() }
    }

    private fun capture() {
        try {
            val photoFile = File(filesDir, "decline_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            tempImagePath = photoFile.absolutePath
            tempImageUri = FileProvider.getUriForFile(
                this, "${packageName}.fileProvider", photoFile
            )
            cameraLauncher.launch(tempImageUri!!)
        } catch (e: Exception) {
            ToastUtil.showShort(this, "Camera error: ${e.message}")
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success || tempImagePath == null) {
                ToastUtil.showShort(this, "Photo capture was cancelled")
                return@registerForActivityResult
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val f = File(tempImagePath!!)
                val finalFile = if (f.exists() && f.length() > 0) compressImageFile(f) ?: f else null
                withContext(Dispatchers.Main) {
                    if (finalFile == null) {
                        ToastUtil.showShort(this@DeclineCaptureActivity, "Failed to get photo")
                    } else {
                        picturePaths.add(finalFile.absolutePath)
                        updateUi()
                    }
                }
            }
        }

    private fun compressImageFile(inputFile: File, targetKB: Int = 300): File? = try {
        val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
        if (bitmap == null) null else {
            val stream = ByteArrayOutputStream()
            var quality = 100
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            while (stream.size() / 1024 > targetKB && quality > 10) {
                stream.reset(); quality -= 5
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            val out = File(filesDir, "compressed_${inputFile.name}")
            out.writeBytes(stream.toByteArray())
            if (out.exists()) inputFile.delete()
            out
        }
    } catch (e: Exception) { null }

    private fun saveDecline() {
        if (picturePaths.size != REQUIRED_PICTURES) {
            ZdAlert.warn(this, "Pictures Required",
                "Please capture $REQUIRED_PICTURES pictures before saving.")
            return
        }

        binding.btnSaveDecline.isEnabled = false

        val decline = ParcelDeclineEntity(
            declineGuid = UUID.randomUUID().toString(),
            mauzaId = intent.getLongExtra("mauzaId", 0L).takeIf { it > 0 }
                ?: sharedPreferences.getLong(
                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                ),
            mauzaName = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty(),
            areaName = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty(),
            serverParcelId = intent.getLongExtra("serverParcelId", 0L).takeIf { it > 0 },
            parcelNo = intent.getStringExtra("parcelNo").orEmpty(),
            subParcelNo = "",
            parcelStatus = intent.getStringExtra("status").orEmpty(),
            khewatInfo = intent.getStringExtra("khewat").orEmpty(),
            remarks = binding.etDeclineRemarks.text.toString().trim(),
            geomWKT = intent.getStringExtra("geomWkt"),
            centroid = intent.getStringExtra("centroid"),
            calculatedArea = intent.getDoubleExtra("acres", 0.0),
            latitude = currentLocation?.latitude,
            longitude = currentLocation?.longitude,
            pic1Path = picturePaths[0],
            pic2Path = picturePaths[1],
            pic3Path = picturePaths[2]
        )

        lifecycleScope.launch {
            try {
                declineRepository.saveDecline(decline)
                ToastUtil.showShort(this@DeclineCaptureActivity, "Decline saved locally")
                finish()
            } catch (e: Exception) {
                binding.btnSaveDecline.isEnabled = true
                ToastUtil.showShort(this@DeclineCaptureActivity, "Error: ${e.message}")
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}