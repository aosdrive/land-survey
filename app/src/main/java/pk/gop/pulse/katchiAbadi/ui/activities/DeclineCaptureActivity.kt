package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import pk.gop.pulse.katchiAbadi.data.repository.ParcelActivityLogger
import pk.gop.pulse.katchiAbadi.databinding.ActivityDeclineCaptureBinding
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAction
import pk.gop.pulse.katchiAbadi.domain.model.ParcelDeclineEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Captures three proof pictures plus remarks and saves one `parcel_declines` row per parcel.
 *
 * Launched either with the single-parcel extras ([EXTRA_SERVER_PARCEL_ID], [EXTRA_PARCEL_NO], ...)
 * or, with [EXTRA_IS_MULTIPLE] = true, with the parallel arrays ([EXTRA_SERVER_PARCEL_IDS],
 * [EXTRA_PARCEL_NOS], ...). In multi mode every parcel gets its own row and its own GUID, while the
 * remarks, location and the three picture paths are shared by all rows.
 */
@AndroidEntryPoint
class DeclineCaptureActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var declineRepository: DeclineRepositoryImpl
    @Inject lateinit var logger: ParcelActivityLogger
    private lateinit var binding: ActivityDeclineCaptureBinding
    private val picturePaths = mutableListOf<String>()
    private var tempImagePath: String? = null
    private var tempImageUri: Uri? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    /** One parcel to decline, read from the intent (single extras or one slot of the arrays). */
    private data class DeclineTarget(
        val serverParcelId: Long?,
        val mauzaId: Long,
        val parcelNo: String,
        val khewat: String,
        val status: String,
        val acres: Double,
        val geomWkt: String?,
        val centroid: String?,
        val areaName: String,
        /** "" for a whole parcel; "1", "2_1"... for a server sub-parcel. */
        val subParcelNo: String = ""
    ) {
        val label get() = if (subParcelNo.isBlank()) parcelNo else "$parcelNo/$subParcelNo"
    }

    private var targets: List<DeclineTarget> = emptyList()

    companion object {
        const val REQUIRED_PICTURES = 3

        // Single-parcel extras
        const val EXTRA_SERVER_PARCEL_ID = "serverParcelId"   // Long
        const val EXTRA_MAUZA_ID         = "mauzaId"          // Long
        const val EXTRA_PARCEL_NO        = "parcelNo"         // String
        const val EXTRA_KHEWAT           = "khewat"           // String
        const val EXTRA_STATUS           = "status"           // String
        const val EXTRA_ACRES            = "acres"            // Double
        const val EXTRA_GEOM_WKT         = "geomWkt"          // String
        const val EXTRA_CENTROID         = "centroid"         // String
        const val EXTRA_AREA_NAME        = "areaName"         // String, optional
        const val EXTRA_SUB_PARCEL_NO    = "subParcelNo"      // String, optional ("" = whole parcel)

        // Multi-parcel extras (parallel arrays, all the same length)
        const val EXTRA_IS_MULTIPLE       = "isMultipleParcel" // Boolean
        const val EXTRA_SERVER_PARCEL_IDS = "serverParcelIds"  // LongArray
        const val EXTRA_MAUZA_IDS         = "mauzaIds"         // LongArray
        const val EXTRA_PARCEL_NOS        = "parcelNos"        // Array<String>
        const val EXTRA_KHEWATS           = "khewats"          // Array<String>
        const val EXTRA_STATUSES          = "statuses"         // Array<String>
        const val EXTRA_ACRES_LIST        = "acresList"        // DoubleArray
        const val EXTRA_GEOM_WKTS         = "geomWkts"         // Array<String>
        const val EXTRA_CENTROIDS         = "centroids"        // Array<String>
        const val EXTRA_AREA_NAMES        = "areaNames"        // Array<String>
        const val EXTRA_SUB_PARCEL_NOS    = "subParcelNos"     // Array<String>, optional

        /** Parcel numbers shown in the header before the list is cut off with "+N more". */
        private const val MAX_LISTED_PARCEL_NOS = 15
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeclineCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targets = readTargets()

        supportActionBar?.title = if (targets.size > 1) "Decline Parcels" else "Decline Parcel"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        bindHeader()

        binding.btnCapture.setOnClickListener { requestPermissionsAndCapture() }
        binding.btnSaveDecline.setOnClickListener { saveDecline() }

        updateUi()
    }

    /** Builds the parcel list from the multi-mode arrays, or from the single-parcel extras. */
    private fun readTargets(): List<DeclineTarget> {
        if (intent.getBooleanExtra(EXTRA_IS_MULTIPLE, false)) {
            val ids       = intent.getLongArrayExtra(EXTRA_SERVER_PARCEL_IDS)
            val mauzaIds  = intent.getLongArrayExtra(EXTRA_MAUZA_IDS)
            val parcelNos = intent.getStringArrayExtra(EXTRA_PARCEL_NOS)
            val khewats   = intent.getStringArrayExtra(EXTRA_KHEWATS)
            val statuses  = intent.getStringArrayExtra(EXTRA_STATUSES)
            val acresList = intent.getDoubleArrayExtra(EXTRA_ACRES_LIST)
            val geomWkts  = intent.getStringArrayExtra(EXTRA_GEOM_WKTS)
            val centroids = intent.getStringArrayExtra(EXTRA_CENTROIDS)
            val areaNames = intent.getStringArrayExtra(EXTRA_AREA_NAMES)
            val subNos    = intent.getStringArrayExtra(EXTRA_SUB_PARCEL_NOS)

            val count = ids?.size ?: parcelNos?.size ?: 0
            val list = (0 until count).map { i ->
                DeclineTarget(
                    serverParcelId = ids?.getOrNull(i)?.takeIf { it > 0 },
                    mauzaId        = mauzaIds?.getOrNull(i) ?: 0L,
                    parcelNo       = parcelNos?.getOrNull(i).orEmpty(),
                    khewat         = khewats?.getOrNull(i).orEmpty(),
                    status         = statuses?.getOrNull(i).orEmpty(),
                    acres          = acresList?.getOrNull(i) ?: 0.0,
                    geomWkt        = geomWkts?.getOrNull(i),
                    centroid       = centroids?.getOrNull(i),
                    areaName       = areaNames?.getOrNull(i).orEmpty(),
                    subParcelNo    = subNos?.getOrNull(i).orEmpty()
                )
            }
            if (list.isNotEmpty()) return list
            // Fall through to the single-parcel extras when the arrays are missing or empty.
        }

        return listOf(
            DeclineTarget(
                serverParcelId = intent.getLongExtra(EXTRA_SERVER_PARCEL_ID, 0L).takeIf { it > 0 },
                mauzaId        = intent.getLongExtra(EXTRA_MAUZA_ID, 0L),
                parcelNo       = intent.getStringExtra(EXTRA_PARCEL_NO).orEmpty(),
                khewat         = intent.getStringExtra(EXTRA_KHEWAT).orEmpty(),
                status         = intent.getStringExtra(EXTRA_STATUS).orEmpty(),
                acres          = intent.getDoubleExtra(EXTRA_ACRES, 0.0),
                geomWkt        = intent.getStringExtra(EXTRA_GEOM_WKT),
                centroid       = intent.getStringExtra(EXTRA_CENTROID),
                areaName       = intent.getStringExtra(EXTRA_AREA_NAME).orEmpty(),
                subParcelNo    = intent.getStringExtra(EXTRA_SUB_PARCEL_NO).orEmpty()
            )
        )
    }

    private fun bindHeader() {
        if (targets.size <= 1) {
            val single = targets.first()
            binding.tvDeclineHeader.text = "Parcel ${single.label}"
            binding.tvDeclineParcels.visibility = View.GONE
            binding.tvDeclineSub.text =
                "${single.status} · ${String.format(Locale.US, "%.2f Acres", single.acres)}"
            return
        }

        binding.tvDeclineHeader.text = "${targets.size} parcels"
        binding.tvDeclineParcels.text = "Parcels ${joinParcelNos(targets)}"
        binding.tvDeclineParcels.visibility = View.VISIBLE
        binding.tvDeclineSub.text = buildMultiSubHeader(targets)
        binding.tilDeclineRemarks.hint = "Why are these parcels being declined?"
    }

    /** "12, 13, 15" — cut off with "+N more" when the list is very long. */
    private fun joinParcelNos(list: List<DeclineTarget>): String {
        val nos = list.map { it.label.ifBlank { "?" } }
        return if (nos.size <= MAX_LISTED_PARCEL_NOS) nos.joinToString(", ")
        else nos.take(MAX_LISTED_PARCEL_NOS).joinToString(", ") +
                " +${nos.size - MAX_LISTED_PARCEL_NOS} more"
    }

    /** e.g. "2 unmapped · 1 without survey · 18.60 Acres" */
    private fun buildMultiSubHeader(list: List<DeclineTarget>): String {
        var unmapped = 0
        var noSurvey = 0
        var other = 0
        list.forEach { t ->
            when {
                t.status.contains("unmapped", ignoreCase = true) -> unmapped++
                t.status.contains("without", ignoreCase = true) ||
                        t.status.contains("no survey", ignoreCase = true) -> noSurvey++
                else -> other++
            }
        }
        val parts = mutableListOf<String>()
        if (unmapped > 0) parts += "$unmapped unmapped"
        if (noSurvey > 0) parts += "$noSurvey without survey"
        if (other > 0) parts += "$other other"
        parts += String.format(Locale.US, "%.2f Acres", list.sumOf { it.acres })
        return parts.joinToString(" · ")
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

        // Shared by every row: remarks, location, mauza name, picture paths and the pref fallbacks.
        val prefMauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )
        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()
        val prefAreaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()
        val remarks = binding.etDeclineRemarks.text.toString().trim()
        val latitude = currentLocation?.latitude
        val longitude = currentLocation?.longitude

        // One row per parcel, each with a fresh GUID (the server de-duplicates per GUID).
        val declines = targets.map { t ->
            ParcelDeclineEntity(
                declineGuid = UUID.randomUUID().toString(),
                mauzaId = t.mauzaId.takeIf { it > 0 } ?: prefMauzaId,
                mauzaName = mauzaName,
                // Prefer the parcel's own server area; Map Analysis shows every area of a mauza.
                areaName = t.areaName.ifBlank { prefAreaName },
                serverParcelId = t.serverParcelId,
                parcelNo = t.parcelNo,
                subParcelNo = t.subParcelNo,
                parcelStatus = t.status,
                khewatInfo = t.khewat,
                remarks = remarks,
                geomWKT = t.geomWkt,
                centroid = t.centroid,
                calculatedArea = t.acres,
                latitude = latitude,
                longitude = longitude,
                pic1Path = picturePaths[0],
                pic2Path = picturePaths[1],
                pic3Path = picturePaths[2]
            )
        }

        lifecycleScope.launch {
            try {
                declineRepository.saveDeclines(declines)
                logDeclines(declines)
                val msg = if (declines.size == 1) "Decline saved locally"
                          else "${declines.size} declines saved locally"
                ToastUtil.showShort(this@DeclineCaptureActivity, msg)
                finish()
            } catch (e: Exception) {
                binding.btnSaveDecline.isEnabled = true
                ToastUtil.showShort(this@DeclineCaptureActivity, "Error: ${e.message}")
            }
        }
    }

    /** Audit trail: one DECLINED entry per saved row, stamped with the capture location. */
    private fun logDeclines(declines: List<ParcelDeclineEntity>) {
        currentLocation?.let {
            logger.lastLat = it.latitude
            logger.lastLng = it.longitude
        }
        declines.forEach { d ->
            logger.log(
                parcelId = d.serverParcelId ?: 0L,
                parcelNo = d.parcelNo,
                action = ParcelAction.DECLINED,
                subParcelNo = d.subParcelNo,
                mauzaId = d.mauzaId,
                areaName = d.areaName,
                details = "status=${d.parcelStatus}, remarks=${d.remarks}, guid=${d.declineGuid}"
            )
        }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}