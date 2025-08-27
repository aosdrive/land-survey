package pk.gop.pulse.katchiAbadi.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.post.Floors
import pk.gop.pulse.katchiAbadi.data.remote.post.Partition
import pk.gop.pulse.katchiAbadi.data.remote.post.Pictures
import pk.gop.pulse.katchiAbadi.data.remote.post.SurveyPost
import pk.gop.pulse.katchiAbadi.databinding.ActivitySettingsBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var context: Context

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        binding.apply {

            val allowedData = sharedPreferences.getInt(
                Constants.SHARED_PREF_ALLOW_DOWNLOAD_SAVED_DATA,
                Constants.SHARED_PREF_DEFAULT_DOWNLOAD_SAVED_DATA
            )

            cvDownloadSavedData.visibility = when (allowedData) {
                0 -> View.GONE
                1 -> View.VISIBLE
                else -> View.GONE
            }

            cvDownloadSavedData.setOnClickListener {
                // Handle download saved data
                lifecycleScope.launch {
                    val totalPendingRecords = database.surveyFormDao().totalCount()
                    if (totalPendingRecords > 0) {

                        val builder = AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Confirm!")
                            .setCancelable(false)
                            .setMessage("Are you sure, you want to download all records.")
                            .setPositiveButton("Proceed") { _, _ ->

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    postAllSavedData()
                                } else {
                                    if (checkPermissionDownload()) {
                                        lifecycleScope.launch {
                                            postAllSavedData()
                                        }
                                    } else {
                                        requestPermissionDownload()
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)

                        // Create the AlertDialog object
                        val dialog = builder.create()
                        dialog.show()

                        // Get the buttons from the dialog
                        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                        // Set button text size and style
                        positiveButton.textSize =
                            16f // Change the size according to your preference
                        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold

                        negativeButton.textSize =
                            16f // Change the size according to your preference
                        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
                    } else {

                        Toast.makeText(context, "No record found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            cvPrivacyPolicy.setOnClickListener {
                Intent(this@SettingsActivity, PrivacyPolicyActivity::class.java).apply {
                    startActivity(this)
                }
            }

            cvSentRecords.setOnClickListener {
                Intent(this@SettingsActivity, SentRecordsActivity::class.java).apply {
                    startActivity(this)
                }
            }

            tvVersion.text = Constants.VERSION_NAME
        }
    }

    private fun postAllSavedData() {

        // Create and show the progress dialog
        val progressDialog = ProgressDialog(context).apply {
            setMessage("Saving data, please wait...")
            setCancelable(false)
            show()
        }

        // Run the file writing operation in a coroutine
        CoroutineScope(Dispatchers.IO).launch {

            try {

                val userId: String = sharedPreferences.getString(
                    Constants.SHARED_PREF_USER_NAME,
                    Constants.SHARED_PREF_DEFAULT_STRING
                ).toString()

                @SuppressLint("SimpleDateFormat") val dateFormat: DateFormat =
                    SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
                var fileName = "ParcelRecords" + dateFormat.format(Date()) + ".tsv"

                if (userId != "0") {
                    fileName =
                        "${userId}_SavedData_" + dateFormat.format(
                            Date()
                        ) + ".tsv"
                }

                val mimeType = "text/tab-separated-values"

                val resolver = context.contentResolver
                val outputStream: OutputStream?

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri =
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    outputStream = uri?.let { resolver.openOutputStream(it) }
                } else {
                    val downloadsDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    outputStream = FileOutputStream(file)
                }

                outputStream?.use { os ->

//                    os.write("RecordId\tJSON\n".toByteArray()) // Write headers

                    val recordsList = database.surveyFormDao().getAllSurveyRecordsDetails()

                    for (record in recordsList) {
                        val items = database.surveyFormDao()
                            .getCompleteSavedRecord(record.parcelNo, record.uniqueId)

                        for (surveyFormEntity in items) {

                            val floorsList = ArrayList<Floors>()
                            if (surveyFormEntity.floorsList != "") {
                                val jsonObject1 = JSONObject(surveyFormEntity.floorsList)
                                val jsonArray1 = jsonObject1.getJSONArray("floors")

                                for (i in 0 until jsonArray1.length()) {

                                    val floors = Floors()

                                    val jsonObjectFloor = jsonArray1.getJSONObject(i)
                                    val floorNumber = jsonObjectFloor.getInt("floor_number")
                                    val partitions = jsonObjectFloor.getJSONArray("partitions")

                                    val partitionList = ArrayList<Partition>()

                                    for (j in 0 until partitions.length()) {
                                        val partitionObject = partitions.getJSONObject(j)

                                        val partition = Partition(
                                            PartitionNumber = partitionObject.getInt("partition_number"),
                                            Landuse = partitionObject.getString("landuse"),
                                            CommercialActivity = partitionObject.getString("commercial_activity"),
                                            Occupancy = partitionObject.getString("occupancy"),
                                            TenantName = partitionObject.getString("tenant_name"),
                                            TenantFatherName = partitionObject.getString("tenant_father_name"),
                                            TenantCnic = partitionObject.getString("tenant_cnic"),
                                            TenantMobile = partitionObject.getString("tenant_mobile"),
                                        )

                                        partitionList.add(partition)
                                    }
                                    floors.FloorNumber = floorNumber
                                    floors.Partitions = partitionList

                                    floorsList.add(floors)
                                }
                            }

                            // Create Pictures
                            val picturesList = ArrayList<Pictures>()

                            val jsonObject = JSONObject(surveyFormEntity.picturesList)
                            val jsonArray = jsonObject.getJSONArray("pictures")
                            for (i in 0 until jsonArray.length()) {

                                val item = jsonArray.getJSONObject(i)
                                val path = item.getString("path")

                                // Check if the file exists at the specified path
                                val file = File(path)
                                if (file.exists()) {

                                    val bitmap = BitmapFactory.decodeFile(path)
                                    val byteArrayOutputStream = ByteArrayOutputStream()

                                    val fileSizeInBytes = file.length()
                                    val fileSizeInKB = fileSizeInBytes / 1024

                                    var quality = 100
                                    if (fileSizeInKB in 251..400) {
                                        quality = 95
                                    } else if (fileSizeInKB > 400) {
                                        quality = 90
                                    }

                                    println("Quality: $quality")

                                    bitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        quality,
                                        byteArrayOutputStream
                                    )

                                    val byteArrayImage = byteArrayOutputStream.toByteArray()
                                    val encodedImage =
                                        Base64.encodeToString(byteArrayImage, Base64.NO_WRAP)

                                    val picture = Pictures(
                                        Type = item.getString("picture_type"),
                                        OtherType = item.getString("picture_other_type"),
                                        Number = item.getInt("picture_number"),
                                        PicData = encodedImage
                                    )

                                    picturesList.add(picture)
                                } else {
                                    // Handle the case where the file does not exist, if necessary
                                    val picture = Pictures(
                                        Type = item.getString("picture_type"),
                                        OtherType = item.getString("picture_other_type"),
                                        Number = item.getInt("picture_number"),
                                        PicData = "Image not found"
                                    )

                                    picturesList.add(picture)
                                }
                            }

                            var discrepancyPicture = ""

                            if (surveyFormEntity.parcelOperation != "Same") {
                                // Check if the file exists at the specified path
                                val file = File(surveyFormEntity.discrepancyPicturePath)
                                if (file.exists()) {
                                    val bitmap =
                                        BitmapFactory.decodeFile(surveyFormEntity.discrepancyPicturePath)
                                    val byteArrayOutputStream = ByteArrayOutputStream()

                                    val fileSizeInBytes = file.length()
                                    val fileSizeInKB = fileSizeInBytes / 1024

                                    var quality = 100
                                    if (fileSizeInKB in 251..400) {
                                        quality = 95
                                    } else if (fileSizeInKB > 400) {
                                        quality = 90
                                    }

                                    bitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        quality,
                                        byteArrayOutputStream
                                    )

                                    val byteArrayImage = byteArrayOutputStream.toByteArray()
                                    discrepancyPicture =
                                        Base64.encodeToString(byteArrayImage, Base64.NO_WRAP)
                                } else {
                                    // Handle the case where the file does not exist, if necessary
                                    discrepancyPicture = "Image not found"
                                }
                            }

                            var isDiscrepancy = false
                            var discrepancyId = 0

                            if (surveyFormEntity.parcelStatus == Constants.Parcel_SAME) {

                                if (surveyFormEntity.parcelOperation == "Split" && surveyFormEntity.surveyStatus == Constants.Survey_SAME_Unit) {
                                    isDiscrepancy = true
                                    discrepancyId = 1
                                } else if (surveyFormEntity.parcelOperation == "Merge" && surveyFormEntity.surveyStatus == Constants.Survey_SAME_Unit) {
                                    isDiscrepancy = true
                                    discrepancyId = 2
                                } else if (surveyFormEntity.parcelOperation == "Same" && surveyFormEntity.surveyStatus == Constants.Survey_New_Unit) {
                                    isDiscrepancy = true
                                    discrepancyId = 3
                                } else if (surveyFormEntity.parcelOperation == "Split" && surveyFormEntity.surveyStatus == Constants.Survey_New_Unit) {
                                    isDiscrepancy = true
                                    discrepancyId = 4
                                } else if (surveyFormEntity.parcelOperation == "Merge" && surveyFormEntity.surveyStatus == Constants.Survey_New_Unit) {
                                    isDiscrepancy = true
                                    discrepancyId = 5
                                } else {
                                    isDiscrepancy = false
                                    discrepancyId = 0
                                }

                            } else if (surveyFormEntity.parcelStatus == Constants.Parcel_New) {

                                if (surveyFormEntity.surveyStatus == Constants.Survey_SAME_Unit) {
                                    isDiscrepancy = true
                                    discrepancyId = 6
                                } else {
                                    isDiscrepancy = true
                                    discrepancyId = 7

                                }
                            }

                            // Create SurveyPost instance
                            val surveyPost = SurveyPost(
                                propertyId = surveyFormEntity.surveyId,
                                propertyNumber = surveyFormEntity.propertyNumber,
                                parcelId = surveyFormEntity.parcelNo,
                                subParcelId = when (surveyFormEntity.parcelOperation) {
                                    "Split" -> surveyFormEntity.subParcelId
                                    else -> 0
                                },
                                parcelOperationValue = surveyFormEntity.parcelOperationValue,
                                isDiscrepancy = isDiscrepancy,
                                discrepancyId = discrepancyId,
                                interviewStatus = surveyFormEntity.interviewStatus,
                                ownerName = surveyFormEntity.name,
                                ownerFatherName = surveyFormEntity.fatherName,
                                ownerGender = if (surveyFormEntity.interviewStatus == "Respondent Present" && surveyFormEntity.surveyId > 0) {
                                    surveyFormEntity.gender
                                } else {
                                    ""
                                },
                                ownerCNIC = surveyFormEntity.cnic,
                                cnicSource = surveyFormEntity.cnicSource,
                                cnicOtherSource = surveyFormEntity.cnicOtherSource,
                                ownerMobileNo = surveyFormEntity.mobile,
                                mobileSource = surveyFormEntity.mobileSource,
                                mobileOtherSource = surveyFormEntity.mobileOtherSource,
                                area = surveyFormEntity.area,
                                ownershipType = surveyFormEntity.ownershipType,
                                ownershipOtherType = when (surveyFormEntity.ownershipType) {
                                    "Other" -> surveyFormEntity.ownershipOtherType
                                    else -> ""
                                },
                                floorsList = floorsList,
                                picturesList = picturesList,
                                remarks = surveyFormEntity.remarks,
                                kachiAbadiId = surveyFormEntity.kachiAbadiId,
                                userId = surveyFormEntity.userId,
                                gpsAccuracy = surveyFormEntity.gpsAccuracy,
                                gpsAltitude = surveyFormEntity.gpsAltitude,
                                gpsProvider = surveyFormEntity.gpsProvider,
                                gpsTimestamp = surveyFormEntity.gpsTimestamp,
                                latitude = surveyFormEntity.latitude,
                                longitude = surveyFormEntity.longitude,
                                timeZoneId = surveyFormEntity.timeZoneId,
                                mobileTimestamp = surveyFormEntity.mobileTimestamp,
                                appVersion = surveyFormEntity.appVersion,
                                discrepancyPicture = discrepancyPicture,
                                uniqueId = surveyFormEntity.uniqueId,
                                centroidGeom = surveyFormEntity.centroidGeom,
                                geom = surveyFormEntity.geom,
                                qrCode = surveyFormEntity.qrCode,
                            )

                            val recordId = surveyFormEntity.pkId
                            val body = surveyPost.toJson()

                            val item =
                                "${recordId}\t${body}\n"
                            os.write(item.toByteArray())

                        }

                    }

                    os.flush()

                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(
                            context,
                            "File saved to Downloads folder",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } ?: run {
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Failed to create file", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Exception Occurred:\n${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private val requestDownloadPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                lifecycleScope.launch {
                    postAllSavedData()
                }
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this@SettingsActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    // User has permanently denied the permission, open settings dialog
                    showSettingsDialog()
                } else {
                    // Display a rationale and request permission again
                    showMessageOKCancel(
                        "You need to allow location permission"
                    ) { _, _ ->
                        requestPermissionDownload()
                    }
                }
            }
        }


    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(context)
            .setMessage("You have denied Storage permission permanently. Please go to settings to enable it.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                startActivity(intent)
            }.setNegativeButton("Cancel", null)


        // Create the AlertDialog object
        val dialog = builder.create()
        dialog.show()

        // Get the buttons from the dialog
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        // Set button text size and style
        positiveButton.textSize =
            16f // Change the size according to your preference
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold

        negativeButton.textSize =
            16f // Change the size according to your preference
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
    }

    private fun showMessageOKCancel(
        message: String,
        okListener: DialogInterface.OnClickListener
    ) {
        val builder =
            AlertDialog.Builder(context).setMessage(message).setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)

        // Create the AlertDialog object
        val dialog = builder.create()
        dialog.show()

        // Get the buttons from the dialog
        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        // Set button text size and style
        positiveButton.textSize =
            16f // Change the size according to your preference
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold

        negativeButton.textSize =
            16f // Change the size according to your preference
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
    }

    private fun requestPermissionDownload() {
        requestDownloadPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun checkPermissionDownload(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}