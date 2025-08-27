package pk.gop.pulse.katchiAbadi.fragments.form

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.devstune.searchablemultiselectspinner.SearchableItem
import com.devstune.searchablemultiselectspinner.SearchableMultiSelectSpinner
import com.devstune.searchablemultiselectspinner.SelectionCompleteListener
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import com.esri.arcgisruntime.arcgisservices.LevelOfDetail
import com.esri.arcgisruntime.arcgisservices.TileInfo
import com.esri.arcgisruntime.data.TileCache
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.PointCollection
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.layers.ArcGISTiledLayer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.Viewpoint
import com.esri.arcgisruntime.mapping.view.Callout
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.mapping.view.ViewpointChangedListener
import com.esri.arcgisruntime.symbology.SimpleFillSymbol
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol
import com.esri.arcgisruntime.symbology.Symbol
import com.esri.arcgisruntime.symbology.TextSymbol
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.activities.NotAtHomeActivity
import pk.gop.pulse.katchiAbadi.activities.SurveyActivity
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.CustomTileLayer
import pk.gop.pulse.katchiAbadi.common.DownloadType
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcel
import pk.gop.pulse.katchiAbadi.common.SubParcel
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.response.SubParcelStatus
import pk.gop.pulse.katchiAbadi.databinding.FragmentMapBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.SurveyStatusCodes
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.math.roundToInt


@AndroidEntryPoint
class FragmentMap : Fragment() {

    private val viewModel: SharedFormViewModel by activityViewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var context: Context
    private var sdCardRoot: File? = null
    private val wgs84 by lazy {
        SpatialReferences.getWgs84()
    }
    private var mTiledLayer: ArcGISTiledLayer? = null
    private var arcGISMap: ArcGISMap? = null
    private lateinit var surveyParcelsGraphics: GraphicsOverlay
    private lateinit var surveyLabelGraphics: GraphicsOverlay
    private lateinit var currentLocationGraphicOverlay: GraphicsOverlay
    private lateinit var unSurveyedBlocks: SimpleFillSymbol
    private lateinit var surveyedBlocks: SimpleFillSymbol
    private lateinit var lockedBlocks: SimpleFillSymbol
    private lateinit var revisitBlocks: SimpleFillSymbol
    lateinit var mCallOut: Callout
    private val permissionRequestCode = 200
    private lateinit var tpkPath: String
    private lateinit var tpkExtent: Envelope
    private var ids: ArrayList<Long> = arrayListOf()
    private var enableNewPoint = true
    private lateinit var tvParcelNo: TextView
    private lateinit var tvParcelNoUni: TextView
    private lateinit var dialogView: View
    private lateinit var tvMergeParcel: TextView
    private lateinit var tvMergeParcelHi: TextView
    private val originalGraphicSymbols = mutableMapOf<Long, Symbol>()
    private val originalLabelGraphics = mutableMapOf<Long, Graphic>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var enableZoom: Boolean = true
    private lateinit var graphicCentoid: Graphic
    private lateinit var viewpointChangedListener: ViewpointChangedListener
    private var job: Job? = null
    private var lm: LocationManager? = null
    private var ls: LocationListener? = null
    private val redLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 1f)
    private val cyanLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.CYAN, 2f)
    private val magentaLine = SimpleLineSymbol(
        SimpleLineSymbol.Style.SOLID,
        Color.MAGENTA,
        2f
    )


    private var isMergeMode = false
    private val selectedMergeParcels = LinkedHashMap<String, String>()
    private val defaultParcelSymbol = SimpleFillSymbol(
        SimpleFillSymbol.Style.SOLID,
        Color.argb(50, 255, 255, 255),  // transparent white
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GRAY, 1f)
    )

    private val greenLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 3f)

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 👇 Back button click listener
        binding.ivBack.setOnClickListener {
            val intent = Intent(requireContext(), MenuActivity::class.java)
            startActivity(intent)
            requireActivity().finish() // Optional: if you want to close the current activity
        }
        // Set header text
        setHeaderText()
        context = requireContext()
        lm = context.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager

        binding.btnDoneMerge.setOnClickListener {
            if (selectedMergeParcels.size < 1) {
                Toast.makeText(
                    requireContext(),
                    "Select at least 1 parcel to merge",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            isMergeMode = false
            binding.mergeControlBar.visibility = View.GONE

            // ✅ Safely restore dialogView/card
            dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE

            Toast.makeText(
                requireContext(),
                "Merged parcels: ${selectedMergeParcels.values.joinToString(", ")}",
                Toast.LENGTH_SHORT
            ).show()


            // ✅ KEEP the parcel info visible after merge
            // Do NOT clear tvMergeParcel, tvMergeParcelHi, or viewModel here

            binding.parcelMapview.setOnTouchListener(
                DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
            )
        }

        binding.btnCancelMerge.setOnClickListener {
            isMergeMode = false
            selectedMergeParcels.clear()
            restoreOriginalGraphics()

            binding.mergeControlBar.visibility = View.GONE

            // ✅ Safely restore dialogView/card
            dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE

            // ✅ Clear state
            viewModel.parcelOperationValue = ""
            tvMergeParcel.text = ""
            tvMergeParcelHi.text = ""

            binding.parcelMapview.setOnTouchListener(
                DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
            )
        }


    }


    override fun onResume() {
        super.onResume()
        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
        try {
            binding.apply {
                viewpointChangedListener = ViewpointChangedListener {
                    val currentZoomLevel = parcelMapview.mapScale
                    if (currentZoomLevel < 5000) {
                        if (!parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)) {
                            parcelMapview.graphicsOverlays.add(surveyLabelGraphics)
                        }
                    } else {
                        if (parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)) {
                            parcelMapview.graphicsOverlays.remove(surveyLabelGraphics)
                        }
                    }
                }
            }

            context = requireContext()
            sdCardRoot = requireContext().filesDir
            currentLocationGraphicOverlay = GraphicsOverlay()

            sdCardRoot?.let {
                loadMap(ids, false)
            } ?: run {
                Toast.makeText(context, "Internal Storage not accessible", Toast.LENGTH_LONG).show()
            }
            _binding?.parcelMapview?.resume()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Restart the map screen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupMap() {
        with(binding) {
            fab.setOnClickListener {
                handleFabClick()
            }
            ivSearch.setOnClickListener {
                closeCallOut()
                val items: MutableList<SearchableItem> = ArrayList()

                viewLifecycleOwner.lifecycleScope.launch {
                    ids.clear()
                    //Update this to mauza id and area name
                    val areaId = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    )
                    //Update this to mauza id and area name
//                    val areaName = sharedPreferences.getLong(
//                        Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
//                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
//                    )


                    val mauzaId = sharedPreferences.getLong(
                        Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
                    )

                    val areaName = sharedPreferences.getString(
                        Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                        Constants.SHARED_PREF_DEFAULT_STRING
                    ).orEmpty()
// update the Active parcels instead of parceldao
//                    val parcels = database.parcelDao().getAllParcelsWithAreaId(areaId)
                    val parcels =
                        database.activeParcelDao().getParcelsByMauzaAndArea(mauzaId, areaName)

                    for (parcel in parcels) {
                        if (viewModel.parcelNo != parcel.parcelNo)
                            items.add(SearchableItem("${parcel.parcelNo}", "${parcel.id}"))
                    }

                    SearchableMultiSelectSpinner.show(
                        requireContext(),
                        "Select Parcels",
                        "Done",
                        items,
                        object :
                            SelectionCompleteListener {
                            override fun onCompleteSelection(selectedItems: ArrayList<SearchableItem>) {
                                if (selectedItems.size > 0) {
                                    enableNewPoint = false

                                    viewLifecycleOwner.lifecycleScope.launch {
                                        ids.clear()
                                        for (item in selectedItems) {
                                            ids.add(item.text.toLong())
                                        }

                                        loadMap(ids, true)
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Parcel not selected",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        })
                }
            }

            ivReset.setOnClickListener {
                closeCallOut()
                viewLifecycleOwner.lifecycleScope.launch {
                    ids.clear()
                    enableNewPoint = true
                    loadMap(ids, false)
                }
            }
        }
    }

    private fun loadMap(ids: ArrayList<Long>, showLabels: Boolean) {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
//                loadMapTPK(ids, showLabels)
            }

            DownloadType.TILES -> {
                loadMapTiles(ids, showLabels)
            }
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    private fun loadMapTiles(ids: ArrayList<Long>, showLabels: Boolean) {
        with(binding) {

            surveyParcelsGraphics = GraphicsOverlay()
            surveyLabelGraphics = GraphicsOverlay()
            parcelMapview.graphicsOverlays.clear()

            setupMap()

            progressBar.visibility = View.VISIBLE
            layoutInfo.visibility = View.GONE
            layoutRejected.visibility = View.GONE
            fab.visibility = View.GONE
            parcelMapview.visibility = View.GONE

            job = CoroutineScope(Dispatchers.IO).launch {

                unSurveyedBlocks =
                    SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.WHITE, redLine)
                surveyedBlocks =
                    SimpleFillSymbol(SimpleFillSymbol.Style.NULL, Color.WHITE, greenLine)

                revisitBlocks = SimpleFillSymbol(
                    SimpleFillSymbol.Style.SOLID, Color.argb(
                        80,
                        Color.red(Color.MAGENTA),
                        Color.green(Color.MAGENTA),
                        Color.blue(Color.MAGENTA)
                    ), // Set the fill color,
                    SimpleLineSymbol(
                        SimpleLineSymbol.Style.SOLID,
                        Color.MAGENTA,
                        1.5f
                    )
                )

                lockedBlocks = SimpleFillSymbol(
                    SimpleFillSymbol.Style.SOLID, Color.argb(
                        80, Color.red(Color.BLUE), Color.green(Color.BLUE), Color.blue(Color.BLUE)
                    ), // Set the fill color,
                    SimpleLineSymbol(
                        SimpleLineSymbol.Style.SOLID,
                        ContextCompat.getColor(context, R.color.parcel_blue),
                        1.5f
                    )
                )

                val areaId = sharedPreferences.getLong(
                    Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
                    Constants.SHARED_PREF_DEFAULT_INT.toLong()
                )


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


//                val parcels = if (ids.isNotEmpty()) {
//                    database.parcelDao().searchParcels(ids, areaId)
//                } else {
//                    database.parcelDao().getAllParcelsWithAreaId(areaId)
//                }

                val parcels = if (ids.isNotEmpty()) {
                    database.activeParcelDao().searchParcels(ids)
                } else {
                    database.activeParcelDao().getParcelsByMauzaAndArea(mauzaId, areaName)
                }

                val polygonsList = mutableListOf<Polygon>()

                val gson = Gson()

                for (parcel in parcels) {
                    val parcelGeom = parcel.geomWKT

                    if (parcelGeom.isNullOrBlank()) {
                        Log.e("ParcelProcessing", "Empty geometry for parcel ${parcel.id}")
                        continue
                    }

                    try {
                        when {
                            parcelGeom.contains("MULTIPOLYGON (((") -> {
                                val polygons = Utility.getMultiPolygonFromString(parcelGeom, wgs84)
                                for (polygon in polygons) {
                                    try {
                                        val simplifiedPolygon = Utility.simplifyPolygon(polygon)
                                        polygonsList.add(simplifiedPolygon)
                                        addGraphics(parcel = parcel, polygon = polygon, gson = gson)
                                    } catch (e: Exception) {
                                        Log.e(
                                            "ParcelProcessing",
                                            "Error simplifying multipolygon for parcel ${parcel.id}: ${e.message}",
                                            e
                                        )
                                        // Add original polygon as fallback or skip this polygon
                                        polygonsList.add(polygon)
                                        addGraphics(parcel = parcel, polygon = polygon, gson = gson)
                                    }
                                }
                            }

                            parcelGeom.contains("POLYGON ((") -> {
                                try {
                                    val rawPolygon = Utility.getPolygonFromString(parcelGeom, wgs84)
                                    val simplifiedPolygon = Utility.simplifyPolygon(rawPolygon)
                                    polygonsList.add(simplifiedPolygon)
                                    addGraphics(
                                        parcel = parcel,
                                        polygon = simplifiedPolygon,
                                        gson = gson
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        "ParcelProcessing",
                                        "Error processing POLYGON for parcel ${parcel.id}: ${e.message}",
                                        e
                                    )
                                    // Try to use raw polygon without simplification
                                    try {
                                        val rawPolygon =
                                            Utility.getPolygonFromString(parcelGeom, wgs84)
                                        polygonsList.add(rawPolygon)
                                        addGraphics(
                                            parcel = parcel,
                                            polygon = rawPolygon,
                                            gson = gson
                                        )
                                    } catch (e2: Exception) {
                                        Log.e(
                                            "ParcelProcessing",
                                            "Failed to process any polygon for parcel ${parcel.id}",
                                            e2
                                        )
                                        // Skip this parcel entirely
                                        continue
                                    }
                                }
                            }

                            else -> {
                                try {
                                    val rawPolygon = Utility.getPolyFromString(parcelGeom, wgs84)
                                    val simplifiedPolygon = Utility.simplifyPolygon(rawPolygon)
                                    polygonsList.add(simplifiedPolygon)
                                    addGraphics(
                                        parcel = parcel,
                                        polygon = simplifiedPolygon,
                                        gson = gson
                                    )
                                } catch (e: Exception) {
                                    Log.e(
                                        "ParcelProcessing",
                                        "Error processing generic polygon for parcel ${parcel.id}: ${e.message}",
                                        e
                                    )
                                    // Try to use raw polygon without simplification
                                    try {
                                        val rawPolygon =
                                            Utility.getPolyFromString(parcelGeom, wgs84)
                                        polygonsList.add(rawPolygon)
                                        addGraphics(
                                            parcel = parcel,
                                            polygon = rawPolygon,
                                            gson = gson
                                        )
                                    } catch (e2: Exception) {
                                        Log.e(
                                            "ParcelProcessing",
                                            "Failed to process any polygon for parcel ${parcel.id}",
                                            e2
                                        )
                                        // Skip this parcel entirely
                                        continue
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "ParcelProcessing",
                            "Unexpected error processing parcel ${parcel.id}: ${e.message}",
                            e
                        )
                        // Continue with next parcel
                        continue
                    }

//                    if (parcelGeom.contains("MULTIPOLYGON (((")) {
//                        val polygons = Utility.getMultiPolygonFromString(parcelGeom, wgs84)
//                        for (polygon in polygons) {
//                            val simplifiedPolygon = Utility.simplifyPolygon(polygon)
//                            polygonsList.add(simplifiedPolygon)
//                            addGraphics(parcel = parcel, polygon = polygon, gson = gson)
//                        }
//                    } else if (parcelGeom.contains("POLYGON ((")) {
//                        val polygon =
//                            Utility.simplifyPolygon(Utility.getPolygonFromString(parcelGeom, wgs84))
//                        polygonsList.add(polygon)
//                        addGraphics(parcel = parcel, polygon = polygon, gson = gson)
//                    } else {
//                        val polygon =
//                            Utility.simplifyPolygon(Utility.getPolyFromString(parcelGeom, wgs84))
//                        polygonsList.add(polygon)
//                        addGraphics(parcel = parcel, polygon = polygon, gson = gson)
//                    }
                }

                /////////////////////////////////////////////////////////////

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

                val webMercatorEnvelope = GeometryEngine.project(
                    bufferedExtent,
                    SpatialReferences.getWebMercator()
                ) as Envelope

                /////////////////////////////////////////////////////////////

                var parcelsCount = parcels.size
                var acceptedCount = 0
                var rejectedCount = 0
                var surveyedCount = 0
                var lockedCount = 0
                var unSurveyedCount = 0

                for (parcel in parcels) {
                    when (parcel.surveyStatusCode) {
                        1 -> {
                            unSurveyedCount++
                        }


                        2 -> {
                            surveyedCount++
                        }

                        else -> {
                            unSurveyedCount++

                        }
                    }
                }

                val totalSurveyedCount = surveyedCount + acceptedCount

                withContext(Dispatchers.Main) {

                    binding.tvParcelCount.text = "Parcel Count: $parcelsCount"
                    binding.tvSurveyedParcelCount.text = "($totalSurveyedCount)"
                    binding.tvUnsurveyedParcelCount.text = "($unSurveyedCount)"
                    binding.tvLcckedParcelCount.text = "($lockedCount)"
                    binding.tvRevisitParcelCount.text = "($rejectedCount)"

                    // Initialize TileManager
                    val tileManager = TileManager(requireContext())

                    // Define Levels of Detail (LODs) from zoom 7 to 14
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

                    // Create TileInfo
                    val tileInfo = TileInfo(
                        96, // Dots per inch (DPI)
                        TileInfo.ImageFormat.PNG24, // Image format
                        levelsOfDetail, // List of Levels of Detail (LODs)
                        Point(
                            -20037508.3427892,
                            20037508.3427892,
                            SpatialReferences.getWebMercator()
                        ), // Origin
                        SpatialReferences.getWebMercator(), // Spatial reference
                        256, // Tile width in pixels
                        256 // Tile height in pixels
                    )

                    val minZoomLevel = sharedPreferences.getInt(
                        Constants.SHARED_PREF_MAP_MIN_SCALE,
                        Constants.SHARED_PREF_DEFAULT_MIN_SCALE
                    )

                    val maxZoomLevel = sharedPreferences.getInt(
                        Constants.SHARED_PREF_MAP_MAX_SCALE,
                        Constants.SHARED_PREF_DEFAULT_MAX_SCALE
                    )

                    // Create CustomTileLayer
                    val customTileLayer =
                        CustomTileLayer(
                            tileInfo,
                            webMercatorEnvelope,
                            tileManager,
                            folderKey,
                            minZoomLevel,
                            maxZoomLevel
                        )
                    // Create map with the custom tile layer
                    val map = ArcGISMap(Basemap(customTileLayer))
                    map.initialViewpoint = Viewpoint(webMercatorEnvelope)

                    ArcGISRuntimeEnvironment.setLicense("runtimelite,1000,rud5883837740,none,ZZ0RJAY3FLCB0YRJD136")
                    binding.parcelMapview.map = map
                    binding.parcelMapview.isAttributionTextVisible = false

                    if (showLabels) {
                        parcelMapview.graphicsOverlays.add(surveyLabelGraphics)
                        parcelMapview.removeViewpointChangedListener(viewpointChangedListener)
                    } else {
                        parcelMapview.removeViewpointChangedListener(viewpointChangedListener)
                        parcelMapview.addViewpointChangedListener(viewpointChangedListener)
                    }

                    parcelMapview.graphicsOverlays.add(surveyParcelsGraphics)
                    mCallOut = parcelMapview.callout

                    try {
                        IdentifyFeatureLayerTouchListener(
                            context,
                            parcelMapview,
                            this@FragmentMap.surveyParcelsGraphics
                        ).also { parcelMapview.onTouchListener = it }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Restart the map screen.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    progressBar.visibility = View.GONE
                    layoutInfo.visibility = View.VISIBLE
                    layoutRejected.visibility = View.VISIBLE
                    fab.visibility = View.VISIBLE
                    parcelMapview.visibility = View.VISIBLE
                    ivReset.visibility = View.VISIBLE
                    ivSearch.visibility = View.GONE
                }
            }
        }
    }

    private fun addGraphics(parcel: ActiveParcelEntity, polygon: Polygon, gson: Gson) {
        val area = GeometryEngine.areaGeodetic(
            polygon,
            AreaUnit(AreaUnitId.SQUARE_FEET),
            GeodeticCurveType.NORMAL_SECTION
        ).roundToInt()

        val myPolygonCenterLatLon = polygon.extent.center

        var isRejected = 0
        val symbol: SimpleFillSymbol
        val highlightColor: Int
        val textColor: Int

        when (parcel.surveyStatusCode) {
            1 -> {
                symbol = unSurveyedBlocks
                highlightColor = Color.YELLOW
                textColor = ContextCompat.getColor(context, R.color.parcel_red)
            }


            2 -> {

                symbol = surveyedBlocks
                highlightColor = Color.BLACK
                textColor = ContextCompat.getColor(context, R.color.parcel_green)


            }

            else -> {
                symbol = unSurveyedBlocks
                highlightColor = Color.YELLOW
                textColor = ContextCompat.getColor(context, R.color.parcel_red)

            }
        }

        val parcelGraphic = Graphic(
            polygon, symbol
        )


        val attr = parcelGraphic.attributes
        attr["parcel_id"] = parcel.id
        attr["parcel_no"] = parcel.parcelNo
        attr["sub_parcel_no"] = parcel.subParcelNo
        attr["surveyStatusCode"] = parcel.surveyStatusCode
        attr["area"] = area
        attr["geomWKT"] = parcel.geomWKT
        attr["centroid"] = parcel.centroid
        attr["isRejected"] = isRejected

        val polyLabelSymbol = TextSymbol().apply {
            text = "${parcel.parcelNo}\n ${parcel.khewatInfo}"
            size = 10f
            color = textColor
            horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
            verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
            haloColor = highlightColor
            haloWidth = 1f
            fontWeight = TextSymbol.FontWeight.BOLD
        }

        val labelGraphic = Graphic(myPolygonCenterLatLon, polyLabelSymbol)


        val parcelId = parcel.id

// Save original symbols
        originalGraphicSymbols[parcelId] = symbol
        originalLabelGraphics[parcelId] = labelGraphic

        val attrLabel = labelGraphic.attributes
        attr["parcel_id"] = parcel.id
        attr["parcel_no"] = parcel.parcelNo
        attr["sub_parcel_no"] = parcel.subParcelNo
        attr["khewatInfo"] = parcel.khewatInfo
        attr["surveyStatusCode"] = parcel.surveyStatusCode
        attr["area"] = area
        attr["geomWKT"] = parcel.geomWKT
        attr["centroid"] = parcel.centroid
        attr["isRejected"] = isRejected

        surveyLabelGraphics.graphics.add(labelGraphic)
        surveyParcelsGraphics.graphics.add(parcelGraphic)
    }

    private fun restoreOriginalGraphics() {
        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue
            val originalSymbol = originalGraphicSymbols[parcelId]
            if (originalSymbol != null) {
                graphic.symbol = originalSymbol
            }
        }

        // Optionally reset labels too (remove merge labels and add back original ones)
        surveyLabelGraphics.graphics.clear()
        surveyLabelGraphics.graphics.addAll(originalLabelGraphics.values)
    }

    private fun handleFabClick() {
        if (checkPermission()) {
            startLocationProcess(true)
        } else {
            requestPermission()
        }
    }


    private fun setHeaderText() {
        val mauzaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        )

        val areaName = sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        )

        if (areaName != null) {
            binding.tvHeader.text = "$mauzaName ($areaName) Map"
        } else {
            binding.tvHeader.text = "$mauzaName (Map)"
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            permissionRequestCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationProcess(true)
            } else {

                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    // User has permanently denied the permission, open settings dialog
                    showSettingsDialog("Location")
                } else {
                    // Display a rationale and request permission again
                    showMessageOKCancel(
                        "You need to allow location permission"
                    ) { _, _ ->
                        requestPermission()
                    }
                }
            }
        }
    }

    private fun showSettingsDialog(value: String) {
        val builder = AlertDialog.Builder(context)
            .setMessage("You have denied $value permission permanently. Please go to settings to enable it.")
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

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
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

    private fun startLocationProcess(enableZoom: Boolean) {
        try {

            if (Utility.checkGPS(requireActivity())) {
                Utility.showProgressAlertDialog(context, "Please wait, fetching location...")
                getCurrentLocationFromFusedProvider(enableZoom)
            } else {
                Utility.buildAlertMessageNoGps(requireActivity())
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location Exception :${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocationFromFusedProvider(zoom: Boolean) {
        try {

            enableZoom = zoom

            locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(1500)
                .setMaxUpdateDelayMillis(3000)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Current Location Exception :${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleLocationResult(locationResult: LocationResult) {
        for (location in locationResult.locations) {
            val locationAccuracy = location.accuracy.roundToInt()

            val meterAccuracy = sharedPreferences.getInt(
                Constants.SHARED_PREF_METER_ACCURACY,
                Constants.SHARED_PREF_DEFAULT_ACCURACY
            )

            val accuracy = if (enableZoom) {
                meterAccuracy
            } else {
                Constants.locationMediumAccuracy
            }

            Log.d("TAG", locationAccuracy.toString())
            Log.d("TAG", location.provider.toString())

            if (locationAccuracy < accuracy) {

                if (Build.VERSION.SDK_INT < 31) {
                    @Suppress("DEPRECATION")
                    if (!location.isFromMockProvider) {
                        Utility.dismissProgressAlertDialog()
                        if (!enableZoom) {
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                        }
                        setLocation(location, enableZoom)
                    } else {
                        Utility.dismissProgressAlertDialog()
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        Utility.exitApplication(
                            "Warning!",
                            "Please disable mock/fake location. The application will exit now.",
                            requireActivity()
                        )
                    }
                } else {
                    if (!location.isMock) {
                        Utility.dismissProgressAlertDialog()
                        if (!enableZoom) {
                            fusedLocationClient.removeLocationUpdates(locationCallback)
                        }
                        setLocation(location, enableZoom)
                    } else {
                        Utility.dismissProgressAlertDialog()
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        Utility.exitApplication(
                            "Warning!",
                            "Please disable mock/fake location. The application will exit now.",
                            requireActivity()
                        )
                    }
                }

            }
        }
    }

    override fun onPause() {
        _binding?.parcelMapview?.pause()
        super.onPause()
        stopLocationUpdates()
        stopLoadingParcels()
    }

    private fun stopLocationUpdates() {
        if (this::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        ls?.let { lm?.removeUpdates(it) }
    }

    private fun setLocation(location: Location, enableZoom: Boolean) {
        stopLocationUpdates() // Optional: stop updates after getting a good fix

        // Show marker directly without checking parcel DB
        drawMarker(
            location.latitude.toString(),
            location.longitude.toString(),
            enableZoom
        )
    }


    private fun drawMarker(lat: String, lng: String, enableZoom: Boolean) {
        try {
            val latitude = lat.toDoubleOrNull()
            val longitude = lng.toDoubleOrNull()

            if (latitude == null || longitude == null ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                Log.e("TAG", "Invalid coordinates: lat=$latitude, lng=$longitude")
                return
            }

            // Create the point using WGS84 (no projection needed)
            val location = Point(longitude, latitude, SpatialReferences.getWgs84())

            // Create the marker symbol
            val markerSymbol = SimpleMarkerSymbol(
                SimpleMarkerSymbol.Style.CIRCLE,
                ContextCompat.getColor(context, R.color.current_location),
                22f
            ).apply {
                outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
            }

            // Ensure the overlay is added only once
            if (!binding.parcelMapview.graphicsOverlays.contains(currentLocationGraphicOverlay)) {
                binding.parcelMapview.graphicsOverlays.add(currentLocationGraphicOverlay)
            }

            // Clear old markers and add the new one
            currentLocationGraphicOverlay.graphics.clear()
            currentLocationGraphicOverlay.graphics.add(Graphic(location, markerSymbol))

            // Optionally zoom to the point
            if (enableZoom) {
                binding.parcelMapview.setViewpointAsync(Viewpoint(location, 1000.0))
            }

            Log.d("TAG", "Marker drawn at lat=$latitude, lng=$longitude")

        } catch (e: Exception) {
            Log.e("TAG", "drawMarker error: ${e.message}", e)
        }
    }


    private fun loadMapFile() {

        val mauzaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val areaId = sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )

        val filesDir = File(context.filesDir, "MapTpk/${areaId}")

        val destinationFile = File(filesDir, "${mauzaId}_${areaId}.tpk")

        Log.d("TAG", "inside load map file")

        if (!destinationFile.exists()) {
            Toast.makeText(context, "No offline map available", Toast.LENGTH_LONG).show()
        } else {
            val cache = TileCache(destinationFile.path)
            mTiledLayer = ArcGISTiledLayer(cache)

            arcGISMap = ArcGISMap(mTiledLayer?.spatialReference)
            arcGISMap?.basemap = Basemap(mTiledLayer)
            ArcGISRuntimeEnvironment.setLicense("runtimelite,1000,rud5883837740,none,ZZ0RJAY3FLCB0YRJD136")
            binding.parcelMapview.map = arcGISMap
            binding.parcelMapview.isAttributionTextVisible = false
        }
        Log.d("TAG", "leaving load map file")
    }

    private inner class IdentifyFeatureLayerTouchListener(
        context: Context?, mapView: MapView, private val go: GraphicsOverlay
    ) : DefaultMapViewOnTouchListener(context, mapView) {

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean {
            return e1 != null
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {

            try {
                val screenPoint: android.graphics.Point? = try {
                    android.graphics.Point(e.x.toInt(), e.y.toInt())
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Restart the map screen and tap again on the map",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                // Return if screenPoint is null
                if (screenPoint == null) {
                    Toast.makeText(
                        context,
                        "Restart the map screen and tap again on the map",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                // Check if the click is within the extent of the tile package
                val mapPoint: Point? = try {
                    binding.parcelMapview.screenToLocation(screenPoint)
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Restart the map screen and tap again on the map",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                // Return if mapPoint is null
                if (mapPoint == null) {
                    Toast.makeText(
                        context,
                        "Restart the map screen and tap again on the map",
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                val tpkExtent = binding.parcelMapview.visibleArea.extent
                val isContained = GeometryEngine.contains(tpkExtent, mapPoint)

                if (!isContained) {
                    return true
                }

                if (mCallOut.isShowing) {
                    mCallOut.dismiss()
                }

                val identifyGO =
                    mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 3.0, false, 1)
                identifyGO.addDoneListener {
                    try {
                        val igr = identifyGO.get()
                        if (igr != null && igr.graphics.isNotEmpty()) {
                            val graphic = igr.graphics[0]
                            graphic?.let { showSurveyedCallOutNew(it, screenPoint) }
                        } else {
                            /*if (enableNewPoint)
                                displayCallOut(screenPoint)*/
                        }
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Restart the map screen and tap again on the map",
                    Toast.LENGTH_LONG
                ).show()
                return false
            }

            return true
        }

    }


    @SuppressLint("ClickableViewAccessibility")
    private fun showSurveyedCallOutNew(graphics: Graphic, screenPoint: android.graphics.Point) {
        val inflater = this.layoutInflater
        dialogView = inflater.inflate(R.layout.cardview_map_info_new, null)
//        val dialogView: View = inflater.inflate(R.layout.cardview_map_info_new, null)

        val attr = graphics.attributes

        tvParcelNo = dialogView.findViewById<TextView>(R.id.tv_parcel_no_value)
        tvParcelNoUni = dialogView.findViewById<TextView>(R.id.tv_parcel_no_uni_value)
        val tvParcelArea = dialogView.findViewById<TextView>(R.id.tv_parcel_area_value)
        val lActionButtons = dialogView.findViewById<LinearLayout>(R.id.layout_action_buttons)
        val lParcel = dialogView.findViewById<LinearLayout>(R.id.layout_parcel)
        val btnStartSurvey = dialogView.findViewById<Button>(R.id.btn_start_survey)
        val btnRevisitSurvey = dialogView.findViewById<Button>(R.id.btn_revisit_survey)
        val btnRetakePicturesSurvey =
            dialogView.findViewById<Button>(R.id.btn_retake_pictures_survey)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

        val delete = dialogView.findViewById<ImageView>(R.id.delete)
        val mapLocation = dialogView.findViewById<ImageView>(R.id.mapLocaton)
        val directions = dialogView.findViewById<ImageView>(R.id.directions)

        val lSplitParcel = dialogView.findViewById<LinearLayout>(R.id.layout_split_parcel)
        val etSplitParcel = dialogView.findViewById<EditText>(R.id.et_split_parcel)

        val lMergeParcel = dialogView.findViewById<LinearLayout>(R.id.layout_merge_parcel)
        tvMergeParcel = dialogView.findViewById<TextView>(R.id.tv_merge_parcel)
        tvMergeParcelHi = dialogView.findViewById<TextView>(R.id.tv_merge_parcel_hi)

        val rgParcel = dialogView.findViewById<RadioGroup>(R.id.rg_parcel)

        rgParcel.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_same -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.GONE
                }

                R.id.rb_split -> {
                    lSplitParcel.visibility = View.VISIBLE
                    lMergeParcel.visibility = View.GONE
                }

                R.id.rb_merge -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.VISIBLE
                }
            }
        }

        tvParcelNo.text = attr["parcel_no"].toString()
        tvParcelNoUni.text = attr["khewatInfo"].toString()
        tvParcelArea.text = "${attr["area"]} Sq. Ft."

        // Ensure MapView has a valid map
        if (binding.parcelMapview.map == null) {
            Toast.makeText(context, "MapView does not have a valid map", Toast.LENGTH_LONG).show()
            return
        }

        val mapPoint: Point? = try {
            binding.parcelMapview.screenToLocation(screenPoint)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "screenToLocation threw an exception: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (mapPoint == null) {
            Toast.makeText(
                context,
                "screenToLocation returned null for screenPoint: $screenPoint",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        when (attr["surveyStatusCode"].toString().toInt()) {
            1 -> {
                lParcel.visibility = View.VISIBLE
                btnStartSurvey.visibility = View.VISIBLE
                btnRevisitSurvey.visibility = View.GONE
                btnRetakePicturesSurvey.visibility = View.GONE
                delete.visibility = View.GONE

            }

            2 -> {
                lParcel.visibility = View.GONE
                lSplitParcel.visibility = View.GONE
                lMergeParcel.visibility = View.GONE
                btnStartSurvey.visibility = View.GONE
                btnRevisitSurvey.visibility = View.GONE
                delete.visibility = View.GONE
//                lParcel.visibility = View.VISIBLE
//                btnStartSurvey.visibility = View.VISIBLE
//                btnRevisitSurvey.visibility = View.GONE
//                btnRetakePicturesSurvey.visibility = View.GONE
//                delete.visibility = View.GONE
            }

            else -> {
//                // Check for revisit conditions
//                if (attr["isRejected"].toString().toInt() == 1) {
//                    lParcel.visibility = View.VISIBLE
//                    btnStartSurvey.visibility = View.VISIBLE
//                    btnRevisitSurvey.visibility = View.GONE
//                    btnRetakePicturesSurvey.visibility = View.GONE
//                    delete.visibility = View.GONE
//                } else if (attr["isRejected"].toString().toInt() == 2) {
//                    lParcel.visibility = View.GONE
//                    btnStartSurvey.visibility = View.GONE
//                    btnRevisitSurvey.visibility = View.GONE
//                    btnRetakePicturesSurvey.visibility = View.VISIBLE
//                    delete.visibility = View.GONE
//                } else {
//                    lParcel.visibility = View.GONE
//                    lSplitParcel.visibility = View.GONE
//                    lMergeParcel.visibility = View.GONE
//                    btnStartSurvey.visibility = View.GONE
//                    btnRevisitSurvey.visibility = View.GONE
//                    delete.visibility = View.GONE
//                }
            }
        }

        tvMergeParcel.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Tap on parcels to select for merge",
                Toast.LENGTH_SHORT
            ).show()

            isMergeMode = true
            selectedMergeParcels.clear()

            dialogView.findViewById<View>(R.id.card_root).visibility = View.GONE
            binding.mergeControlBar.visibility = View.VISIBLE

            // Reset all parcel symbols
            for (graphic in surveyParcelsGraphics.graphics) {
                graphic.symbol = defaultParcelSymbol
            }

            // Get base parcel info from selected callout graphic
            val baseParcelId = graphics.attributes["parcel_id"].toString()

            // Highlight base parcel but don't include it in selection
            graphics.symbol = SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                Color.argb(100, 255, 140, 0), // orange fill
                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 3f) // red outline
            )

            // Handle user taps for selecting merge parcels
            val touchListener =
                object : DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview) {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!isMergeMode) return super.onSingleTapConfirmed(e)

                        val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

                        val identifyFuture = binding.parcelMapview.identifyGraphicsOverlayAsync(
                            surveyParcelsGraphics,
                            screenPoint,
                            10.0,
                            false
                        )

                        identifyFuture.addDoneListener {
                            try {
                                val result = identifyFuture.get()
                                if (result.graphics.isNotEmpty()) {
                                    val selectedGraphic = result.graphics[0]
                                    val parcelId =
                                        selectedGraphic.attributes["parcel_id"].toString()
                                    val parcelNo =
                                        selectedGraphic.attributes["parcel_no"].toString()
                                    val surveyStatusCode =
                                        selectedGraphic.attributes["surveyStatusCode"].toString()
                                            .toInt()
                                    // Skip base parcel from selection
                                    if (parcelId == baseParcelId) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Base parcel cannot be selected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@addDoneListener
                                    }
                                    if (surveyStatusCode == 2) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Surveyed parcel cannot be selected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@addDoneListener
                                    }

                                    if (selectedMergeParcels.containsKey(parcelId)) {
                                        // Unselect
                                        selectedMergeParcels.remove(parcelId)
                                        selectedGraphic.symbol = defaultParcelSymbol
                                        Toast.makeText(
                                            requireContext(),
                                            "Parcel unselected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        // Select
                                        selectedMergeParcels[parcelId] = parcelNo
                                        selectedGraphic.symbol = SimpleFillSymbol(
                                            SimpleFillSymbol.Style.SOLID,
                                            Color.argb(80, 30, 144, 255),
                                            SimpleLineSymbol(
                                                SimpleLineSymbol.Style.SOLID,
                                                Color.BLUE,
                                                2f
                                            )
                                        )
                                    }

                                    // Preserve original text behavior
                                    val parcelNos = selectedMergeParcels.values.joinToString(", ")
                                    val parcelIds = selectedMergeParcels.keys.joinToString(", ")

                                    tvMergeParcel.text = parcelNos
                                    tvMergeParcelHi.text = parcelIds
                                    viewModel.parcelOperationValue = parcelNos
                                } else {
                                    Toast.makeText(
                                        requireContext(),
                                        "No parcel found at this location",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    requireContext(),
                                    "Error identifying parcel",
                                    Toast.LENGTH_SHORT
                                ).show()
                                e.printStackTrace()
                            }
                        }

                        return true
                    }
                }

            binding.parcelMapview.onTouchListener = touchListener
        }

//
//
//        btnStartSurvey.setOnClickListener { view: View? ->
//            val radioButton: RadioButton =
//                dialogView.findViewById(rgParcel.checkedRadioButtonId)
//
//            viewModel.parcelOperation = radioButton.text.toString()
//
//            var action = R.id.action_fragmentMap_to_fragmentParcelDetails
//
////            var action = R.id.action_fragmentMap_to_fragmentSurveyList
//            when (rgParcel.checkedRadioButtonId) {
//                R.id.rb_same -> {
//                    viewModel.parcelOperationValue = ""
//                    viewModel.imageTaken = 0
//                    viewModel.discrepancyPicturePath = ""
//                }
//
//
//                R.id.rb_split -> {
//                    if (etSplitParcel.text.toString().trim().isEmpty()) {
//                        etSplitParcel.apply {
//                            setText("")
//                            error = "Field cannot be empty"
//                            requestFocus()
//                        }
//                        return@setOnClickListener
//                    }
//
//                    if (etSplitParcel.text.toString().trim().toInt() < 2) {
//                        etSplitParcel.apply {
//                            setText("")
//                            error = "Enter valid number of parcels"
//                            requestFocus()
//                        }
//                        return@setOnClickListener
//                    }
//
//                    viewModel.parcelOperationValue = etSplitParcel.text.toString().trim()
//                    viewModel.subParcelList.clear()
//
//                    if (viewModel.subParcelList.isEmpty()) {
//                        val totalParts: Int = viewModel.parcelOperationValue.toInt()
//                        val subParcels = ArrayList<SubParcel>()
//                        for (i in 1..totalParts) {
//                            subParcels.add(SubParcel(id = i))
//                        }
//                        viewModel.subParcelList = subParcels
//                    }
//
//                    // ✅ Navigate to intermediate SubParcelList screen
//                    findNavController().navigate(R.id.action_fragmentMap_to_fragmentSubParcelList)
//                    return@setOnClickListener
//                }
//
//
//                R.id.rb_merge -> {
//                    if (tvMergeParcel.text.toString().trim().isEmpty()) {
//                        Toast.makeText(
//                            requireContext(),
//                            "Merge parcel field is empty",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                        return@setOnClickListener
//                    }
//
//                   // viewModel.parcelOperationValue = tvMergeParcel.text.toString().trim()
//                   // viewModel.parcelOperationValue = tvMergeParcelHi.text.toString().trim()
//
//                }
//            }
//
//            val context = requireContext()
//            val intent = Intent(context, SurveyActivity::class.java).apply {
//                putExtra("parcelId", attr["parcel_id"].toString().toLong())
//                putExtra("parcelNo", attr["parcel_no"].toString())
//                putExtra("subParcelNo", attr["sub_parcel_no"].toString())
//                putExtra("parcelArea",attr["area"].toString())
//                putExtra("khewatInfo",attr["khewatInfo"].toString())
//                putExtra("parcelOperation", viewModel.parcelOperation)
//                putExtra("parcelOperationValue", viewModel.parcelOperationValue)
//                putExtra("parcelOperationValueHi", tvMergeParcelHi.text.toString().trim())
//                // Add other extras if needed (areaName, parcelId, etc.)
//            }
//            startActivity(intent)
//        }
//


        btnStartSurvey.setOnClickListener { view: View? ->
            val radioButton: RadioButton =
                dialogView.findViewById(rgParcel.checkedRadioButtonId)

            viewModel.parcelOperation = radioButton.text.toString()
            viewModel.parcelId = attr["parcel_id"].toString().toLong()
            viewModel.parcelNo = attr["parcel_no"].toString().toLong()
            viewModel.subParcelNo = attr["sub_parcel_no"].toString()


            when (rgParcel.checkedRadioButtonId) {


                // ---------------- SAME ----------------
                R.id.rb_same -> {
                    viewModel.parcelOperationValue = ""
                    viewModel.imageTaken = 0
                    viewModel.discrepancyPicturePath = ""

                    val context = requireContext()
                    val intent = Intent(context, SurveyActivity::class.java).apply {
                        putExtra("parcelId", attr["parcel_id"].toString().toLong())
                        putExtra("parcelNo", attr["parcel_no"].toString())
                        putExtra("subParcelNo", attr["sub_parcel_no"].toString())
                        putExtra("parcelArea", attr["area"].toString())
                        putExtra("khewatInfo", attr["khewatInfo"].toString())
                        putExtra("parcelOperation", viewModel.parcelOperation)
                        putExtra("parcelOperationValue", viewModel.parcelOperationValue)
                        putExtra("parcelOperationValueHi", tvMergeParcelHi.text.toString().trim())
                    }
                    startActivity(intent)
                }

                // ---------------- SPLIT ----------------
                R.id.rb_split -> {
                    if (etSplitParcel.text.toString().trim().isEmpty()) {
                        etSplitParcel.apply {
                            setText("")
                            error = "Field cannot be empty"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etSplitParcel.text.toString().trim().toInt() < 2) {
                        etSplitParcel.apply {
                            setText("")
                            error = "Enter valid number of parcels"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    viewModel.parcelOperationValue = etSplitParcel.text.toString().trim()
                    viewModel.subParcelList.clear()

                    if (viewModel.subParcelList.isEmpty()) {
                        val totalParts: Int = viewModel.parcelOperationValue.toInt()
                        val subParcels = ArrayList<SubParcel>()
                        for (i in 1..totalParts) {
                            subParcels.add(SubParcel(id = i))
                        }
                        viewModel.subParcelList = subParcels
                    }

                    // Pass same data as Bundle to intermediate SubParcelList screen
                    val bundle = Bundle().apply {
                        putLong("parcelId", attr["parcel_id"].toString().toLong())
                        putString("parcelNo", attr["parcel_no"].toString())
                        putString("subParcelNo", attr["sub_parcel_no"].toString())
                        putString("parcelArea", attr["area"].toString())
                        putString("khewatInfo", attr["khewatInfo"].toString())
                        putString("parcelOperation", viewModel.parcelOperation)
                        putString("parcelOperationValue", viewModel.parcelOperationValue)
                        putString("parcelOperationValueHi", tvMergeParcelHi.text.toString().trim())
                    }

                    findNavController().navigate(
                        R.id.action_fragmentMap_to_fragmentSubParcelList,
                        bundle
                    )
                }

                // ---------------- MERGE ----------------
                R.id.rb_merge -> {
                    if (tvMergeParcel.text.toString().trim().isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "Merge parcel field is empty",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }

                    val context = requireContext()
                    val intent = Intent(context, SurveyActivity::class.java).apply {
                        putExtra("parcelId", attr["parcel_id"].toString().toLong())
                        putExtra("parcelNo", attr["parcel_no"].toString())
                        putExtra("subParcelNo", attr["sub_parcel_no"].toString())
                        putExtra("parcelArea", attr["area"].toString())
                        putExtra("khewatInfo", attr["khewatInfo"].toString())
                        putExtra("parcelOperation", viewModel.parcelOperation)
                        putExtra("parcelOperationValue", viewModel.parcelOperationValue)
                        putExtra("parcelOperationValueHi", tvMergeParcelHi.text.toString().trim())
                    }
                    startActivity(intent)
                }
            }
        }

        btnRevisitSurvey.setOnClickListener {

            graphicCentoid = graphics

            closeCallOut()

            if (checkPermission()) {
                if (Utility.checkGPS(requireActivity())) {
                    Utility.showProgressAlertDialog(context, "Please wait, fetching location...")
                    getCurrentLocation()
                } else {
                    Utility.buildAlertMessageNoGps(requireActivity())
                }
            } else {
                requestPermission()
            }
        }

        btnRetakePicturesSurvey.setOnClickListener {
            graphicCentoid = graphics

            val gson = Gson()

            val subParcelsList: List<SubParcelStatus> = try {
                gson.fromJson(
                    attr["subParcelsStatusList"].toString(),
                    Array<SubParcelStatus>::class.java
                ).toList()
            } catch (e: Exception) {
                emptyList() // Handle invalid JSON gracefully
            }

            var action = 0

            if (subParcelsList.isNotEmpty()) {

                when (subParcelsList.size) {
                    1 -> {
                        val subParcelStatus: SubParcelStatus = subParcelsList.first()

                        if (subParcelStatus.pictureRevisitRequired) {
                            viewModel.parcelOperation = "Same"
                            viewModel.parcelOperationValue = ""
                            viewModel.imageTaken = 0
                            viewModel.discrepancyPicturePath = ""
                            viewModel.newStatusId = attr["newStatusId"].toString().toInt()
                            val gson = Gson()
                            val rejectedSubParcel = RejectedSubParcel(
                                id = subParcelStatus.subParcelNo.toInt(),
                                fieldRecordId = subParcelStatus.fieldRecordId,
                                subParcelNoAction = if (subParcelStatus.fullRevisitRequired) {
                                    "Revisit"
                                } else {
                                    "Retake Picture"
                                },
                                pictureRevisitRequired = true,
                                fullRevisitRequired = subParcelStatus.fullRevisitRequired,
                                position = 0
                            )
                            viewModel.subParcelsStatusList = gson.toJson(rejectedSubParcel)
                        }

                        // Move to "Pictures Screen"
                        action = R.id.action_fragmentMap_to_fragmentFormRemarks

                    }

                    else -> {
                        viewModel.rejectedSubParcelsList.clear()

                        val rejectedSubParcel =
                            arrayListOf<RejectedSubParcel>() // Use a mutable list
                        for (item in subParcelsList) {
                            if (item.fullRevisitRequired || item.pictureRevisitRequired) {
                                rejectedSubParcel.add(
                                    RejectedSubParcel(
                                        id = item.subParcelNo.toInt(),
                                        fieldRecordId = item.fieldRecordId,
                                        subParcelNoAction = if (item.fullRevisitRequired) {
                                            "Revisit"
                                        } else {
                                            "Retake Picture"
                                        },
                                        pictureRevisitRequired = item.pictureRevisitRequired,
                                        fullRevisitRequired = item.fullRevisitRequired,
                                        position = 0
                                    )
                                )
                            }
                        }
                        viewModel.rejectedSubParcelsList = rejectedSubParcel

                        viewModel.parcelOperation = "Split"
                        viewModel.parcelOperationValue = "${rejectedSubParcel.size}"
                        viewModel.imageTaken = 0
                        viewModel.discrepancyPicturePath = ""

                        // Move to "New List of target rejected SubParcels Screen"
                        action = R.id.action_fragmentMap_to_fragmentRejectedSubParcelList
                    }
                }

                viewModel.parcelPkId = attr["parcel_pkid"].toString().toLong()
                viewModel.parcelId = attr["parcel_id"].toString().toLong()
                viewModel.parcelNo = attr["parcel_no"].toString().toLong()
                viewModel.subParcelNo = attr["sub_parcel_no"].toString()
                viewModel.parcelStatus = Constants.Parcel_SAME
                viewModel.geom = attr["geom"].toString()
                viewModel.centroid = attr["centroid"].toString()
                viewModel.isRevisit = 1

                viewModel.performCriticalOperation()

                closeCallOut()
                if (isAdded && action != 0) {
                    findNavController().navigate(action)
                } else {
                    Toast.makeText(context, "Action Undefined", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        delete.setOnClickListener {

            graphicCentoid = graphics

            val builder = AlertDialog.Builder(requireContext())
                .setTitle("Confirm!")
                .setCancelable(false)
                .setMessage("Are you sure, you want to delete this record.")

            builder.setPositiveButton("Proceed") { dialog, _ ->
                dialog.dismiss()

                viewLifecycleOwner.lifecycleScope.launch {

                    val centroid = attr["centroid"].toString()

                    val listOfNAHRecords = database.notAtHomeSurveyFormDao()
                        .getAllSurveyFormWrtCentroid(centroid)

                    if (listOfNAHRecords.isNotEmpty()) {
                        listOfNAHRecords.forEach { survey ->

                            when (survey.parcelOperation) {
                                "Split" -> {
                                    val recordsList = database.notAtHomeSurveyFormDao()
                                        .getRecord(survey.parcelNo, survey.uniqueId)
                                    for (record in recordsList) {
                                        database.parcelDao().updateParcelSurveyStatus(
                                            record.newStatusId,
                                            ParcelStatus.DEFAULT,
                                            record.centroidGeom
                                        )
                                        database.surveyDao()
                                            .updateSurveyStatus(false, record.surveyId)
                                    }
                                    database.notAtHomeSurveyFormDao()
                                        .deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                                }

                                "Merge" -> {
                                    if (survey.parcelOperationValue.contains(",")) {
                                        val parcelNos =
                                            survey.parcelOperationValue.split(",").toMutableList()
                                        for (parcelNo in parcelNos) {

                                            val newStatusId = database.parcelDao().getNewStatusId(
                                                parcelNo.toLong(),
                                                survey.kachiAbadiId
                                            )

                                            database.parcelDao()
                                                .updateParcelSurveyStatusWrtParcelId(
                                                    newStatusId,
                                                    ParcelStatus.DEFAULT,
                                                    parcelNo.toLong()
                                                )
                                        }
                                    } else {
                                        val newStatusId = database.parcelDao().getNewStatusId(
                                            survey.parcelOperationValue.toLong(),
                                            survey.kachiAbadiId
                                        )

                                        database.parcelDao()
                                            .updateParcelSurveyStatusWrtParcelId(
                                                newStatusId,
                                                ParcelStatus.DEFAULT,
                                                survey.parcelOperationValue.toLong()
                                            )
                                    }

                                    val recordsList = database.notAtHomeSurveyFormDao()
                                        .getRecord(survey.parcelNo, survey.uniqueId)
                                    for (record in recordsList) {
                                        database.parcelDao().updateParcelSurveyStatus(
                                            record.newStatusId,
                                            ParcelStatus.DEFAULT,
                                            record.centroidGeom
                                        )
                                        database.surveyDao()
                                            .updateSurveyStatus(false, record.surveyId)
                                    }
                                    database.notAtHomeSurveyFormDao()
                                        .deleteSavedRecord(survey.parcelNo, survey.uniqueId)

                                }

                                else -> {
                                    val recordsList = database.notAtHomeSurveyFormDao()
                                        .getRecord(survey.parcelNo, survey.uniqueId)
                                    for (record in recordsList) {
                                        database.parcelDao().updateParcelSurveyStatus(
                                            record.newStatusId,
                                            ParcelStatus.DEFAULT,
                                            record.centroidGeom
                                        )
                                        database.surveyDao()
                                            .updateSurveyStatus(false, record.surveyId)
                                    }
                                    database.notAtHomeSurveyFormDao()
                                        .deleteSavedRecord(survey.parcelNo, survey.uniqueId)
                                }
                            }
                        }

                        closeCallOut()
                        ids.clear()
                        enableNewPoint = true
                        loadMap(ids, false)
                    }
                }
            }

            builder.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }

            val dialog = builder.create()
            dialog.show()

            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

            positiveButton.textSize = 16f
            positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            negativeButton.textSize = 16f
            negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

        }

        mapLocation.setOnClickListener { view: View? ->

            closeCallOut()
            val projectedPoint =
                GeometryEngine.project(mapPoint, SpatialReferences.getWebMercator()) as Point

            // Convert the projected point to a geographic point
            val geoPoint =
                GeometryEngine.project(projectedPoint, SpatialReferences.getWgs84()) as Point

            // Get the latitude and longitude values of the geographic point
            val latitude = geoPoint.y
            val longitude = geoPoint.x

            // Print the latitude and longitude values
            println("latitude77=$latitude")
            println("longitude77=$longitude")

//            val uri = String.format(Locale.ENGLISH, "geo:%f,%f", latitude, longitude)
            val uri = String.format(
                Locale.ENGLISH,
                "geo:%f,%f?q=%f,%f(Label)",
                latitude,
                longitude,
                latitude,
                longitude
            )
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            context.startActivity(intent)
            mCallOut.dismiss()
        }

        directions.setOnClickListener { view: View? ->
            closeCallOut()
            val projectedPoint =
                GeometryEngine.project(mapPoint, SpatialReferences.getWebMercator()) as Point

            // Convert the projected point to a geographic point
            val geoPoint =
                GeometryEngine.project(projectedPoint, SpatialReferences.getWgs84()) as Point

            // Get the latitude and longitude values of the geographic point
            val latitude = geoPoint.y
            val longitude = geoPoint.x

            // Print the latitude and longitude values
            println("latitude77=$latitude")
            println("longitude77=$longitude")

            val uri = "http://maps.google.com/maps?daddr=$latitude,$longitude"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
        }

        btnCancel.setOnClickListener {
            closeCallOut()
        }

        setCalloutDisplayLocation(mapPoint)
        val callOutStyle = Callout.Style(context)
        callOutStyle.borderColor = R.color.primaryColor
        callOutStyle.borderWidth = 2
        mCallOut.style = callOutStyle
        mCallOut.location = mapPoint //new Point(x,y,mMapView.getSpatialReference()));
        mCallOut.content = dialogView
        mCallOut.show()
    }

    private fun updateMergeParcelDisplay() {

    }

    private fun closeCallOut() {
        if (::mCallOut.isInitialized && mCallOut.isShowing) {
            mCallOut.dismiss()
        }
    }

    private fun reVisitValidation(location: Location) {

        viewLifecycleOwner.lifecycleScope.launch {
            val attr = graphicCentoid.attributes
            val centroid = attr["centroid"].toString()

            val item = database.notAtHomeSurveyFormDao()
                .getSurveyForm(centroid)

            val providedTime = item.gpsTimestamp

            val currentMobileTime = Utility.convertGpsTimeToString(location.time)

            Log.d("TAG", currentMobileTime)

            if (isCurrentTimeGreaterThanProvidedTime(currentMobileTime, providedTime)) {
                val restrictionMillis: Long = when (item.visitCount) {
                    0, 1 -> 2 * 60 * 60 * 1000L // 2 hours in milliseconds
                    else -> {
                        // Set threshold to start of next day for the second visit
                        val dateTimeFormatter =
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
                        val providedDateTime = LocalDateTime.parse(providedTime, dateTimeFormatter)
                        val nextVisitDateTime =
                            providedDateTime.toLocalDate().plusDays(1).atStartOfDay()
                        Duration.between(providedDateTime, nextVisitDateTime).toMillis() + 60 * 1000
                    }
                }

                val remainingTime =
                    calculateRemainingTime(currentMobileTime, providedTime, restrictionMillis)

                if (remainingTime <= 0) {

                    Intent(context, NotAtHomeActivity::class.java).apply {
                        val bundle = Bundle().apply {
                            putLong("parcelNo", tvParcelNo.text.toString().toLong())
                            putString("uniqueId", item.uniqueId)
                            putString("parcelOperation", item.parcelOperation)
                        }
                        putExtra("bundle_data", bundle)
                        startActivity(this)

                    }
                } else {
                    val remainingHours = remainingTime / (60 * 60 * 1000)
                    val remainingMinutes = (remainingTime % (60 * 60 * 1000)) / (60 * 1000)

                    var message =
                        "Please go for this Survey after:\n$remainingHours hours and $remainingMinutes minutes"

                    if (remainingHours.toInt() == 0) {
                        message = "Please go for this Survey after:\n$remainingMinutes minutes"
                    }

                    val builder = AlertDialog.Builder(requireActivity())
                        .setTitle("Info!")
                        .setCancelable(false)
                        .setMessage(message)
                        .setPositiveButton("Ok", null)
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
            } else {
                val builder = AlertDialog.Builder(requireActivity())
                    .setTitle("Alert!")
                    .setCancelable(false)
                    .setMessage("Please try again after few minutes.")
                    .setPositiveButton("Ok", null)
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
        }
    }

    private fun isCurrentTimeGreaterThanProvidedTime(
        currentTime: String,
        providedTime: String
    ): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        try {
            val currentDateTime = sdf.parse(currentTime)
            val providedDateTime = sdf.parse(providedTime)
            return currentDateTime != null && providedDateTime != null && currentDateTime.after(
                providedDateTime
            )
        } catch (e: ParseException) {
            e.printStackTrace()
            return false
        }
    }

    private fun calculateRemainingTime(
        currentTime: String,
        providedTime: String, millisToRestrict: Long,
    ): Long {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

        val providedDateTime = LocalDateTime.parse(providedTime, dateTimeFormatter)
        val providedTimestamp =
            providedDateTime.atZone(ZoneId.of("Asia/Karachi")).toInstant().toEpochMilli()
        val restrictionEndTime = providedTimestamp + millisToRestrict

        val currentDateTime = LocalDateTime.parse(currentTime, dateTimeFormatter)
        val currentTimestamp =
            currentDateTime.atZone(ZoneId.of("Asia/Karachi")).toInstant().toEpochMilli()

        return restrictionEndTime - currentTimestamp
    }

    private fun setCalloutDisplayLocation(mapPoint: Point) {
        val envelope: Envelope = binding.parcelMapview.visibleArea.extent
        val centerPoint = envelope.center
        val x = mapPoint.x
        val factor = (envelope.yMax - centerPoint.y) / 2
        val y = mapPoint.y + factor
        val lastZoomPoint = Point(x, y, binding.parcelMapview.spatialReference)
        binding.parcelMapview.setViewpointCenterAsync(lastZoomPoint)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.parcelMapview?.dispose()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                handleLocationResult(locationResult)
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Intent(context, MenuActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                    requireActivity().finish()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun getCurrentLocation() {
        try {
            ls = object : LocationListener {
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                }

                override fun onLocationChanged(location: Location) {

                    viewModel.currentLocation = Utility.convertGpsTimeToString(location.time)

                    if (Build.VERSION.SDK_INT < 31) {
                        if (!location.isFromMockProvider) {
                            Utility.dismissProgressAlertDialog()
                            ls?.let { lm?.removeUpdates(it) }
                            reVisitValidation(location)
                        } else {
                            Utility.dismissProgressAlertDialog()
                            ls?.let { lm?.removeUpdates(it) }
                            Utility.exitApplication(
                                "Warning!",
                                "Please disable mock/fake location. The application will exit now.",
                                requireActivity()
                            )
                        }
                    } else {
                        if (!location.isMock) {
                            Utility.dismissProgressAlertDialog()
                            ls?.let { lm?.removeUpdates(it) }
                            reVisitValidation(location)
                        } else {
                            Utility.dismissProgressAlertDialog()
                            ls?.let { lm?.removeUpdates(it) }
                            Utility.exitApplication(
                                "Warning!",
                                "Please disable mock/fake location. The application will exit now.",
                                requireActivity()
                            )
                        }
                    }
                }
            }
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            ls?.let {
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0f, it)
//                lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 1f, it)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Current Location Exception :${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

}
