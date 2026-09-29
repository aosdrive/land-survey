package pk.gop.pulse.katchiAbadi.ui.activities

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import pk.gop.pulse.katchiAbadi.data.remote.response.FreePolygonStartResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaDetail
import pk.gop.pulse.katchiAbadi.data.repository.DeclineRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.JKGrowerRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.LookupRepository
import pk.gop.pulse.katchiAbadi.data.repository.NewSurveyRepositoryImpl
import pk.gop.pulse.katchiAbadi.data.repository.OfficerRepository
import pk.gop.pulse.katchiAbadi.data.repository.TaskRepository
import pk.gop.pulse.katchiAbadi.databinding.ActivityMenuBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.AoiBoundaryEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAction
import pk.gop.pulse.katchiAbadi.data.repository.ParcelActivityLogger
import pk.gop.pulse.katchiAbadi.data.repository.LessorRepository
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
import pk.gop.pulse.katchiAbadi.domain.model.MauzaBoundaryEntity
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.withTimeoutOrNull
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType

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

    @Inject
    lateinit var declineRepository: DeclineRepositoryImpl

    @Inject
    lateinit var newSurveyRepository: NewSurveyRepositoryImpl

    @Inject
    lateinit var logger: ParcelActivityLogger

    @Inject
    lateinit var lessorRepository: LessorRepository

    private enum class SyncMode { NORMAL, FREE_POLYGON }
    private var syncMode = SyncMode.NORMAL

    private enum class TileScope { AREA, FULL_MAUZA, BOUNDARY }

    // How many tiles download at once. Kept modest so a rural connection is not
    // swamped; OkHttp is configured to allow at least this many per host.
    private val TILE_PARALLELISM = 8
    private var pendingTasksCount = 0
    private var pendingDeclinesCount = 0
    private var pendingValidationsCount = 0
    private var currentTabId = 0

    override fun onCreate(savedInstanceState: Bundle?) {

        sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        applySavedTheme()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)
        context = this@MenuActivity
        setupActionBar()
        setupBottomNav()

        lifecycleScope.launch {
            taskRepository.livePendingCount().collect { count ->
                pendingTasksCount = count
                binding.tvPendingTasksTitle.text =
                    if (count > 0) "PENDING TASKS ($count)" else "PENDING TASKS"
                refreshUploadsBadge()
            }
        }

        lifecycleScope.launch {
            declineRepository.livePendingCount().collect { count ->
                pendingDeclinesCount = count
                binding.tvPendingDeclinesTitle.text =
                    if (count > 0) "PENDING DECLINES ($count)" else "PENDING DECLINES"
                refreshUploadsBadge()
            }
        }

        lifecycleScope.launch {
            database.activeParcelDao().livePendingVarietyValidationCount().collect { count ->
                pendingValidationsCount = count
                binding.tvPendingValidationsTitle.text =
                    if (count > 0) "PENDING VALIDATIONS ($count)" else "PENDING VALIDATIONS"
                refreshUploadsBadge()
            }
        }



        lifecycleScope.launch(Dispatchers.IO) {
            val n = database.activeParcelDao().discardLocalOnlyValidations()
            if (n > 0) Log.w("VARIETY", "Discarded $n local-only validations")
        }

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

        // Live badge: pending tasks
        lifecycleScope.launch {
            taskRepository.livePendingCount().collect { count ->
                binding.tvPendingTasksTitle.text =
                    if (count > 0) "PENDING TASKS ($count)" else "PENDING TASKS"
            }
        }

// Live badge: pending declines
        lifecycleScope.launch {
            declineRepository.livePendingCount().collect { count ->
                binding.tvPendingDeclinesTitle.text =
                    if (count > 0) "PENDING DECLINES ($count)" else "PENDING DECLINES"
            }
        }

// Live badge: pending variety validations
        lifecycleScope.launch {
            database.activeParcelDao().livePendingVarietyValidationCount().collect { count ->
                binding.tvPendingValidationsTitle.text =
                    if (count > 0) "PENDING VALIDATIONS ($count)" else "PENDING VALIDATIONS"
            }
        }

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            cvSyncData.setOnClickListener {
                if (!Utility.checkTimeZone(this@MenuActivity)) return@setOnClickListener

                if (!Utility.checkInternetConnection(this@MenuActivity)) {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val pendingSurveys = withContext(Dispatchers.IO) {
                        database.newSurveyNewDao().totalPendingCount()
                    }

                    val pendingValidations = withContext(Dispatchers.IO) {
                        database.activeParcelDao().getPendingVarietyValidations()
                            .count { it.id > 0 }
                    }

                    // ---- Case 1: saved surveys pending ----
                    if (pendingSurveys > 0) {
                        val recordWord = if (pendingSurveys == 1) "record" else "records"

                        ZdAlert.confirmDanger(
                            context = this@MenuActivity,
                            title = "Sync Blocked — $pendingSurveys $recordWord Pending",
                            message = "You cannot sync new data while you have $pendingSurveys saved $recordWord " +
                                    "waiting to be uploaded. Syncing would overwrite the parcels these " +
                                    "$recordWord belong to.\n\n" +
                                    "Please upload or delete the pending $recordWord from Saved Records first, then sync again.",
                            cancelText = "Cancel",
                            onConfirm = {
                                startActivity(
                                    Intent(this@MenuActivity, NewSavedRecordsActivity::class.java)
                                )
                            }
                        )
                        return@launch
                    }

                    // ---- Case 2: variety validations pending ----
                    if (pendingValidations > 0) {
                        val word = if (pendingValidations == 1) "validation" else "validations"

                        ZdAlert.confirm(
                            context = this@MenuActivity,
                            title = "Sync Blocked — $pendingValidations $word Pending",
                            message = "You have $pendingValidations variety $word saved on this device " +
                                    "that have not reached the server yet. Downloading the area again " +
                                    "would delete ${if (pendingValidations == 1) "it" else "them"} permanently.\n\n" +
                                    "Tap Upload Now to send ${if (pendingValidations == 1) "it" else "them"} first, then sync again.",
                            confirmText = "Upload Now",
                            cancelText = "Cancel",
                            onConfirm = {
                                lifecycleScope.launch {
                                    Utility.showProgressAlertDialog(
                                        this@MenuActivity,
                                        "Uploading validations..."
                                    )

                                    val result = withContext(Dispatchers.IO) {
                                        newSurveyRepository.uploadVarietyValidations()
                                    }

                                    Utility.dismissProgressAlertDialog()

                                    when (result) {
                                        is Resource.Success -> ToastUtil.showShort(
                                            this@MenuActivity,
                                            "${result.data} validation(s) uploaded. You can sync now."
                                        )

                                        is Resource.Error -> ToastUtil.showShort(
                                            this@MenuActivity,
                                            result.message ?: "Upload failed"
                                        )

                                        else -> Unit
                                    }
                                }
                            }
                        )
                        return@launch
                    }

                    showSyncModeDialog()
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
                        ZdAlert.warn(
                            context = this@MenuActivity,
                            title = "Notice",
                            message = "Not Available Yet!",
                            buttonText = "OK"
                        )
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

                    val hasBoundary = withContext(Dispatchers.IO) {
                        areaName == Constants.FREE_POLYGON_AREA &&
                                database.mauzaBoundaryDao().getByMauzaId(mauzaId) != null
                    }

                    if ((recordsCount > 0 || hasBoundary) && file.exists()) {

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

            cvPendingDeclines.setOnClickListener {
                startActivity(Intent(this@MenuActivity, PendingDeclinesActivity::class.java))
            }

            cvPendingValidations.setOnClickListener {
                if (!Utility.checkInternetConnection(this@MenuActivity)) {
                    Utility.dialog(context, "Please connect to the internet to upload validations.", "No Internet!")
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    Utility.showProgressAlertDialog(this@MenuActivity, "Uploading validations...")

                    val result = withContext(Dispatchers.IO) {
                        newSurveyRepository.uploadVarietyValidations()
                    }

                    Utility.dismissProgressAlertDialog()

                    when (result) {
                        is Resource.Success -> ToastUtil.showShort(
                            this@MenuActivity,
                            if (result.data!! > 0) "${result.data} validation(s) uploaded"
                            else "No pending validations"
                        )
                        is Resource.Error -> ToastUtil.showShort(
                            this@MenuActivity,
                            result.message ?: "Upload failed"
                        )
                        else -> Unit
                    }
                }
            }

            cvSettings.setOnClickListener {
                startActivity(Intent(this@MenuActivity, SettingsActivity::class.java))
            }
            cvTheme.setOnClickListener { showThemeDialog() }
            cvClearCache.setOnClickListener { confirmClearCache() }
            cvClearData.setOnClickListener { confirmClearAllData() }
            cvLogout.setOnClickListener { startLogout() }
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

//                        showMauzaSelectionDialog(
//                            sortedMauzas = mauzaList.sortedBy { mauza -> mauza.mauzaName },
//                            meterDistance = settings?.meterDistance,
//                            meterAccuracy = settings?.meterAccuracy,
//                            allowedAreas = settings?.allowedDownloadableAreas,
//                            minScale = settings?.minScaleTiles,
//                            maxScale = settings?.maxScaleTiles
//                        )

                        val sorted = mauzaList.sortedBy { mauza -> mauza.mauzaName }

                        if (syncMode == SyncMode.FREE_POLYGON) {
                            showFreePolygonMauzaDialog(
                                sortedMauzas = sorted,
                                meterDistance = settings?.meterDistance,
                                meterAccuracy = settings?.meterAccuracy,
                                allowedAreas = settings?.allowedDownloadableAreas,
                                minScale = settings?.minScaleTiles,
                                maxScale = settings?.maxScaleTiles
                            )
                        } else {
                            showMauzaSelectionDialog(
                                sortedMauzas = sorted,
                                meterDistance = settings?.meterDistance,
                                meterAccuracy = settings?.meterAccuracy,
                                allowedAreas = settings?.allowedDownloadableAreas,
                                minScale = settings?.minScaleTiles,
                                maxScale = settings?.maxScaleTiles
                            )
                        }

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
//  BOTTOM NAVIGATION
// =====================================================================
    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener {
            binding.scrollView.smoothScrollTo(0, 0)
        }
        currentTabId = R.id.nav_home
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.llTabHome.visibility = View.VISIBLE
    }

    private fun tabViewFor(itemId: Int): View = when (itemId) {
        R.id.nav_data    -> binding.llTabData
        R.id.nav_pending -> binding.llTabPending
        R.id.nav_tasks   -> binding.llTabTasks
        R.id.nav_more    -> binding.llTabMore
        else             -> binding.llTabHome
    }

    private fun showTab(itemId: Int) {
        if (itemId == currentTabId) return

        val outgoing = tabViewFor(currentTabId)
        val incoming = tabViewFor(itemId)
        // left se right ja rahe hain ya ulta — slide direction uske hisaab se
        val forward = tabOrder(itemId) > tabOrder(currentTabId)
        val shift = if (forward) 36f else -36f

        currentTabId = itemId
        binding.scrollView.scrollTo(0, 0)
        popNavIcon(itemId)

        outgoing.animate().cancel()
        incoming.animate().cancel()

        outgoing.animate()
            .alpha(0f)
            .translationX(-shift)
            .setDuration(120)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.alpha = 1f
                outgoing.translationX = 0f

                incoming.alpha = 0f
                incoming.translationX = shift
                incoming.translationY = 12f
                incoming.visibility = View.VISIBLE
                incoming.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
            }
            .start()
    }

    private fun tabOrder(itemId: Int) = when (itemId) {
        R.id.nav_home    -> 0
        R.id.nav_data    -> 1
        R.id.nav_pending -> 2
        R.id.nav_tasks   -> 3
        else             -> 4
    }

    /** Selected tab ke icon par halka sa bounce. */
    private fun popNavIcon(itemId: Int) {
        val itemView = binding.bottomNav.findViewById<View>(itemId) ?: return
        val icon = itemView.findViewById<View>(
            com.google.android.material.R.id.navigation_bar_item_icon_view
        ) ?: return

        icon.animate().cancel()
        icon.scaleX = 0.8f
        icon.scaleY = 0.8f
        icon.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(260)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
            .start()
    }

    /** Uploads tab par total pending ka red badge. */
    private fun refreshUploadsBadge() {
        val total = pendingTasksCount + pendingDeclinesCount + pendingValidationsCount
        val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_pending)
        if (total > 0) {
            badge.isVisible = true
            badge.number = total
            badge.backgroundColor = Color.parseColor("#B47B12")   // wahi amber jo chips mein hai
            badge.badgeTextColor = Color.WHITE
        } else {
            badge.isVisible = false
        }
    }

    private fun showSyncModeDialog() {
        // Corporate Farms has no mauza/area - go straight to tehsil selection, and
        // free-polygon mode does not apply there.
        if (isCorporateFarm) {
            fetchAndShowTehsilDialog()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_sync_mode, null)

        val dialog = AlertDialog.Builder(this, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<View>(R.id.card_sync_normal).setOnClickListener {
            dialog.dismiss()
            syncMode = SyncMode.NORMAL
            viewModel.mauzaNewData()
        }
        view.findViewById<View>(R.id.card_sync_free_polygon).setOnClickListener {
            dialog.dismiss()
            syncMode = SyncMode.FREE_POLYGON
            viewModel.mauzaNewData()
        }
        view.findViewById<View>(R.id.btn_sync_mode_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }



    // =====================================================================
//  SUMMARY CARD
// =====================================================================
    private data class AreaSummary(
        val totalAcres: Double, val surveyedAcres: Double,
        val totalParcels: Int, val surveyedParcels: Int,
        val growers: Int
    )

    private fun refreshSummaryCard() {
        lifecycleScope.launch {
            val mauzaId = sharedPreferences.getLong(
                Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                Constants.SHARED_PREF_DEFAULT_INT.toLong()
            )
            val areaName = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty()

            // Corporate has no mauza: its parcels carry mauzaId = 0 with the tehsil in
            // areaAssigned, so an area name on its own already means something is selected.
            if (areaName.isBlank() || (mauzaId <= 0 && !isCorporateFarm)) {
                showEmptySummary()
                return@launch
            }

            val summary = withContext(Dispatchers.IO) {
                val parcels = database.activeParcelDao()
                    .getActiveParcelsByMauzaAndArea(mauzaId, areaName)

                var total = 0.0
                var surveyed = 0.0
                var surveyedCount = 0

                for (p in parcels) {
                    val wkt = p.geomWKT
                    if (wkt.isNullOrBlank()) continue
                    val polys: List<Polygon> = try {
                        when {
                            wkt.contains("MULTIPOLYGON") ->
                                Utility.getMultiPolygonFromString(wkt, wgs84)
                            wkt.contains("POLYGON ((") ->
                                listOfNotNull(Utility.getPolygonFromString(wkt, wgs84))
                            else -> listOfNotNull(Utility.getPolyFromString(wkt, wgs84))
                        }
                    } catch (e: Exception) { emptyList() }

                    val acres = polys.sumOf {
                        GeometryEngine.areaGeodetic(
                            it, AreaUnit(AreaUnitId.SQUARE_FEET), GeodeticCurveType.NORMAL_SECTION
                        )
                    } / 43560.0

                    total += acres
                    if (p.surveyStatusCode == 2) { surveyed += acres; surveyedCount++ }
                }

                // Corporate has no growers - the equivalent downloaded list is lessors.
                val growers = try {
                    if (isCorporateFarm) database.lessorDao().getAllLessors().size
                    else jkGrowerRepository
                        .syncGrowersForMouza(mauzaId, forceRefresh = false).size
                } catch (e: Exception) { 0 }

                AreaSummary(total, surveyed, parcels.size, surveyedCount, growers)
            }

            binding.apply {
                val remaining = (summary.totalAcres - summary.surveyedAcres).coerceAtLeast(0.0)
                val pct = if (summary.totalAcres > 0)
                    ((summary.surveyedAcres / summary.totalAcres) * 100).toInt().coerceIn(0, 100)
                else 0

                tvStatTotalArea.text = acres(summary.totalAcres)
                tvStatSurveyedArea.text = acres(summary.surveyedAcres)
                tvStatRemainingArea.text = acres(remaining)

                tvStatTotalParcels.text = "${summary.totalParcels} parcels"
                tvStatSurveyedParcels.text = "${summary.surveyedParcels} parcels"
                tvStatRemainingParcels.text =
                    "${summary.totalParcels - summary.surveyedParcels} parcels"

                tvStatGrowers.text = summary.growers.toString()
                tvStatGrowersLabel.text =
                    if (isCorporateFarm) "Lessors downloaded for this tehsil"
                    else "Growers downloaded for this mauza"
                tvSummaryPct.text = "$pct%"

                // progress ko halka sa animate karein
                android.animation.ObjectAnimator
                    .ofInt(progressSummary, "progress", progressSummary.progress, pct)
                    .setDuration(500).start()
            }
        }
    }

    private fun showEmptySummary() = binding.apply {
        tvSelectedAbadiName.text = "No area downloaded"
        listOf(tvStatTotalArea, tvStatSurveyedArea, tvStatRemainingArea).forEach { it.text = "—" }
        listOf(tvStatTotalParcels, tvStatSurveyedParcels, tvStatRemainingParcels)
            .forEach { it.text = "0 parcels" }
        tvStatGrowers.text = "0"
        tvStatGrowersLabel.text =
            if (isCorporateFarm) "Lessors downloaded for this tehsil"
            else "Growers downloaded for this mauza"
        tvSummaryPct.text = "0%"
        progressSummary.progress = 0
    }

    private fun acres(v: Double) = String.format(Locale.US, "%.1f ac", v)


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

//            sharedPreferences.edit()
//                .putLong(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA, selectedMauza.mauzaId)
//                .putInt(
//                    Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_FeetPerMarla,
//                    selectedMauza.unit
//                )
//                .putString(
//                    Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME,
//                    selectedMauza.mauzaName
//                )
//                .putInt(
//                    Constants.SHARED_PREF_METER_DISTANCE,
//                    meterDistance?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_DISTANCE
//                )
//                .putInt(
//                    Constants.SHARED_PREF_METER_ACCURACY,
//                    meterAccuracy?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_ACCURACY
//                )
//                .putInt(
//                    Constants.SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS,
//                    allowedAreas?.toIntOrNull()
//                        ?: Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
//                )
//                .putInt(
//                    Constants.SHARED_PREF_MAP_MIN_SCALE,
//                    minScale?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_MIN_SCALE
//                )
//                .putInt(
//                    Constants.SHARED_PREF_MAP_MAX_SCALE,
//                    maxScale?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_MAX_SCALE
//                )
//                .putInt(Constants.SHARED_PREF_ALLOW_DOWNLOAD_SAVED_DATA, 0)
//                .apply()



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

    private fun saveSelectedMauzaPrefs(
        mauza: MauzaDetail,
        meterDistance: String?,
        meterAccuracy: String?,
        allowedAreas: String?,
        minScale: String?,
        maxScale: String?
    ) {
        sharedPreferences.edit()
            .putLong(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA, mauza.mauzaId)
            .putInt(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_FeetPerMarla, mauza.unit)
            .putString(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME, mauza.mauzaName)
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
                allowedAreas?.toIntOrNull() ?: Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
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
    }


    private fun showFreePolygonMauzaDialog(
        sortedMauzas: List<MauzaDetail>,
        meterDistance: String?,
        meterAccuracy: String?,
        allowedAreas: String?,
        minScale: String?,
        maxScale: String?
    ) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_mouza_list, null)
        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)

        val adapter = ArrayAdapter(
            this, R.layout.spinner_item_drop_down, sortedMauzas.map { it.mauzaName }
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = adapter

        // Next button ka text badal dein taake user ko pata ho
        (view.findViewById<View>(R.id.btn_next_mauza) as? TextView)?.text = "Download Boundary"

        val dialog = AlertDialog.Builder(this, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btn_cancel_mauza).setOnClickListener { dialog.dismiss() }

        view.findViewById<View>(R.id.btn_next_mauza).setOnClickListener {
            dialog.dismiss()
            val selected = sortedMauzas[spinner.selectedItemPosition]
            saveSelectedMauzaPrefs(
                selected, meterDistance, meterAccuracy, allowedAreas, minScale, maxScale
            )
            startFreePolygonFlow(selected)
        }

        dialog.show()
    }

    private fun startFreePolygonFlow(mauza: MauzaDetail) {
        lifecycleScope.launch {
            val totalCount = withContext(Dispatchers.IO) {
                database.activeParcelDao().countAllByMauza(mauza.mauzaId)
            }
            Log.d("FREE_POLY", "mauza=${mauza.mauzaId} local parcels=$totalCount")

            // Server khud faisla karega (startFreePolygon 409 deta hai agar allowed nahi).
            // Local surveyed-check hata diya: upload ho chuke drawn parcels bhi
            // status=2 ke saath local DB me rehte hain aur ghalat block karte the.
            val msg = if (totalCount > 0)
                "$totalCount local parcel(s) of '${mauza.mauzaName}' will be refreshed. " +
                        "The mauza boundary and any parcels already on the server for this mauza " +
                        "will be downloaded, and you can draw new parcels on the map."
            else
                "The boundary of '${mauza.mauzaName}' will be downloaded. " +
                        "You will draw parcels yourself on the map."

            ZdAlert.confirm(
                context = this@MenuActivity,
                title = "Download Boundary?",
                message = msg,
                confirmText = "Download",
                cancelText = "Cancel"
            ) {
                runFreePolygonDownload(mauza)
            }
        }
    }
    private fun runFreePolygonDownload(mauza: MauzaDetail) {
        lifecycleScope.launch {
            try {
                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""

                Utility.showProgressAlertDialog(this@MenuActivity, "Getting location & checking mauza...")

                val loc = getCurrentLocationOrNull()
                val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"

                val startResp = try {
                    serverApi.startFreePolygon(
                        "Bearer $token", deviceInfo, Constants.VERSION_NAME,
                        mauza.mauzaId, Constants.SURVEY_YEAR,
                        loc?.latitude, loc?.longitude, loc?.accuracy
                    )
                } catch (e: Exception) {
                    Utility.dismissProgressAlertDialog()
                    ToastUtil.showShort(this@MenuActivity, "Server error: ${e.message}")
                    return@launch
                }
                Log.e("FREE_POLY", "startFreePolygon → ${startResp.code()} ${startResp.raw().request.url}")
                Utility.dismissProgressAlertDialog()
                if (startResp.code() == 401) {
                    val err = startResp.errorBody()?.string().orEmpty()
                    Log.e("FREE_POLY", "401 from StartFreePolygon → $err")

                    val forceLogout = err.contains("shouldLogout", true) ||
                            err.contains("isUpdateRequired", true) ||
                            err.contains("expired", true) ||
                            err.isBlank()          // JWT middleware ka khali 401 = token invalid

                    if (forceLogout) {
                        handleSessionExpired()
                    } else {
                        ZdAlert.error(
                            context = this@MenuActivity,
                            title = "Not Authorized",
                            message = err.ifBlank { "Server rejected the request (401)." },
                            buttonText = "OK"
                        )
                    }
                    return@launch
                }

                val body = startResp.body() ?: run {
                    // 409 / 404 / 500 par body errorBody mein hoti hai
                    val err = startResp.errorBody()?.string().orEmpty()
                    try { Gson().fromJson(err, FreePolygonStartResponse::class.java) }
                    catch (e: Exception) { null }
                }

                if (!startResp.isSuccessful || body == null || !body.success) {
                    ZdAlert.error(
                        context = this@MenuActivity,
                        title = "Free Polygon Not Allowed",
                        message = body?.message
                            ?: "Survey is already in progress in this mauza. Please download the acre grid (Normal Sync) and survey on it.",
                        buttonText = "OK"
                    )
                    return@launch
                }
                Log.d("FREE_POLY", "Server: ${body.message}")

                // ===== 2) LOCAL DEACTIVATE + BOUNDARY + GROWERS + TILES (pehle jaisa) =====
                showDownloadProgressDialog()
                markStep(1, StepState.ACTIVE)
                setStepMessage("Fetching mauza boundary...")

                withContext(Dispatchers.IO) {
                    database.activeParcelDao().deactivateAllByMauza(mauza.mauzaId)
                }

                val result = fetchAndStoreMauzaBoundary(token, mauza.mauzaId, mauza.mauzaName)
                if (result is Resource.Error) {
                    if (::progressAlertDialog.isInitialized && progressAlertDialog.isShowing)
                        progressAlertDialog.dismiss()
                    ToastUtil.showShort(this@MenuActivity, result.message ?: "Boundary download failed")
                    return@launch
                }
                markStep(1, StepState.DONE)
                setStepDetail(1, "boundary")

                markStep(2, StepState.ACTIVE)
                setStepMessage("Fetching grower list...")
                try {
                    val growers = withContext(Dispatchers.IO) {
                        jkGrowerRepository.syncGrowersForMouza(mauza.mauzaId, forceRefresh = true)
                    }
                    setStepDetail(2, "${growers.size}")
                } catch (e: Exception) {
                    setStepDetail(2, "skipped")
                }
                markStep(2, StepState.DONE)
                
                // ===== 2b) Server par pehle se maujood free-polygon parcels wapas lao =====
                setStepMessage("Fetching existing parcels...")
                try {
                    val restored = withContext(Dispatchers.IO) {
                        restoreServerParcelsForFreePolygon(mauza)
                    }
                    Log.d("FREE_POLY", "Restored $restored server parcels for free polygon")
                } catch (e: Exception) {
                    Log.w("FREE_POLY", "Existing parcels fetch skipped: ${e.message}")
                }

                sharedPreferences.edit()
                    .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, mauza.mauzaId)
                    .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, mauza.mauzaName)
                    .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, Constants.FREE_POLYGON_AREA)
                    .putBoolean(Constants.SHARED_PREF_FREE_POLYGON_ACTIVE, true)
                    .apply()

                downloadMapTilesNew(TileScope.BOUNDARY)

            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                if (::progressAlertDialog.isInitialized && progressAlertDialog.isShowing)
                    progressAlertDialog.dismiss()
                Log.e("FREE_POLY", "Error: ${e.message}", e)
                ToastUtil.showShort(this@MenuActivity, "Error: ${e.message}")
            }
        }
    }

    /** Free polygon mode me server par pehle se bane (draw + upload kiye) parcels dobara local DB me daalta hai. */
    private suspend fun restoreServerParcelsForFreePolygon(mauza: MauzaDetail): Int {
        val raw = serverApi.getActiveParcelsByMauzaAndArea(
            mauza.mauzaId, Constants.FREE_POLYGON_AREA
        )
        if (!raw.isSuccessful) return 0
        val parcels = raw.body()?.parcelsData.orEmpty()
        if (parcels.isEmpty()) return 0

        val groupId = sharedPreferences.getInt(Constants.SHARED_PREF_USER_GROUP_ID, 0).toLong()

        database.withTransaction {
            database.activeParcelDao()
                .deleteParcelsByMauzaAndArea(mauza.mauzaId, Constants.FREE_POLYGON_AREA)
            val entities = parcels.map { dto ->
                ActiveParcelEntity(
                    pkid = 0,
                    id = dto.id,
                    parcelNo = dto.parcelNo,
                    subParcelNo = dto.subParcelNo,
                    mauzaId = dto.mauzaId,
                    mauzaName = mauza.mauzaName,
                    khewatInfo = dto.khewatInfo,
                    areaAssigned = Constants.FREE_POLYGON_AREA,
                    geomWKT = dto.geomWKT,
                    centroid = dto.centriod,
                    distance = dto.distance,
                    parcelType = dto.parcelType,
                    parcelAreaKMF = dto.parcelAreaKMF,
                    parcelAreaAbadiDeh = dto.parcelAreaAbadiDeh,
                    surveyStatusCode = dto.surveyStatusCode,
                    surveyId = dto.surveyId,
                    isActivate = true,
                    unitId = mauza.unit.toLong(),
                    groupId = groupId,
                    growerCodes = dto.growerCodes,
                    variety = dto.variety,
                    varietyValidationStatus = if (dto.isVarietyValidated) dto.varietyValidationStatus else null,
                    varietyValidationUploaded = true,
                    isLocallyDrawn = false
                )
            }
            database.activeParcelDao().insertActiveParcels(entities)
        }
        return parcels.size
    }

    private fun handleSessionExpired() {
        // Same clean-up as a manual logout, so an expired session cannot leave the
        // previous account's area selection behind for whoever logs in next.
        ToastUtil.showShort(this, "Session expired")
        clearLocalDataAndNavigate()
    }

    private suspend fun getCurrentLocationOrNull(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    val cts = CancellationTokenSource()
                    LocationServices.getFusedLocationProviderClient(this@MenuActivity)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }
        } catch (e: Exception) {
            Log.w("FREE_POLY", "Location failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchAndStoreMauzaBoundary(
        token: String,
        mauzaId: Long,
        mauzaName: String
    ): SimpleResource {
        return try {
            val response = serverApi.getMauzaBoundary("Bearer $token", mauzaId)
            if (!response.isSuccessful) {
                return Resource.Error("Server error: ${response.code()}")
            }
            val body = response.body()
            val dto = body?.data
            val wkt = dto?.geomWKT
            if (dto == null || wkt.isNullOrBlank()) {
                return Resource.Error(body?.message ?: "No boundary returned")
            }

            val entity = MauzaBoundaryEntity(
                mauzaId = dto.mauzaId,
                mauzaName = mauzaName,
                groupId = dto.groupId,
                unitId = dto.unitId,
                zoneId = dto.zoneId,
                circleId = dto.circleId,
                area = dto.area,
                geomWKT = wkt,
                geomType = dto.geomType
            )
            withContext(Dispatchers.IO) { database.mauzaBoundaryDao().insert(entity) }
            Log.d("FREE_POLY", "Boundary saved for $mauzaName (${wkt.length} chars)")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e("FREE_POLY", "Boundary fetch failed: ${e.message}", e)
            Resource.Error("Failed: ${e.localizedMessage}")
        }
    }

    private suspend fun extentFromBoundary(mauzaId: Long): Envelope? {
        val boundary = withContext(Dispatchers.IO) {
            database.mauzaBoundaryDao().getByMauzaId(mauzaId)
        } ?: return null
        val wkt = boundary.geomWKT
        if (wkt.isBlank()) return null

        val polys = mutableListOf<Polygon>()
        try {
            when {
                wkt.contains("MULTIPOLYGON") ->
                    Utility.getMultiPolygonFromString(wkt, wgs84)
                        ?.mapTo(polys) { Utility.simplifyPolygon(it) }

                wkt.contains("POLYGON ((") ->
                    Utility.getPolygonFromString(wkt, wgs84)
                        ?.let { polys.add(Utility.simplifyPolygon(it)) }

                wkt.contains("POLYGON") ->
                    Utility.getPolyFromString(wkt, wgs84)
                        ?.let { polys.add(Utility.simplifyPolygon(it)) }
            }
        } catch (e: Exception) {
            Log.e("FREE_POLY", "Boundary WKT parse error: ${e.message}")
        }
        if (polys.isEmpty()) return null

        val combined = GeometryEngine.union(polys)
        return (GeometryEngine.buffer(combined.extent, 0.0001) as Polygon).extent
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
                            ToastUtil.showShort(
                                this@MenuActivity,
                                result.message ?: "Download failed."
                            )
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
// ↓↓↓ yahan add karein
            Log.d("VARIETY_DEBUG", "raw=${parcels.take(5).map { it.id to it.variety }}")
            Log.d(
                "VARIETY_DEBUG",
                "nullCount=${parcels.count { it.variety.isNullOrBlank() }} / ${parcels.size}"
            )
            Log.d("VARIETY_DEBUG", "rawJson=${Gson().toJson(parcels.first())}")
            val surveyed = parcels.filter { it.surveyStatusCode == 2 }
            Log.d(
                "VARIETY_DEBUG",
                "surveyed=${surveyed.size}, blankVariety=${surveyed.count { it.variety.isNullOrBlank() }}"
            )
            Log.d(
                "VARIETY_DEBUG",
                "sample=${surveyed.take(5).map { Triple(it.id, it.variety, it.growerCodes) }}"
            )
// ↑↑↑
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
                        growerCodes = parcelDto.growerCodes,
                        variety = parcelDto.variety,
                        varietyValidationStatus = if (parcelDto.isVarietyValidated) parcelDto.varietyValidationStatus else null,
                        varietyValidationUploaded = true
                    )
                }
                database.activeParcelDao().insertActiveParcels(entities)
            }
            Log.d("FETCH_DEBUG", "STEP 3 Parcels inserted")

            // ===== Boundary bhi le aao (best-effort) — map par mauza ki hadd nazar aaye =====
            try {
                val rawToken = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "") ?: ""
                val b = fetchAndStoreMauzaBoundary(rawToken, mauzaId, mauzaName)
                Log.d("FETCH_DEBUG", "Boundary fetch: ${if (b is Resource.Success) "ok" else "skipped"}")
            } catch (e: Exception) {
                Log.w("FETCH_DEBUG", "Boundary fetch skipped: ${e.message}")
            }

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
                .putBoolean(Constants.SHARED_PREF_FREE_POLYGON_ACTIVE, false)
                .apply()

            Resource.Success(Unit)

        } catch (e: Exception) {
            Log.e("FETCH_PARCELS", "Error: ${e.message}", e)
            Resource.Error("Failed to sync data: ${e.localizedMessage}")
        }
    }


    private fun startLogout() {
        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) {
                database.newSurveyNewDao().totalPendingCount()
            }

            // Logging out wipes every downloaded area, so unuploaded work must go
            // first - same rule the sync flow already enforces.
            if (pending > 0) {
                val word = if (pending == 1) "record" else "records"
                ZdAlert.confirmDanger(
                    context = this@MenuActivity,
                    title = "Logout Blocked - $pending $word Pending",
                    message = "Logging out clears the downloaded areas from this device. " +
                            "You still have $pending saved $word waiting to be uploaded, and " +
                            "they would be lost.\n\nPlease upload them from Saved Records first.",
                    confirmText = "Open Saved Records",
                    cancelText = "Cancel"
                ) {
                    startActivity(
                        Intent(this@MenuActivity, NewSavedRecordsActivity::class.java)
                    )
                }
                return@launch
            }

            ZdAlert.confirm(
                context = this@MenuActivity,
                title = "Exit!",
                message = "Are you sure you want to log out? The downloaded areas on this " +
                        "device will be cleared.",
                confirmText = "Yes",
                cancelText = "No"
            ) {
                Utility.showProgressAlertDialog(this@MenuActivity, "Please wait...")
                performLogout()
            }
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
        lifecycleScope.launch {
            // Downloaded areas belong to the account that downloaded them. startLogout()
            // already refused to get here while any survey was still unuploaded, so
            // clearing the parcels and tiles cannot lose a surveyor's work.
            withContext(Dispatchers.IO) {
                try {
                    database.activeParcelDao().deleteAllParcels()
                } catch (e: Exception) {
                    Log.e("LOGOUT", "Clearing parcels failed: ${e.message}")
                }
                try {
                    File(filesDir, "MapTiles").deleteRecursively()
                    File(filesDir, "MapTpk").deleteRecursively()
                    File(cacheDir, "MapTiles").deleteRecursively()
                } catch (e: Exception) {
                    Log.e("LOGOUT", "Clearing map tiles failed: ${e.message}")
                }
            }

            sharedPreferences.edit()
                .putInt(Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_INACTIVE)
                .putLong(Constants.SHARED_PREF_USER_ID, Constants.SHARED_PREF_DEFAULT_INT.toLong())
                .putString(Constants.SHARED_PREF_USER_CNIC, Constants.SHARED_PREF_DEFAULT_STRING)
                .putString(Constants.SHARED_PREF_USER_NAME, Constants.SHARED_PREF_DEFAULT_STRING)
                .putString(Constants.SHARED_PREF_TOKEN, Constants.SHARED_PREF_DEFAULT_STRING)

                // Identity of the account that just left. All of these are rewritten on the
                // next login, but clearing them keeps a stale corporate flag or group name
                // from being read in the gap before that happens.
                .remove(Constants.SHARED_PREF_IS_CORPORATE_USER)
                .remove(Constants.SHARED_PREF_USER_GROUP_ID)
                .remove(Constants.SHARED_PREF_USER_GROUP_NAME)
                .remove(Constants.SHARED_PREF_USER_UNIT_ID)
                .remove(Constants.SHARED_PREF_USER_UNIT_NAME)
                .remove(Constants.SHARED_PREF_USER_ROLE)

                // Area selection belongs to the account, not the device. Without this the
                // next user inherits the previous one's mauza/area - and after a corporate
                // session that means mauzaId = 0 with a tehsil name, which points every
                // parcel query at the wrong data.
                .remove(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID)
                .remove(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME)
                .remove(Constants.SHARED_PREF_USER_SELECTED_AREA_ID)
                .remove(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME)
                .remove(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID)
                .remove(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME)
                .remove(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_ID)
                .remove(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name)
                .remove(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA)
                .remove(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME)
                .remove(Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_FeetPerMarla)
                .remove(Constants.SHARED_PREF_FREE_POLYGON_ACTIVE)
                .apply()

            Intent(this@MenuActivity, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(this)
            }
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

                var rawExtent = when (scope) {
                    TileScope.BOUNDARY   -> extentFromBoundary(mauzaId)
                    TileScope.FULL_MAUZA -> extentFromBoundary(mauzaId) ?: extentFromWholeMauza(mauzaId)
                    TileScope.AREA       -> extentFromArea(mauzaId, areaName)
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
                        Log.d(
                            "TILE_SCOPE",
                            "User cancelled large download ($tilesToDownload tiles)"
                        )
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    setDownloadTotal(tilesToDownload)
                    Log.e("tiles", "downloadMapTilesNew ($scope): $tilesToDownload")
                }

                // ---- Download loop ----
                // Tiles are fetched in parallel with a bounded number of workers. The old
                // loop awaited one tile before starting the next, which was the main reason
                // a download took so long.
                val tileQueue = ArrayList<Triple<Int, Int, Int>>(maxOf(tilesToDownload, 16))
                for (zoomLevel in minOf(minZoomLevel, relevantMaxZoom)..relevantMaxZoom) {
                    val x1 = getTileX(extent.xMin, zoomLevel)
                    val y1 = getTileY(extent.yMin, zoomLevel)
                    val x2 = getTileX(extent.xMax, zoomLevel)
                    val y2 = getTileY(extent.yMax, zoomLevel)

                    for (tileX in minOf(x1, x2)..maxOf(x1, x2)) {
                        for (tileY in minOf(y1, y2)..maxOf(y1, y2)) {
                            tileQueue.add(Triple(zoomLevel, tileY, tileX))
                        }
                    }
                }

                // Connectivity is checked once here. The old code asked ConnectivityManager
                // once per tile, which cost thousands of calls on a large area.
                if (!Utility.checkInternetConnection(this@MenuActivity)) {
                    aborted = true
                } else {
                    val done = java.util.concurrent.atomic.AtomicInteger(0)
                    val lastUiUpdate = java.util.concurrent.atomic.AtomicLong(0L)
                    val gate = Semaphore(TILE_PARALLELISM)

                    coroutineScope {
                        tileQueue.map { (zoomLevel, tileY, tileX) ->
                            async(Dispatchers.IO) {
                                gate.withPermit {
                                    tileManager.downloadAndCacheTileNew(
                                        zoomLevel,
                                        tileY,
                                        tileX,
                                        folderKey,
                                        "cached",
                                        minZoomLevel,
                                        relevantMaxZoom
                                    )
                                }

                                val finished = done.incrementAndGet()

                                // Progress updates are throttled. Hopping to the main thread
                                // for every single tile was itself a bottleneck.
                                val now = System.currentTimeMillis()
                                val previous = lastUiUpdate.get()
                                if (finished == tileQueue.size ||
                                    (now - previous >= 120L && lastUiUpdate.compareAndSet(previous, now))
                                ) {
                                    withContext(Dispatchers.Main) {
                                        setDownloadProgress(finished, tilesToDownload)
                                    }
                                }
                            }
                        }.awaitAll()
                    }

                    currentTile = done.get()
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

        binding.tvSelectedAbadiName.text =
            if (!downloadedMauzaName.isNullOrEmpty() && !downloadedAreaName.isNullOrEmpty())
                "$downloadedMauzaName  ·  $downloadedAreaName"
            else "No area downloaded"
        refreshSummaryCard()
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

                for (zoomLevel in minOf(minZoomLevel, relevantMaxZoom)..relevantMaxZoom) {
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
                                // Dono branches yehi toast dikhate the, is liye ek dafa yahan.
                                ToastUtil.showShort(context, "Data downloaded successfully")
                                ZdAlert.confirm(
                                    context = this@MenuActivity,
                                    title = "Download complete",
                                    message = "Do you want to select the current downloaded area to start survey?",
                                    confirmText = "Yes",
                                    cancelText = "No"
                                ) {
                                    updateSelectedMauzaAndArea()
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
                        "(roughly ${
                            String.format(
                                Locale.US,
                                "%.0f MB",
                                estMb
                            )
                        }, $estMinutes+ minutes).\n\n" +
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

        // A large area (a whole tehsil) walks currentMaxZoom down to 16, but the
        // settings minimum is 18 - that leaves an inverted 18..16 range, so nothing
        // was downloaded at all and the map came up with no imagery behind the
        // parcels. Clamp the floor to whatever the ceiling ended up being.

        do {
            tilesToDownload = 0

            for (zoomLevel in minOf(minZoomLevel, currentMaxZoom)..currentMaxZoom) {
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
        refreshMoreTab()
        lifecycleScope.launch {
            if (Utility.checkInternetConnection(this@MenuActivity)) {
                officerRepository.refreshFromServer()
                lookupRepository.refreshAll()
            }
        }
    }

    private fun refreshMoreTab() {
        val userName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()
        binding.tvLogoutSubtitle.text =
            if (userName.isBlank()) "Sign out of this device" else "Signed in as $userName"

        binding.tvSettingsSubtitle.text = "App preferences  ·  v${Constants.VERSION_NAME}"

        binding.tvThemeSubtitle.text = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO  -> "Currently: Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Currently: Dark"
            else -> "Currently: System default"
        }

        lifecycleScope.launch {
            val mb = withContext(Dispatchers.IO) { folderSizeBytes(cacheDir) / (1024.0 * 1024.0) }
            binding.tvCacheSize.text = String.format(Locale.US, "%.1f MB of temporary files", mb)
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
            if (isCorporateFarm) {
                // Corporate writes the tehsil into both the mauza and area prefs, so
                // joining them would read "Gambat  ·  Gambat".
                val tehsil = downloadedAreaName.orEmpty()
                tvMouzaCaption.visibility = if (tehsil.isBlank()) View.GONE else View.VISIBLE
                tvMouzaName.visibility = if (tehsil.isBlank()) View.GONE else View.VISIBLE
                tvMouzaName.text = tehsil
                binding.tvSelectedAbadiName.text =
                    if (tehsil.isBlank()) "No tehsil downloaded" else tehsil
            } else {
                if (mouzaName.isNullOrEmpty()) {
                    tvMouzaCaption.visibility = View.GONE
                    tvMouzaName.visibility = View.GONE
                } else {
                    tvMouzaCaption.visibility = View.VISIBLE
                    tvMouzaName.text = mouzaName
                    tvMouzaName.visibility = View.VISIBLE
                }

                binding.tvSelectedAbadiName.text =
                    if (!downloadedMauzaName.isNullOrEmpty() && !downloadedAreaName.isNullOrEmpty())
                        "$downloadedMauzaName  ·  $downloadedAreaName"
                    else "No area downloaded"
            }
            refreshSummaryCard()
        }

        supportActionBar?.title = if (userName.isNullOrBlank()) {
            "Land Survey"
        } else {
            "Welcome, $userName"
        }

        val groupName = when {
            // Corporate users carry a real group on the server, but the product name
            // they should see is Corporate Farm, not the Katchi Abadi default.
            isCorporateFarm -> "Corporate Farm"

            // Fall back to the product name whenever the server sends no group name,
            // not only when groupId is 0 - otherwise the chip disappears entirely.
            else -> sharedPreferences
                .getString(Constants.SHARED_PREF_USER_GROUP_NAME, "")
                .orEmpty()
                .ifBlank { "Zaraat Dost" }
        }
        val unitName =
            sharedPreferences.getString(Constants.SHARED_PREF_USER_UNIT_NAME, "").orEmpty()

        binding.apply {
            if (groupName.isNotBlank() || unitName.isNotBlank()) {
                tvGroupUnit.text = listOf(groupName, unitName)
                    .filter { it.isNotBlank() }
                    .joinToString("  •  ")
                llGroupUnit.visibility = View.VISIBLE
            } else {
                llGroupUnit.visibility = View.GONE
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

        val hasBoundary = withContext(Dispatchers.IO) {
            areaName == Constants.FREE_POLYGON_AREA &&
                    database.mauzaBoundaryDao().getByMauzaId(mauzaId) != null
        }

        if ((parcelsCount > 0 || hasBoundary) && file.exists()) {
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = false

//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        when (item.itemId) {
//            R.id.action_settings -> {
//                Intent(this@MenuActivity, SettingsActivity::class.java).apply {
//                    startActivity(this)
//                }
//                return true
//            }
//
//            R.id.action_theme -> {
//                showThemeDialog()
//                return true
//            }
//
//            R.id.action_logout -> {
//                startLogout()
//                return true
//            }
//
//            R.id.action_clear_cache -> {
//                confirmClearCache()
//                return true
//            }
//
//            R.id.action_clear_data -> {
//                confirmClearAllData()
//                return true
//            }
//
//            else -> return super.onOptionsItemSelected(item)
//        }
//    }

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

            ZdAlert.list(
                context = this,
                title = "Choose Theme",
                items = options.toList(),
                selectedIndex = currentMode
            ) { which ->
                try {
                    val newMode = when (which) {
                        0 -> AppCompatDelegate.MODE_NIGHT_NO
                        1 -> AppCompatDelegate.MODE_NIGHT_YES
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }

                    MyApplication.updateTheme(this, newMode)
                    recreateWithDelay()
                } catch (e: Exception) {
                    Log.e("MenuActivity", "Error applying theme: ${e.message}")
                }
            }
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

//    private fun updateMenuIconColors(menu: Menu) {
//        val iconColor = ContextCompat.getColor(this, R.color.white)
//
//        for (i in 0 until menu.size()) {
//            val menuItem = menu.getItem(i)
//            menuItem.icon?.let { icon ->
//                val wrappedIcon = DrawableCompat.wrap(icon)
//                DrawableCompat.setTint(wrappedIcon, iconColor)
//                menuItem.icon = wrappedIcon
//            }
//        }
//    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupStatusBar()
        invalidateOptionsMenu()
    }

    // =====================================================================
    //  Corporate Farms
    //
    //  Gate: the Users.isCorperateUser flag from the login response, not unit/group id.
    //  A corporate surveyor gets no mauza or area - they get a tehsil.
    //  The trick is to write that tehsil into the existing mauza/area prefs
    //  (mauzaId = 0, mauzaName = areaName = tehsil) so FragmentMap, tile download
    //  and the survey form keep working unchanged.
    // =====================================================================

    private val isCorporateFarm: Boolean
        get() = sharedPreferences.getBoolean(Constants.SHARED_PREF_IS_CORPORATE_USER, false)

    private fun fetchAndShowTehsilDialog() {
        lifecycleScope.launch {
            try {
                Utility.showProgressAlertDialog(this@MenuActivity, "Loading tehsils...")
                val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "").orEmpty()
                val response = serverApi.getTehsilNames("Bearer $token", "")
                Utility.dismissProgressAlertDialog()

                if (!response.isSuccessful) {
                    ZdAlert.error(
                        context = this@MenuActivity,
                        title = "Could not load tehsils",
                        message = "Server ne ${response.code()}. Please try again in a moment."
                    )
                    return@launch
                }

                val tehsils = response.body().orEmpty().filter { it.isNotBlank() }
                if (tehsils.isEmpty()) {
                    ZdAlert.warn(
                        context = this@MenuActivity,
                        title = "No tehsils",
                        message = "No tehsil is assigned to this account yet."
                    )
                    return@launch
                }

                showTehsilSelectionDialog(tehsils)
            } catch (e: Exception) {
                Utility.dismissProgressAlertDialog()
                Log.e("CORP_SYNC", "Tehsil fetch failed: ${e.message}", e)
                ZdAlert.error(
                    context = this@MenuActivity,
                    title = "Could not load tehsils",
                    message = e.localizedMessage ?: "Network problem"
                )
            }
        }
    }

    private fun showTehsilSelectionDialog(tehsils: List<String>) {
        val view = LayoutInflater.from(this@MenuActivity)
            .inflate(R.layout.dialog_mouza_list, null)

        val spinner = view.findViewById<Spinner>(R.id.spn_kachi_abadis)
        spinner.adapter = ArrayAdapter(
            this@MenuActivity, R.layout.spinner_item_drop_down, tehsils
        )

        val dialog = AlertDialog.Builder(this@MenuActivity, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<View>(R.id.btn_cancel_mauza).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btn_next_mauza).setOnClickListener {
            dialog.dismiss()
            val tehsil = tehsils.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
            startCorporateDownload(tehsil)
        }
        dialog.show()
    }

    /** Boundary + surveyed parcels utaro, prefs set karo, phir tiles. */
    private fun startCorporateDownload(tehsil: String) {
        lifecycleScope.launch {
            val token = sharedPreferences.getString(Constants.SHARED_PREF_TOKEN, "").orEmpty()

            Utility.showProgressAlertDialog(this@MenuActivity, "Downloading boundary for $tehsil...")
            val boundary = fetchAndStoreAoiBoundary(token, tehsil)
            if (boundary is Resource.Error) {
                Utility.dismissProgressAlertDialog()
                ZdAlert.error(
                    context = this@MenuActivity,
                    title = "Boundary not available",
                    message = boundary.message ?: "Boundary download failed"
                )
                return@launch
            }

            Utility.showProgressAlertDialog(this@MenuActivity, "Syncing parcels for $tehsil...")
            val parcelCount = syncSurveyedParcelsForTehsil(token, tehsil)
            Utility.dismissProgressAlertDialog()

            if (parcelCount < 0) {
                ZdAlert.error(
                    context = this@MenuActivity,
                    title = "Parcels not downloaded",
                    message = "$tehsil parcels could not be downloaded. Check your connection and try again."
                )
                return@launch
            }

            // Lessors are a tehsil-wide list; pulling them here means the survey form
            // has them offline and the summary card can show a real count.
            Utility.showProgressAlertDialog(this@MenuActivity, "Downloading lessors...")
            val lessorCount = when (val r = lessorRepository.fetchAndCacheLessors(forceRefresh = true)) {
                is Resource.Success -> r.data?.size ?: 0
                else -> 0
            }
            Utility.dismissProgressAlertDialog()
            Log.d("CORP_SYNC", "Cached $lessorCount lessors")

            selectTehsil(tehsil)
            logger.log(
                parcelId = 0L,
                parcelNo = "",
                action = ParcelAction.SYNCED,
                areaName = tehsil,
                details = "Corporate tehsil sync: $parcelCount parcels"
            )

            ZdAlert.confirm(
                context = this@MenuActivity,
                title = "$tehsil is ready",
                message = "$parcelCount parcels saved. Download offline map tiles now?",
                confirmText = "Download",
                cancelText = "Later"
            ) {
                downloadMapTilesNew(TileScope.AREA)
            }
        }
    }

    /**
     * The server returns pipe-delimited text, not JSON:
     *   Tehsil||The_Id||District||Area||WKT
     */
    private suspend fun fetchAndStoreAoiBoundary(
        token: String,
        tehsil: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = serverApi.getAOIByTehsil("Bearer $token", tehsil)
            if (!response.isSuccessful) {
                return@withContext Resource.Error(
                    if (response.code() == 404) "The server has no boundary for this tehsil."
                    else "Server error: ${response.code()}"
                )
            }

            val text = response.body()?.string().orEmpty()
            if (text.isBlank()) return@withContext Resource.Error("The server returned an empty boundary.")

            val entities = mutableListOf<AoiBoundaryEntity>()
            text.split("\n").forEach { line ->
                if (line.isBlank()) return@forEach
                val p = line.split("||")
                if (p.size < 5) return@forEach

                val wkt = p[4].trim()
                if (wkt.isBlank() || !wkt.contains("POLYGON", ignoreCase = true)) return@forEach

                entities.add(
                    AoiBoundaryEntity(
                        tehsil = p[0].trim().ifBlank { tehsil },
                        theId = p[1].trim(),
                        district = p[2].trim(),
                        area = p[3].trim().toDoubleOrNull() ?: 0.0,
                        geomWKT = wkt
                    )
                )
            }

            if (entities.isEmpty()) return@withContext Resource.Error("No valid boundary geometry was found.")

            database.withTransaction {
                database.aoiBoundaryDao().deleteByTehsil(tehsil)
                database.aoiBoundaryDao().insertAll(entities)
            }
            Log.d("CORP_SYNC", "Saved ${entities.size} boundary part(s) for $tehsil")
            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e("CORP_SYNC", "Boundary error: ${e.message}", e)
            Resource.Error("Boundary download failed: ${e.localizedMessage}")
        }
    }

    /**
     * Pipe-delimited parcels:
     *   Id||Parcel_No||SubParcelNo||S_Status||AttachedSurveyId||IsDrawn||WKT
     *
     * Parcels whose survey is still unuploaded (statusBit = 0) are kept, otherwise
     * a re-sync would wipe the surveyor's work.
     *
     * Returns the parcel count, or -1 on failure.
     */
    private suspend fun syncSurveyedParcelsForTehsil(
        token: String,
        tehsil: String
    ): Int = withContext(Dispatchers.IO) {
        try {
            val response = serverApi.getSurveyedParcelsByTehsil("Bearer $token", tehsil)
            if (!response.isSuccessful) {
                Log.w("CORP_SYNC", "Parcels: server ${response.code()}")
                return@withContext -1
            }

            val text = response.body()?.string().orEmpty()
            val entities = mutableListOf<ActiveParcelEntity>()

            text.split("\n").forEach { line ->
                if (line.isBlank()) return@forEach
                val p = line.split("||")
                if (p.size < 7) return@forEach

                val id = p[0].trim().toLongOrNull() ?: return@forEach
                val wkt = p[6].trim()
                if (wkt.isBlank()) return@forEach

                val status = p[3].trim()

                entities.add(
                    ActiveParcelEntity(
                        pkid = 0,
                        id = id,
                        parcelNo = p[1].trim().toLongOrNull() ?: 0L,
                        subParcelNo = p[2].trim(),
                        mauzaId = 0L,
                        mauzaName = tehsil,
                        khewatInfo = "0",
                        areaAssigned = tehsil,
                        geomWKT = wkt,
                        centroid = "",
                        distance = Constants.SHARED_PREF_DEFAULT_DISTANCE,
                        parcelType = "",
                        parcelAreaKMF = null,
                        parcelAreaAbadiDeh = null,
                        surveyStatusCode = if (status.equals("Surveyed", true)) 2 else 1,
                        // The server sends a GUID but surveyId is Int? here. Forcing a GUID into an
                        // Int would corrupt the data, so null - surveyStatusCode = 2 already marks
                        // the parcel as surveyed.
                        surveyId = null,
                        isActivate = true,
                        unitId = sharedPreferences
                            .getInt(Constants.SHARED_PREF_USER_UNIT_ID, 0).toLong(),
                        groupId = sharedPreferences
                            .getInt(Constants.SHARED_PREF_USER_GROUP_ID, 0).toLong(),
                        isLocallyDrawn = p[5].trim() == "1"
                    )
                )
            }

            val pendingIds = database.newSurveyNewDao().getPendingParcelIds()
            database.withTransaction {
                database.activeParcelDao().deleteParcelsForTehsilExcept(tehsil, pendingIds)
                if (entities.isNotEmpty()) {
                    database.activeParcelDao().insertActiveParcels(entities)
                }
            }

            Log.d(
                "CORP_SYNC",
                "Restored ${entities.size} parcel(s) for $tehsil (kept ${pendingIds.size} pending)"
            )
            entities.size
        } catch (e: Exception) {
            Log.e("CORP_SYNC", "Parcel sync error: ${e.message}", e)
            -1
        }
    }

    /**
     * Writes the tehsil into the mauza/area prefs. This is deliberate - it is why the
     * rest of the app (map, tile download, survey form) runs completely unchanged.
     */
    private fun selectTehsil(tehsil: String) {
        sharedPreferences.edit()
            .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L)
            .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, 0L)
            .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, tehsil)
            .putString(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_NAME, tehsil)
            .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, tehsil)
            .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, tehsil)
            .apply()
    }
}
