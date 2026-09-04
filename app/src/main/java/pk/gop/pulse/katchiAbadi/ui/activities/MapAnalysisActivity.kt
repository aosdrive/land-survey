package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.arcgisservices.LevelOfDetail
import com.esri.arcgisruntime.arcgisservices.TileInfo
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.Geometry
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.TextSymbol
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.CustomTileLayer
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.databinding.ActivityMapAnalysisBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.AnalyticsParcelDto
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert
import java.util.Locale
import javax.inject.Inject

private const val TAG = "MapAnalysis"

private const val STATUS_UNMAPPED_CANE = "Unmapped cane cells"
private const val STATUS_CANE_NO_SURVEY = "Cane without a survey"

private val COLOR_UNMAPPED = Color.parseColor("#EF4444")   // red
private val COLOR_NO_SURVEY = Color.parseColor("#FACC15")  // yellow

@AndroidEntryPoint
class MapAnalysisActivity : AppCompatActivity() {

    @Inject
    lateinit var serverApi: ServerApi

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase

    private lateinit var binding: ActivityMapAnalysisBinding
    private val wgs84 by lazy { SpatialReferences.getWgs84() }

    private val parcelOverlay = GraphicsOverlay()
    private val labelOverlay = GraphicsOverlay()

    /** Row + parsed geometry — attributes banane ke liye dono chahiye. */
    private data class AnalyticsItem(val row: AnalyticsParcelDto, val geom: Geometry)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Map Analysis"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.mapView.addViewpointChangedListener {
            labelOverlay.isVisible = binding.mapView.mapScale < 5000
        }

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
                val rows = withContext(Dispatchers.IO) {
                    serverApi.getAnalyticsParcels(mauzaId.toInt(), null).features.orEmpty()
                }

                Log.d(TAG, "Analytics parcels received: ${rows.size}")

                val unmapped = mutableListOf<AnalyticsItem>()
                val noSurvey = mutableListOf<AnalyticsItem>()
                val labels = mutableListOf<Graphic>()
                var skipped = 0

                withContext(Dispatchers.Default) {
                    rows.forEach { row ->
                        try {
                            val status = row.status?.trim()
                            if (status != STATUS_UNMAPPED_CANE && status != STATUS_CANE_NO_SURVEY) return@forEach

                            val geom = parseWkt(row.geometryWKT) ?: return@forEach
                            if (geom.isEmpty) { skipped++; return@forEach }

                            if (status == STATUS_UNMAPPED_CANE) {
                                unmapped.add(AnalyticsItem(row, geom))
                            } else {
                                noSurvey.add(AnalyticsItem(row, geom))
                            }

                            labels.add(buildLabel(row, geom))
                        } catch (e: Exception) {
                            skipped++
                            Log.w(TAG, "Row skip (ParcelNo=${row.parcelNo}): ${e.message}")
                        }
                    }
                }

                Log.d(TAG, "unmapped=${unmapped.size} noSurvey=${noSurvey.size} skipped=$skipped")

                parcelOverlay.graphics.clear()
                drawParcels(unmapped, COLOR_UNMAPPED)
                drawParcels(noSurvey, COLOR_NO_SURVEY)

                labelOverlay.graphics.clear()
                labelOverlay.graphics.addAll(labels)

                val unmappedGeoms = unmapped.map { it.geom }
                val noSurveyGeoms = noSurvey.map { it.geom }

                binding.tvUnmapped.text =
                    "Unmapped cane cells: ${unmapped.size} · ${"%.2f".format(acresOf(unmappedGeoms))} ac"
                binding.tvNoSurvey.text =
                    "Cane without a survey: ${noSurvey.size} · ${"%.2f".format(acresOf(noSurveyGeoms))} ac"

                val all = unmappedGeoms + noSurveyGeoms
                Log.d(TAG, "Drawable geometries: ${all.size}")

                if (all.isEmpty()) {
                    ToastUtil.showShort(this@MapAnalysisActivity, "Is mauze me koi parcel nahi mila.")
                } else {
                    setupMapImagery(all)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Load failed: ${e.message}", e)
                ToastUtil.showShort(this@MapAnalysisActivity, "Failed: ${e.message}")
            } finally {
                busy(false)
            }
        }
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

            val customTileLayer = CustomTileLayer(
                tileInfo, webMercatorEnvelope, tileManager, folderKey, minZoomLevel, 16
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

            // ⚠️ Touch listener SAB SE AAKHIR ME — map set hone ke baad
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
                    Log.d(TAG, "TAP at ${e.x}, ${e.y}")
                    val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
                    val future = binding.mapView
                        .identifyGraphicsOverlayAsync(parcelOverlay, screenPoint, 20.0, false)

                    future.addDoneListener {
                        try {
                            val result = future.get()
                            Log.d(TAG, "Identify hits = ${result.graphics.size}")
                            if (result.graphics.isNotEmpty()) {
                                val g = result.graphics[0]
                                runOnUiThread { showParcelActionCard(g) }
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

    private fun drawParcels(items: List<AnalyticsItem>, color: Int) {
        if (items.isEmpty()) return
        val symbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID,
            Color.argb(110, Color.red(color), Color.green(color), Color.blue(color)),
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, color, 1.6f)
        )
        items.forEach { item ->
            val g = Graphic(item.geom, symbol)
            g.attributes["server_parcel_id"] = item.row.parcelId.toLong()
            g.attributes["mauza_id"] = item.row.mauzaId.toLong()
            g.attributes["parcel_no"] = item.row.parcelNo.orEmpty()
            g.attributes["khewat"] = item.row.khewat.orEmpty()
            g.attributes["status"] = item.row.status?.trim().orEmpty()
            g.attributes["acres"] = (item.row.parcelAcres ?: item.row.computedAcres ?: 0.0)
            g.attributes["geom_wkt"] = item.row.geometryWKT.orEmpty()
            val c = item.geom.extent.center
            g.attributes["centroid"] = String.format(Locale.US, "POINT(%.8f %.8f)", c.x, c.y)
            parcelOverlay.graphics.add(g)
        }
    }

    // ================= ACTION CARD =================

    private fun showParcelActionCard(graphic: Graphic) {
        val attr = graphic.attributes
        val parcelNo = attr["parcel_no"]?.toString().orEmpty()
        val khewat = attr["khewat"]?.toString().orEmpty()
        val status = attr["status"]?.toString().orEmpty()
        val acres = attr["acres"]?.toString()?.toDoubleOrNull() ?: 0.0

        val view = layoutInflater.inflate(R.layout.dialog_parcel_action, null)
        val dialog = AlertDialog.Builder(this, R.style.ZdDialog)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.tv_action_status).text = status.uppercase(Locale.US)
        view.findViewById<TextView>(R.id.tv_action_parcel).text = "Parcel $parcelNo"
        view.findViewById<TextView>(R.id.tv_action_details).text = buildString {
            if (khewat.isNotBlank()) append("Khewat: $khewat\n")
            append(String.format(Locale.US, "%.2f Acres", acres))
        }
        view.findViewById<View>(R.id.view_status_dot).setBackgroundColor(
            if (status == STATUS_UNMAPPED_CANE) COLOR_UNMAPPED else COLOR_NO_SURVEY
        )

        val acceptBtn = view.findViewById<TextView>(R.id.btn_action_accept)
        if (status == STATUS_UNMAPPED_CANE) {
            acceptBtn.alpha = 0.4f
            acceptBtn.setOnClickListener {
                ToastUtil.showShort(this, "Accept for unmapped cells is not available yet.")
            }
        } else {
            acceptBtn.setOnClickListener {
                dialog.dismiss()
                handleAccept(graphic)
            }
        }

        view.findViewById<View>(R.id.btn_action_decline).setOnClickListener {
            dialog.dismiss()
            handleDecline(graphic)
        }
        view.findViewById<View>(R.id.btn_action_cancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun handleAccept(graphic: Graphic) {
        val attr = graphic.attributes

        val serverParcelId = attr["server_parcel_id"]?.toString()?.toLongOrNull() ?: 0L
        val parcelNoStr = attr["parcel_no"]?.toString().orEmpty()
        val parcelNoLong = parcelNoStr.toLongOrNull()
        val geomWkt = attr["geom_wkt"]?.toString().orEmpty()
        val centroid = attr["centroid"]?.toString().orEmpty()
        val khewat = attr["khewat"]?.toString().orEmpty()
        val acres = attr["acres"]?.toString()?.toDoubleOrNull() ?: 0.0
        val mauzaId = attr["mauza_id"]?.toString()?.toLongOrNull() ?: 0L

        if (parcelNoLong == null || serverParcelId <= 0) {
            ToastUtil.showShort(this, "Parcel information is incomplete.")
            return
        }
        if (geomWkt.isBlank()) {
            ToastUtil.showShort(this, "Parcel geometry not available.")
            return
        }

        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()
        val areaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ).orEmpty()

        lifecycleScope.launch {
            busy(true, "Preparing survey...")
            try {
                val ready = withContext(Dispatchers.IO) {
                    val existing = database.activeParcelDao().getParcelById(serverParcelId)
                    if (existing != null) {
                        if (existing.surveyStatusCode == 2) return@withContext "ALREADY_SURVEYED"
                        return@withContext "OK"
                    }

                    val parcel = ActiveParcelEntity(
                        pkid = 0,
                        id = serverParcelId,
                        parcelNo = parcelNoLong,
                        subParcelNo = "",
                        mauzaId = mauzaId,
                        mauzaName = mauzaName,
                        khewatInfo = khewat,
                        areaAssigned = areaName,
                        geomWKT = geomWkt,
                        centroid = centroid,
                        distance = 0,
                        parcelType = "",
                        parcelAreaKMF = "",
                        parcelAreaAbadiDeh = "0",
                        surveyStatusCode = 1,
                        surveyId = null,
                        isActivate = true,
                        unitId = 0L,
                        groupId = 0L,
                        growerCodes = null
                    )
                    database.activeParcelDao().insertActiveParcels(listOf(parcel))
                    Log.d(TAG, "Inserted parcel $serverParcelId from analytics")
                    "OK"
                }

                if (ready == "ALREADY_SURVEYED") {
                    ZdAlert.warn(
                        this@MapAnalysisActivity,
                        "Already Surveyed",
                        "This parcel has already been surveyed on this device."
                    )
                    return@launch
                }

                val areaSqFt = (acres * 43560.0).toInt()

                startActivity(
                    Intent(this@MapAnalysisActivity, SurveyActivity::class.java).apply {
                        putExtra("parcelId", serverParcelId)
                        putExtra("parcelNo", parcelNoStr)
                        putExtra("subParcelNo", "")
                        putExtra("parcelArea", areaSqFt.toString())
                        putExtra("khewatInfo", khewat)
                        putExtra("parcelOperation", "Same")
                        putExtra("parcelOperationValue", "")
                        putExtra("parcelOperationValueHi", "")
                        putExtra("unitId", 0L)
                        putExtra("groupId", 0L)
                        putExtra("mauzaName", mauzaName)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Accept failed: ${e.message}", e)
                ToastUtil.showShort(this@MapAnalysisActivity, "Failed: ${e.message}")
            } finally {
                busy(false)
            }
        }
    }

    private fun handleDecline(graphic: Graphic) {
        val attr = graphic.attributes
        startActivity(
            Intent(this, DeclineCaptureActivity::class.java).apply {
                putExtra("serverParcelId", attr["server_parcel_id"]?.toString()?.toLongOrNull() ?: 0L)
                putExtra("mauzaId", attr["mauza_id"]?.toString()?.toLongOrNull() ?: 0L)
                putExtra("parcelNo", attr["parcel_no"]?.toString().orEmpty())
                putExtra("khewat", attr["khewat"]?.toString().orEmpty())
                putExtra("status", attr["status"]?.toString().orEmpty())
                putExtra("acres", attr["acres"]?.toString()?.toDoubleOrNull() ?: 0.0)
                putExtra("geomWkt", attr["geom_wkt"]?.toString().orEmpty())
                putExtra("centroid", attr["centroid"]?.toString().orEmpty())
            }
        )
    }

    // ================= HELPERS =================

    private fun buildLabel(row: AnalyticsParcelDto, geom: Geometry): Graphic {
        val color =
            if (row.status?.trim() == STATUS_UNMAPPED_CANE) COLOR_UNMAPPED else COLOR_NO_SURVEY

        val text = buildString {
            append(row.parcelNo.orEmpty())
            row.khewat?.takeIf { it.isNotBlank() }?.let { append("\n"); append(it) }
            val acres = row.parcelAcres ?: row.computedAcres
            if (acres != null && acres > 0) {
                append("\n")
                append(String.format(Locale.US, "%.2f Acres", acres))
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

        return Graphic(geom.extent.center, symbol)
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

    private fun acresOf(geoms: List<Geometry>): Double =
        geoms.sumOf { g ->
            try {
                GeometryEngine.areaGeodetic(
                    g,
                    com.esri.arcgisruntime.geometry.AreaUnit(
                        com.esri.arcgisruntime.geometry.AreaUnitId.ACRES
                    ),
                    com.esri.arcgisruntime.geometry.GeodeticCurveType.NORMAL_SECTION
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
    override fun onResume() { super.onResume(); binding.mapView.resume() }
    override fun onDestroy() { binding.mapView.dispose(); super.onDestroy() }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}