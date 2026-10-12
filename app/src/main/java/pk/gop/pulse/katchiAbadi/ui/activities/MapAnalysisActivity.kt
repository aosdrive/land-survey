package pk.gop.pulse.katchiAbadi.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.arcgisservices.LevelOfDetail
import com.esri.arcgisruntime.arcgisservices.TileInfo
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.Geometry
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.TextSymbol
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.CustomTileLayer
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.data.repository.MapAnalysisRepository
import pk.gop.pulse.katchiAbadi.data.repository.ParcelActivityLogger
import pk.gop.pulse.katchiAbadi.databinding.ActivityMapAnalysisBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.AnalyticsParcelDto
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAcceptEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelAction
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "MapAnalysis"

// Analytics statuses as the server job writes them into SurveyAnalyticsParcels.
private const val STATUS_UNMAPPED_CANE = "Unmapped cane cells"
private const val STATUS_CANE_NO_SURVEY = "Cane without a survey"
private const val STATUS_SURVEYED_NOT_CANE = "Surveyed, but not cane"

private val COLOR_YELLOW = Color.parseColor("#FACC15")
private val COLOR_RED = Color.parseColor("#EF4444")
private val COLOR_GREEN = Color.parseColor("#22C55E")
private val COLOR_PURPLE = Color.parseColor("#A855F7")
private val COLOR_ORANGE = Color.parseColor("#F97316")   // survey removed, may be surveyed again
private val COLOR_SELECTED = Color.parseColor("#0096FF")   // blue (multi-select)

/** The two kinds of analytics parcel the surveyor works on. */
private enum class AnalyticsKind {
    /** Satellite shows cane but there is no survey (both "Unmapped cane cells" and "Cane without a survey"). */
    CANE_NO_SURVEY,
    /** A survey exists but satellite shows no cane: the survey has to be removed. */
    SURVEYED_NOT_CANE
}

/** Maps a status string to a kind; null = not shown on this screen. */
private fun analyticsKind(status: String?): AnalyticsKind? {
    val s = status?.trim()?.lowercase(Locale.US) ?: return null
    return when {
        s == STATUS_UNMAPPED_CANE.lowercase(Locale.US) || s == STATUS_CANE_NO_SURVEY.lowercase(Locale.US) ->
            AnalyticsKind.CANE_NO_SURVEY
        s == STATUS_SURVEYED_NOT_CANE.lowercase(Locale.US) ||
            (s.contains("surveyed") && (s.contains("not cane") || s.contains("no cane") ||
                s.contains("without cane") || s.contains("non cane") || s.contains("non-cane"))) ->
            AnalyticsKind.SURVEYED_NOT_CANE
        else -> null
    }
}

/** What a parcel is drawn as: what it needs, or what the surveyor already did to it. */
private enum class ParcelCategory(val color: Int, val title: String) {
    CANE_NO_SURVEY(COLOR_YELLOW, "Cane without survey"),
    SURVEYED_NOT_CANE(COLOR_RED, "Surveyed but not cane"),
    /** A red parcel whose survey this surveyor removed; only these may be surveyed again from here. */
    SURVEY_REMOVED(COLOR_ORANGE, "Survey removed - survey again"),
    DONE(COLOR_GREEN, "Done"),
    DECLINED(COLOR_PURPLE, "Declined")
}

/**
 * Map Analysis: the mauza's "Unmapped cane cells" (red) and "Cane without a survey" (yellow)
 * parcels, with Accept / Decline / Split / Merge / Survey actions, single or multi-select.
 *
 * Every parcel keeps its own server area (AreaAssigned): rows inserted into active_parcels
 * from here carry that area, never the selected-area preference, so a parcel can not move
 * to another area through a survey, a split or a merge started on this screen.
 */
@AndroidEntryPoint
class MapAnalysisActivity : AppCompatActivity() {

    @Inject
    lateinit var serverApi: ServerApi

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var mapAnalysisRepository: MapAnalysisRepository

    @Inject
    lateinit var logger: ParcelActivityLogger

    private lateinit var binding: ActivityMapAnalysisBinding
    private val wgs84 by lazy { SpatialReferences.getWgs84() }

    private val parcelOverlay = GraphicsOverlay()
    private val labelOverlay = GraphicsOverlay()
    private val splitOverlay = GraphicsOverlay()

    /**
     * One drawable parcel. [key] is the server parcel id, or the negative local id of a
     * sub-parcel that exists only in active_parcels (split on this device, not uploaded yet).
     */
    private data class AnalyticsItem(
        val key: Long,
        val row: AnalyticsParcelDto,
        val geom: Geometry,
        val kind: AnalyticsKind,
        var category: ParcelCategory,
        val parcelNo: String,
        val subParcelNo: String,
        val mauzaId: Long,
        val areaAssigned: String,
        val khewat: String,
        val geomWkt: String,
        val acres: Double,
        /** Exists only in active_parcels (negative id); becomes a server parcel on survey upload. */
        val isLocal: Boolean,
        /** A sub-parcel the server already knows (its parent was split and uploaded). */
        val isServerChild: Boolean,
        /** Other sub-parcel numbers of this parcel number that are already active on the server. */
        val existingSubs: Set<String> = emptySet()
    ) {
        val isWholeServerParcel get() = !isLocal && !isServerChild
        val label get() = if (subParcelNo.isBlank()) parcelNo else "$parcelNo/$subParcelNo"
    }

    private val items = mutableListOf<AnalyticsItem>()
    private val itemByKey = mutableMapOf<Long, AnalyticsItem>()
    private val graphicByKey = mutableMapOf<Long, Graphic>()
    private val labelByKey = mutableMapOf<Long, Graphic>()

    // ---- multi-select ----
    private var selectionMode = false
    private val selectedKeys = linkedSetOf<Long>()

    // ---- split ----
    private var splitTarget: AnalyticsItem? = null
    private val splitPoints = mutableListOf<Point>()

    // Set when Decline / the survey form is opened; checked in onResume to recolour the parcels.
    private val pendingDeclineKeys = mutableListOf<Long>()
    private val pendingSurveyKeys = mutableListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Map Analysis"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.mapView.addViewpointChangedListener {
            labelOverlay.isVisible = binding.mapView.mapScale < 5000
        }

        setupSelectionBar()
        setupSplitControls()

        // Back leaves split / selection mode first; only then does it close the screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    splitTarget != null -> exitSplitMode()
                    selectionMode -> exitSelectionMode()
                    else -> finish()
                }
            }
        })

        loadAnalyticsParcels()
    }

    // ================= LOAD =================

    private fun loadAnalyticsParcels() {
        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )
        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()

        if (mauzaId <= 0) {
            ToastUtil.showShort(this, "Download the Mauza First...")
            finish()
            return
        }

        binding.tvHeader.text = "Mauza: $mauzaName"

        lifecycleScope.launch {
            busy(true, "Loading parcels...")
            try {
                // Server first; without internet this falls back to the copy saved on the device.
                val load = mapAnalysisRepository.loadAnalyticsParcels(mauzaId.toInt())
                val rows = load.rows.filter { analyticsKind(it.status) != null }

                Log.d(TAG, "Analytics parcels received: ${rows.size} (fromCache=${load.fromCache})")

                val loaded = withContext(Dispatchers.IO) { buildItems(rows) }

                Log.d(TAG, "categories=${loaded.groupingBy { it.category }.eachCount()}")

                items.clear(); items.addAll(loaded)
                itemByKey.clear(); loaded.forEach { itemByKey[it.key] = it }
                graphicByKey.clear(); labelByKey.clear()
                parcelOverlay.graphics.clear(); labelOverlay.graphics.clear()
                loaded.forEach { drawItem(it) }

                updateSummary()

                if (load.fromCache) {
                    val savedOn = load.cachedAt?.let {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(it))
                    } ?: "earlier"
                    ToastUtil.showLong(
                        this@MapAnalysisActivity,
                        "Offline: showing parcels saved on $savedOn"
                    )
                }

                val all = loaded.map { it.geom }
                Log.d(TAG, "Drawable geometries: ${all.size}")

                if (all.isEmpty()) {
                    ToastUtil.showShort(this@MapAnalysisActivity, "Is mauze me koi parcel nahi mila.")
                } else {
                    setupMapImagery(all)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Load failed: ${e.message}", e)
                ZdAlert.error(
                    this@MapAnalysisActivity,
                    "Parcels Not Available",
                    "No internet, and this mauza's parcels are not saved on the device yet. " +
                        "Open Map Analysis once while online, or download the mauza again.",
                    buttonText = "Close"
                ) { finish() }
            } finally {
                busy(false)
            }
        }
    }

    /**
     * Turns the server rows into drawable items. Runs on IO: it also looks at active_parcels
     * for work done on this device (local split children, saved surveys) that the server
     * does not know yet.
     */
    private suspend fun buildItems(rows: List<AnalyticsParcelDto>): List<AnalyticsItem> {
        val prefsArea = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, ""
        ).orEmpty()

        // What was done on this device, including work not uploaded yet: the server's
        // "action" only knows about uploaded accepts and declines.
        val acceptedIds = mapAnalysisRepository.acceptedParcelIds()
        val declinedIds = database.parcelDeclineDao().getDeclinedParcelIds().toSet()
        val serverIds = rows.map { it.parcelId.toLong() }
        val surveyedIds = database.activeParcelDao().getSurveyedParcelIds(serverIds).toSet()
        // Removed-survey parcels surveyed again on this device: a survey saved after the removal, or
        // one still waiting for upload (it goes up after the removal). active_parcels alone can not
        // tell the new survey from the old one the area download brought along.
        val resurveyedIds: Set<Long> = if (acceptedIds.isEmpty()) emptySet() else {
            val removedAt = database.parcelAcceptDao().getAllAccepts()
                .groupBy { it.serverParcelId }
                .mapValues { (_, accepts) -> accepts.minOf { it.createdAt } }
            database.newSurveyNewDao().getSurveysForParcels(acceptedIds.toList())
                .filter { s -> s.statusBit == 0 || s.createdAt > (removedAt[s.parcelId] ?: Long.MAX_VALUE) }
                .map { it.parcelId }
                .toSet()
        }
        // Parcels split on this device: the parent row is inactive and its children are local.
        val inactiveLocalIds = database.activeParcelDao().getAllDeactivatedParcels().map { it.id }.toSet()

        val result = mutableListOf<AnalyticsItem>()
        var skipped = 0

        rows.forEach { row ->
            try {
                val id = row.parcelId.toLong()
                val kind = analyticsKind(row.status) ?: return@forEach
                val baseCategory = if (kind == AnalyticsKind.CANE_NO_SURVEY) ParcelCategory.CANE_NO_SURVEY
                                   else ParcelCategory.SURVEYED_NOT_CANE
                // The analytics table already labels a sub-parcel "<no>/<sub>", and the Parcels row
                // carries the same sub in SubParcelNo. Keep only the number here; otherwise the map
                // label and the survey form read "2297/1/1" and the number does not parse for
                // active_parcels, which blocks Survey and Split on every server sub-parcel.
                val rawNo = row.parcelNo.orEmpty().trim()
                val parcelNo = rawNo.substringBefore('/').trim()
                val sub = row.subParcelNo.orEmpty().trim()
                    .let { if (it == "0") "" else it }
                    .ifBlank { rawNo.substringAfter('/', "").trim() }
                val isServerChild = sub.isNotBlank()
                val area = row.areaAssigned?.takeIf { it.isNotBlank() } ?: prefsArea

                // A cane parcel split on this device: draw its local sub-parcels instead of it.
                if (kind == AnalyticsKind.CANE_NO_SURVEY && !isServerChild && id in inactiveLocalIds) {
                    val parentRow = database.activeParcelDao().getParcelById(id)
                    val parcelNoLong = parcelNo.toLongOrNull()
                    if (parentRow != null && parcelNoLong != null) {
                        val children = database.activeParcelDao()
                            .getParcelsByNumber(parcelNoLong, parentRow.mauzaId, parentRow.areaAssigned)
                            .filter { it.isActivate && it.id != id && it.subParcelNo.isNotBlank() && it.subParcelNo != "0" }
                        if (children.isNotEmpty()) {
                            children.forEach { child ->
                                val geom = parseWkt(child.geomWKT) ?: return@forEach
                                if (geom.isEmpty) return@forEach
                                result.add(
                                    AnalyticsItem(
                                        key = child.id,
                                        row = row,
                                        geom = geom,
                                        kind = kind,
                                        category = if (child.surveyStatusCode == 2) ParcelCategory.DONE else baseCategory,
                                        parcelNo = parcelNo,
                                        subParcelNo = child.subParcelNo,
                                        mauzaId = child.mauzaId,
                                        areaAssigned = child.areaAssigned,
                                        khewat = child.khewatInfo,
                                        geomWkt = child.geomWKT,
                                        acres = acresOf(listOf(geom)),
                                        isLocal = child.id < 0,
                                        isServerChild = child.id > 0
                                    )
                                )
                            }
                            return@forEach
                        }
                    }
                }

                val geom = parseWkt(row.geometryWKT) ?: return@forEach
                if (geom.isEmpty) { skipped++; return@forEach }

                val accepted = row.action == "Accepted" || id in acceptedIds
                val category = when {
                    // A removed survey is done again once the parcel was surveyed anew: on this
                    // device (a survey newer than the removal) or on the server after both uploads.
                    accepted && (id in resurveyedIds || (row.action == "Accepted" && row.surveyStatusCode == 2)) ->
                        ParcelCategory.DONE
                    // Survey removed by this surveyor: only such a parcel may be surveyed again from here.
                    accepted -> ParcelCategory.SURVEY_REMOVED
                    // "Cane without survey" is done once it is surveyed (server or this device).
                    kind == AnalyticsKind.CANE_NO_SURVEY && isServerChild && row.surveyStatusCode == 2 -> ParcelCategory.DONE
                    kind == AnalyticsKind.CANE_NO_SURVEY && id in surveyedIds -> ParcelCategory.DONE
                    row.action == "Declined" || id in declinedIds -> ParcelCategory.DECLINED
                    else -> baseCategory
                }

                result.add(
                    AnalyticsItem(
                        key = id,
                        row = row,
                        geom = geom,
                        kind = kind,
                        category = category,
                        parcelNo = parcelNo,
                        subParcelNo = sub,
                        mauzaId = row.mauzaId.toLong(),
                        areaAssigned = area,
                        khewat = row.khewat.orEmpty(),
                        geomWkt = row.geometryWKT.orEmpty(),
                        // A sub-parcel row carries its parent's analytics acres; show its own area.
                        acres = if (isServerChild) (row.computedAcres ?: acresOf(listOf(geom)))
                                else (row.parcelAcres ?: row.computedAcres ?: acresOf(listOf(geom))),
                        isLocal = false,
                        isServerChild = isServerChild,
                        existingSubs = parseSubs(row.existingSubParcelNos)
                    )
                )
            } catch (e: Exception) {
                skipped++
                Log.w(TAG, "Row skip (ParcelNo=${row.parcelNo}): ${e.message}")
            }
        }

        if (skipped > 0) Log.w(TAG, "Rows skipped: $skipped")
        return result
    }

    // ================= MAP + OFFLINE TILES =================

    private fun setupMapImagery(geoms: List<Geometry>) {
        try {
            val mauzaId = sharedPreferences.getLong(
                Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                Constants.SHARED_PREF_DEFAULT_INT.toLong()
            )
            val areaName = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty()

            val sanitizedAreaName = areaName.replace(Regex("[^a-zA-Z0-9_]"), "_")
            val folderKey = "mauza_${mauzaId}_area_${sanitizedAreaName}"

            val combinedGeometry = GeometryEngine.union(geoms)
            val bufferedGeometry =
                GeometryEngine.buffer(combinedGeometry.extent, 0.0001) as Polygon
            val webMercatorEnvelope = GeometryEngine.project(
                bufferedGeometry.extent,
                SpatialReferences.getWebMercator()
            ) as Envelope

            val tileManager = TileManager(this)

            val levelsOfDetail = listOf(
                LevelOfDetail(7, 1222.992452561855, 591657527.591555),
                LevelOfDetail(8, 611.4962262809275, 295828763.7957775),
                LevelOfDetail(9, 305.7481131404638, 147914381.89788872),
                LevelOfDetail(10, 152.8740565702319, 73957190.94894436),
                LevelOfDetail(11, 76.43702828511594, 36978595.47447218),
                LevelOfDetail(12, 38.21851414255798, 18489297.73723609),
                LevelOfDetail(13, 19.10925707127899, 9244648.868618045),
                LevelOfDetail(14, 9.554628535639495, 4622324.4343090225),
                LevelOfDetail(15, 4.777314267819747, 2311162.2171545113),
                LevelOfDetail(16, 2.3886571339098737, 1155581.1085772556),
                LevelOfDetail(17, 1.1943285669549368, 577790.5542886278),
                LevelOfDetail(18, 0.5971642834774684, 288895.2771443139),
                LevelOfDetail(19, 0.2985821417387342, 144447.63857215695),
                LevelOfDetail(20, 0.1492910708693671, 72223.81928607848)
            )

            val tileInfo = TileInfo(
                96,
                TileInfo.ImageFormat.PNG24,
                levelsOfDetail,
                Point(-20037508.3427892, 20037508.3427892, SpatialReferences.getWebMercator()),
                SpatialReferences.getWebMercator(),
                256,
                256
            )

            val minZoomLevel = sharedPreferences.getInt(
                Constants.SHARED_PREF_MAP_MIN_SCALE,
                Constants.SHARED_PREF_DEFAULT_MIN_SCALE
            )

            val maxZoomLevel = sharedPreferences.getInt(
                Constants.SHARED_PREF_MAP_MAX_SCALE,
                Constants.SHARED_PREF_DEFAULT_MAX_SCALE
            )

            // Imagery used to come only from tiles downloaded with the area, so a mauza
            // without them showed a blank map. Missing tiles are now fetched while online
            // and kept in the same folder, so they still show once the device is offline.
            val appContext = applicationContext
            val customTileLayer = CustomTileLayer(
                tileInfo, webMercatorEnvelope, tileManager, folderKey, minZoomLevel, maxZoomLevel,
                canDownloadMissing = { Utility.checkInternetConnection(appContext) }
            )

            customTileLayer.addDoneLoadingListener {
                Log.d(TAG, "BASEMAP status=${customTileLayer.loadStatus}, error=${customTileLayer.loadError}")
            }

            val map = ArcGISMap(Basemap(customTileLayer))
            map.initialViewpoint = Viewpoint(webMercatorEnvelope)

            ArcGISRuntimeEnvironment.setLicense(
                "runtimelite,1000,rud5883837740,none,ZZ0RJAY3FLCB0YRJD136"
            )

            binding.mapView.map = map
            binding.mapView.isAttributionTextVisible = false

            binding.mapView.graphicsOverlays.clear()
            binding.mapView.graphicsOverlays.add(parcelOverlay)
            binding.mapView.graphicsOverlays.add(labelOverlay)
            binding.mapView.graphicsOverlays.add(splitOverlay)

            // The touch listener goes on last, after the map is set.
            attachTouchListener()

            Log.d(TAG, "Imagery setup done, folderKey=$folderKey, graphics=${parcelOverlay.graphics.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Map setup failed: ${e.message}", e)
            ToastUtil.showShort(this, "Map load nahi hui.")
        }
    }

    private fun attachTouchListener() {
        binding.mapView.onTouchListener =
            object : DefaultMapViewOnTouchListener(this, binding.mapView) {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

                    if (splitTarget != null) {
                        addSplitPoint(screenPoint)
                        return true
                    }

                    val future = binding.mapView
                        .identifyGraphicsOverlayAsync(parcelOverlay, screenPoint, 20.0, false)

                    future.addDoneListener {
                        try {
                            val result = future.get()
                            if (result.graphics.isNotEmpty()) {
                                val key = result.graphics[0].attributes["key"]?.toString()?.toLongOrNull()
                                val item = key?.let { itemByKey[it] } ?: return@addDoneListener
                                runOnUiThread {
                                    if (selectionMode) toggleSelection(item) else showParcelActionCard(item)
                                }
                            }
                        } catch (ex: Exception) {
                            Log.e(TAG, "Identify failed: ${ex.message}", ex)
                        }
                    }
                    return true
                }
            }
        Log.d(TAG, "Touch listener attached")
    }

    // ================= DRAWING =================

    private fun fillSymbolFor(category: ParcelCategory): SimpleFillSymbol {
        val color = category.color
        return SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID,
            Color.argb(110, Color.red(color), Color.green(color), Color.blue(color)),
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, color, 1.6f)
        )
    }

    private val selectedSymbol = SimpleFillSymbol(
        SimpleFillSymbol.Style.SOLID,
        Color.argb(120, Color.red(COLOR_SELECTED), Color.green(COLOR_SELECTED), Color.blue(COLOR_SELECTED)),
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3f)
    )

    private fun drawItem(item: AnalyticsItem) {
        val g = Graphic(item.geom, fillSymbolFor(item.category))
        g.attributes["key"] = item.key
        parcelOverlay.graphics.add(g)
        graphicByKey[item.key] = g

        val label = buildLabel(item, item.category.color)
        labelOverlay.graphics.add(label)
        labelByKey[item.key] = label
    }

    private fun removeItem(key: Long) {
        graphicByKey.remove(key)?.let { parcelOverlay.graphics.remove(it) }
        labelByKey.remove(key)?.let { labelOverlay.graphics.remove(it) }
        itemByKey.remove(key)
        items.removeAll { it.key == key }
        selectedKeys.remove(key)
    }

    /** Recolours a parcel after an accept, decline or survey, without reloading the map. */
    private fun setCategory(key: Long, category: ParcelCategory) {
        val item = itemByKey[key] ?: return
        if (item.category == category) return
        item.category = category
        if (key !in selectedKeys) graphicByKey[key]?.symbol = fillSymbolFor(category)
        (labelByKey[key]?.symbol as? TextSymbol)?.color = category.color
        updateSummary()
    }

    private fun updateSummary() {
        fun line(category: ParcelCategory): String {
            val list = items.filter { it.category == category }
            return "${category.title}: ${list.size} · ${"%.2f".format(acresOf(list.map { it.geom }))} ac"
        }
        binding.tvUnmapped.text = line(ParcelCategory.CANE_NO_SURVEY)     // yellow row of the legend
        binding.tvNoSurvey.text = line(ParcelCategory.SURVEYED_NOT_CANE)  // red row of the legend
        binding.tvRemoved.text = line(ParcelCategory.SURVEY_REMOVED)      // orange row of the legend
        binding.tvAccepted.text = line(ParcelCategory.DONE)
        binding.tvDeclined.text = line(ParcelCategory.DECLINED)
    }

    // ================= SINGLE PARCEL: ACTION CARD =================

    private fun showParcelActionCard(item: AnalyticsItem) {
        val status = item.row.status?.trim().orEmpty()

        // Already acted on: show what happened instead of offering the actions again.
        when (item.category) {
            ParcelCategory.DONE -> {
                if (item.kind == AnalyticsKind.CANE_NO_SURVEY) {
                    ZdAlert.warn(
                        this, "Already Surveyed",
                        "Parcel ${item.label} has been surveyed. Nothing more to do here.",
                        details = listOf("Analytics status: $status")
                    )
                } else {
                    ZdAlert.warn(
                        this, "Surveyed Again",
                        "The survey of parcel ${item.label} was removed and the parcel has been surveyed again. Nothing more to do here.",
                        details = listOf("Analytics status: $status")
                    )
                }
                return
            }
            ParcelCategory.DECLINED -> {
                ZdAlert.warn(
                    this, "Already Declined",
                    "Parcel ${item.label} has already been declined.",
                    details = listOf("Analytics status: $status")
                )
                return
            }
            else -> Unit
        }

        val view = layoutInflater.inflate(R.layout.dialog_parcel_action, null)
        val dialog = AlertDialog.Builder(this, R.style.ZdDialog)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val cane = item.kind == AnalyticsKind.CANE_NO_SURVEY
        // A red parcel whose survey was removed from this screen may be surveyed again (no split).
        val surveyAgain = item.category == ParcelCategory.SURVEY_REMOVED
        val surveyHere = cane || surveyAgain
        view.findViewById<TextView>(R.id.tv_action_status).text = item.category.title.uppercase(Locale.US)
        view.findViewById<TextView>(R.id.tv_action_parcel).text = "Parcel ${item.label}"
        // Plain words: what this parcel is and what the surveyor has to do.
        view.findViewById<TextView>(R.id.tv_action_hint).text = when {
            surveyAgain ->
                "You removed this parcel's survey. It is back in the survey queue, so you can survey it " +
                    "again now. The removal uploads before the new survey."
            cane ->
                "Satellite shows sugarcane on this parcel but it has no survey. Survey it now. " +
                    "If only a part of it has cane, split it first and survey that part."
            else ->
                "This parcel has a survey, but satellite shows no sugarcane there. " +
                    "Remove the survey so the parcel goes back to the survey queue."
        }
        view.findViewById<TextView>(R.id.tv_action_details).text = buildString {
            if (item.khewat.isNotBlank()) append("Khewat: ${item.khewat}\n")
            append(String.format(Locale.US, "%.2f Acres", item.acres))
            if (item.subParcelNo.isNotBlank()) append("\nSub-parcel ${item.subParcelNo}")
            if (item.isLocal) append(" (split on this device, not uploaded yet)")
            if (item.existingSubs.isNotEmpty()) {
                append("\nServer already has sub-parcel(s): ${item.existingSubs.sorted().joinToString(", ")}")
            }
            append("\nArea: ${item.areaAssigned}")
        }
        // Tint (not setBackgroundColor) so the dot keeps its round shape
        view.findViewById<View>(R.id.view_status_dot).backgroundTintList =
            ColorStateList.valueOf(item.category.color)

        val mainBtn = view.findViewById<TextView>(R.id.btn_action_accept)
        mainBtn.text = if (surveyHere) "SURVEY" else "REMOVE SURVEY"
        mainBtn.setOnClickListener {
            dialog.dismiss()
            if (surveyHere) openSurvey(listOf(item)) else showAcceptDialog(listOf(item))
        }

        val declineBtn = view.findViewById<TextView>(R.id.btn_action_decline)
        declineBtn.text = "DECLINE (cannot do this)"
        declineBtn.alpha = if (item.isLocal) 0.4f else 1f
        // A removed survey is already an action on this parcel; a decline would be hidden by it.
        declineBtn.visibility = if (surveyAgain) View.GONE else View.VISIBLE
        declineBtn.setOnClickListener {
            dialog.dismiss()
            openDecline(listOf(item))
        }

        // Split (and merge, via multi-select) only for cane parcels that need a survey.
        val splitBtn = view.findViewById<View>(R.id.btn_action_split)
        splitBtn.visibility = if (cane) View.VISIBLE else View.GONE
        splitBtn.setOnClickListener {
            dialog.dismiss()
            enterSplitMode(item)
        }
        view.findViewById<View>(R.id.btn_action_cancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    /** "Surveyed but not cane": the only action is removing its survey on the server. */
    private fun needsUnsurvey(item: AnalyticsItem) =
        item.category == ParcelCategory.SURVEYED_NOT_CANE && !item.isLocal

    /**
     * "Cane without survey" parcels (and their sub-parcels) are surveyed, and so is a red parcel
     * whose survey was removed from this screen - but only that one, never an untouched red parcel.
     */
    private fun canSurvey(item: AnalyticsItem) =
        item.category == ParcelCategory.CANE_NO_SURVEY || item.category == ParcelCategory.SURVEY_REMOVED

    /** Split and merge stay limited to cane parcels; a removed survey is surveyed as it is. */
    private fun canSplitOrMerge(item: AnalyticsItem) =
        item.category == ParcelCategory.CANE_NO_SURVEY

    // ================= MULTI-SELECT =================

    private fun setupSelectionBar() {
        binding.btnSelectMode.setOnClickListener { enterSelectionMode() }
        binding.btnSelCancel.setOnClickListener { exitSelectionMode() }

        binding.btnSelAccept.setOnClickListener {
            val list = selectedItems().filter { needsUnsurvey(it) }
            if (list.isEmpty()) {
                ToastUtil.showShort(this, "Select red parcels (surveyed but not cane) to remove their survey.")
                return@setOnClickListener
            }
            showAcceptDialog(list)
        }
        binding.btnSelSurvey.setOnClickListener {
            val list = selectedItems().filter { canSurvey(it) }
            if (list.isEmpty()) {
                ToastUtil.showShort(this, "Select yellow parcels (cane without survey) or orange ones (survey removed) to survey.")
                return@setOnClickListener
            }
            openSurvey(list)
        }
        binding.btnSelDecline.setOnClickListener {
            val list = selectedItems().filter {
                !it.isLocal && it.category != ParcelCategory.DONE &&
                    it.category != ParcelCategory.DECLINED && it.category != ParcelCategory.SURVEY_REMOVED
            }
            if (list.isEmpty()) {
                ToastUtil.showShort(this, "Nothing to decline in the selection.")
                return@setOnClickListener
            }
            openDecline(list)
        }
        binding.btnSelSplit.setOnClickListener {
            val list = selectedItems()
            if (list.size != 1) {
                ToastUtil.showShort(this, "Select exactly one parcel to split.")
                return@setOnClickListener
            }
            enterSplitMode(list[0])
        }
        binding.btnSelMerge.setOnClickListener {
            val list = selectedItems()
            if (list.size < 2) {
                ToastUtil.showShort(this, "Select at least two parcels to merge.")
                return@setOnClickListener
            }
            openMerge(list)
        }
    }

    private fun selectedItems(): List<AnalyticsItem> = selectedKeys.mapNotNull { itemByKey[it] }

    private fun enterSelectionMode() {
        if (splitTarget != null) exitSplitMode()
        selectionMode = true
        selectedKeys.clear()
        binding.btnSelectMode.visibility = View.GONE
        binding.layoutSelectionBar.visibility = View.VISIBLE
        updateSelectionBar()
        ToastUtil.showShort(this, "Tap parcels to select them")
    }

    private fun exitSelectionMode() {
        selectedKeys.toList().forEach { key ->
            itemByKey[key]?.let { graphicByKey[key]?.symbol = fillSymbolFor(it.category) }
        }
        selectedKeys.clear()
        selectionMode = false
        binding.layoutSelectionBar.visibility = View.GONE
        binding.btnSelectMode.visibility = View.VISIBLE
    }

    private fun toggleSelection(item: AnalyticsItem) {
        if (item.category == ParcelCategory.DECLINED || item.category == ParcelCategory.DONE) {
            ToastUtil.showShort(this, "Parcel ${item.label} is already ${item.category.title.lowercase(Locale.US)}; nothing to do.")
            return
        }
        if (selectedKeys.remove(item.key)) {
            graphicByKey[item.key]?.symbol = fillSymbolFor(item.category)
        } else {
            selectedKeys.add(item.key)
            graphicByKey[item.key]?.symbol = selectedSymbol
        }
        updateSelectionBar()
    }

    private fun updateSelectionBar() {
        val list = selectedItems()
        binding.tvSelectionCount.text = "${list.size} selected"
        fun enable(view: View, on: Boolean) { view.isEnabled = on; view.alpha = if (on) 1f else 0.4f }
        enable(binding.btnSelAccept, list.any { needsUnsurvey(it) })
        enable(binding.btnSelSurvey, list.any { canSurvey(it) })
        enable(binding.btnSelDecline, list.any { !it.isLocal })
        enable(binding.btnSelSplit, list.size == 1 && canSplitOrMerge(list[0]))
        enable(binding.btnSelMerge, list.size >= 2 && list.all { canSplitOrMerge(it) && !it.isLocal })
    }

    // ================= ACCEPT (UNSURVEY) =================

    private fun showAcceptDialog(targets: List<AnalyticsItem>) {
        val view = layoutInflater.inflate(R.layout.dialog_accept_parcel, null)
        // Non-cancelable like ZdAlert: only Confirm or Cancel closes it.
        val dialog = AlertDialog.Builder(this, R.style.ZdDialog)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.tv_accept_parcel).text =
            if (targets.size == 1) "Parcel ${targets[0].label}"
            else "${targets.size} parcels: ${targets.joinToString(", ") { it.label }}"
        val remarksInput = view.findViewById<EditText>(R.id.et_accept_remarks)

        view.findViewById<View>(R.id.btn_accept_confirm).setOnClickListener {
            dialog.dismiss()
            saveAccepts(targets, remarksInput.text?.toString()?.trim().orEmpty())
        }
        view.findViewById<View>(R.id.btn_accept_cancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun saveAccepts(targets: List<AnalyticsItem>, remarks: String) {
        lifecycleScope.launch {
            busy(true, "Saving survey removal...")
            try {
                val location = currentLocationOrNull()
                location?.let {
                    logger.lastLat = it.first
                    logger.lastLng = it.second
                }
                var saved = 0
                var already = 0
                var keptPending = 0
                targets.forEach { item ->
                    if (mapAnalysisRepository.isAccepted(item.key)) {
                        already++
                    } else {
                        mapAnalysisRepository.saveAccept(buildAccept(item, remarks, location))
                        saved++
                    }
                    val hasPendingSurvey = withContext(Dispatchers.IO) {
                        database.newSurveyNewDao().getSurveysByParcelId(item.key).isNotEmpty()
                    }
                    if (hasPendingSurvey) {
                        // This device already holds the parcel's next survey; it uploads after the
                        // removal, so a second survey must not be started here.
                        keptPending++
                        setCategory(item.key, ParcelCategory.DONE)
                    } else {
                        // The old survey no longer counts on this device either, so the parcel can be
                        // surveyed again from here (a row only exists if the area was downloaded).
                        withContext(Dispatchers.IO) { database.activeParcelDao().updateSurveyStatus(item.key, 1) }
                        setCategory(item.key, ParcelCategory.SURVEY_REMOVED)
                    }
                }
                if (selectionMode) exitSelectionMode()

                val msg = buildString {
                    append(if (saved == 1) "Survey removal saved on the device." else "$saved survey removals saved on the device.")
                    if (already > 0) append(" $already already done.")
                    if (keptPending > 0) append(" $keptPending already has a survey waiting for upload; it goes up after the removal.")
                    if (keptPending < targets.size) append(" You can survey these parcels again from this screen.")
                    append(" Upload from Pending Survey Removals on the home screen.")
                }
                ToastUtil.showLong(this@MapAnalysisActivity, msg)
            } catch (e: Exception) {
                Log.e(TAG, "Survey removal save failed: ${e.message}", e)
                ZdAlert.error(this@MapAnalysisActivity, "Not Saved", e.message ?: "Unknown error")
            } finally {
                busy(false)
            }
        }
    }

    private fun buildAccept(item: AnalyticsItem, remarks: String, location: Pair<Double, Double>?) =
        ParcelAcceptEntity(
            acceptGuid = UUID.randomUUID().toString(),
            mauzaId = item.mauzaId.takeIf { it > 0 }
                ?: sharedPreferences.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L),
            mauzaName = sharedPreferences.getString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, "").orEmpty(),
            areaName = item.areaAssigned,
            serverParcelId = item.key,
            // Descriptive only on the server; keep the sub-parcel visible ("2297/1").
            parcelNo = item.label,
            parcelStatus = item.row.status?.trim().orEmpty(),
            khewatInfo = item.khewat,
            remarks = remarks,
            latitude = location?.first,
            longitude = location?.second
        )

    // ================= SURVEY (yellow parcels, sub-parcels) =================

    /**
     * Makes sure the parcel exists in active_parcels so the survey form and the upload pipeline
     * can find it. Rows are created with the parcel's own server area, never the selected area.
     */
    private suspend fun ensureActiveParcelRow(item: AnalyticsItem): ActiveParcelEntity? {
        val existing = database.activeParcelDao().getParcelById(item.key)
        if (existing != null) {
            // A removed survey: a row still marked surveyed from the area download (old survey) must
            // not block the new survey. Only a survey saved on this device keeps it at 2.
            if (item.category == ParcelCategory.SURVEY_REMOVED && existing.surveyStatusCode == 2 &&
                database.newSurveyNewDao().getSurveysByParcelId(existing.id).isEmpty()
            ) {
                database.activeParcelDao().updateSurveyStatus(existing.id, 1)
                return database.activeParcelDao().getParcelById(existing.id)
            }
            return existing
        }
        if (item.isLocal) return null
        val parcelNoLong = item.parcelNo.toLongOrNull() ?: return null
        if (item.geomWkt.isBlank()) return null

        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()
        val c = item.geom.extent.center
        val parcel = ActiveParcelEntity(
            pkid = 0,
            id = item.key,
            parcelNo = parcelNoLong,
            subParcelNo = item.subParcelNo,
            mauzaId = item.mauzaId,
            mauzaName = mauzaName,
            khewatInfo = item.khewat,
            areaAssigned = item.areaAssigned,
            geomWKT = item.geomWkt,
            centroid = String.format(Locale.US, "POINT(%.8f %.8f)", c.x, c.y),
            distance = 0,
            parcelType = "",
            parcelAreaKMF = "",
            parcelAreaAbadiDeh = "0",
            // Only a sub-parcel the server already surveyed starts as surveyed; an accepted
            // (unsurveyed) whole parcel must stay 1 so it can be surveyed from here.
            surveyStatusCode = when {
                item.category == ParcelCategory.SURVEY_REMOVED -> 1          // survey removed: open again
                item.isServerChild && item.row.surveyStatusCode == 2 -> 2
                else -> 1
            },
            surveyId = null,
            isActivate = true,
            unitId = 0L,
            groupId = 0L,
            growerCodes = null
        )
        database.activeParcelDao().insertActiveParcels(listOf(parcel))
        Log.d(TAG, "Inserted parcel ${item.key} (area ${item.areaAssigned}) from analytics")
        return database.activeParcelDao().getParcelById(item.key)
    }

    private fun areaSqFt(geom: Geometry): Int = try {
        GeometryEngine.areaGeodetic(
            geom, AreaUnit(AreaUnitId.SQUARE_FEET), GeodeticCurveType.NORMAL_SECTION
        ).roundToInt()
    } catch (e: Exception) { 0 }

    private fun openSurvey(targets: List<AnalyticsItem>) {
        lifecycleScope.launch {
            busy(true, "Preparing survey...")
            try {
                val ready = mutableListOf<AnalyticsItem>()
                val incomplete = mutableListOf<AnalyticsItem>()
                withContext(Dispatchers.IO) {
                    targets.forEach { item ->
                        val row = ensureActiveParcelRow(item)
                        when {
                            row == null -> {
                                Log.w(TAG, "No active_parcels row for ${item.label}")
                                incomplete.add(item)
                            }
                            row.surveyStatusCode == 2 -> Unit   // already surveyed on this device
                            else -> ready.add(item)
                        }
                    }
                }
                targets.filter { it !in ready }.forEach { item ->
                    if (database.activeParcelDao().getParcelById(item.key)?.surveyStatusCode == 2) {
                        setCategory(item.key, ParcelCategory.DONE)
                    }
                }
                if (ready.isEmpty()) {
                    val incompleteNote = if (incomplete.isEmpty()) "" else
                        " Parcel information is incomplete for ${incomplete.joinToString { it.label }}; " +
                            "reload Map Analysis and try again."
                    if (incomplete.size == targets.size) {
                        // Nothing was surveyed; the rows could not be created (bad number or no geometry).
                        ZdAlert.warn(this@MapAnalysisActivity, "Parcel Incomplete", incompleteNote.trim())
                    } else {
                        ZdAlert.warn(
                            this@MapAnalysisActivity, "Already Surveyed",
                            "The selected parcel(s) have already been surveyed on this device.$incompleteNote"
                        )
                    }
                    return@launch
                }

                val first = ready[0]
                val mauzaName = sharedPreferences.getString(
                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
                ).orEmpty()

                pendingSurveyKeys.clear(); pendingSurveyKeys.addAll(ready.map { it.key })
                if (selectionMode) exitSelectionMode()

                startActivity(
                    Intent(this@MapAnalysisActivity, SurveyActivity::class.java).apply {
                        putExtra("parcelId", first.key)
                        putExtra("parcelNo", first.parcelNo)
                        putExtra("subParcelNo", first.subParcelNo)
                        putExtra("parcelArea", areaSqFt(first.geom).toString())
                        putExtra("khewatInfo", first.khewat)
                        putExtra("parcelOperation", "Same")
                        putExtra("parcelOperationValue", "")
                        putExtra("parcelOperationValueHi", "")
                        putExtra("unitId", 0L)
                        putExtra("groupId", 0L)
                        putExtra("mauzaName", mauzaName)
                        // The parcel's own area / mauza, so the survey does not take the selected-area pref.
                        putExtra("areaName", first.areaAssigned)
                        putExtra("mauzaId", first.mauzaId)
                        if (ready.size > 1) {
                            // One form, one separate "Same" survey per parcel.
                            putExtra("isBatchSurvey", true)
                            putExtra("batchParcelIds", ready.map { it.key }.toLongArray())
                            putExtra("batchParcelNos", ready.map { it.parcelNo }.toTypedArray())
                            putExtra("batchSubParcelNos", ready.map { it.subParcelNo }.toTypedArray())
                            putExtra("batchAreaNames", ready.map { it.areaAssigned }.toTypedArray())
                            putExtra("batchKhewats", ready.map { it.khewat }.toTypedArray())
                            putExtra("batchParcelAreas", ready.map { areaSqFt(it.geom).toString() }.toTypedArray())
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Open survey failed: ${e.message}", e)
                ToastUtil.showShort(this@MapAnalysisActivity, "Failed: ${e.message}")
            } finally {
                busy(false)
            }
        }
    }

    // ================= MERGE =================

    /**
     * Same contract as the main map's merge: the first parcel is the base, the others are
     * members; parcelOperationValue carries the member numbers, parcelOperationValueHi their ids.
     */
    private fun openMerge(targets: List<AnalyticsItem>) {
        if (targets.any { it.isLocal }) {
            ToastUtil.showLong(this, "A sub-parcel split on this device can not be merged before it is uploaded.")
            return
        }
        if (targets.any { !canSplitOrMerge(it) }) {
            ToastUtil.showLong(this, "Only 'Cane without survey' parcels can be merged.")
            return
        }
        if (targets.map { it.areaAssigned }.distinct().size > 1) {
            ToastUtil.showLong(this, "Parcels from different areas can not be merged.")
            return
        }

        lifecycleScope.launch {
            busy(true, "Preparing merge...")
            try {
                val rows = withContext(Dispatchers.IO) { targets.map { ensureActiveParcelRow(it) } }
                if (rows.any { it == null }) {
                    ToastUtil.showShort(this@MapAnalysisActivity, "Parcel information is incomplete.")
                    return@launch
                }
                if (rows.any { it!!.surveyStatusCode == 2 }) {
                    ZdAlert.warn(
                        this@MapAnalysisActivity, "Already Surveyed",
                        "One of the selected parcels has already been surveyed on this device."
                    )
                    return@launch
                }

                val base = targets[0]
                val members = targets.drop(1)
                val mauzaName = sharedPreferences.getString(
                    Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING
                ).orEmpty()

                logger.log(
                    parcelId = base.key, parcelNo = base.parcelNo, action = ParcelAction.MERGE_SELECTED,
                    subParcelNo = base.subParcelNo, mauzaId = base.mauzaId, areaName = base.areaAssigned,
                    details = "Map Analysis merge: base ${base.label} + ${members.joinToString { it.label }}",
                    relatedParcelIds = members.joinToString(",") { it.key.toString() }
                )

                pendingSurveyKeys.clear(); pendingSurveyKeys.addAll(targets.map { it.key })
                if (selectionMode) exitSelectionMode()

                startActivity(
                    Intent(this@MapAnalysisActivity, SurveyActivity::class.java).apply {
                        putExtra("parcelId", base.key)
                        putExtra("parcelNo", base.parcelNo)
                        putExtra("subParcelNo", base.subParcelNo)
                        putExtra("parcelArea", areaSqFt(base.geom).toString())
                        putExtra("khewatInfo", base.khewat)
                        putExtra("parcelOperation", "Merge")
                        putExtra("parcelOperationValue", members.joinToString(", ") { it.parcelNo })
                        putExtra("parcelOperationValueHi", members.joinToString(", ") { it.key.toString() })
                        putExtra("unitId", 0L)
                        putExtra("groupId", 0L)
                        putExtra("mauzaName", mauzaName)
                        putExtra("areaName", base.areaAssigned)
                        putExtra("mauzaId", base.mauzaId)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Open merge failed: ${e.message}", e)
                ToastUtil.showShort(this@MapAnalysisActivity, "Failed: ${e.message}")
            } finally {
                busy(false)
            }
        }
    }

    // ================= DECLINE =================

    private fun openDecline(targets: List<AnalyticsItem>) {
        val list = targets.filter { !it.isLocal }
        if (list.isEmpty()) {
            ToastUtil.showLong(this, "A sub-parcel split on this device can not be declined; decline the whole parcel instead.")
            return
        }
        pendingDeclineKeys.clear(); pendingDeclineKeys.addAll(list.map { it.key })
        if (selectionMode) exitSelectionMode()

        fun centroidOf(item: AnalyticsItem): String {
            val c = item.geom.extent.center
            return String.format(Locale.US, "POINT(%.8f %.8f)", c.x, c.y)
        }

        startActivity(
            Intent(this, DeclineCaptureActivity::class.java).apply {
                val first = list[0]
                putExtra("serverParcelId", first.key)
                putExtra("mauzaId", first.mauzaId)
                putExtra("parcelNo", first.parcelNo)
                putExtra("subParcelNo", first.subParcelNo)
                putExtra("khewat", first.khewat)
                putExtra("status", first.row.status?.trim().orEmpty())
                putExtra("acres", first.acres)
                putExtra("geomWkt", first.geomWkt)
                putExtra("centroid", centroidOf(first))
                putExtra("areaName", first.areaAssigned)
                if (list.size > 1) {
                    // One set of pictures + remarks, one decline row per parcel.
                    putExtra("isMultipleParcel", true)
                    putExtra("serverParcelIds", list.map { it.key }.toLongArray())
                    putExtra("mauzaIds", list.map { it.mauzaId }.toLongArray())
                    putExtra("parcelNos", list.map { it.parcelNo }.toTypedArray())
                    putExtra("subParcelNos", list.map { it.subParcelNo }.toTypedArray())
                    putExtra("khewats", list.map { it.khewat }.toTypedArray())
                    putExtra("statuses", list.map { it.row.status?.trim().orEmpty() }.toTypedArray())
                    putExtra("acresList", list.map { it.acres }.toDoubleArray())
                    putExtra("geomWkts", list.map { it.geomWkt }.toTypedArray())
                    putExtra("centroids", list.map { centroidOf(it) }.toTypedArray())
                    putExtra("areaNames", list.map { it.areaAssigned }.toTypedArray())
                }
            }
        )
    }

    // ================= SPLIT =================

    private fun setupSplitControls() {
        binding.btnApplySplit.setOnClickListener { applySplit() }
        binding.btnCancelSplit.setOnClickListener { exitSplitMode() }
    }

    private fun enterSplitMode(item: AnalyticsItem) {
        if (!canSplitOrMerge(item)) {
            ZdAlert.warn(this, "Can Not Split", "Only 'Cane without survey' parcels can be split. Parcel ${item.label} is ${item.category.title.lowercase(Locale.US)}.")
            return
        }
        if (item.geom !is Polygon) {
            ToastUtil.showShort(this, "This parcel's shape can not be split.")
            return
        }
        if (item.isServerChild) {
            // The upload pipeline deactivates the whole parcel of a split, not a server sub-parcel,
            // so splitting one again would leave it active on the server as a ghost. Not supported.
            ZdAlert.warn(
                this, "Can Not Split",
                "Parcel ${item.label} is already a sub-parcel on the server and can not be split again " +
                    "from Map Analysis. Survey it as it is."
            )
            return
        }
        if (selectionMode) exitSelectionMode()

        splitTarget = item
        splitPoints.clear()
        splitOverlay.graphics.clear()
        graphicByKey[item.key]?.symbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID, Color.argb(150, 0, 255, 0),
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 4f)
        )
        binding.btnSelectMode.visibility = View.GONE
        binding.layoutSelectionBar.visibility = View.GONE
        binding.layoutSplitControls.visibility = View.VISIBLE
        binding.tvSplitHint.text = buildString {
            append("Parcel ${item.label}: tap 2 points across it to draw the split line")
            if (item.existingSubs.isNotEmpty()) {
                append(". Server already has sub-parcel(s) ${item.existingSubs.sorted().joinToString(", ")}; ")
                append("new parts get the next free numbers")
            }
        }
        ToastUtil.showShort(this, "Tap points to draw a line across the parcel")
    }

    private fun exitSplitMode() {
        splitTarget?.let { item -> graphicByKey[item.key]?.symbol = fillSymbolFor(item.category) }
        splitTarget = null
        splitPoints.clear()
        splitOverlay.graphics.clear()
        binding.layoutSplitControls.visibility = View.GONE
        binding.btnSelectMode.visibility = View.VISIBLE
    }

    private fun addSplitPoint(screenPoint: android.graphics.Point) {
        val mapPoint = binding.mapView.screenToLocation(screenPoint) ?: return
        if (splitPoints.isNotEmpty() && splitPoints.last() == mapPoint) return
        splitPoints.add(mapPoint)
        updateSplitLineVisual()
        binding.tvSplitHint.text = "${splitPoints.size} point(s). " +
            if (splitPoints.size >= 2) "Press APPLY SPLIT." else "Tap one more point across the parcel."
    }

    private fun updateSplitLineVisual() {
        splitOverlay.graphics.clear()
        if (splitPoints.size >= 2) {
            val builder = PolylineBuilder(binding.mapView.spatialReference)
            splitPoints.forEach { builder.addPoint(it) }
            splitOverlay.graphics.add(
                Graphic(builder.toGeometry(), SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 3f))
            )
        }
        splitPoints.forEach { p ->
            splitOverlay.graphics.add(
                Graphic(p, SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.RED, 8f).apply {
                    outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
                })
            )
        }
    }

    private fun applySplit() {
        val item = splitTarget ?: return
        if (splitPoints.size < 2) {
            ToastUtil.showShort(this, "Draw a line with at least 2 points across the parcel.")
            return
        }
        val polygon = item.geom as? Polygon ?: return

        lifecycleScope.launch {
            busy(true, "Splitting parcel...")
            try {
                val parts = withContext(Dispatchers.Default) { cutPolygon(polygon, splitPoints) }
                when {
                    parts.isEmpty() -> ToastUtil.showLong(this@MapAnalysisActivity,
                        "Split failed. Make sure the line crosses the whole parcel.")
                    parts.size == 1 -> ToastUtil.showLong(this@MapAnalysisActivity,
                        "The line does not divide the parcel. Draw it from one side to the other.")
                    parts.size > 2 -> ToastUtil.showLong(this@MapAnalysisActivity,
                        "The line produced ${parts.size} parts. Only 2-way splits are supported.")
                    else -> createSplitParcels(item, parts)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Split failed: ${e.message}", e)
                ZdAlert.error(this@MapAnalysisActivity, "Split Failed", e.message ?: "Unknown error")
            } finally {
                busy(false)
            }
        }
    }

    /** Cuts [polygon] with the tapped line, extended past the parcel so it always crosses it. */
    private fun cutPolygon(polygon: Polygon, points: List<Point>): List<Polygon> {
        val sr = polygon.spatialReference
        val projected = points.map { p ->
            if (p.spatialReference == sr) p else GeometryEngine.project(p, sr) as Point
        }
        val first = projected.first()
        val last = projected.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        val len = sqrt(dx * dx + dy * dy)
        if (len == 0.0) return emptyList()
        val ext = maxOf(polygon.extent.width, polygon.extent.height) * 2.0
        val ux = dx / len
        val uy = dy / len

        val builder = PolylineBuilder(sr)
        builder.addPoint(Point(first.x - ux * ext, first.y - uy * ext, sr))
        projected.forEach { builder.addPoint(it) }
        builder.addPoint(Point(last.x + ux * ext, last.y + uy * ext, sr))
        val line: Polyline = builder.toGeometry()

        if (!GeometryEngine.intersects(polygon, line)) return emptyList()
        return GeometryEngine.cut(polygon, line)
            .mapNotNull { it as? Polygon }
            .filter { !it.isEmpty }
    }

    /**
     * Same local contract as the main map's split: the parent row stays (inactive) and the
     * children get negative ids, sub-parcel numbers "1"/"2" and everything else copied from the
     * parent row (area, mauza, khewat). The survey upload recreates them on the server.
     */
    private suspend fun createSplitParcels(item: AnalyticsItem, parts: List<Polygon>) {
        val parentRow = withContext(Dispatchers.IO) { ensureActiveParcelRow(item) }
        if (parentRow == null) {
            ToastUtil.showShort(this, "Parcel information is incomplete.")
            return
        }
        if (parentRow.surveyStatusCode == 2) {
            setCategory(item.key, ParcelCategory.DONE)
            ZdAlert.warn(this, "Can Not Split", "Parcel ${item.label} has already been surveyed on this device.")
            exitSplitMode()
            return
        }

        val localIdBase = -System.currentTimeMillis()
        val parentSub = parentRow.subParcelNo
        // Sub-parcel numbers already taken for this parcel number: on the server (sent with the
        // analytics row), in active_parcels and on screen. Reusing one makes the server attach our
        // part to the existing, possibly surveyed, sub-parcel instead of creating it (parcel 453/2).
        val used = usedSubNumbers(item, parentRow)
        var nextNo = 1
        val children = parts.mapIndexed { i, poly ->
            var newSub: String
            do {
                newSub = if (parentSub.isBlank() || parentSub == "0") nextNo.toString() else "${parentSub}_$nextNo"
                nextNo++
            } while (newSub in used)
            val c = poly.extent.center
            parentRow.copy(
                pkid = 0,
                id = localIdBase - i,
                subParcelNo = newSub,
                geomWKT = polygonToWkt(poly),
                centroid = String.format(Locale.US, "POINT(%.8f %.8f)", c.x, c.y),
                surveyStatusCode = 1,
                surveyId = null,
                isActivate = true,
                growerCodes = null,
                variety = null,
                varietyValidationStatus = null
                // areaAssigned / mauzaId / khewatInfo stay the parent's - never the selected area.
            )
        }

        val location = currentLocationOrNull()
        location?.let { logger.lastLat = it.first; logger.lastLng = it.second }

        withContext(Dispatchers.IO) {
            database.activeParcelDao().insertActiveParcels(children)
            database.activeParcelDao().updateParcelActivationStatus(parentRow.id, false)
        }
        logger.log(
            parentRow, ParcelAction.SPLIT,
            details = "Map Analysis split into ${children.size} parts: ${children.joinToString { it.subParcelNo }}",
            relatedParcelIds = children.joinToString(",") { it.id.toString() }
        )

        // Replace the parent on the map with its children.
        removeItem(item.key)
        children.forEach { child ->
            val geom = parseWkt(child.geomWKT) ?: return@forEach
            val childItem = AnalyticsItem(
                key = child.id, row = item.row, geom = geom, kind = item.kind,
                category = ParcelCategory.CANE_NO_SURVEY,
                parcelNo = item.parcelNo, subParcelNo = child.subParcelNo,
                mauzaId = child.mauzaId, areaAssigned = child.areaAssigned, khewat = child.khewatInfo,
                geomWkt = child.geomWKT, acres = acresOf(listOf(geom)),
                isLocal = true, isServerChild = false
            )
            items.add(childItem); itemByKey[child.id] = childItem
            drawItem(childItem)
        }
        splitTarget = null
        exitSplitMode()
        updateSummary()

        ToastUtil.showLong(
            this,
            "Parcel ${item.label} split into ${children.joinToString { "${item.parcelNo}/${it.subParcelNo}" }}. " +
                "Tap a part and press Survey."
        )
    }

    /** POLYGON((x y, ...),(hole...)) in the polygon's own units (WGS84 lon lat). */
    private fun polygonToWkt(polygon: Polygon): String {
        val rings = polygon.parts.map { part ->
            val pts = part.points.map { String.format(Locale.US, "%.8f %.8f", it.x, it.y) }.toMutableList()
            if (pts.isNotEmpty() && pts.first() != pts.last()) pts.add(pts.first())
            "(" + pts.joinToString(",") + ")"
        }
        return "POLYGON(" + rings.joinToString(",") + ")"
    }

    // ================= HELPERS =================

    /** Best-effort GPS fix for the accept record; null if permission is missing or no fix in 5s. */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocationOrNull(): Pair<Double, Double>? {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return null

        val client = LocationServices.getFusedLocationProviderClient(this)
        val fresh = withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }
        val location = fresh ?: withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { cont ->
                client.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }
        return location?.let { it.latitude to it.longitude }
    }

    private fun buildLabel(item: AnalyticsItem, color: Int): Graphic {
        val text = buildString {
            append(item.label)
            item.khewat.takeIf { it.isNotBlank() }?.let { append("\n"); append(it) }
            if (item.acres > 0) {
                append("\n")
                append(String.format(Locale.US, "%.2f Acres", item.acres))
            }
        }

        val symbol = TextSymbol().apply {
            this.text = text
            size = 11f
            this.color = color
            horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
            verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
            haloColor = Color.BLACK
            haloWidth = 1.5f
            fontWeight = TextSymbol.FontWeight.BOLD
        }

        return Graphic(item.geom.extent.center, symbol)
    }

    private fun parseWkt(wkt: String?): Geometry? {
        if (wkt.isNullOrBlank()) return null
        return try {
            when {
                wkt.contains("MULTIPOLYGON") -> {
                    val polys = Utility.getMultiPolygonFromString(wkt, wgs84)
                    when {
                        polys.isNullOrEmpty() -> null
                        polys.size == 1 -> polys[0]
                        else -> GeometryEngine.union(polys)
                    }
                }
                wkt.contains("POLYGON") -> Utility.getPolygonFromString(wkt, wgs84)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "WKT parse failed: ${e.message}")
            null
        }
    }

    /** "2,3" from the server -> {"2","3"}; blank and "0" are not sub-parcel numbers. */
    private fun parseSubs(raw: String?): Set<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() && it != "0" }.toSet()

    /**
     * Every sub-parcel number already in use for this parcel number: active on the server (from the
     * analytics rows of this parcel and its siblings), in active_parcels (downloaded, or split
     * earlier on this device) and on screen.
     */
    private suspend fun usedSubNumbers(item: AnalyticsItem, parentRow: ActiveParcelEntity): Set<String> {
        val used = mutableSetOf<String>()
        used += item.existingSubs
        items.filter { it.parcelNo == item.parcelNo && it.mauzaId == item.mauzaId }.forEach {
            if (it.subParcelNo.isNotBlank()) used += it.subParcelNo
            used += it.existingSubs
        }
        withContext(Dispatchers.IO) {
            database.activeParcelDao()
                .getParcelsByNumber(parentRow.parcelNo, parentRow.mauzaId, parentRow.areaAssigned)
                .forEach { if (it.subParcelNo.isNotBlank() && it.subParcelNo != "0") used += it.subParcelNo }
        }
        return used
    }

    private fun acresOf(geoms: List<Geometry>): Double =
        geoms.sumOf { g ->
            try {
                GeometryEngine.areaGeodetic(
                    g, AreaUnit(AreaUnitId.ACRES), GeodeticCurveType.NORMAL_SECTION
                )
            } catch (e: Exception) { 0.0 }
        }

    private fun busy(on: Boolean, msg: String = "") {
        binding.progressLoading.visibility = if (on) View.VISIBLE else View.GONE
        binding.tvStatus.visibility = if (on && msg.isNotBlank()) View.VISIBLE else View.GONE
        binding.tvStatus.text = msg
    }

    // ================= LIFECYCLE =================

    override fun onPause() { binding.mapView.pause(); super.onPause() }
    override fun onResume() {
        super.onResume()
        binding.mapView.resume()
        refreshReturnedParcels()
    }

    /** Back from Decline or the survey form: recolour the parcels the user saved there. */
    private fun refreshReturnedParcels() {
        val declineKeys = pendingDeclineKeys.toList()
        val surveyKeys = pendingSurveyKeys.toList()
        if (declineKeys.isEmpty() && surveyKeys.isEmpty()) return
        pendingDeclineKeys.clear()
        pendingSurveyKeys.clear()

        lifecycleScope.launch {
            try {
                if (declineKeys.isNotEmpty()) {
                    val declined = withContext(Dispatchers.IO) {
                        declineKeys.filter { database.parcelDeclineDao().countForParcel(it) > 0 }
                    }
                    declined.forEach { setCategory(it, ParcelCategory.DECLINED) }
                }
                if (surveyKeys.isNotEmpty()) {
                    val surveyed = withContext(Dispatchers.IO) {
                        database.activeParcelDao().getSurveyedParcelIds(surveyKeys)
                    }
                    surveyed.forEach { setCategory(it, ParcelCategory.DONE) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Refresh after return failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() { binding.mapView.dispose(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean {
        when {
            splitTarget != null -> exitSplitMode()
            selectionMode -> exitSelectionMode()
            else -> finish()
        }
        return true
    }
}
