package pk.gop.pulse.katchiAbadi.ui.activities

import OwnerResponse
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
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
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
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.MyApplication
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.DownloadFileTask
import pk.gop.pulse.katchiAbadi.common.DownloadType
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaDetail
import pk.gop.pulse.katchiAbadi.data.repository.JKGrowerRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.LookupRepository
import pk.gop.pulse.katchiAbadi.data.repository.OfficerRepository
import pk.gop.pulse.katchiAbadi.data.repository.TaskRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivityMenuBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.LogoutUseCase
import pk.gop.pulse.katchiAbadi.presentation.base.BaseActivity
import pk.gop.pulse.katchiAbadi.presentation.menu.MenuViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.IntentUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlin.coroutines.resume

@Suppress("DEPRECATION")
@AndroidEntryPoint
class MenuActivity : BaseActivity() {

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

    @Inject
    lateinit var logoutUseCase: LogoutUseCase

    private val logoutScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Inject
    lateinit var jkGrowerRepository: JKGrowerRepositoryImpl

    private var job: Job? = null
    private lateinit var progressAlertDialog: AlertDialog

    /** Custom download-progress dialog ka inflated view. */
    private var progressView: View? = null

    private lateinit var tileManager: TileManager
    private val wgs84 by lazy {
        SpatialReferences.getWgs84()
    }

    private var downloadComplete: Boolean? = null

    private var downloadFileTask: DownloadFileTask? = null

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var officerRepository: OfficerRepository

    @Inject
    lateinit var lookupRepository: LookupRepository


    override fun onCreate(savedInstanceState: Bundle?) {

        sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        applySavedTheme()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@MenuActivity
        setupActionBar()


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

                    progressDialogTwo = ProgressDialog(this@MenuActivity).apply {
                        setMessage("Updating database, please wait...")
                        setCancelable(false)
                        show()
                    }

                    try {
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

                        withContext(Dispatchers.Main) {
                            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                                startActivity(this)
                                finish()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
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

        binding.cvPendingTasks.setOnClickListener {
            startActivity(Intent(this@MenuActivity, PendingTasksActivity::class.java))
        }

        // Live badge: shows pending count on the tile title
        lifecycleScope.launch {
            taskRepository.livePendingCount().collect { count ->
                binding.tvPendingTasksTitle.text = if (count > 0) {
                    "PENDING TASKS ($count)"
                } else {
                    "PENDING TASKS"
                }
            }
        }

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            cvSyncData.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                if (Utility.checkInternetConnection(this@MenuActivity)) {
                    // Koi download limit nahi - seedha mauza list mangwao
                    viewModel.mauzaNewData()
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }

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

            cvMapAnalysis.setOnClickListener {
//                if (!Utility.checkInternetConnection(this@MenuActivity)) {
//                    Utility.dialog(context, "Map analysis needs an internet connection.", "No Internet!")
//                    return@setOnClickListener
//                }
                startActivity(Intent(this@MenuActivity, MapAnalysisActivity::class.java))
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

                                ToastUtil.showShort(this@MenuActivity, "Session expired")
                            } else {
                                ToastUtil.showShort(context, msg)
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
                            ToastUtil.showShort(context, "No Mauzas assigned to this user.")
                            return@collect
                        }

                        showMauzaSelectionDialog(
                            sortedMauzas = mauzaList.sortedBy { mauza -> mauza.mauzaName },
                            meterDistance = settings?.meterDistance,
                            meterAccuracy = settings?.meterAccuracy,
                            allowedAreas = settings?.allowedDownloadableAreas,
                            minScale = settings?.minScaleTiles,
                            maxScale = settings?.maxScaleTiles
                        )
                    }

                    is ResourceSealed.Error -> {
                        Utility.dismissProgressAlertDialog()
                        ToastUtil.showShort(context, it.message ?: "Something went wrong")
                    }

                    else -> Unit
                }
            }
        }
    }


    // =====================================================================
    //  STEP 1 : MAUZA SELECTION  (custom layout, no system title/buttons)
    // =====================================================================
    private fun showMauzaSelectionDialog(
        sortedMauzas: List<MauzaDetail>,
        meterDistance: String?,
        meterAccuracy: String?,
        allowedAreas: String?,
        minScale: String?,
        maxScale: String?
    ) {
        val view = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_mouza_list, null)

        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
        val mauzaAdapter = ArrayAdapter(
            this@MenuActivity,
            R.layout.spinner_item_drop_down,
            sortedMauzas.map { it.mauzaName }
        )
        // popup ki rows alag layout se, warna selected item wali layout hi use hoti hai
        mauzaAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = mauzaAdapter

        val dialog = AlertDialog.Builder(this@MenuActivity, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btn_cancel_mauza).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btn_next_mauza).setOnClickListener {
            dialog.dismiss()

            val selectedMauza = sortedMauzas[spinner.selectedItemPosition]

            sharedPreferences.edit()
                .putLong(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA, selectedMauza.mauzaId)
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
                    meterDistance?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_DISTANCE
                )
                .putInt(
                    Constants.SHARED_PREF_METER_ACCURACY,
                    meterAccuracy?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_ACCURACY
                )
                .putInt(
                    Constants.SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS,
                    allowedAreas?.toIntOrNull()
                        ?: Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
                )
                .putInt(
                    Constants.SHARED_PREF_MAP_MIN_SCALE,
                    minScale?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_MIN_SCALE
                )
                .putInt(
                    Constants.SHARED_PREF_MAP_MAX_SCALE,
                    maxScale?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_MAX_SCALE
                )
                .putInt(Constants.SHARED_PREF_ALLOW_DOWNLOAD_SAVED_DATA, 0)
                .apply()

            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

            lifecycleScope.launch {
                try {
                    Utility.showProgressAlertDialog(this@MenuActivity, "Fetching areas...")

                    val areaResponse = serverApi.getAreasByMauzaId(
                        selectedMauza.mauzaId,
                        "Bearer $token"
                    )

                    Utility.dismissProgressAlertDialog()

                    val areaList = areaResponse.areas
                    if (areaList.isEmpty()) {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "No areas found for this Mauza."
                        )
                        return@launch
                    }

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

        dialog.show()
    }


    // =====================================================================
    //  STEP 2 : AREA SELECTION
    // =====================================================================
    private fun showAreaSelectionDialog(mauza: MauzaDetail, areaList: List<String>) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_area_list, null)
        val spinner = view.findViewById<Spinner>(R.id.spn_areas)

        val areaAdapter = ArrayAdapter(this, R.layout.spinner_item_drop_down, areaList)
        areaAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = areaAdapter

        val dialog = AlertDialog.Builder(this, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btn_cancel_area).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btn_download_area).setOnClickListener {
            dialog.dismiss()

            val selectedArea = spinner.selectedItem as String
            val groupId = selectedArea.toLongOrNull() ?: 0L

            askTileScope(mauza.mauzaName, selectedArea) { scope ->
                lifecycleScope.launch {
                    showDownloadProgressDialog()

                    val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

                    val result = fetchAndStoreActiveParcels(
                        mauzaId = mauza.mauzaId,
                        mauzaName = mauza.mauzaName,
                        areaName = selectedArea,
                        unitId = mauza.unit.toLong(),
                        groupId = groupId,
                        token = "Bearer $token"
                    )

                    when (result) {
                        is Resource.Success -> downloadMapTilesNew(scope)
                        is Resource.Error -> {
                            if (::progressAlertDialog.isInitialized && progressAlertDialog.isShowing) {
                                progressAlertDialog.dismiss()
                            }
                            ToastUtil.showShort(this@MenuActivity, result.message ?: "Download failed.")
                        }
                        else -> Unit
                    }
                }
            }
        }

        dialog.show()
    }

    private fun askTileScope(
        mauzaName: String,
        areaName: String,
        onPicked: (TileScope) -> Unit
    ) {
        val view = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_map_scope, null)

        val dialog = AlertDialog.Builder(this@MenuActivity, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()

        // Let the CardView's rounded corners show through
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.tv_scope_area_title).text = areaName.uppercase(Locale.US)
        view.findViewById<TextView>(R.id.tv_scope_mauza_title).text = mauzaName.uppercase(Locale.US)

        view.findViewById<View>(R.id.card_scope_area).setOnClickListener {
            dialog.dismiss()
            onPicked(TileScope.AREA)
        }

        view.findViewById<View>(R.id.card_scope_mauza).setOnClickListener {
            dialog.dismiss()
            onPicked(TileScope.FULL_MAUZA)
        }

        view.findViewById<View>(R.id.btn_scope_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    /** Downloaded area ke parcels se extent (mojooda behaviour). */
    private suspend fun extentFromArea(mauzaId: Long, areaName: String): Envelope? {
        val parcels = database.activeParcelDao().getParcelsByMauzaAndArea(mauzaId, areaName)

        val polygonsList = mutableListOf<Polygon>()
        for (parcel in parcels) {
            try {
                val multi = Utility.getMultiPolygonFromString(parcel.geomWKT, wgs84)
                multi.mapTo(polygonsList) { Utility.simplifyPolygon(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (polygonsList.isEmpty()) return null

        val combined = GeometryEngine.union(polygonsList)
        return (GeometryEngine.buffer(combined.extent, 0.0001) as Polygon).extent
    }

    /** Poore mauze ki extent — analytics API se (local DB me sirf ek area hota hai). */
    private suspend fun extentFromWholeMauza(mauzaId: Long): Envelope? {
        return try {
            val rows = serverApi.getAnalyticsParcels(mauzaId.toInt(), null).features.orEmpty()
            Log.d("TILE_SCOPE", "Analytics rows for extent: ${rows.size}")

            val polys = mutableListOf<Polygon>()
            for (row in rows) {
                val wkt = row.geometryWKT ?: continue
                try {
                    when {
                        wkt.contains("MULTIPOLYGON") ->
                            Utility.getMultiPolygonFromString(wkt, wgs84)
                                ?.mapTo(polys) { Utility.simplifyPolygon(it) }

                        wkt.contains("POLYGON") ->
                            Utility.getPolygonFromString(wkt, wgs84)
                                ?.let { polys.add(Utility.simplifyPolygon(it)) }
                    }
                } catch (e: Exception) {
                    Log.w("TILE_SCOPE", "WKT skip: ${e.message}")
                }
            }
            if (polys.isEmpty()) return null

            val combined = GeometryEngine.union(polys)
            (GeometryEngine.buffer(combined.extent, 0.0001) as Polygon).extent
        } catch (e: Exception) {
            Log.e("TILE_SCOPE", "Mauza extent failed: ${e.message}", e)
            null
        }
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
            Log.d("FETCH_DEBUG", "STEP 1: Calling parcels API")
            Log.d("FETCH_DEBUG", "mauzaId: $mauzaId, areaName: '$areaName'")

            withContext(Dispatchers.Main) {
                markStep(1, StepState.ACTIVE)
                setStepMessage("Fetching parcel records...")
            }

            val rawResponse = serverApi.getActiveParcelsByMauzaAndArea(mauzaId, areaName)
            Log.d("FETCH_DEBUG", "STEP 1 Response code: ${rawResponse.code()}")

            if (!rawResponse.isSuccessful) {
                val errorBody = rawResponse.errorBody()?.string() ?: "Unknown error"
                Log.e("FETCH_DEBUG", "STEP 1 Error: $errorBody")
                return Resource.Error("Server error: $errorBody")
            }

            val response = rawResponse.body()
            if (response == null) {
                Log.e("FETCH_DEBUG", "STEP 2 Body is null")
                return Resource.Error("Empty response")
            }

            val parcels = response.parcelsData ?: emptyList()
            Log.d("FETCH_DEBUG", "STEP 2 Got ${parcels.size} parcels")

            withContext(Dispatchers.Main) {
                setStepDetail(1, "${parcels.size}")
                setStepMessage("Saving ${parcels.size} parcels...")
            }

            database.withTransaction {
                database.activeParcelDao().deleteParcelsByMauzaAndArea(mauzaId, areaName)
                val entities = parcels.map { parcelDto ->
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
                        groupId = groupId,
                        growerCodes = parcelDto.growerCodes
                    )
                }
                database.activeParcelDao().insertActiveParcels(entities)
            }
            Log.d("FETCH_DEBUG", "STEP 3 Parcels inserted")

            withContext(Dispatchers.Main) {
                markStep(1, StepState.DONE)
                markStep(2, StepState.ACTIVE)
                setStepMessage("Fetching grower list...")
            }

            // JK growers ko mauza-wise sync karo (offline ke liye)
            try {
                val growers = jkGrowerRepository.syncGrowersForMouza(mauzaId, forceRefresh = true)
                Log.d("FETCH_DEBUG", "STEP 4 Synced ${growers.size} JK growers")

                withContext(Dispatchers.Main) {
                    setStepDetail(2, "${growers.size}")
                    markStep(2, StepState.DONE)
                }
            } catch (e: Exception) {
                Log.e("FETCH_DEBUG", "STEP 4 JK growers sync failed: ${e.message}")

                // grower list na mile to bhi survey chal sakta hai
                withContext(Dispatchers.Main) {
                    setStepDetail(2, "skipped")
                    markStep(2, StepState.DONE)
                }
            }

            sharedPreferences.edit()
                .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, mauzaId)
                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, mauzaName)
                .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, areaName)
                .apply()

            Resource.Success(Unit)

        } catch (e: Exception) {
            Log.e("FETCH_PARCELS", "Error: ${e.message}", e)
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

        val builder = AlertDialog.Builder(this)
            .setTitle("Exit!")
            .setCancelable(false)
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton(yesText) { _, _ ->
                Utility.showProgressAlertDialog(this, "Please wait...")
                performLogout()
            }
            .setNegativeButton(noText, null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
    }

    private fun performLogout() {
        val userId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        if (userId == Constants.SHARED_PREF_DEFAULT_INT.toLong()) {
            Utility.dismissProgressAlertDialog()
            clearLocalDataAndNavigate()
            return
        }

        logoutScope.launch {
            try {
                logoutUseCase(userId, "Android").collect { result ->
                    when (result) {
                        is Resource.Success -> {
                            Utility.dismissProgressAlertDialog()
                            ToastUtil.showShort(this@MenuActivity, "Logged out successfully")
                            delay(800)
                            clearLocalDataAndNavigate()
                        }

                        is Resource.Error -> {
                            Utility.dismissProgressAlertDialog()
                            Toast.makeText(
                                this@MenuActivity,
                                "Logged out locally",
                                Toast.LENGTH_SHORT
                            ).show()
                            clearLocalDataAndNavigate()
                        }

                        is Resource.Loading -> Unit

                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("LOGOUT", "Logout exception: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Session ended")
                delay(800)
                clearLocalDataAndNavigate()
            }
        }
    }

    private fun clearLocalDataAndNavigate() {
        sharedPreferences.edit()
            .putInt(Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_INACTIVE)
            .putLong(Constants.SHARED_PREF_USER_ID, Constants.SHARED_PREF_DEFAULT_INT.toLong())
            .putString(Constants.SHARED_PREF_USER_CNIC, Constants.SHARED_PREF_DEFAULT_STRING)
            .putString(Constants.SHARED_PREF_USER_NAME, Constants.SHARED_PREF_DEFAULT_STRING)
            .putString(Constants.SHARED_PREF_TOKEN, Constants.SHARED_PREF_DEFAULT_STRING)
            .apply()

        Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(this)
            finish()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (::progressDialogTwo.isInitialized) {
            if (progressDialogTwo.isShowing) {
                progressDialogTwo.dismiss()
            }
        }
        logoutScope.cancel()
    }

    private fun downloadMapNew() {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> Unit
            DownloadType.TILES -> downloadMapTilesNew()
        }
    }

    private fun downloadMap() {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> Unit
            DownloadType.TILES -> downloadMapTiles()
        }
    }


    // =====================================================================
    //  PROGRESS DIALOG helpers
    // =====================================================================
    private fun showDownloadProgressDialog() {
        progressView = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_downlaod_progress, null)

        progressAlertDialog = AlertDialog.Builder(this@MenuActivity, R.style.ZdDialog)
            .setView(progressView)
            .setCancelable(false)
            .create()

        // tilesToDownload abhi maloom nahi, is liye bar ghoomti rahe
        progressView?.findViewById<ProgressBar>(R.id.progress_download)?.isIndeterminate = true

        markStep(1, StepState.PENDING)
        markStep(2, StepState.PENDING)
        markStep(3, StepState.PENDING)

        progressAlertDialog.show()
    }

    private fun setDownloadTotal(total: Int) {
        progressView?.findViewById<ProgressBar>(R.id.progress_download)?.apply {
            isIndeterminate = false
            progress = 0
        }
        progressView?.findViewById<TextView>(R.id.tv_progress_percent)?.text = "0%"
        progressView?.findViewById<TextView>(R.id.tv_progress_message)?.text =
            "$total tiles to download"
    }

    private enum class TileScope { AREA, FULL_MAUZA }

    private enum class StepState { PENDING, ACTIVE, DONE }

    private fun markStep(step: Int, state: StepState) {
        val v = progressView ?: return

        val flId = when (step) {
            1 -> R.id.fl_step_1; 2 -> R.id.fl_step_2; else -> R.id.fl_step_3
        }
        val ivId = when (step) {
            1 -> R.id.iv_step_1; 2 -> R.id.iv_step_2; else -> R.id.iv_step_3
        }
        val tvId = when (step) {
            1 -> R.id.tv_step_1; 2 -> R.id.tv_step_2; else -> R.id.tv_step_3
        }

        val disc = v.findViewById<View>(flId)
        val icon = v.findViewById<ImageView>(ivId)
        val label = v.findViewById<TextView>(tvId)

        when (state) {
            StepState.PENDING -> {
                disc.setBackgroundResource(R.drawable.zd_step_pending)
                icon.visibility = View.INVISIBLE
                label.setTextColor(Color.parseColor("#8CA893"))
            }

            StepState.ACTIVE -> {
                disc.setBackgroundResource(R.drawable.zd_step_active)
                icon.setImageResource(R.drawable.zd_step_dot)
                icon.visibility = View.VISIBLE
                label.setTextColor(Color.parseColor("#1B5E20"))
            }

            StepState.DONE -> {
                disc.setBackgroundResource(R.drawable.zd_step_done)
                icon.setImageResource(R.drawable.ic_check_white_12)
                icon.visibility = View.VISIBLE
                label.setTextColor(Color.parseColor("#4C7A4C"))
            }
        }
    }

    /** Steps ke neeche wali line (percent ke saath). */
    private fun setStepMessage(text: String) {
        progressView?.findViewById<TextView>(R.id.tv_progress_message)?.text = text
    }

    private fun setStepDetail(step: Int, text: String) {
        val id = when (step) {
            1 -> R.id.tv_step_1_detail; 2 -> R.id.tv_step_2_detail
            else -> R.id.tv_step_3_detail
        }
        progressView?.findViewById<TextView>(id)?.text = text
    }

    private fun setDownloadProgress(current: Int, total: Int) {
        val pct = if (total > 0) (current * 100) / total else 0
        progressView?.findViewById<TextView>(R.id.tv_progress_percent)?.text = "$pct%"
        progressView?.findViewById<ProgressBar>(R.id.progress_download)?.progress = pct
        progressView?.findViewById<TextView>(R.id.tv_progress_message)?.text =
            "Downloading tile $current of $total"
    }


    private fun downloadMapTilesNew(scope: TileScope = TileScope.AREA) {
        downloadComplete = false

        if (!::progressAlertDialog.isInitialized || !progressAlertDialog.isShowing) {
            showDownloadProgressDialog()
        }

        markStep(3, StepState.ACTIVE)
        setStepMessage(
            if (scope == TileScope.FULL_MAUZA) "Preparing full mauza map..."
            else "Preparing offline map..."
        )

        tileManager = TileManager(this)

        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )
        val areaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()

        val sanitizedAreaName = areaName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val folderKey = "mauza_${mauzaId}_area_${sanitizedAreaName}"   // ← wahi purana key

        val minZoomLevel = sharedPreferences.getInt(
            Constants.SHARED_PREF_MAP_MIN_SCALE,
            Constants.SHARED_PREF_DEFAULT_MIN_SCALE
        )
        val maxZoomLevel = 16

        var currentTile = 0
        var relevantMaxZoom = 0
        var tilesToDownload = 0
        var aborted = false

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // ---- Extent depends on the chosen scope ----
                var rawExtent = if (scope == TileScope.FULL_MAUZA) {
                    extentFromWholeMauza(mauzaId)
                } else {
                    extentFromArea(mauzaId, areaName)
                }

                if (rawExtent == null && scope == TileScope.FULL_MAUZA) {
                    Log.w("TILE_SCOPE", "Mauza extent unavailable, falling back to area")
                    withContext(Dispatchers.Main) {
                        ToastUtil.showShort(
                            this@MenuActivity,
                            "Full mauza data unavailable — downloading the selected area instead."
                        )
                    }
                    rawExtent = extentFromArea(mauzaId, areaName)
                }

                if (rawExtent == null) {
                    aborted = true
                    withContext(Dispatchers.Main) {
                        ToastUtil.showShort(this@MenuActivity, "No valid geometry found.")
                    }
                    return@launch
                }

                val extent = rawExtent

                // ---- Count tiles ----
                val result = calculateTilesToDownload(extent, minZoomLevel, maxZoomLevel)
                relevantMaxZoom = result.first
                tilesToDownload = result.second

                // ---- Warn before a huge download ----
                if (scope == TileScope.FULL_MAUZA && tilesToDownload > 3000) {
                    val proceed = withContext(Dispatchers.Main) {
                        confirmLargeDownload(tilesToDownload)
                    }
                    if (!proceed) {
                        aborted = true
                        Log.d("TILE_SCOPE", "User cancelled large download ($tilesToDownload tiles)")
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    setDownloadTotal(tilesToDownload)
                    Log.e("tiles", "downloadMapTilesNew ($scope): $tilesToDownload")
                }

                // ---- Download loop ----
                for (zoomLevel in minZoomLevel..relevantMaxZoom) {
                    val x1 = getTileX(extent.xMin, zoomLevel)
                    val y1 = getTileY(extent.yMin, zoomLevel)
                    val x2 = getTileX(extent.xMax, zoomLevel)
                    val y2 = getTileY(extent.yMax, zoomLevel)

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
                                    folderKey,
                                    "cached",
                                    minZoomLevel,
                                    relevantMaxZoom
                                )
                                withContext(Dispatchers.Main) {
                                    setDownloadProgress(currentTile, tilesToDownload)
                                }
                            }
                        }
                    }
                }

            } finally {
                withContext(Dispatchers.Main) {

                    // Cancelled or nothing to do — leave previously downloaded tiles alone
                    if (aborted) {
                        if (!(isFinishing || isDestroyed) &&
                            ::progressAlertDialog.isInitialized &&
                            progressAlertDialog.isShowing
                        ) {
                            progressAlertDialog.dismiss()
                        }
                        ToastUtil.showShort(this@MenuActivity, "Map download cancelled")
                        return@withContext
                    }

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

                            markStep(3, StepState.DONE)
                            setStepDetail(3, "$tilesToDownload tiles")
                            setStepMessage("Download complete")
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
                            progressAlertDialog.dismiss()
                        }

                        destinationFile.deleteRecursively()
                        downloadComplete = false
                        ToastUtil.showShort(this@MenuActivity, "Download Incomplete")
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
                tvSelectedAbadiName.text = "$downloadedMauzaName ($downloadedAreaName)"
                llSelectedAbadi.visibility = View.VISIBLE
            } else {
                llSelectedAbadi.visibility = View.INVISIBLE
            }
        }
    }

    private fun downloadMapTiles() {

        downloadComplete = false

        showDownloadProgressDialog()

        tileManager = TileManager(this)

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
                            polygonsList.add(Utility.simplifyPolygon(polygon))
                        }
                    } else if (parcelGeom.contains("POLYGON ((")) {
                        Utility.getPolygonFromString(parcelGeom, wgs84)?.let { parsedPolygon ->
                            polygonsList.add(Utility.simplifyPolygon(parsedPolygon))
                        } ?: Log.w("downloadMapTiles", "Skipped invalid POLYGON WKT: $parcelGeom")
                    } else {
                        Utility.getPolyFromString(parcelGeom, wgs84)?.let { parsedPolygon ->
                            polygonsList.add(Utility.simplifyPolygon(parsedPolygon))
                        } ?: Log.w("downloadMapTiles", "Skipped invalid geometry: $parcelGeom")
                    }
                }

                val combinedGeometry = GeometryEngine.union(polygonsList)
                val combinedExtent = combinedGeometry.extent
                val bufferedGeometry =
                    GeometryEngine.buffer(combinedExtent, 0.0001) as Polygon
                val bufferedExtent = bufferedGeometry.extent

                val result =
                    calculateTilesToDownload(bufferedExtent, minZoomLevel, maxZoomLevel)
                relevantMaxZoom = result.first
                tilesToDownload = result.second

                withContext(Dispatchers.Main) {
                    setDownloadTotal(tilesToDownload)
                }

                for (zoomLevel in minZoomLevel..relevantMaxZoom) {
                    val x1: Int = getTileX(bufferedExtent.xMin, zoomLevel)
                    val y1: Int = getTileY(bufferedExtent.yMin, zoomLevel)
                    val x2: Int = getTileX(bufferedExtent.xMax, zoomLevel)
                    val y2: Int = getTileY(bufferedExtent.yMax, zoomLevel)

                    var minX = x1
                    var maxX = x2
                    var minY = y1
                    var maxY = y2

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

                    for (tileX in minX..maxX) {
                        for (tileY in minY..maxY) {
                            if (Utility.checkInternetConnection(this@MenuActivity)) {
                                currentTile++
                                tileManager.downloadAndCacheTile(
                                    zoomLevel,
                                    tileY,
                                    tileX,
                                    areaId,
                                    "cached",
                                    minZoomLevel,
                                    relevantMaxZoom,
                                )
                                withContext(Dispatchers.Main) {
                                    setDownloadProgress(currentTile, tilesToDownload)
                                }
                            }
                        }
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
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

                            progressAlertDialog.dismiss()

                            if (selectedAreaId == Constants.SHARED_PREF_DEFAULT_INT.toLong() ||
                                selectedAreaId == areaId
                            ) {
                                updateSelectedMauzaAndArea()
                                ToastUtil.showShort(context, "Data downloaded successfully")
                            } else {
                                val builder = AlertDialog.Builder(this@MenuActivity)
                                    .setMessage("Do you want to select the current downloaded area to start survey?")
                                    .setPositiveButton("YES") { dlg, _ ->
                                        updateSelectedMauzaAndArea()
                                        ToastUtil.showShort(
                                            context,
                                            "Data downloaded successfully"
                                        )
                                        dlg.dismiss()
                                    }.setNegativeButton("No") { dlg, _ ->
                                        ToastUtil.showShort(
                                            context,
                                            "Data downloaded successfully"
                                        )
                                        dlg.dismiss()
                                    }

                                val dlg = builder.create()
                                dlg.show()

                                dlg.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
                                    textSize = 16f
                                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                                }
                                dlg.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
                                    textSize = 16f
                                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                                }
                            }
                            downloadComplete = true
                        }

                    } else {

                        if (!(isFinishing || isDestroyed)) {
                            progressAlertDialog.dismiss()
                        }

                        File(context.filesDir, "MapTiles/${areaId}").deleteRecursively()
                        downloadComplete = false

                        ToastUtil.showShort(this@MenuActivity, "Data Incomplete")
                    }
                }
            }
        }
    }

    /** Asks the user before a big tile download. Returns true to proceed. */
    private suspend fun confirmLargeDownload(tiles: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            val estMb = tiles * 25.0 / 1024.0               // rough: ~25 KB per tile
            val estMinutes = (tiles / 120).coerceAtLeast(1) // rough: ~2 tiles/sec

            val view = LayoutInflater.from(this@MenuActivity)
                .inflate(R.layout.dialog_large_download, null)

            view.findViewById<TextView>(R.id.tv_large_download_message).text =
                "Downloading the entire mauza will fetch $tiles map tiles " +
                        "(roughly ${String.format(Locale.US, "%.0f MB", estMb)}, $estMinutes+ minutes).\n\n" +
                        "Choosing the selected area only keeps this much smaller."

            val dialog = AlertDialog.Builder(this@MenuActivity, R.style.ZdDialog)
                .setView(view)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            var answered = false

            // Always resume, otherwise the coroutine would hang forever
            dialog.setOnDismissListener {
                if (!answered && cont.isActive) cont.resume(false)
            }

            view.findViewById<View>(R.id.btn_large_confirm).setOnClickListener {
                answered = true
                dialog.dismiss()
                if (cont.isActive) cont.resume(true)
            }

            view.findViewById<View>(R.id.btn_large_cancel).setOnClickListener {
                answered = true
                dialog.dismiss()
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation { if (dialog.isShowing) dialog.dismiss() }
            dialog.show()
        }

    private fun calculateTilesToDownload(
        bufferedExtent: Envelope,
        minZoomLevel: Int,
        maxZoomLevel: Int
    ): Pair<Int, Int> {
        var tilesToDownload: Int
        var currentMaxZoom = maxZoomLevel

        do {
            tilesToDownload = 0

            for (zoomLevel in minZoomLevel..currentMaxZoom) {
                val x1: Int = getTileX(bufferedExtent.xMin, zoomLevel)
                val y1: Int = getTileY(bufferedExtent.yMin, zoomLevel)
                val x2: Int = getTileX(bufferedExtent.xMax, zoomLevel)
                val y2: Int = getTileY(bufferedExtent.yMax, zoomLevel)

                var minX = x1
                var maxX = x2
                var minY = y1
                var maxY = y2

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

                tilesToDownload += (maxX - minX + 1) * (maxY - minY + 1)
            }

            if (tilesToDownload <= 2000 || currentMaxZoom <= 16) {
                break
            }

            currentMaxZoom--
        } while (true)

        return Pair(currentMaxZoom, tilesToDownload)
    }

    private fun copyFolder(sourceFolder: File, destinationFolder: File) {
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs()
        }

        sourceFolder.listFiles()?.forEach { file ->
            val destinationFile = File(destinationFolder, file.name)
            if (file.isDirectory) {
                copyFolder(file, destinationFile)
            } else {
                file.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun getTileX(lon: Double, zoom: Int): Int {
        var xtile = floor((lon + 180) / 360 * (1 shl zoom)).toInt()
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
    }

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    override fun onResume() {
        super.onResume()
        checkParcelsAndUpdateUI()
        lifecycleScope.launch {
            if (Utility.checkInternetConnection(this@MenuActivity)) {
                officerRepository.refreshFromServer()
                lookupRepository.refreshAll()
            }
        }
    }

    private fun checkParcelsAndUpdateUI() {
        lifecycleScope.launch {
            try {
                checkDatabaseAndFileStatus(1)
            } catch (e: Exception) {
                ToastUtil.showShort(context, "Resume Error: ${e.message}")
            }
        }
    }

    private suspend fun checkDatabaseAndFileStatus(retries: Int) {

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

        binding.apply {
            if (mouzaName.isNullOrEmpty()) {
                tvMouzaCaption.visibility = View.GONE
                tvMouzaName.visibility = View.GONE
            } else {
                tvMouzaCaption.visibility = View.VISIBLE
                tvMouzaName.text = mouzaName
                tvMouzaName.visibility = View.VISIBLE
            }

            if (!downloadedMauzaName.isNullOrEmpty() && !downloadedAreaName.isNullOrEmpty()) {
                tvSelectedAbadiName.text = "$downloadedMauzaName ($downloadedAreaName)"
                llSelectedAbadi.visibility = View.VISIBLE
            } else {
                llSelectedAbadi.visibility = View.INVISIBLE
            }
        }

        supportActionBar?.title = if (userName.isNullOrBlank()) {
            "Land Survey"
        } else {
            "Welcome, $userName"
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
                delay(100)
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
            menuInflater.inflate(R.menu.item_options_menu, menu)
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

            R.id.action_clear_cache -> {
                confirmClearCache()
                return true
            }

            R.id.action_clear_data -> {
                confirmClearAllData()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    // ---------------------------------------------------------------------
    //  CLEAR CACHE  -  temporary files only
    // ---------------------------------------------------------------------
    private fun confirmClearCache() {
        if (job != null) {
            ZdAlert.warn(
                this,
                "Download in Progress",
                "Clearing the cache now would discard the download that is currently running. Please let it finish first."
            )
            return
        }

        lifecycleScope.launch {
            val sizeMb = withContext(Dispatchers.IO) {
                folderSizeBytes(cacheDir) / (1024.0 * 1024.0)
            }

            ZdAlert.confirm(
                context = this@MenuActivity,
                title = "Clear Cache?",
                message = "This will remove ${
                    String.format(Locale.US, "%.1f MB", sizeMb)
                } of temporary files. Your downloaded maps, saved surveys and records will not be affected.",
                confirmText = "Clear Cache",
                iconRes = R.drawable.ic_broom_28
            ) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                    }
                    ToastUtil.showShort(this@MenuActivity, "Cache cleared successfully")
                }
            }
        }
    }


    // ---------------------------------------------------------------------
    //  CLEAR ALL DATA  -  full reset, same as Settings > Clear Data
    // ---------------------------------------------------------------------
    private fun confirmClearAllData() {
        if (job != null) {
            ZdAlert.warn(
                this,
                "Download in Progress",
                "Please wait for the current download to finish, or cancel it, before clearing your data."
            )
            return
        }

        lifecycleScope.launch {
            // Most important check: how many surveys have not reached the server
            val pending = withContext(Dispatchers.IO) {
                database.newSurveyNewDao().totalPendingCount()
            }

            val storageMb = withContext(Dispatchers.IO) {
                (folderSizeBytes(filesDir) + folderSizeBytes(cacheDir)) / (1024.0 * 1024.0)
            }

            // -------- Case 1: unuploaded records exist --------
            if (pending > 0) {
                val recordWord = if (pending == 1) "record" else "records"

                ZdAlert.confirmDanger(
                    context = this@MenuActivity,
                    title = "$pending $recordWord Not Uploaded",
                    message = "You have $pending saved $recordWord that has not reached the server yet. " +
                            "Clearing your data will delete ${if (pending == 1) "it" else "them"} permanently — " +
                            "this cannot be undone.\n\nUpload from Saved Records first.",
                    confirmText = "Go to Saved Records",
                    cancelText = "Cancel",
                    onConfirm = {
                        startActivity(
                            Intent(this@MenuActivity, NewSavedRecordsActivity::class.java)
                        )
                    }
                )
                return@launch
            }

            // -------- Case 2: everything is uploaded --------
            ZdAlert.confirmDanger(
                context = this@MenuActivity,
                title = "Clear All Data?",
                message = "The app will be reset to a fresh state, freeing ${
                    String.format(Locale.US, "%.1f MB", storageMb)
                } of storage. You will be signed out and will need to download your mauza again.",
                details = listOf(
                    "Downloaded parcels and offline maps",
                    "Saved surveys and pictures",
                    "Grower list and lookup data",
                    "Your login session"
                ),
                confirmText = "Clear Everything"
            ) {
                clearAllData()
            }
        }
    }

    private fun clearAllData() {
        lifecycleScope.launch {
            Utility.showProgressAlertDialog(this@MenuActivity, "Clearing data...")

            withContext(Dispatchers.IO) {
                // 1) Every Room table
                try {
                    database.clearAllTables()
                } catch (e: Exception) {
                    Log.e("CLEAR_DATA", "clearAllTables failed: ${e.message}")
                }

                // 2) filesDir - offline maps, survey images, tpk files
                //    Room's .db files live in databases/, not here, so this is safe
                filesDir.listFiles()?.forEach { it.deleteRecursively() }

                // 3) cacheDir
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }

                // 4) SharedPreferences - login, selected mauza, everything
                sharedPreferences.edit().clear().apply()
                getSharedPreferences("theme_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            }

            Utility.dismissProgressAlertDialog()
            ToastUtil.showShort(this@MenuActivity, "All data cleared")

            // 5) Back to login with the task fully cleared
            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(this)
            }
            finishAffinity()
        }
    }

    private fun folderSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkBottomUp().filter { it.isFile }.map { it.length() }.sum()
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

                        MyApplication.updateTheme(this, newMode)
                        dialog.dismiss()
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
            }
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

                window.statusBarColor = if (isDarkMode) {
                    ContextCompat.getColor(this, R.color.dark_primary)
                } else {
                    ContextCompat.getColor(this, R.color.forest_green)
                }

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
        val iconColor = ContextCompat.getColor(this, R.color.white)

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
        setupStatusBar()
        invalidateOptionsMenu()
    }
}