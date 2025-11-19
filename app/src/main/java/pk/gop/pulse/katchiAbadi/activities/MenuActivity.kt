package pk.gop.pulse.katchiAbadi.activities

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.PointCollection
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.MyApplication
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.DownloadFileTask
import pk.gop.pulse.katchiAbadi.common.DownloadTpkTask
import pk.gop.pulse.katchiAbadi.common.DownloadType
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.response.KachiAbadiList
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaDetail
import pk.gop.pulse.katchiAbadi.databinding.ActivityMenuBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonEntity
import pk.gop.pulse.katchiAbadi.presentation.menu.MenuViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.IntentUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

@Suppress("DEPRECATION")
@AndroidEntryPoint
class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding
    private val viewModel: MenuViewModel by viewModels()
    private lateinit var context: Context

    private lateinit var progressDialog: ProgressDialog
    private lateinit var progressDialogTwo: ProgressDialog

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var serverApi: ServerApi

//    @Inject
//    lateinit var retrofit: Retrofit

    private var job: Job? = null
    private lateinit var progressAlertDialog: AlertDialog
    private lateinit var tileManager: TileManager
    private val wgs84 by lazy {
        SpatialReferences.getWgs84()
    }

    private var downloadComplete: Boolean? = null

    private var downloadFileTask: DownloadFileTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        applySavedTheme()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Initialize SharedPreferences for theme persistence
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@MenuActivity
        setupActionBar()
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()


        lifecycleScope.launch {

            val count = withContext(Dispatchers.IO) {
                database.parcelDao().checkDataSaved()
            }

            if (count > 0) {

                val updateDatabase = sharedPreferences.getInt(
                    Constants.SHARED_PREF_UPDATE_DATABASE,
                    Constants.SHARED_PREF_DEFAULT_INT
                )

                if (updateDatabase == 0) {

                    // Create and show the ProgressDialog
                    progressDialogTwo = ProgressDialog(this@MenuActivity).apply {
                        setMessage("Updating database, please wait...")
                        setCancelable(false)
                        show()
                    }

                    try {
                        // Perform the database updates on the IO dispatcher
                        withContext(Dispatchers.IO) {
                            database.parcelDao().updateAllTablesInTransaction(
                                database.surveyFormDao(),
                                database.tempSurveyFormDao(),
                                database.notAtHomeSurveyFormDao()
                            )
                        }

                        sharedPreferences.edit()
                            .putInt(Constants.SHARED_PREF_UPDATE_DATABASE, 1)
                            .apply()
                        sharedPreferences.edit().putInt(
                            Constants.SHARED_PREF_LOGIN_STATUS,
                            Constants.LOGIN_STATUS_INACTIVE
                        ).putLong(
                            Constants.SHARED_PREF_USER_ID,
                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                        ).putString(
                            Constants.SHARED_PREF_USER_CNIC,
                            Constants.SHARED_PREF_DEFAULT_STRING
                        ).putString(
                            Constants.SHARED_PREF_USER_NAME,
                            Constants.SHARED_PREF_DEFAULT_STRING
                        ).apply()

                        // Transition to the AuthActivity
                        withContext(Dispatchers.Main) {
                            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                                startActivity(this)
                                finish()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() // Handle any errors here
                        // Dismiss the ProgressDialog in the finally block
                        if (progressDialog.isShowing) {
                            progressDialog.dismiss()
                        }
                    }
                } else {
                    sharedPreferences.edit()
                        .putInt(Constants.SHARED_PREF_UPDATE_DATABASE, 1)
                        .apply()
                }
            } else {
                sharedPreferences.edit()
                    .putInt(Constants.SHARED_PREF_UPDATE_DATABASE, 1)
                    .apply()
            }
        }

        binding.cvMyTasks.setOnClickListener {
            Intent(this@MenuActivity, TaskListActivity::class.java).apply {
                startActivity(this)
            }
        }

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            cvSyncData.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                if (Utility.checkInternetConnection(this@MenuActivity)) {

                    lifecycleScope.launch {
                        val allowedDownloadableAreas = sharedPreferences.getInt(
                            Constants.SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS,
                            Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
                        )

                        val mauzaId = sharedPreferences.getLong(
                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                        )

                        val areaName = sharedPreferences.getString(
                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                            Constants.SHARED_PREF_DEFAULT_STRING
                        ).orEmpty()
                        //val distinctParcelCount = database.parcelDao().getDistinctParcelCount()
                        val distinctParcelCount = database.activeParcelDao()
                            .getParcelsCountByMauzaAndArea(mauzaId, areaName)

                        if (distinctParcelCount != 0 && distinctParcelCount == allowedDownloadableAreas) {
                            val builder = AlertDialog.Builder(context)
                                .setTitle("Confirm!")
                                .setCancelable(false)
                                .setMessage(
                                    "You have already downloaded $allowedDownloadableAreas areas. To download a new one, please delete an existing area.\n" +
                                            "\nDo you want to proceed with deleting a saved area?"
                                )
                                .setPositiveButton("Yes") { _, _ ->
                                    Intent(
                                        this@MenuActivity,
                                        DownloadedAreasActivity::class.java
                                    ).apply {
                                        startActivity(this)
                                    }
                                }
                                .setNegativeButton("No", null)

                            // Create the AlertDialog object
                            val dialog = builder.create()
                            dialog.show()

                            // Get the buttons from the dialog
                            val positiveButton =
                                dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                            val negativeButton =
                                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                            // Set button text size and style
                            positiveButton.textSize =
                                16f // Change the size according to your preference
                            positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold

                            negativeButton.textSize =
                                16f // Change the size according to your preference
                            negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
                        } else {
//                            viewModel.mouzaData()
                            viewModel.mauzaNewData()
                        }
                    }
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }
//            cvSyncedAreas.setOnClickListener {
//                Log.d("CLICK", "cvSyncedAreas clicked")
//                lifecycleScope.launch(Dispatchers.Main) {
//                    val downloadedAreas = database.activeParcelDao().getDistinctDownloadedAreas()
//                    if (downloadedAreas.isNotEmpty()) {
//                        val intent = Intent(this@MenuActivity, DownloadedAreasActivity::class.java)
//                        startActivity(intent)
//                   } else {
//                        AlertDialog.Builder(this@MenuActivity)
//                            .setTitle("Notice")
//                            .setMessage("Not Available Yet!")
//                            .setPositiveButton("OK", null)
//                            .show()
//                    }
//                }
//            }
            cvSyncedAreas.setOnClickListener {
                Log.d("CLICK", "cvSyncedAreas clicked")

                lifecycleScope.launch(Dispatchers.Main) {
                    val downloadedAreas = withContext(Dispatchers.IO) {
                        database.activeParcelDao().getDistinctDownloadedAreas()
                    }

                    if (downloadedAreas.isNotEmpty()) {
                        IntentUtil.startActivity(
                            this@MenuActivity,
                            DownloadedAreasActivity::class.java
                        )
                    } else {
                        AlertDialog.Builder(this@MenuActivity)
                            .setTitle("Notice")
                            .setMessage("Not Available Yet!")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }

            cvStartSurvey.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                lifecycleScope.launch {

                    val mouzaId = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    )

                    val areaId = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    )


                    val mauzaId = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    )

                    val areaName = sharedPreferences.getString(
                        Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                        Constants.SHARED_PREF_DEFAULT_STRING
                    ).orEmpty()


                    val sanitizedAreaName = areaName.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val folderKey = "mauza_${mauzaId}_area_${sanitizedAreaName}"

                    val sdCardRoot = context.filesDir

                    val file = when (Constants.MAP_DOWNLOAD_TYPE) {
                        DownloadType.TPK -> {
                            val filesDir = File(sdCardRoot, "MapTpk/${folderKey}")
                            File(filesDir, "${mouzaId}_${areaId}.tpk")
                        }

                        DownloadType.TILES -> {
                            File(context.filesDir, "MapTiles/${folderKey}")
                        }
                    }

                    val recordsCount =
                        database.activeParcelDao().getParcelsCountByMauzaAndArea(mauzaId, areaName)

                    if (recordsCount > 0 && file.exists()) {

                        cvStartSurvey.isEnabled = true
                        cvStartSurvey.isFocusable = true

                        cvPropertyList.isEnabled = true
                        cvPropertyList.isFocusable = true

                        llStartSurvey.setBackgroundColor(Color.WHITE)
                        llPropertyList.setBackgroundColor(Color.WHITE)

                        Intent(this@MenuActivity, SurveyFormActivity::class.java).apply {
                            startActivity(this)
                        }
                    } else {
                        cvStartSurvey.isEnabled = false
                        cvStartSurvey.isFocusable = false

                        cvPropertyList.isEnabled = false
                        cvPropertyList.isFocusable = false

                        llStartSurvey.setBackgroundColor(Color.LTGRAY)
                        llPropertyList.setBackgroundColor(Color.LTGRAY)
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Parcels/TPK is not available, download the data"
                        )
                    }
                }
            }

            cvPropertyList.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                Intent(this@MenuActivity, SurveyListActivity::class.java).apply {
                    startActivity(this)
                }
            }

            cvUploadRecords.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                lifecycleScope.launch {
//                    database.surveyFormDao().u1pdateSurveyStatusUnSent() // TODO delete this
                    val totalPendingRecords = database.newSurveyNewDao().totalPendingCount()
                    if (totalPendingRecords > 0) {
                        Intent(this@MenuActivity, NewSavedRecordsActivity::class.java).apply {
                            startActivity(this)
                        }
                    } else {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "No saved record available yet."
                        )
                    }
                }
            }

        }

        lifecycleScope.launch {
            viewModel.sync.collect {
                when (it) {
                    is Resource.Loading -> {
                        Utility.showProgressAlertDialog(
                            context, "Please wait! downloading data..."
                        )
                    }

                    is Resource.Success -> {
                        Utility.dismissProgressAlertDialog()
                        downloadMap()
                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()

                        it.message?.let { msg ->
                            if (msg.contains("401")) {
                                sharedPreferences.edit().putInt(
                                    Constants.SHARED_PREF_LOGIN_STATUS,
                                    Constants.LOGIN_STATUS_INACTIVE
                                ).putString(
                                    Constants.SHARED_PREF_USER_NAME,
                                    Constants.SHARED_PREF_DEFAULT_STRING
                                ).apply()

                                Intent(this@MenuActivity, AuthActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    startActivity(this)
                                    finish()
                                }

                                // Session has expired

                                ToastUtil.showShort(
                                    this@MenuActivity,
                                    "Session expired"
                                )
                            } else {
                                ToastUtil.showShort(
                                    context,
                                    msg
                                )
                            }
                        }

                    }

                    else -> Unit

                }
            }
        }



        lifecycleScope.launch {
            viewModel.mauzaNew.collect { it ->
                when (it) {
                    is ResourceSealed.Loading -> {
                        Utility.showProgressAlertDialog(
                            context, "Please wait! Getting assigned mauzas..."
                        )
                    }

                    is ResourceSealed.Success -> {
                        Utility.dismissProgressAlertDialog()

                        val mauzaList = it.data ?: emptyList()
                        val settings = it.info

                        if (mauzaList.isEmpty()) {
                            ToastUtil.showShort(
                                context,
                                "No Mauzas assigned to this user."
                            )
                            return@collect
                        }

                        // Sort Mauzas alphabetically
                        val sortedMauzas = mauzaList.sortedBy { mauza -> mauza.mauzaName }

                        val view = LayoutInflater.from(this@MenuActivity)
                            .inflate(R.layout.dialog_mouza_list, null)

                        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
                        val adapter = ArrayAdapter(
                            this@MenuActivity,
                            R.layout.spinner_item_drop_down,
                            sortedMauzas.map { it.mauzaName } // display names
                        )
                        spinner.adapter = adapter

                        val builder = AlertDialog.Builder(this@MenuActivity)
                            .setView(view)
                            .setCancelable(false)
                            .setTitle("Select a Mauza")
                            .setPositiveButton("Next") { _, _ ->
                                val selectedIndex = spinner.selectedItemPosition
                                val selectedMauza = sortedMauzas[selectedIndex]

                                // Save Mauza Info
                                sharedPreferences.edit()
                                    .putLong(
                                        Constants.SHARED_PREF_USER_ASSIGNED_MOUZA,
                                        selectedMauza.mauzaId
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_FeetPerMarla,
                                        selectedMauza.unit
                                    )
                                    .putString(
                                        Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME,
                                        selectedMauza.mauzaName
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_METER_DISTANCE,
                                        settings?.meterDistance?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_DISTANCE
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_METER_ACCURACY,
                                        settings?.meterAccuracy?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_ACCURACY
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS,
                                        settings?.allowedDownloadableAreas?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_MAP_MIN_SCALE,
                                        settings?.minScaleTiles?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_MIN_SCALE
                                    )
                                    .putInt(
                                        Constants.SHARED_PREF_MAP_MAX_SCALE,
                                        settings?.maxScaleTiles?.toIntOrNull()
                                            ?: Constants.SHARED_PREF_DEFAULT_MAX_SCALE
                                    )
                                    .putInt(Constants.SHARED_PREF_ALLOW_DOWNLOAD_SAVED_DATA, 0)
                                    .apply()

                                // 👇 Now call API to get Area List using selected Mauza ID
                                val token =
                                    sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "")
                                        ?: ""
//                                val retrofit = Retrofit.Builder()
//                                    .baseUrl(Constants.BASE_URL)
//                                    .addConverterFactory(GsonConverterFactory.create())
//                                    .build()

//                                val api = retrofit.create(ServerApi::class.java)
//


                                lifecycleScope.launch {
                                    try {
                                        Utility.showProgressAlertDialog(
                                            this@MenuActivity,
                                            "Fetching areas..."
                                        )
                                        Log.d("API_CALL", "Calling getAreasByMauzaId with mauzaId: ${selectedMauza.mauzaId}")

                                        val areaResponse = serverApi.getAreasByMauzaId(
                                            selectedMauza.mauzaId,
                                            "Bearer $token"
                                        )

                                        Log.d("API_RESPONSE", "Raw response: $areaResponse")
                                        Log.d("API_RESPONSE", "Response areas field: ${areaResponse.areas}")
                                        Log.d("API_RESPONSE", "Areas type: ${areaResponse.areas::class.java}")


                                        Utility.dismissProgressAlertDialog()

                                        val areaList = areaResponse.areas

                                        val validAreas = areaList.filter { area ->
                                            area.isNotBlank() && !area.matches(Regex("^\\d+$")) // Remove empty and numeric-only strings
                                        }

                                        Log.d("AREA_FILTER", "Original areas: $areaList")
                                        Log.d("AREA_FILTER", "Filtered valid areas: $validAreas")


                                        if (areaList.isEmpty()) {
                                            ToastUtil.showShort(
                                                this@MenuActivity,
                                                "No areas found for this Mauza."
                                            )
                                            return@launch
                                        }

                                        // 👇 Show area selection dialog
                                        showAreaSelectionDialog(selectedMauza, areaList)

                                    } catch (e: Exception) {
                                        Utility.dismissProgressAlertDialog()

                                        ToastUtil.showShort(
                                            this@MenuActivity,
                                            "Error fetching areas: ${e.message}"
                                        )
                                    }
                                }
                            }
                            .setNegativeButton("Cancel", null)

                        val dialog = builder.create()
                        dialog.show()

                        // Set font styles
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                        }
                    }

                    is ResourceSealed.Error -> {
                        Utility.dismissProgressAlertDialog()
                        ToastUtil.showShort(
                            context,
                            it.message ?: "Something went wrong"
                        )
                    }

                    else -> Unit
                }
            }
        }

    }

    private fun showAreaSelectionDialog(mauza: MauzaDetail, areaList: List<String>) {
        Log.d("AREA_DIALOG", "Showing areas for mauza ${mauza.mauzaId}: $areaList")
        Log.d("AREA_DIALOG", "Mauza unit: ${mauza.unit}")

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_area_list, null)
        val spinner = view.findViewById<Spinner>(R.id.spn_areas)

        val adapter = ArrayAdapter(this, R.layout.spinner_item_drop_down, areaList)
        spinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Select Area")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ ->
                val selectedArea = spinner.selectedItem as String

                // ✅ Convert area to groupId (if area is numeric)
                val groupId = selectedArea.toLongOrNull() ?: 0L

                Log.d("AREA_DIALOG", "Selected area: $selectedArea, groupId: $groupId, unitId: ${mauza.unit}")

                lifecycleScope.launch {
                    Utility.showProgressAlertDialog(this@MenuActivity, "Downloading data...")
                    val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

                    val result = fetchAndStoreActiveParcels(
                        mauzaId = mauza.mauzaId,
                        mauzaName = mauza.mauzaName,
                        areaName = selectedArea,
                        unitId = mauza.unit.toLong(),
                        groupId = groupId,
                        token = "Bearer $token"
                    )

                    Utility.dismissProgressAlertDialog()

                    when (result) {
                        is Resource.Success -> {
                            downloadMapNew()
                            ToastUtil.showShort(
                                this@MenuActivity,
                                "Download successful!"
                            )
                        }
                        is Resource.Error -> {
                            ToastUtil.showShort(
                                this@MenuActivity,
                                result.message ?: "Download failed."
                            )
                        }
                        is Resource.Loading -> TODO()
                        is Resource.Unspecified -> TODO()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun fetchAndStoreActiveParcels(
        mauzaId: Long,
        mauzaName: String,
        areaName: String,
        unitId: Long,
        groupId: Long,
        token: String
    ): SimpleResource {
        return try {
            Log.d("FETCH_PARCELS", "=== Starting fetchAndStoreActiveParcels ===")
            Log.d("FETCH_PARCELS", "mauzaId: $mauzaId, mauzaName: $mauzaName, areaName: $areaName")
            Log.d("FETCH_PARCELS", "unitId: $unitId, groupId: $groupId")

            val authToken = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

            val response = serverApi.getActiveParcelsByMauzaAndArea(mauzaId, areaName, "Bearer $authToken")

            Log.d("FETCH_PARCELS", "API Response received: ${response.parcelsData.size} parcels")

            database.withTransaction {
                database.activeParcelDao().deleteParcelsByMauzaAndArea(mauzaId, areaName)
                Log.d("FETCH_PARCELS", "Deleted old parcels for mauzaId: $mauzaId, area: $areaName")

                val entities = response.parcelsData.mapIndexed { index, parcelDto ->
                    Log.d("FETCH_PARCELS", """
                    Parcel $index:
                    - id: ${parcelDto.id}
                    - parcelNo: ${parcelDto.parcelNo}
                    - unitId: $unitId (from mauza.unit)
                    - groupId: $groupId (from area)
                """.trimIndent())

                    ActiveParcelEntity(
                        pkid = 0,
                        id = parcelDto.id,
                        parcelNo = parcelDto.parcelNo,
                        subParcelNo = parcelDto.subParcelNo,
                        mauzaId = parcelDto.mauzaId,
                        mauzaName = mauzaName,
                        khewatInfo = parcelDto.khewatInfo,
                        areaAssigned = parcelDto.areaAssigned,
                        geomWKT = parcelDto.geomWKT,
                        centroid = parcelDto.centriod,
                        distance = parcelDto.distance,
                        parcelType = parcelDto.parcelType,
                        parcelAreaKMF = parcelDto.parcelAreaKMF,
                        parcelAreaAbadiDeh = parcelDto.parcelAreaAbadiDeh,
                        surveyStatusCode = parcelDto.surveyStatusCode,
                        surveyId = parcelDto.surveyId,
                        isActivate = true,
                        unitId = unitId,
                        groupId = groupId
                    )
                }

                Log.d("FETCH_PARCELS", "Inserting ${entities.size} parcels into database")

                entities.take(3).forEachIndexed { index, entity ->
                    Log.d("FETCH_PARCELS", "Entity $index: unitId=${entity.unitId}, groupId=${entity.groupId}")
                }

                database.activeParcelDao().insertActiveParcels(entities)

                val insertedParcels = database.activeParcelDao().getActiveParcelsByMauzaAndArea(mauzaId, areaName)
                Log.d("FETCH_PARCELS", "✅ Verification: ${insertedParcels.size} parcels inserted")

                insertedParcels.take(3).forEachIndexed { index, parcel ->
                    Log.d("FETCH_PARCELS", """
                    ✅ Inserted Parcel $index:
                    - id: ${parcel.id}
                    - parcelNo: ${parcel.parcelNo}
                    - unitId: ${parcel.unitId}
                    - groupId: ${parcel.groupId}
                """.trimIndent())
                }
            }

            val khewatIds = response.parcelsData.mapNotNull { it.mauzaId }.distinct()
            Log.d("FETCH_PARCELS", "Extracted ${khewatIds.size} unique khewat IDs")

            val ownerResponses = serverApi.getOwnersFromDbOffline("Bearer $authToken", khewatIds)
            Log.d("FETCH_PARCELS", "Fetched ${ownerResponses.size} owners")

            val persons = ownerResponses.map { detail ->
                SurveyPersonEntity(
                    surveyId = 0,
                    ownershipType = "Owner",
                    personId = detail.person_ID,
                    firstName = detail.first_Name.orEmpty(),
                    lastName = detail.last_Name.orEmpty(),
                    gender = "",
                    relation = detail.relation.orEmpty(),
                    religion = detail.caste.orEmpty(),
                    mobile = detail.mobile.orEmpty(),
                    nic = detail.nic.orEmpty(),
                    growerCode = detail.grower_Code.orEmpty(),
                    personArea = detail.area_KMF.orEmpty(),
                    extra1 = detail.extra1.orEmpty(),
                    extra2 = detail.extra2.orEmpty(),
                    mauzaId = detail.mauza_Id,
                    mauzaName = detail.mauza_Name.orEmpty()
                )
            }

            database.withTransaction {
                database.personDao().deleteByMauzaId(mauzaId)
                database.personDao().insertAll(persons)
                Log.d("FETCH_PARCELS", "Inserted ${persons.size} persons")
            }

            sharedPreferences.edit()
                .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, mauzaId)
                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, mauzaName)
                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, areaName)
                .apply()

            Log.d("FETCH_PARCELS", "=== COMPLETED SUCCESSFULLY ===")

            Resource.Success(Unit)

        } catch (e: Exception) {
            Log.e("FETCH_PARCELS", "❌ Error: ${e.message}", e)
            Resource.Error("Failed to sync data: ${e.localizedMessage}")
        }
    }


    private fun startLogout() {
        val grayColor = Color.GRAY

        val yesText = SpannableString("Yes").apply {
            setSpan(ForegroundColorSpan(grayColor), 0, length, 0)
        }

        val noText = SpannableString("No").apply {
            setSpan(ForegroundColorSpan(grayColor), 0, length, 0)
        }

        val builder = AlertDialog.Builder(context)
            .setTitle("Exit!")
            .setCancelable(false)
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton(yesText) { _, _ ->
                Utility.showProgressAlertDialog(context, "Please wait...")
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    sharedPreferences.edit().putInt(
                        Constants.SHARED_PREF_LOGIN_STATUS,
                        Constants.LOGIN_STATUS_INACTIVE
                    ).putLong(
                        Constants.SHARED_PREF_USER_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    ).putString(
                        Constants.SHARED_PREF_USER_CNIC,
                        Constants.SHARED_PREF_DEFAULT_STRING
                    ).putString(
                        Constants.SHARED_PREF_USER_NAME,
                        Constants.SHARED_PREF_DEFAULT_STRING
                    ).apply()
                    Utility.dismissProgressAlertDialog()
                    Intent(this@MenuActivity, AuthActivity::class.java).apply {
                        startActivity(this)
                        finish()
                    }
                }, 100)
            }
            .setNegativeButton(noText, null)

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        // You can still style other properties (optional)
        positiveButton.textSize = 16f
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

        negativeButton.textSize = 16f
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    }

    private fun downloadMapNew() {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
//                startDownloadingTPK()
            }

            DownloadType.TILES -> {
                downloadMapTilesNew()
            }
        }
    }

    private fun downloadMap() {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
//                startDownloadingTPK()
            }

            DownloadType.TILES -> {
                downloadMapTiles()
            }
        }
    }

    private fun downloadMapTilesNew() {
        downloadComplete = false

        progressAlertDialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Please wait!")
            .setMessage("Initializing download...")
            .setCancelable(false)
            .create()

        tileManager = TileManager(this)
        progressAlertDialog.show()

        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val areaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()

        val sanitizedAreaName = areaName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val folderKey = "mauza_${mauzaId}_area_${sanitizedAreaName}"

        val minZoomLevel = sharedPreferences.getInt(
            Constants.SHARED_PREF_MAP_MIN_SCALE,
            Constants.SHARED_PREF_DEFAULT_MIN_SCALE
        )

//        val maxZoomLevel = sharedPreferences.getInt(
//            Constants.SHARED_PREF_MAP_MAX_SCALE,
//            Constants.SHARED_PREF_DEFAULT_MAX_SCALE
//        )

        val maxZoomLevel = 16

        var currentTile = 0
        var relevantMaxZoom = 0
        var tilesToDownload = 0

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val parcels = database.activeParcelDao().getParcelsByMauzaAndArea(mauzaId, areaName)

                val polygonsList = mutableListOf<Polygon>()
                for (parcel in parcels) {
                    val geomString = parcel.geomWKT
                    try {
                        val multiPolygons = Utility.getMultiPolygonFromString(geomString, wgs84)
                        multiPolygons.mapTo(polygonsList) { Utility.simplifyPolygon(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val combinedGeometry = GeometryEngine.union(polygonsList)
                val bufferedExtent =
                    GeometryEngine.buffer(combinedGeometry.extent, 0.0001) as Polygon
                val result =
                    calculateTilesToDownload(bufferedExtent.extent, minZoomLevel, maxZoomLevel)

                relevantMaxZoom = result.first
                tilesToDownload = result.second

                withContext(Dispatchers.Main) {
                    progressAlertDialog.setMessage("Total tiles to download: $tilesToDownload")
                    Log.e("tiles", "downloadMapTilesNew: $tilesToDownload")
                }

                for (zoomLevel in minZoomLevel..relevantMaxZoom) {
                    val extent = bufferedExtent.extent
                    val (x1, y1, x2, y2) = listOf(
                        getTileX(extent.xMin, zoomLevel),
                        getTileY(extent.yMin, zoomLevel),
                        getTileX(extent.xMax, zoomLevel),
                        getTileY(extent.yMax, zoomLevel)
                    )

                    val minX = minOf(x1, x2)
                    val maxX = maxOf(x1, x2)
                    val minY = minOf(y1, y2)
                    val maxY = maxOf(y1, y2)

                    for (tileX in minX..maxX) {
                        for (tileY in minY..maxY) {
                            if (Utility.checkInternetConnection(this@MenuActivity)) {
                                currentTile++
                                tileManager.downloadAndCacheTileNew(
                                    zoomLevel,
                                    tileY,
                                    tileX,
                                    folderKey, // use sanitized mauza+areaName as folder
                                    "cached",
                                    minZoomLevel,
                                    relevantMaxZoom
                                )
                                withContext(Dispatchers.Main) {
                                    progressAlertDialog.setMessage("Downloading tile $currentTile of $tilesToDownload")
                                }
                            }
                        }
                    }
                }

            } finally {
                withContext(Dispatchers.Main) {
                    val sourceFile = File(context.cacheDir, "MapTiles/$folderKey")
                    val destinationFile = File(context.filesDir, "MapTiles/$folderKey")

                    if (currentTile == tilesToDownload) {
                        if (!(isFinishing || isDestroyed)) {
                            copyFolder(sourceFile, destinationFile)
                            sourceFile.deleteRecursively()

                            binding.apply {
                                cvStartSurvey.isEnabled = true
                                cvPropertyList.isEnabled = true
                                llStartSurvey.setBackgroundColor(Color.WHITE)
                                llPropertyList.setBackgroundColor(Color.WHITE)
                            }



                            sharedPreferences.edit()
                                .putInt(
                                    Constants.SHARED_PREF_SYNC_STATUS,
                                    Constants.SYNC_STATUS_SUCCESS
                                )
                                .apply()

                            progressAlertDialog.setMessage("Download complete")
                            progressAlertDialog.dismiss()

                            updateSelectedMauzaAndArea()

                            Toast.makeText(
                                context,
                                "Data downloaded successfully",
                                Toast.LENGTH_LONG
                            ).show()
                            downloadComplete = true
                        }
                    } else {
                        if (!(isFinishing || isDestroyed)) {
                            progressAlertDialog.setMessage("Download Incomplete")
                            progressAlertDialog.dismiss()
                        }

                        destinationFile.deleteRecursively()
                        downloadComplete = false
//                        deleteUnSyncedData()
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Download Incomplete"
                        )
                    }
                }
            }
        }
    }

    private fun updateSelectedMauzaAndArea() {
        val downloadedMauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )
        val downloadedMauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        )
        val downloadedAreaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )
        val downloadedAreaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
            Constants.SHARED_PREF_DEFAULT_STRING
        )

        sharedPreferences.edit().apply {
            putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, downloadedMauzaId)
            putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, downloadedMauzaName)
            putLong(Constants.SHARED_PREF_USER_SELECTED_AREA_ID, downloadedAreaId)
            putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, downloadedAreaName)
            apply()
        }

        binding.apply {
            if (!downloadedMauzaName.isNullOrEmpty() && !downloadedAreaName.isNullOrEmpty()) {
                tvSelectedAbadiName.text = "$downloadedMauzaName\n($downloadedAreaName)"
                llSelectedAbadi.visibility = View.VISIBLE
            } else {
                llSelectedAbadi.visibility = View.INVISIBLE
            }
        }
    }

    private fun downloadMapTiles() {

        downloadComplete = false

        // Initialize the ProgressDialog
        progressAlertDialog = AlertDialog.Builder(this@MenuActivity)
            .setTitle("Please wait!")
            .setMessage("Initializing download...")
            .setCancelable(false)
            .create()

        tileManager = TileManager(this)

        progressAlertDialog.show()

        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val areaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val minZoomLevel = sharedPreferences.getInt(
            Constants.SHARED_PREF_MAP_MIN_SCALE,
            Constants.SHARED_PREF_DEFAULT_MIN_SCALE
        )

        val maxZoomLevel = sharedPreferences.getInt(
            Constants.SHARED_PREF_MAP_MAX_SCALE,
            Constants.SHARED_PREF_DEFAULT_MAX_SCALE
        )

        var currentTile = 0
        var relevantMaxZoom = 0
        var tilesToDownload = 0

        job = CoroutineScope(Dispatchers.IO).launch {
            try {

                val parcels = database.parcelDao().getAllParcelsWithAreaId(areaId)

                val polygonsList = mutableListOf<Polygon>()

                for (parcel in parcels) {
                    val parcelGeom = parcel.geom
                    if (parcelGeom.contains("MULTIPOLYGON (((")) {
                        val polygons = Utility.getMultiPolygonFromString(parcelGeom, wgs84)
                        for (polygon in polygons) {
                            val simplifiedPolygon = Utility.simplifyPolygon(polygon)
                            polygonsList.add(simplifiedPolygon)
                        }
                    } else if (parcelGeom.contains("POLYGON ((")) {
                        Utility.getPolygonFromString(parcelGeom, wgs84)?.let { parsedPolygon ->
                            val polygon = Utility.simplifyPolygon(parsedPolygon)
                            polygonsList.add(polygon)
                        } ?: Log.w("downloadMapTiles", "Skipped invalid POLYGON WKT: $parcelGeom")
                    } else {
                        Utility.getPolyFromString(parcelGeom, wgs84)?.let { parsedPolygon ->
                            val polygon = Utility.simplifyPolygon(parsedPolygon)
                            polygonsList.add(polygon)
                        } ?: Log.w(
                            "downloadMapTiles",
                            "Skipped invalid malformed geometry: $parcelGeom"
                        )
                    }
                }

                // Union all polygons into a single geometry
                val combinedGeometry = GeometryEngine.union(polygonsList)

                // Get the extent (bounding box) of the combined geometry
                val combinedExtent = combinedGeometry.extent

                // Apply a 10-meter buffer to the extent in WGS84
                val bufferDistance = 0.0001 // Roughly 50 meters in latitude/longitude
                val bufferedGeometry =
                    GeometryEngine.buffer(combinedExtent, bufferDistance) as Polygon

                // Get the extent of the buffered polygon
                val bufferedExtent = bufferedGeometry.extent

//                 Get minX, minY, maxX, and maxY from the buffered envelope in WGS84
//                val minX = bufferedExtent.xMin
//                val minY = bufferedExtent.yMin
//                val maxX = bufferedExtent.xMax
//                val maxY = bufferedExtent.xMax

//                Log.d("MapExtent", "$minX, $minY, $maxX, $maxY")

                val result =
                    calculateTilesToDownload(bufferedExtent, minZoomLevel, maxZoomLevel)
                relevantMaxZoom = result.first
                tilesToDownload = result.second


                // Add these logs
                Log.d("TileDownload", "=== TILE DOWNLOAD STARTED ===")
                Log.d("TileDownload", "Min Zoom Level: $minZoomLevel")
                Log.d("TileDownload", "Max Zoom Level: $maxZoomLevel")
                Log.d("TileDownload", "Relevant Max Zoom: $relevantMaxZoom")
                Log.d("TileDownload", "Total Tiles to Download: $tilesToDownload")
                Log.d("TileDownload", "Buffered Extent: ${bufferedExtent.xMin}, ${bufferedExtent.yMin}, ${bufferedExtent.xMax}, ${bufferedExtent.yMax}")
                // Now use relevantMaxZoom and tilesToDownload in your download logic
                println("Max Zoom Level: $relevantMaxZoom")
                println("Total Tiles to Download: $tilesToDownload")

                // Update the progress dialog with the total number of tiles
                withContext(Dispatchers.Main) {
                    progressAlertDialog.setMessage("Total tiles to download: $tilesToDownload")
                }

                // Download the tiles
                for (zoomLevel in minZoomLevel..relevantMaxZoom) {
                    val x1: Int = getTileX(bufferedExtent.xMin, zoomLevel)
                    val y1: Int = getTileY(bufferedExtent.yMin, zoomLevel)
                    val x2: Int = getTileX(bufferedExtent.xMax, zoomLevel)
                    val y2: Int = getTileY(bufferedExtent.yMax, zoomLevel)

                    var minX = x1
                    var maxX = x2
                    var minY = y1
                    var maxY = y2 // Assume the min , max values

                    if (minX > maxX) {
                        val temp = minX
                        minX = maxX
                        maxX = temp
                    }

                    if (minY > maxY) {
                        val temp = minY
                        minY = maxY
                        maxY = temp
                    }

                    val tilesForThisZoom = (maxX - minX + 1) * (maxY - minY + 1)
                    Log.d("TileDownload", "--- Zoom Level $zoomLevel ---")
                    Log.d("TileDownload", "Tile range: X($minX-$maxX), Y($minY-$maxY)")
                    Log.d("TileDownload", "Tiles for zoom $zoomLevel: $tilesForThisZoom")

                    for (tileX in minX..maxX) {
                        for (tileY in minY..maxY) {
                            if (Utility.checkInternetConnection(this@MenuActivity)) {
                                currentTile++
                                Log.v("TileDownload", "Downloading tile $currentTile/$tilesToDownload - Zoom:$zoomLevel, X:$tileX, Y:$tileY")
                                tileManager.downloadAndCacheTile(
                                    zoomLevel,
                                    tileY,
                                    tileX,
                                    areaId,
                                    "cached",
                                    minZoomLevel,
                                    relevantMaxZoom,
                                )
                                // Update progress dialog
                                withContext(Dispatchers.Main) {
                                    progressAlertDialog.setMessage("Downloading tile $currentTile of $tilesToDownload")
                                }
                            }
                        }
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Hide the progress dialog when done
                    if (currentTile == tilesToDownload) {

                        if (!(isFinishing || isDestroyed)) {

                            val sourceFile = File(context.cacheDir, "MapTiles/${areaId}")
                            val destinationFile = File(context.filesDir, "MapTiles/${areaId}")

                            copyFolder(sourceFile, destinationFile)

                            sourceFile.deleteRecursively()

                            binding.apply {
                                cvStartSurvey.isEnabled = true
                                cvPropertyList.isEnabled = true

                                llStartSurvey.setBackgroundColor(Color.WHITE)
                                llPropertyList.setBackgroundColor(Color.WHITE)
                            }

                            sharedPreferences.edit().putInt(
                                Constants.SHARED_PREF_SYNC_STATUS, Constants.SYNC_STATUS_SUCCESS
                            ).apply()

                            val selectedAreaId = sharedPreferences.getLong(
                                Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                                Constants.SHARED_PREF_DEFAULT_INT.toLong()
                            )

                            progressAlertDialog.setMessage("Download complete")
                            progressAlertDialog.dismiss()

                            if (selectedAreaId == Constants.SHARED_PREF_DEFAULT_INT.toLong() || selectedAreaId == areaId) {
                                val downloadedMauzaId = sharedPreferences.getLong(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                )

                                val downloadedMauzaName = sharedPreferences.getString(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME,
                                    Constants.SHARED_PREF_DEFAULT_STRING
                                )

                                val downloadedAreaId = sharedPreferences.getLong(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
                                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                )

                                val downloadedAreaName = sharedPreferences.getString(
                                    Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                                    Constants.SHARED_PREF_DEFAULT_STRING
                                )

                                sharedPreferences.edit().putLong(
                                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                                    downloadedMauzaId
                                ).apply()

                                sharedPreferences.edit().putString(
                                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
                                    downloadedMauzaName
                                ).apply()

                                sharedPreferences.edit().putLong(
                                    Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                                    downloadedAreaId
                                ).apply()

                                sharedPreferences.edit().putString(
                                    Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                                    downloadedAreaName
                                ).apply()

                                //visible the selected mauza area header
                                binding.apply {
                                    if (downloadedMauzaName != "" && downloadedAreaName != "") {
                                        tvSelectedAbadiName.text =
                                            "$downloadedMauzaName\n($downloadedAreaName)"
                                        llSelectedAbadi.visibility = View.VISIBLE
                                    } else {
                                        llSelectedAbadi.visibility = View.INVISIBLE
                                    }
                                }
                                ToastUtil.showShort(
                                    context,
                                    "Data downloaded successfully"
                                )
                            } else {
                                val builder = AlertDialog.Builder(this@MenuActivity)
                                    .setMessage("Do you want to select the current downloaded area to start survey?")
                                    .setPositiveButton("YES") { dialog, _ ->
                                        val downloadedMauzaId = sharedPreferences.getLong(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                        )

                                        val downloadedMauzaName = sharedPreferences.getString(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME,
                                            Constants.SHARED_PREF_DEFAULT_STRING
                                        )

                                        val downloadedAreaId = sharedPreferences.getLong(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID,
                                            Constants.SHARED_PREF_DEFAULT_INT.toLong()
                                        )

                                        val downloadedAreaName = sharedPreferences.getString(
                                            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                                            Constants.SHARED_PREF_DEFAULT_STRING
                                        )

                                        sharedPreferences.edit().putLong(
                                            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                                            downloadedMauzaId
                                        ).apply()

                                        sharedPreferences.edit().putString(
                                            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
                                            downloadedMauzaName
                                        ).apply()

                                        sharedPreferences.edit().putLong(
                                            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                                            downloadedAreaId
                                        ).apply()

                                        sharedPreferences.edit().putString(
                                            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                                            downloadedAreaName
                                        ).apply()

                                        //visible the selected mauza area header
                                        binding.apply {
                                            if (downloadedMauzaName != "" && downloadedAreaName != "") {
                                                tvSelectedAbadiName.text =
                                                    "$downloadedMauzaName\n($downloadedAreaName)"
                                                llSelectedAbadi.visibility = View.VISIBLE
                                            } else {
                                                llSelectedAbadi.visibility = View.INVISIBLE
                                            }
                                        }
                                        ToastUtil.showShort(
                                            context,
                                            "Data downloaded successfully"
                                        )
                                        dialog.dismiss()
                                    }.setNegativeButton("No") { dialog, _ ->
                                        ToastUtil.showShort(
                                            context,
                                            "Data downloaded successfully"
                                        )
                                        dialog.dismiss()
                                    }

                                val dialog = builder.create()
                                dialog.show()

                                val positiveButton =
                                    dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                                val negativeButton =
                                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                                positiveButton.textSize = 16f
                                positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

                                negativeButton.textSize = 16f
                                negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                            }
                            downloadComplete = true
                        }

                    } else {

                        if (!(isFinishing || isDestroyed)) {
                            progressAlertDialog.setMessage("Download Incomplete")
                            progressAlertDialog.dismiss()
                        }

                        val filesDir = File(context.filesDir, "MapTiles/${areaId}")
                        // Recursively delete all files and subdirectories within filesDir, then delete filesDir itself
                        filesDir.deleteRecursively()

                        downloadComplete = false

//                        deleteUnSyncedData()

                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Data Incomplete"
                        )
                    }

                }
            }
        }
    }

    private fun calculateTilesToDownload(
        bufferedExtent: Envelope,
        minZoomLevel: Int,
        maxZoomLevel: Int
    ): Pair<Int, Int> { // Return type is Pair<Int, Int>
        var tilesToDownload: Int
        var currentMaxZoom = maxZoomLevel

        do {
            tilesToDownload = 0

            // Calculate tiles for the current max zoom level
            for (zoomLevel in minZoomLevel..currentMaxZoom) {
                val x1: Int = getTileX(bufferedExtent.xMin, zoomLevel)
                val y1: Int = getTileY(bufferedExtent.yMin, zoomLevel)
                val x2: Int = getTileX(bufferedExtent.xMax, zoomLevel)
                val y2: Int = getTileY(bufferedExtent.yMax, zoomLevel)

                var minX = x1
                var maxX = x2
                var minY = y1
                var maxY = y2 // Assume the min, max values

                // Ensure min/max values are in correct order
                if (minX > maxX) {
                    val temp = minX
                    minX = maxX
                    maxX = temp
                }
                if (minY > maxY) {
                    val temp = minY
                    minY = maxY
                    maxY = temp
                }

                // Calculate the number of tiles to download
                tilesToDownload += (maxX - minX + 1) * (maxY - minY + 1)
            }

            // Check the number of tiles to download
            if (tilesToDownload <= 2000 || currentMaxZoom <= 16) {
                break // Exit if under the limit or at max zoom level
            }

            // Reduce max zoom level
            currentMaxZoom--
        } while (true)

        return Pair(currentMaxZoom, tilesToDownload) // Return max zoom level and tile count
    }

    private fun copyFolder(sourceFolder: File, destinationFolder: File) {
        // Create the destination folder if it doesn't exist
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        // Loop through each file and folder inside the source folder
        sourceFolder.listFiles()?.forEach { file ->
            val destinationFile = File(destinationFolder, file.name)
            if (file.isDirectory) {
                // Recursively copy sub-folders
                copyFolder(file, destinationFile)
            } else {
                // Copy files using InputStream and OutputStream
                file.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun getTileX(lon: Double, zoom: Int): Int {
        var xtile =
            floor((lon + 180) / 360 * (1 shl zoom)).toInt()

        if (xtile < 0) xtile = 0

        if (xtile >= (1 shl zoom)) xtile = ((1 shl zoom) - 1)

        return xtile
    }

    private fun getTileY(lat: Double, zoom: Int): Int {
        var ytile =
            floor((1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 shl zoom))
                .toInt()

        if (ytile < 0) ytile = 0

        if (ytile >= (1 shl zoom)) ytile = ((1 shl zoom) - 1)

        return ytile
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::progressDialogTwo.isInitialized) {
            if (progressDialogTwo.isShowing) {
                progressDialogTwo.dismiss()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::progressAlertDialog.isInitialized) {
            if (progressAlertDialog.isShowing) {
                progressAlertDialog.dismiss()
            }
        }
        if (::progressDialog.isInitialized) {
            if (progressDialog.isShowing) {
                progressDialog.dismiss()
            }
        }
        stopLoadingParcels()

//        deleteUnSyncedData()
    }

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    override fun onResume() {
        super.onResume()
        // Call the function to check parcels and update UI
        checkParcelsAndUpdateUI()
    }

    private fun checkParcelsAndUpdateUI() {
        // Enable or disable the survey button based on database and file checks
        lifecycleScope.launch {
            try {
                checkDatabaseAndFileStatus(1) // Retry up to 1 time
            } catch (e: Exception) {
                ToastUtil.showShort(
                    context,
                    "Resume Error: ${e.message}"
                )
            }
        }
    }

    private suspend fun checkDatabaseAndFileStatus(retries: Int) {

        // Retrieve all SharedPreferences values once
        val loginStatus = sharedPreferences.getInt(
            Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_INACTIVE
        )

        if (loginStatus == Constants.LOGIN_STATUS_INACTIVE) {
            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                startActivity(this)
                finish()
            }
            return
        }

        val userName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        )

        val mouzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        )

        val selectedAreaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val downloadedMauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        )

        val downloadedAreaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        )


        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val areaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()


        val sanitizedAreaName = areaName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val folderKey = "mauza_${mauzaId}_area_${sanitizedAreaName}"


        // Update UI
        binding.apply {
            tvUserName.text = userName

            if (mouzaName.isNullOrEmpty()) {
                tvMouzaCaption.visibility = View.GONE
                tvMouzaName.visibility = View.GONE
            } else {
                tvMouzaCaption.visibility = View.VISIBLE
                tvMouzaName.text = mouzaName
                tvMouzaName.visibility = View.VISIBLE
            }

            if (selectedAreaId != Constants.SHARED_PREF_DEFAULT_INT.toLong() && !downloadedMauzaName.isNullOrEmpty() && !downloadedAreaName.isNullOrEmpty()) {
                tvSelectedAbadiName.text = "$downloadedMauzaName\n($downloadedAreaName)"
                llSelectedAbadi.visibility = View.VISIBLE
            } else {
                llSelectedAbadi.visibility = View.INVISIBLE
            }
        }


        val parcelsCount =
            withContext(Dispatchers.IO) {
                database.activeParcelDao().getParcelsCountByMauzaAndArea(mauzaId, areaName)
            }

        val sdCardRoot = context.filesDir

        val file = when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
                val filesDir = File(sdCardRoot, "MapTpk/${folderKey}")
                File(filesDir, "${folderKey}_${folderKey}.tpk")
            }

            DownloadType.TILES -> {
                File(sdCardRoot, "MapTiles/${folderKey}")
            }
        }

        if (parcelsCount > 0 && file.exists()) {
            withContext(Dispatchers.Main) {
                binding.apply {
                    cvStartSurvey.isEnabled = true
                    cvStartSurvey.isFocusable = true

                    cvPropertyList.isEnabled = true
                    cvPropertyList.isFocusable = true

                    llStartSurvey.setBackgroundColor(Color.WHITE)
                    llPropertyList.setBackgroundColor(Color.WHITE)
                }
            }
        } else {
            if (retries > 0) {
                delay(100) // Delay for 1 second before retrying
                checkDatabaseAndFileStatus(retries - 1)
            } else {
                withContext(Dispatchers.Main) {
                    binding.apply {
                        cvStartSurvey.isEnabled = false
                        cvStartSurvey.isFocusable = false

                        cvPropertyList.isEnabled = false
                        cvPropertyList.isFocusable = false

                        llStartSurvey.setBackgroundColor(Color.LTGRAY)
                        llPropertyList.setBackgroundColor(Color.LTGRAY)
                    }
                }
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return try {
            val inflater = menuInflater
            inflater.inflate(R.menu.item_options_menu, menu)
            // Tint menu icons based on current theme
            menu?.let { updateMenuIconColors(it) }
            true
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error creating options menu: ${e.message}")
            super.onCreateOptionsMenu(menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> {
                Intent(this@MenuActivity, SettingsActivity::class.java).apply {
                    startActivity(this)
                }
                return true
            }

            R.id.action_theme -> {
                showThemeDialog()
                return true
            }

            R.id.action_logout -> {
                startLogout()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeDialog() {
        try {
            val options = arrayOf("Light", "Dark", "System Default")
            val currentMode = when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_NO -> 0
                AppCompatDelegate.MODE_NIGHT_YES -> 1
                else -> 2
            }

            AlertDialog.Builder(this)
                .setTitle("Choose Theme")
                .setSingleChoiceItems(options, currentMode) { dialog, which ->
                    try {
                        val newMode = when (which) {
                            0 -> AppCompatDelegate.MODE_NIGHT_NO
                            1 -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }

                        // Use the Application helper method to update theme
                        MyApplication.updateTheme(this, newMode)

                        dialog.dismiss()

                        // Recreate activity to apply theme immediately
                        recreateWithDelay()
                    } catch (e: Exception) {
                        Log.e("MenuActivity", "Error applying theme: ${e.message}")
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error showing theme dialog: ${e.message}")
        }
    }

    private fun recreateWithDelay() {
        // Small delay to ensure theme is applied
        Handler(Looper.getMainLooper()).postDelayed({
            recreate()
        }, 100)
    }

    private fun applySavedTheme() {
        val savedTheme =
            sharedPreferences.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
    }

    private fun setupActionBar() {
        try {
            supportActionBar?.apply {
                setDisplayShowTitleEnabled(true)
                elevation = 4f
                // Set ActionBar title to uppercase
                title = title?.toString()?.uppercase()
            }

            // Setup status bar
            setupStatusBar()
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error setting up action bar: ${e.message}")
        }
    }

    private fun setupStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                val isDarkMode = isDarkModeActive()

                // Set status bar color based on theme
                window.statusBarColor = if (isDarkMode) {
                    ContextCompat.getColor(this, R.color.dark_primary)
                } else {
                    ContextCompat.getColor(this, R.color.forest_green)
                }

                // Set status bar icon color (always light for both themes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val flags = window.decorView.systemUiVisibility
                    window.decorView.systemUiVisibility =
                        flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error setting up status bar: ${e.message}")
        }
    }

    private fun isDarkModeActive(): Boolean {
        return try {
            when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
                Configuration.UI_MODE_NIGHT_YES -> true
                Configuration.UI_MODE_NIGHT_NO -> false
                else -> false
            }
        } catch (e: Exception) {
            Log.e("MenuActivity", "Error checking dark mode: ${e.message}")
            false
        }
    }


    private fun updateMenuIconColors(menu: Menu) {
        val isDarkMode = isDarkModeActive()
        val iconColor = if (isDarkMode) {
            ContextCompat.getColor(this, R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.white)
        }

        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            menuItem.icon?.let { icon ->
                val wrappedIcon = DrawableCompat.wrap(icon)
                DrawableCompat.setTint(wrappedIcon, iconColor)
                menuItem.icon = wrappedIcon
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Update UI elements when configuration changes (like system theme change)
        setupStatusBar()
        invalidateOptionsMenu() // This will call onCreateOptionsMenu again
    }


}