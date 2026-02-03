package pk.gop.pulse.katchiAbadi.ui.fragments.form
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.Polyline
import com.esri.arcgisruntime.geometry.PolylineBuilder
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.ui.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.ui.activities.NotAtHomeActivity
import pk.gop.pulse.katchiAbadi.ui.activities.SurveyActivity
import pk.gop.pulse.katchiAbadi.ui.activities.TaskAssignActivity
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.CustomTileLayer
import pk.gop.pulse.katchiAbadi.common.DownloadType
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcel
import pk.gop.pulse.katchiAbadi.common.SubParcel
import pk.gop.pulse.katchiAbadi.common.TileManager
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.response.SubParcelStatus
import pk.gop.pulse.katchiAbadi.data.repository.NewSurveyRepositoryImpl
import pk.gop.pulse.katchiAbadi.databinding.FragmentMapBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.IntentUtil
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import pk.gop.pulse.katchiAbadi.ui.activities.DroneSprayActivity
import pk.gop.pulse.katchiAbadi.ui.activities.InputItemsActivity
import pk.gop.pulse.katchiAbadi.ui.activities.SolarPanelActivity
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
@AndroidEntryPoint
class FragmentMap : Fragment() {
    @Inject
    lateinit var newSurveyRepository: NewSurveyRepositoryImpl
    private lateinit var refreshReceiver: BroadcastReceiver
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
    private var currentLocationGraphic: Graphic? = null

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

    // Merge mode properties
    private var isMergeMode = false
    private val selectedMergeParcels = LinkedHashMap<String, String>()
    private val defaultParcelSymbol = SimpleFillSymbol(
        SimpleFillSymbol.Style.SOLID,
        Color.argb(50, 255, 255, 255),
        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 1f)
    )

    private val greenLine = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 3f)

    // Task Assign Mode properties
    private var isTaskAssignMode = false
    private val selectedTaskParcels = LinkedHashMap<Long, ParcelInfo>()

    // Helper data class for task parcels
    data class ParcelInfo(
        val parcelId: Long,
        val parcelNo: String,
        val subParcelNo: String,
        val area: String,
        val khewatInfo: String,
        val unitId: Long,
        val groupId: Long
    )

    private fun stopLoadingParcels() {
        job?.cancel()
        job = null
    }

    // Parcel splitting properties
    private var isSplitMode = false
    private var splitLine: Graphic? = null
    private val splitPoints = mutableListOf<Point>()
    private lateinit var splitOverlay: GraphicsOverlay
    private var splitTargetGraphic: Graphic? = null

    private val selectedParcelGraphics = mutableListOf<Graphic>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root

    }




    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the action bar
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    handleLocationUpdate(location)
                }
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
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)


//        binding.ivReset.visibility = View.GONE


//        // Back button click listener
//        binding.ivBack.setOnClickListener {
//            val intent = Intent(requireContext(), MenuActivity::class.java)
//            startActivity(intent)
//            requireActivity().finish()
//        }

        // Set header text
        setHeaderText()
        context = requireContext()
        lm = context.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager

        // Merge mode buttons
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
            dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE

            Toast.makeText(
                requireContext(),
                "Merged parcels: ${selectedMergeParcels.values.joinToString(", ")}",
                Toast.LENGTH_SHORT
            ).show()

            binding.parcelMapview.setOnTouchListener(
                DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
            )
        }

        binding.btnCancelMerge.setOnClickListener {
            isMergeMode = false
            selectedMergeParcels.clear()
            restoreOriginalGraphics()

            binding.mergeControlBar.visibility = View.GONE
            dialogView.findViewById<View?>(R.id.card_root)?.visibility = View.VISIBLE
            viewModel.parcelOperationValue = ""
            tvMergeParcel.text = ""
            tvMergeParcelHi.text = ""

            binding.parcelMapview.setOnTouchListener(
                DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview)
            )
        }

        // Task Assign Mode buttons
        binding.btnDoneTaskAssign.setOnClickListener {
            if (selectedTaskParcels.isEmpty()) {
                Toast.makeText(requireContext(), "No parcels selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Pass selected parcels to TaskAssignActivity
            val intent = Intent(requireContext(), TaskAssignActivity::class.java).apply {
                putExtra("isMultipleParcel", true)
                putExtra("parcelCount", selectedTaskParcels.size)

                val parcelIds = selectedTaskParcels.values.map { it.parcelId }.toLongArray()
                val parcelNos = selectedTaskParcels.values.map { it.parcelNo }.toTypedArray()
                val subParcelNos = selectedTaskParcels.values.map { it.subParcelNo }.toTypedArray()
                val areas = selectedTaskParcels.values.map { it.area }.toTypedArray()
                val khewatInfos = selectedTaskParcels.values.map { it.khewatInfo }.toTypedArray()
                val unitIds = selectedTaskParcels.values.map { it.unitId }.toLongArray()
                val groupIds = selectedTaskParcels.values.map { it.groupId }.toLongArray()

                putExtra("parcelIds", parcelIds)
                putExtra("parcelNos", parcelNos)
                putExtra("subParcelNos", subParcelNos)
                putExtra("areas", areas)
                putExtra("khewatInfos", khewatInfos)
                putExtra("unitIds", unitIds)
                putExtra("groupIds", groupIds)
            }

            startActivity(intent)
            exitTaskAssignMode()
        }

        binding.btnCancelTaskAssign.setOnClickListener {
            exitTaskAssignMode()
        }

        // Add split mode setup
        setupSplitOverlay()
        setupSplitModeListeners()
    }



    private fun exitTaskAssignMode() {
        isTaskAssignMode = false
        selectedTaskParcels.clear()

        // Restore original graphics
        restoreOriginalGraphics()

        // Hide control bar
        binding.taskAssignControlBar.visibility = View.GONE

        // Restore default touch listener
        try {
            IdentifyFeatureLayerTouchListener(
                context,
                binding.parcelMapview,
                this@FragmentMap.surveyParcelsGraphics
            ).also { binding.parcelMapview.onTouchListener = it }
        } catch (e: Exception) {
            Log.e("TaskAssign", "Error restoring touch listener: ${e.message}")
        }

        Toast.makeText(requireContext(), "Task assign mode cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun updateTaskAssignSelectionCount() {
        binding.tvTaskAssignCount.text = "${selectedTaskParcels.size} parcel(s) selected"
    }

    private fun setupSplitOverlay() {
        splitOverlay = GraphicsOverlay()
        binding.parcelMapview.graphicsOverlays.add(splitOverlay)
    }

    private fun setupSplitModeListeners() {
//        binding.fabSplitMode.setOnClickListener {
//            toggleSplitMode()
//        }

        binding.btnApplySplit.setOnClickListener {
            applySplit()
        }

        binding.btnCancelSplit.setOnClickListener {
            exitSplitMode()
        }
    }

//    private fun toggleSplitMode() {
//        if (isSplitMode) {
//            exitSplitMode()
//        } else {
//            if (selectedParcelGraphics.isNotEmpty()) {
//                enterSplitMode(selectedParcelGraphics.first())
//            } else {
//                Toast.makeText(context, "Please select a parcel to split", Toast.LENGTH_SHORT)
//                    .show()
//            }
//        }
//    }

    private fun enterSplitMode(graphic: Graphic) {
        if (isSplitMode) {
            exitSplitMode()
        }

        isSplitMode = true
        splitTargetGraphic = graphic
        splitPoints.clear()

        if (!selectedParcelGraphics.contains(graphic)) {
            selectedParcelGraphics.add(graphic)
        }

        val splitHighlightSymbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID,
            Color.argb(150, 0, 255, 0),
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 4f)
        )
        graphic.symbol = splitHighlightSymbol

        showSplitModeUI()

        Toast.makeText(
            context,
            "Split Mode: Tap points to draw a line across the parcel",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun exitSplitMode() {
        if (!isSplitMode) return

        isSplitMode = false
        splitPoints.clear()
        splitLine = null

        splitTargetGraphic?.let { graphic ->
            val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
            graphic.symbol = getSymbolForSurveyStatus(surveyStatus)
        }

        splitOverlay.graphics.clear()
        hideSplitModeUI()
        splitTargetGraphic = null
    }

    private fun showSplitModeUI() {
        binding.layoutSplitControls.visibility = View.VISIBLE
        binding.btnApplySplit.visibility = View.VISIBLE
        binding.btnCancelSplit.visibility = View.VISIBLE
        binding.mergeControlBar.visibility = View.GONE
    }

    private fun hideSplitModeUI() {
        binding.layoutSplitControls.visibility = View.GONE
        binding.btnApplySplit.visibility = View.GONE
        binding.btnCancelSplit.visibility = View.GONE
    }

    private fun applySplit() {
        if (splitPoints.size < 2) {
            Toast.makeText(
                context,
                "Please draw a complete line with at least 2 points",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val targetGraphic = splitTargetGraphic
        if (targetGraphic == null) {
            Toast.makeText(context, "No target parcel selected", Toast.LENGTH_SHORT).show()
            return
        }

        val originalPolygon = targetGraphic.geometry as? Polygon
        if (originalPolygon == null) {
            Toast.makeText(context, "Invalid parcel geometry", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(context, "Processing split...", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    val splitLineGeometry = createSplitLineGeometry()
                    if (splitLineGeometry == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Failed to create split line geometry",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@withContext
                    }

                    val splitPolygons = performPolygonSplit(originalPolygon, splitLineGeometry)

                    withContext(Dispatchers.Main) {
                        when {
                            splitPolygons.isEmpty() -> {
                                Toast.makeText(
                                    context,
                                    "Split operation failed. Please ensure the line completely crosses the parcel.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            splitPolygons.size == 1 -> {
                                Toast.makeText(
                                    context,
                                    "Split line does not completely divide the parcel. Try drawing a line that fully crosses it.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            splitPolygons.size == 2 -> {
                                createSplitParcels(targetGraphic, splitPolygons)
                                Toast.makeText(
                                    context,
                                    "Parcel split successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                exitSplitMode()
                                refreshMapDisplay()
                            }

                            else -> {
                                Toast.makeText(
                                    context,
                                    "Split produced ${splitPolygons.size} parts. Only 2-way splits are currently supported.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error splitting parcel: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("ParcelSplit", "Error during split operation", e)
                }
            }
        }
    }

    private fun createSplitLineGeometry(): Polyline? {
        try {
            if (splitPoints.size < 2) {
                Log.e("ParcelSplit", "Insufficient points for line creation: ${splitPoints.size}")
                return null
            }

            val selectedGraphic = selectedParcelGraphics.firstOrNull()
            if (selectedGraphic == null) {
                Log.e("ParcelSplit", "No selected graphic found")
                return null
            }

            val originalPolygon = selectedGraphic.geometry as? Polygon
            if (originalPolygon == null) {
                Log.e("ParcelSplit", "Selected graphic is not a polygon")
                return null
            }

            val spatialRef = originalPolygon.spatialReference
            val builder = PolylineBuilder(spatialRef)

            for (point in splitPoints) {
                val projectedPoint = if (point.spatialReference != spatialRef) {
                    GeometryEngine.project(point, spatialRef) as Point
                } else {
                    point
                }
                builder.addPoint(projectedPoint)
            }

            val line = builder.toGeometry()

            if (line.isEmpty || line.parts.isEmpty()) {
                Log.e("ParcelSplit", "Created line is empty or invalid")
                return null
            }

            Log.d("ParcelSplit", "Created split line with ${splitPoints.size} points")
            return line

        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error creating split line: ${e.message}")
            return null
        }
    }

    private fun performPolygonSplit(
        polygon: Polygon,
        splitLine: Polyline
    ): List<Polygon> {
        try {
            if (polygon.isEmpty || splitLine.isEmpty) {
                Log.e("ParcelSplit", "Empty geometry provided")
                return emptyList()
            }

            val targetSR = polygon.spatialReference
            val projectedLine = if (splitLine.spatialReference != targetSR) {
                GeometryEngine.project(splitLine, targetSR) as? Polyline
            } else {
                splitLine
            }

            if (projectedLine == null) {
                Log.e("ParcelSplit", "Failed to project split line")
                return emptyList()
            }

            if (!GeometryEngine.intersects(polygon, projectedLine)) {
                Log.e("ParcelSplit", "Split line does not intersect the polygon")
                return emptyList()
            }

            val extendedLine = extendLineToPolygonBounds(projectedLine, polygon)

            if (extendedLine.isEmpty) {
                Log.e("ParcelSplit", "Failed to create extended line")
                return emptyList()
            }

            val cutResult = try {
                GeometryEngine.cut(polygon, extendedLine)
            } catch (e: Exception) {
                Log.e("ParcelSplit", "GeometryEngine.cut failed: ${e.message}")
                return performPolygonSplitWithBuffer(polygon, extendedLine)
            }

            val polygonResults = cutResult.mapNotNull { geometry ->
                when {
                    geometry is Polygon && !geometry.isEmpty -> geometry
                    else -> {
                        Log.w(
                            "ParcelSplit",
                            "Invalid result geometry: ${geometry?.javaClass?.simpleName}"
                        )
                        null
                    }
                }
            }

            Log.d("ParcelSplit", "Split operation produced ${polygonResults.size} polygons")
            return polygonResults

        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error in performPolygonSplit: ${e.message}", e)
            return emptyList()
        }
    }

    private fun performPolygonSplitWithBuffer(
        polygon: Polygon,
        splitLine: Polyline
    ): List<Polygon> {
        try {
            val bufferDistance = 0.00001
            val bufferPolygon = GeometryEngine.buffer(splitLine, bufferDistance) as? Polygon
                ?: return emptyList()

            val difference = GeometryEngine.difference(polygon, bufferPolygon)

            return when (difference) {
                is Polygon -> listOf(difference)
                else -> {
                    Log.w("ParcelSplit", "Buffer method produced non-polygon result")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("ParcelSplit", "Buffer split method failed: ${e.message}")
            return emptyList()
        }
    }

    private fun extendLineToPolygonBounds(
        line: Polyline,
        polygon: Polygon
    ): Polyline {
        try {
            if (line.parts.isEmpty() || line.parts[0].pointCount < 2) {
                Log.e("ParcelSplit", "Invalid line geometry for extension")
                return line
            }

            val part = line.parts[0]
            val startPoint = part.getPoint(0)
            val endPoint = part.getPoint(part.pointCount - 1)

            val dx = endPoint.x - startPoint.x
            val dy = endPoint.y - startPoint.y
            val length = kotlin.math.sqrt(dx * dx + dy * dy)

            if (length == 0.0) {
                Log.e("ParcelSplit", "Zero-length line cannot be extended")
                return line
            }

            val normalizedDx = dx / length
            val normalizedDy = dy / length

            val envelope = polygon.extent
            val maxDimension = maxOf(envelope.width, envelope.height)
            val extensionDistance = maxDimension * 2.0

            val extendedStart = Point(
                startPoint.x - normalizedDx * extensionDistance,
                startPoint.y - normalizedDy * extensionDistance,
                line.spatialReference
            )

            val extendedEnd = Point(
                endPoint.x + normalizedDx * extensionDistance,
                endPoint.y + normalizedDy * extensionDistance,
                line.spatialReference
            )

            val builder = PolylineBuilder(line.spatialReference)
            builder.addPoint(extendedStart)

            for (i in 0 until part.pointCount) {
                builder.addPoint(part.getPoint(i))
            }

            builder.addPoint(extendedEnd)

            val extendedLine = builder.toGeometry()

            if (extendedLine.isEmpty) {
                Log.e("ParcelSplit", "Failed to create valid extended line")
                return line
            }

            Log.d(
                "ParcelSplit",
                "Successfully extended line from ${line.parts[0].pointCount} to ${extendedLine.parts[0].pointCount} points"
            )
            return extendedLine

        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error extending line: ${e.message}")
            return line
        }
    }

    private fun createSplitParcels(originalGraphic: Graphic, splitPolygons: List<Polygon>) {
        val originalAttributes = originalGraphic.attributes
        val originalParcelNo = originalAttributes["parcel_no"]?.toString()?.toLongOrNull() ?: return
        val originalSubParcelNo = originalAttributes["sub_parcel_no"]?.toString() ?: ""

        val originalUnitId = originalAttributes["unit_id"]?.toString()?.toLongOrNull() ?: 0L
        val originalGroupId = originalAttributes["group_id"]?.toString()?.toLongOrNull() ?: 0L

        try {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val originalParcelId =
                            originalAttributes["parcel_id"] as? Long ?: return@withContext

                        Log.d("SPLIT_DEBUG", "=== SPLIT OPERATION STARTED ===")
                        Log.d("SPLIT_DEBUG", "Original parcel ID from graphic: $originalParcelId")

                        val originalParcel =
                            database.activeParcelDao().getParcelById(originalParcelId)

                        if (originalParcel == null) {
                            Log.e(
                                "SPLIT_DEBUG",
                                "❌ Original parcel not found in database with ID: $originalParcelId"
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Original parcel not found",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@withContext
                        }

                        Log.d("SPLIT_DEBUG", "✅ Found original parcel:")
                        Log.d("SPLIT_DEBUG", "   ID: ${originalParcel.id}")
                        Log.d("SPLIT_DEBUG", "   PKID: ${originalParcel.pkid}")
                        Log.d("SPLIT_DEBUG", "   ParcelNo: ${originalParcel.parcelNo}")
                        Log.d("SPLIT_DEBUG", "   SubParcelNo: ${originalParcel.subParcelNo}")
                        Log.d("SPLIT_DEBUG", "   isActivate (before): ${originalParcel.isActivate}")

                        // ✅ STEP 1: Deactivate the original parcel BEFORE creating new ones
                        database.activeParcelDao()
                            .updateParcelActivationStatus(originalParcel.id, false)

                        // Verify deactivation
                        val verifyDeactivated = database.activeParcelDao().getParcelById(originalParcel.id)
                        Log.d("SPLIT_DEBUG", "✅ Deactivated original parcel ID: ${originalParcel.id}")
                        Log.d("SPLIT_DEBUG", "   isActivate (after): ${verifyDeactivated?.isActivate}")

                        // Clean up graphics references
                        cleanupOriginalGraphicsAfterSplit(originalParcelId)

                        // ✅ STEP 2: Get max ID for new parcels
                        val maxId = database.activeParcelDao().getMaxParcelId() ?: 0L
                        Log.d("SPLIT_DEBUG", "Current max parcel ID in database: $maxId")

                        val newParcels = mutableListOf<ActiveParcelEntity>()

                        // ✅ STEP 3: Create split parcels
                        for (i in splitPolygons.indices) {
                            val newSubParcelNo =
                                if (originalSubParcelNo.isBlank() || originalSubParcelNo == "0") {
                                    (i + 1).toString()
                                } else {
                                    "${originalSubParcelNo}_${i + 1}"
                                }

                            val newGeomWKT = convertPolygonToWkt(splitPolygons[i])
                            if (!validateWkt(newGeomWKT)) {
                                Log.e("SPLIT_DEBUG", "❌ Invalid geometry for split parcel ${i + 1}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Invalid geometry for split parcel ${i + 1}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@withContext
                            }

                            val newCentroid = splitPolygons[i].extent.center
                            val centroidWKT =
                                String.format("POINT(%.8f %.8f)", newCentroid.x, newCentroid.y)

                            val newUniqueId = maxId + i + 1

                            val newParcel = originalParcel.copy(
                                pkid = 0,
                                id = newUniqueId,
                                parcelNo = originalParcelNo,
                                subParcelNo = newSubParcelNo,
                                geomWKT = newGeomWKT,
                                centroid = centroidWKT,
                                surveyStatusCode = 1,
                                surveyId = null,
                                isActivate = true, // ✅ Split parcels are active
                                unitId = originalUnitId,
                                groupId = originalGroupId
                            )

                            newParcels.add(newParcel)

                            Log.d("SPLIT_DEBUG", "✅ Created split parcel ${i + 1}:")
                            Log.d("SPLIT_DEBUG", "   New ID: $newUniqueId")
                            Log.d("SPLIT_DEBUG", "   ParcelNo: $originalParcelNo")
                            Log.d("SPLIT_DEBUG", "   SubParcelNo: $newSubParcelNo")
                            Log.d("SPLIT_DEBUG", "   isActivate: ${newParcel.isActivate}")
                        }

                        // ✅ STEP 4: Insert new split parcels
                        database.activeParcelDao().insertActiveParcels(newParcels)
                        Log.d("SPLIT_DEBUG", "✅ Inserted ${newParcels.size} new split parcels into database")

                        // ✅ STEP 5: Verification
                        val activeCount = database.activeParcelDao().countActiveParcelsByNumber(
                            originalParcelNo,
                            originalParcel.mauzaId,
                            originalParcel.areaAssigned
                        )

                        Log.d("SPLIT_DEBUG", "=== VERIFICATION ===")
                        Log.d("SPLIT_DEBUG", "Active parcels with number $originalParcelNo: $activeCount")
                        Log.d("SPLIT_DEBUG", "Expected active count: ${splitPolygons.size}")

                        // Verify each new parcel
                        newParcels.forEach { newParcel ->
                            val verifyParcel = database.activeParcelDao().getParcelById(newParcel.id)
                            Log.d("SPLIT_DEBUG", "Verify parcel ID ${newParcel.id}:")
                            Log.d("SPLIT_DEBUG", "   Exists: ${verifyParcel != null}")
                            Log.d("SPLIT_DEBUG", "   isActivate: ${verifyParcel?.isActivate}")
                        }

                        Log.d("SPLIT_DEBUG", "=== SPLIT OPERATION COMPLETED SUCCESSFULLY ===")

                    } catch (e: Exception) {
                        Log.e("SPLIT_DEBUG", "❌ Database error: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Database error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@withContext
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        // Remove original graphic from map
                        surveyParcelsGraphics.graphics.remove(originalGraphic)
                        selectedParcelGraphics.remove(originalGraphic)

                        Toast.makeText(
                            context,
                            "Parcel $originalParcelNo split into ${splitPolygons.size} parts",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Refresh map to show new split parcels
                        refreshMapDisplay()

                    } catch (e: Exception) {
                        Log.e("SPLIT_DEBUG", "❌ UI update error: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SPLIT_DEBUG", "❌ Error in createSplitParcels: ${e.message}", e)
        }
    }

    private fun validateWkt(wkt: String?): Boolean {
        if (wkt.isNullOrBlank()) return false

        return when {
            wkt.startsWith("POLYGON", ignoreCase = true) && wkt.contains("((") -> true
            wkt.startsWith("POINT", ignoreCase = true) && wkt.contains("(") -> true
            wkt.startsWith("MULTIPOLYGON", ignoreCase = true) && wkt.contains("(((") -> true
            else -> false
        }
    }

    private fun cleanupOriginalGraphicsAfterSplit(originalParcelId: Long) {
        originalGraphicSymbols.remove(originalParcelId)
        originalLabelGraphics.remove(originalParcelId)

        Log.d("CLEANUP_DEBUG", "Removed original symbols for parcel ID: $originalParcelId")
    }

    private fun convertPolygonToWkt(polygon: Polygon): String {
        val stringBuilder = StringBuilder("POLYGON(")

        val exteriorRing = polygon.parts[0]
        stringBuilder.append("(")

        for (i in 0 until exteriorRing.pointCount) {
            val point = exteriorRing.getPoint(i)
            if (i > 0) stringBuilder.append(",")
            stringBuilder.append("${point.x} ${point.y}")
        }

        val firstPoint = exteriorRing.getPoint(0)
        val lastPoint = exteriorRing.getPoint(exteriorRing.pointCount - 1)
        if (firstPoint.x != lastPoint.x || firstPoint.y != lastPoint.y) {
            stringBuilder.append(",${firstPoint.x} ${firstPoint.y}")
        }

        stringBuilder.append(")")

        for (i in 1 until polygon.parts.size) {
            stringBuilder.append(",(")
            val interiorRing = polygon.parts[i]

            for (j in 0 until interiorRing.pointCount) {
                val point = interiorRing.getPoint(j)
                if (j > 0) stringBuilder.append(",")
                stringBuilder.append("${point.x} ${point.y}")
            }

            val firstInteriorPoint = interiorRing.getPoint(0)
            val lastInteriorPoint = interiorRing.getPoint(interiorRing.pointCount - 1)
            if (firstInteriorPoint.x != lastInteriorPoint.x || firstInteriorPoint.y != lastInteriorPoint.y) {
                stringBuilder.append(",${firstInteriorPoint.x} ${firstInteriorPoint.y}")
            }

            stringBuilder.append(")")
        }

        stringBuilder.append(")")
        return stringBuilder.toString()
    }

    private fun updateSplitLineVisual() {
        if (!::splitOverlay.isInitialized) return

        splitOverlay.graphics.clear()

        if (splitPoints.size >= 2) {
            val builder = PolylineBuilder(binding.parcelMapview.spatialReference)
            splitPoints.forEach { builder.addPoint(it) }
            val lineGeometry = builder.toGeometry()

            val lineSymbol = SimpleLineSymbol(
                SimpleLineSymbol.Style.SOLID,
                Color.RED,
                3f
            )

            splitLine = Graphic(lineGeometry, lineSymbol)
            splitOverlay.graphics.add(splitLine!!)
        }

        val pointSymbol = SimpleMarkerSymbol(
            SimpleMarkerSymbol.Style.CIRCLE,
            Color.RED,
            8f
        ).apply {
            outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
        }

        splitPoints.forEach { point ->
            splitOverlay.graphics.add(Graphic(point, pointSymbol))
        }
    }

    private fun refreshMapDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("MapRefresh", "Starting map refresh...")

                // Store grower codes before clearing
                val growerCodesMap = mutableMapOf<Long, String>()
                for (graphic in surveyLabelGraphics.graphics) {
                    val parcelId = graphic.attributes["parcel_id"] as? Long
                    val growerCodes = graphic.attributes["growerCodes"]?.toString()
                    if (parcelId != null && !growerCodes.isNullOrEmpty()) {
                        growerCodesMap[parcelId] = growerCodes
                    }
                }

                withContext(Dispatchers.Main) {
                    surveyParcelsGraphics.graphics.clear()
                    surveyLabelGraphics.graphics.clear()
                    selectedParcelGraphics.clear()
                    if (::splitOverlay.isInitialized) {
                        splitOverlay.graphics.clear()
                    }
                }

                delay(200)
                stopLoadingParcels()

                withContext(Dispatchers.Main) {
                    val currentShowLabels = binding.parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)
                    Log.d("MapRefresh", "Reloading map with current filter")

                    loadMap(ids, currentShowLabels)

                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(1000)

                        // Restore grower codes after map loads
                        for (graphic in surveyLabelGraphics.graphics) {
                            val parcelId = graphic.attributes["parcel_id"] as? Long
                            if (parcelId != null && growerCodesMap.containsKey(parcelId)) {
                                graphic.attributes["growerCodes"] = growerCodesMap[parcelId]
                            }
                        }
                    }
                }

                Log.d("MapRefresh", "Map refresh completed")
            } catch (e: Exception) {
                Log.e("MapRefresh", "Error refreshing map display: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error refreshing map: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        closeCallOut()

        refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "REFRESH_MAP") {
                    Log.d("FragmentMap", "Received map refresh broadcast")
                    refreshMapData()
                }
            }
        }
        val filter = IntentFilter("REFRESH_MAP")
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshReceiver, filter)

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

            // Start continuous location updates
            startContinuousLocationUpdates()

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Restart the map screen.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private fun setupMap() {
        ToastUtil.showLong(context,"Please wait while parcels are being loaded...")
        with(binding) {

            setupSplitOverlay()
            setupSplitModeListeners()

            try {
                IdentifyFeatureLayerTouchListener(
                    context,
                    parcelMapview,
                    this@FragmentMap.surveyParcelsGraphics
                ).also { parcelMapview.onTouchListener = it }
            } catch (e: Exception) {
                ToastUtil.showShort(context,"Restart the map screen.")
            }

            fab.setOnClickListener {
                handleFabClick()
            }

//            ivSearch.setOnClickListener {
//                closeCallOut()
//                val items: MutableList<SearchableItem> = ArrayList()
//
//                viewLifecycleOwner.lifecycleScope.launch {
//                    ids.clear()
//                    val areaId = sharedPreferences.getLong(
//                        Constants.SHARED_PREF_USER_SELECTED_AREA_ID,
//                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
//                    )
//
//                    val mauzaId = sharedPreferences.getLong(
//                        Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
//                        Constants.SHARED_PREF_DEFAULT_INT.toLong()
//                    )
//
//                    val areaName = sharedPreferences.getString(
//                        Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
//                        Constants.SHARED_PREF_DEFAULT_STRING
//                    ).orEmpty()
//
//                    val parcels =
//                        database.activeParcelDao().getActiveParcelsByMauzaAndArea(mauzaId, areaName)
//
//                    for (parcel in parcels) {
//                        if (viewModel.parcelNo != parcel.parcelNo)
//                            items.add(SearchableItem("${parcel.parcelNo}", "${parcel.id}"))
//                    }
//
//                    SearchableMultiSelectSpinner.show(
//                        requireContext(),
//                        "Select Parcels",
//                        "Done",
//                        items,
//                        object :
//                            SelectionCompleteListener {
//                            override fun onCompleteSelection(selectedItems: ArrayList<SearchableItem>) {
//                                if (selectedItems.size > 0) {
//                                    enableNewPoint = false
//
//                                    viewLifecycleOwner.lifecycleScope.launch {
//                                        ids.clear()
//                                        for (item in selectedItems) {
//                                            ids.add(item.text.toLong())
//                                        }
//
//                                        loadMap(ids, true)
//                                    }
//                                } else {
//                                    Toast.makeText(
//                                        context,
//                                        "Parcel not selected",
//                                        Toast.LENGTH_SHORT
//                                    ).show()
//                                }
//                            }
//                        })
//                }
//            }

//            ivReset.setOnClickListener {
//                closeCallOut()
//                viewLifecycleOwner.lifecycleScope.launch {
//                    ids.clear()
//                    enableNewPoint = true
//                    loadMap(ids, false)
//                }
//            }
        }
    }

    private fun loadMap(ids: ArrayList<Long>, showLabels: Boolean) {
        when (Constants.MAP_DOWNLOAD_TYPE) {
            DownloadType.TPK -> {
                // loadMapTPK(ids, showLabels)
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
                    ),
                    SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.MAGENTA, 1.5f)
                )

                lockedBlocks = SimpleFillSymbol(
                    SimpleFillSymbol.Style.SOLID, Color.argb(
                        80, Color.red(Color.BLUE), Color.green(Color.BLUE), Color.blue(Color.BLUE)
                    ),
                    SimpleLineSymbol(
                        SimpleLineSymbol.Style.SOLID,
                        ContextCompat.getColor(context, R.color.parcel_blue),
                        1.5f
                    )
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

                val parcels = if (ids.isNotEmpty()) {
                    val searchResults = database.activeParcelDao().searchParcels(ids)
                    searchResults.filter { it.isActivate }
                } else {
                    database.activeParcelDao().getActiveParcelsByMauzaAndArea(mauzaId, areaName)
                }

                Log.d("LoadMap", "=== PARCEL LOADING DEBUG ===")
                Log.d("LoadMap", "Total parcels from DB: ${parcels.size}")

                val allParcels = database.activeParcelDao().getParcelsByMauzaAndArea(mauzaId, areaName)
                Log.d("LoadMap", "Total ALL parcels (including inactive): ${allParcels.size}")

                val inactiveParcels = allParcels.filter { !it.isActivate }
                Log.d("LoadMap", "Inactive parcels: ${inactiveParcels.size}")

                inactiveParcels.forEach { parcel ->
                    Log.d("LoadMap", "Inactive: ID=${parcel.id}, ParcelNo=${parcel.parcelNo}, SubParcel=${parcel.subParcelNo}")
                }

                val polygonsList = mutableListOf<Polygon>()
                val gson = Gson()

                var processedCount = 0
                var skipCount = 0

                for (parcel in parcels) {
                    try {
                        val parcelGeom = parcel.geomWKT

                        if (parcelGeom.isNullOrBlank()) {
                            Log.w(
                                "LoadMap",
                                "Skipping parcel ${parcel.parcelNo}${parcel.subParcelNo} with empty geometry"
                            )
                            skipCount++
                            continue
                        }

                        val polygons = when {
                            parcelGeom.contains("MULTIPOLYGON") -> {
                                Log.d(
                                    "LoadMap",
                                    "Processing MULTIPOLYGON for parcel ${parcel.parcelNo}"
                                )
                                Utility.getMultiPolygonFromString(parcelGeom, wgs84)
                            }

                            parcelGeom.contains("POLYGON ((") -> {
                                Log.d("LoadMap", "Processing POLYGON for parcel ${parcel.parcelNo}")
                                val polygon = Utility.getPolygonFromString(parcelGeom, wgs84)
                                if (polygon != null) listOf(polygon) else emptyList()
                            }

                            parcelGeom.contains("POLYGON") -> {
                                Log.d(
                                    "LoadMap",
                                    "Processing malformed POLYGON for parcel ${parcel.parcelNo}"
                                )
                                val polygon = Utility.getPolyFromString(parcelGeom, wgs84)
                                if (polygon != null) listOf(polygon) else emptyList()
                            }

                            else -> {
                                Log.e(
                                    "LoadMap",
                                    "Unknown geometry format for parcel ${parcel.parcelNo}: $parcelGeom"
                                )
                                emptyList()
                            }
                        }

                        if (polygons.isNotEmpty()) {
                            for (polygon in polygons) {
                                val simplifiedPolygon = Utility.simplifyPolygon(polygon)
                                if (!simplifiedPolygon.isEmpty) {
                                    polygonsList.add(simplifiedPolygon)
                                    addGraphics(
                                        parcel = parcel,
                                        polygon = simplifiedPolygon,
                                        gson = gson
                                    )
                                    processedCount++
                                }
                            }
                            Log.d(
                                "LoadMap",
                                "Successfully processed parcel ${parcel.parcelNo}${parcel.subParcelNo ?: ""} (ID: ${parcel.id})"
                            )
                        } else {
                            Log.e(
                                "LoadMap",
                                "Failed to parse geometry for parcel ${parcel.parcelNo}${parcel.subParcelNo}: $parcelGeom"
                            )
                            skipCount++
                        }

                    } catch (e: Exception) {
                        Log.e(
                            "LoadMap",
                            "Error processing parcel ${parcel.parcelNo}: ${e.message}",
                            e
                        )
                        skipCount++
                    }
                }

                Log.d(
                    "LoadMap",
                    "Processing complete - Processed: $processedCount, Skipped: $skipCount"
                )

                if (polygonsList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "No valid geometries found to display",
                            Toast.LENGTH_LONG
                        ).show()
                        progressBar.visibility = View.GONE
                        return@withContext
                    }
                    return@launch
                }

                val combinedGeometry = GeometryEngine.union(polygonsList)
                val combinedExtent = combinedGeometry.extent
                val bufferDistance = 0.0001
                val bufferedGeometry =
                    GeometryEngine.buffer(combinedExtent, bufferDistance) as Polygon
                val bufferedExtent = bufferedGeometry.extent
                val webMercatorEnvelope = GeometryEngine.project(
                    bufferedExtent,
                    SpatialReferences.getWebMercator()
                ) as Envelope

                var parcelsCount = parcels.size
                var surveyedCount = 0
                var unSurveyedCount = 0
                var lockedCount = 0
                var rejectedCount = 0

                for (parcel in parcels) {
                    when (parcel.surveyStatusCode) {
                        1 -> unSurveyedCount++
                        2 -> surveyedCount++
                        else -> unSurveyedCount++
                    }
                }

                val totalSurveyedCount = surveyedCount

                withContext(Dispatchers.Main) {
                    binding.tvParcelCount.text = "Parcel Count: $parcelsCount"
                    binding.tvSurveyedParcelCount.text = "($totalSurveyedCount)"
                    binding.tvUnsurveyedParcelCount.text = "($unSurveyedCount)"
//                    binding.tvLcckedParcelCount.text = "($lockedCount)"
//                    binding.tvRevisitParcelCount.text = "($rejectedCount)"

                    val tileManager = TileManager(requireContext())

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
                        Point(
                            -20037508.3427892,
                            20037508.3427892,
                            SpatialReferences.getWebMercator()
                        ),
                        SpatialReferences.getWebMercator(),
                        256,
                        256
                    )

                    val minZoomLevel = sharedPreferences.getInt(
                        Constants.SHARED_PREF_MAP_MIN_SCALE,
                        Constants.SHARED_PREF_DEFAULT_MIN_SCALE
                    )

                    val maxZoomLevel = 16

                    val customTileLayer = CustomTileLayer(
                        tileInfo,
                        webMercatorEnvelope,
                        tileManager,
                        folderKey,
                        minZoomLevel,
                        maxZoomLevel
                    )

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
                        Toast.makeText(context, "Restart the map screen.", Toast.LENGTH_LONG).show()
                    }

                    progressBar.visibility = View.GONE
                    layoutInfo.visibility = View.VISIBLE
                    layoutRejected.visibility = View.VISIBLE
                    fab.visibility = View.VISIBLE
                    parcelMapview.visibility = View.VISIBLE
//                    ivReset.visibility = View.VISIBLE
//                    ivSearch.visibility = View.GONE

                    Log.d("LoadMap", "Map setup completed successfully")
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
        val isHarvested = isParcelHarvested(parcel.id)

        when (parcel.surveyStatusCode) {
            1 -> {
                symbol = unSurveyedBlocks
                textColor = ContextCompat.getColor(context, R.color.parcel_red)
            }
            2 -> {
                if (isHarvested) {
                    symbol = SimpleFillSymbol(
                        SimpleFillSymbol.Style.SOLID,
                        Color.WHITE,
                        SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLACK, 2f)
                    )
                    highlightColor = Color.WHITE
                    textColor = Color.BLACK
                } else {
                    symbol = surveyedBlocks
                    highlightColor = Color.BLACK
                    textColor = ContextCompat.getColor(context, R.color.parcel_green)
                }
            }
            else -> {
                symbol = unSurveyedBlocks
                highlightColor = Color.YELLOW
                textColor = ContextCompat.getColor(context, R.color.parcel_red)
            }
        }

        val parcelGraphic = Graphic(polygon, symbol)
        val attr = parcelGraphic.attributes
        attr["parcel_id"] = parcel.id
        attr["pkid"] = parcel.pkid
        attr["parcel_no"] = parcel.parcelNo
        attr["sub_parcel_no"] = parcel.subParcelNo
        attr["surveyStatusCode"] = parcel.surveyStatusCode
        attr["area"] = area
        attr["geomWKT"] = parcel.geomWKT
        attr["centroid"] = parcel.centroid
        attr["isRejected"] = isRejected
        attr["unit_id"] = parcel.unitId ?: 0L
        attr["group_id"] = parcel.groupId ?: 0L

        val labelText = "${parcel.parcelNo}\n${parcel.khewatInfo ?: ""}"
        val polyLabelSymbol = TextSymbol().apply {
            text = labelText
            size = 16f
            color = textColor
            horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
            verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
            haloWidth = 1f
            fontWeight = TextSymbol.FontWeight.BOLD
        }

        val labelGraphic = Graphic(myPolygonCenterLatLon, polyLabelSymbol)
        val parcelId = parcel.pkid

        // Store initial symbol and label
        originalGraphicSymbols[parcelId] = symbol

        val attrLabel = labelGraphic.attributes
        attrLabel["parcel_id"] = parcel.id
        attrLabel["pkid"] = parcel.pkid
        attrLabel["parcel_no"] = parcel.parcelNo
        attrLabel["sub_parcel_no"] = parcel.subParcelNo
        attrLabel["khewatInfo"] = parcel.khewatInfo
        attrLabel["surveyStatusCode"] = parcel.surveyStatusCode
        attrLabel["area"] = area
        attrLabel["geomWKT"] = parcel.geomWKT
        attrLabel["centroid"] = parcel.centroid
        attrLabel["isRejected"] = isRejected
        attrLabel["unit_id"] = parcel.unitId ?: 0L
        attrLabel["group_id"] = parcel.groupId ?: 0L
        attrLabel["growerCodes"] = ""

        // Add graphics to overlays first
        surveyParcelsGraphics.graphics.add(parcelGraphic)
        surveyLabelGraphics.graphics.add(labelGraphic)

        // Load grower codes for surveyed parcels
        if (parcel.surveyStatusCode == 2) {
            CoroutineScope(Dispatchers.IO).launch {
                val surveyId = parcel.surveyId
                val codes = if (surveyId != null && surveyId > 0) {
                    try {
                        val persons = database.personDao().getPersonsBySurveyId(surveyId)
                        persons.mapNotNull { it.growerCode?.takeIf { code -> code.isNotBlank() } }
                    } catch (e: Exception) {
                        Log.e("AddGraphics", "Error loading grower codes: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val growerText = if (codes.isNotEmpty()) {
                    codes.joinToString(", ")
                } else {
                    ""
                }

                withContext(Dispatchers.Main) {
                    // ✅ SAFE: Use find instead of indexOfFirst to avoid index issues
                    val existingLabel = surveyLabelGraphics.graphics.firstOrNull {
                        it.attributes["parcel_id"] == parcel.id
                    }

                    if (existingLabel != null) {
                        try {
                            // ✅ Store grower codes in attributes
                            existingLabel.attributes["growerCodes"] = growerText

                            // Create updated label text
                            val updatedLabelText = if (growerText.isNotEmpty()) {
                                "${parcel.parcelNo}\n${parcel.khewatInfo ?: ""}\n$growerText"
                            } else {
                                "${parcel.parcelNo}\n${parcel.khewatInfo ?: ""}"
                            }

                            val updatedPolyLabelSymbol = TextSymbol().apply {
                                text = updatedLabelText
                                size = 16f
                                color = textColor
                                horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
                                verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
                                haloWidth = 1f
                                fontWeight = TextSymbol.FontWeight.BOLD
                            }

                            val myPolygonCenterLatLon = polygon.extent.center
                            val updatedLabelGraphic = Graphic(myPolygonCenterLatLon, updatedPolyLabelSymbol)

                            // Copy all attributes including grower codes
                            existingLabel.attributes.forEach { (key, value) ->
                                updatedLabelGraphic.attributes[key] = value
                            }

                            // ✅ SAFE: Remove and add only if label still exists in the list
                            val currentIndex = surveyLabelGraphics.graphics.indexOf(existingLabel)
                            if (currentIndex >= 0 && currentIndex < surveyLabelGraphics.graphics.size) {
                                surveyLabelGraphics.graphics.remove(existingLabel)
                                surveyLabelGraphics.graphics.add(currentIndex, updatedLabelGraphic)

                                // ✅ Update the stored original label
                                originalLabelGraphics[parcelId] = updatedLabelGraphic

                                Log.d("AddGraphics", "✅ Updated grower codes for parcel ${parcel.id}: $growerText")
                            } else {
                                Log.w("AddGraphics", "Label index out of bounds, skipping update for parcel ${parcel.id}")
                            }
                        } catch (e: Exception) {
                            Log.e("AddGraphics", "Error updating label for parcel ${parcel.id}: ${e.message}", e)
                        }
                    } else {
                        Log.w("AddGraphics", "Label not found for parcel ${parcel.id}")
                    }
                }
            }
        } else {
            // ✅ For unsurveyed parcels, store the label immediately
            originalLabelGraphics[parcelId] = labelGraphic
        }
    }
    private fun restoreOriginalGraphics() {
        Log.d("RESTORE_DEBUG", "Starting graphics restoration...")

        // Restore polygon symbols
        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue
            Log.d("RESTORE_DEBUG", "Processing graphic with parcel_id: $parcelId")

            val originalSymbol = originalGraphicSymbols[parcelId]
            if (originalSymbol != null) {
                graphic.symbol = originalSymbol
                Log.d("RESTORE_DEBUG", "Restored symbol for parcel_id: $parcelId")
            } else {
                val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
                val correctSymbol = getSymbolForSurveyStatus(surveyStatus)
                graphic.symbol = correctSymbol
                originalGraphicSymbols[parcelId] = correctSymbol
                Log.d("RESTORE_DEBUG", "Generated new symbol for parcel_id: $parcelId, status: $surveyStatus")
            }
        }

        // Clear and restore labels
        surveyLabelGraphics.graphics.clear()

        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue

            // ✅ Try to get the stored original label first
            val originalLabel = originalLabelGraphics[parcelId]

            if (originalLabel != null) {
                // ✅ Use the stored label which already has grower codes
                surveyLabelGraphics.graphics.add(originalLabel)
                Log.d("RESTORE_DEBUG", "Restored original label for parcel_id: $parcelId")
            } else {
                // Create new label if no original exists
                val parcelNo = graphic.attributes["parcel_no"]?.toString() ?: ""
                val subParcelNo = graphic.attributes["sub_parcel_no"]?.toString() ?: ""
                val khewatInfo = graphic.attributes["khewatInfo"]?.toString() ?: ""
                val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
                // ✅ Try to get stored grower codes from attributes
                val storedGrowerCodes = graphic.attributes["growerCodes"]?.toString() ?: ""

                val displayText = if (subParcelNo.isBlank() || subParcelNo == "0") {
                    parcelNo
                } else {
                    "$parcelNo-$subParcelNo"
                }

                val textColor = when (surveyStatus) {
                    2 -> ContextCompat.getColor(context, R.color.parcel_green)
                    else -> ContextCompat.getColor(context, R.color.parcel_red)
                }

                val highlightColor = when (surveyStatus) {
                    2 -> Color.BLACK
                    else -> Color.YELLOW
                }

                // ✅ Include stored grower codes if available
                val labelText = if (storedGrowerCodes.isNotEmpty()) {
                    "$displayText\n$khewatInfo\n$storedGrowerCodes"
                } else {
                    "$displayText\n$khewatInfo"
                }

                val polyLabelSymbol = TextSymbol().apply {
                    text = labelText
                    size = 10f
                    color = textColor
                    horizontalAlignment = TextSymbol.HorizontalAlignment.CENTER
                    verticalAlignment = TextSymbol.VerticalAlignment.MIDDLE
                    haloColor = highlightColor
                    haloWidth = 1f
                    fontWeight = TextSymbol.FontWeight.BOLD
                }

                val geometry = graphic.geometry
                val centerPoint = if (geometry is Polygon) {
                    geometry.extent.center
                } else {
                    geometry.extent.center
                }

                val newLabel = Graphic(centerPoint, polyLabelSymbol)

                // Copy all attributes including grower codes
                graphic.attributes.forEach { (key, value) ->
                    newLabel.attributes[key] = value
                }

                surveyLabelGraphics.graphics.add(newLabel)
                originalLabelGraphics[parcelId] = newLabel

                Log.d("RESTORE_DEBUG", "Created new label for parcel_id: $parcelId with grower codes: $storedGrowerCodes")
            }
        }

        Log.d("RESTORE_DEBUG", "Graphics restoration completed")
    }

    private fun handleFabClick() {
        if (checkPermission()) {
            getInitialLocation(true)
        } else {
            requestPermission()
        }
    }

    private fun getInitialLocation(enableZoom: Boolean = true) {
        if (!checkPermission()) {
            requestPermission()
            return
        }

        if (!Utility.checkGPS(requireActivity())) {
            Utility.buildAlertMessageNoGps(requireActivity())
            return
        }

        try {
            Utility.showProgressAlertDialog(requireContext(), "Getting location...")

            // Try to get last known location first
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermission()
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && isLocationValid(location)) {
                    Utility.dismissProgressAlertDialog()
                    handleInitialLocation(location, enableZoom)
                } else {
                    // Request fresh location
                    requestFreshLocation(enableZoom)
                }
            }.addOnFailureListener {
                requestFreshLocation(enableZoom)
            }

        } catch (e: SecurityException) {
            Utility.dismissProgressAlertDialog()
            requestPermission()
        }
    }
    private fun requestFreshLocation(enableZoom: Boolean) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).apply {
            setMinUpdateIntervalMillis(1000L)
            setMaxUpdateDelayMillis(3000L)
            setWaitForAccurateLocation(false)
            setMinUpdateDistanceMeters(5f)
        }.build()

        val freshLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val meterAccuracy = sharedPreferences.getInt(
                        Constants.SHARED_PREF_METER_ACCURACY,
                        Constants.SHARED_PREF_DEFAULT_ACCURACY
                    )

                    if (location.accuracy < meterAccuracy && isLocationValid(location) && !isMockLocation(location)) {
                        Utility.dismissProgressAlertDialog()
                        fusedLocationClient.removeLocationUpdates(this)
                        handleInitialLocation(location, enableZoom)
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            freshLocationCallback,
            Looper.getMainLooper()
        )

        // Timeout after 10 seconds
        viewLifecycleOwner.lifecycleScope.launch {
            delay(10000)
            fusedLocationClient.removeLocationUpdates(freshLocationCallback)
            Utility.dismissProgressAlertDialog()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Unable to get accurate location. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleInitialLocation(location: Location, enableZoom: Boolean) {
        updateLocationOnMap(location)

        if (enableZoom) {
            val point = Point(
                location.longitude,
                location.latitude,
                SpatialReferences.getWgs84()
            )
            binding.parcelMapview.setViewpointAsync(Viewpoint(point, 1000.0))
        }

        // Start continuous updates after getting initial location
        startContinuousLocationUpdates()
    }

    private fun startContinuousLocationUpdates() {
        if (!checkPermission()) {
            return
        }

        if (!Utility.checkGPS(requireActivity())) {
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L // Update every 2 seconds
            ).apply {
                setMinUpdateIntervalMillis(1000L) // At least 1 second between updates
                setMaxUpdateDelayMillis(3000L)
                setWaitForAccurateLocation(false)
                setMinUpdateDistanceMeters(5f) // Update when moved 5 meters
            }.build()

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            Log.d("Location", "Started continuous location updates")

        } catch (e: Exception) {
            Log.e("Location", "Error starting location updates: ${e.message}", e)
        }
    }

    private fun handleLocationUpdate(location: Location) {
        try {
            // Validate location
            if (!isLocationValid(location)) {
                Log.w("Location", "Invalid location received")
                return
            }

            // Check for mock location
            if (isMockLocation(location)) {
                stopLocationUpdates()
                Utility.exitApplication(
                    "Warning!",
                    "Please disable mock/fake location. The application will exit now.",
                    requireActivity()
                )
                return
            }

            // Update location on map
            updateLocationOnMap(location)

            // Store current location
            viewModel.currentLocation = Utility.convertGpsTimeToString(location.time)

            Log.d("Location", "Location updated: ${location.latitude}, ${location.longitude}, Accuracy: ${location.accuracy}m")

        } catch (e: Exception) {
            Log.e("Location", "Error handling location update: ${e.message}", e)
        }
    }

    // Check if location is valid
    private fun isLocationValid(location: Location): Boolean {
        return location.latitude in -90.0..90.0 &&
                location.longitude in -180.0..180.0 &&
                location.accuracy < 100f // Reject very inaccurate locations
    }

    private fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    // Update marker on map with smooth animation
    private fun updateLocationOnMap(location: Location) {
        try {
            val point = Point(
                location.longitude,
                location.latitude,
                SpatialReferences.getWgs84()
            )

            // Remove old marker
            currentLocationGraphic?.let {
                currentLocationGraphicOverlay.graphics.remove(it)
            }

            // Create marker symbol
            val markerSymbol = SimpleMarkerSymbol(
                SimpleMarkerSymbol.Style.CIRCLE,
                ContextCompat.getColor(requireContext(), R.color.current_location),
                22f
            ).apply {
                outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
            }

            // Add new marker
            currentLocationGraphic = Graphic(point, markerSymbol)

            if (!binding.parcelMapview.graphicsOverlays.contains(currentLocationGraphicOverlay)) {
                binding.parcelMapview.graphicsOverlays.add(currentLocationGraphicOverlay)
            }

            currentLocationGraphicOverlay.graphics.add(currentLocationGraphic)

            Log.d("Location", "Marker updated at: ${location.latitude}, ${location.longitude}")

        } catch (e: Exception) {
            Log.e("Location", "Error updating location marker: ${e.message}", e)
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("Location", "Stopped location updates")
        }
    }

    private fun startLocationProcess(enableZoom: Boolean) {
        try {
            if (Utility.checkGPS(requireActivity())) {
                Utility.showProgressAlertDialog(context, "Please wait, fetching location...")
                getInitialLocation(enableZoom)
            } else {
                Utility.buildAlertMessageNoGps(requireActivity())
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location Exception: ${e.message}", Toast.LENGTH_SHORT).show()
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
            binding.tvHeader.text = "Mauza: $mauzaName ($areaName)"
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
                    showSettingsDialog("Location")
                } else {
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

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        positiveButton.textSize = 16f
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

        negativeButton.textSize = 16f
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        val builder =
            AlertDialog.Builder(context).setMessage(message).setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

        positiveButton.textSize = 16f
        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

        negativeButton.textSize = 16f
        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    }

//    private fun startLocationProcess(enableZoom: Boolean) {
//        try {
//            if (Utility.checkGPS(requireActivity())) {
//                Utility.showProgressAlertDialog(context, "Please wait, fetching location...")
//                getCurrentLocationFromFusedProvider(enableZoom)
//            } else {
//                Utility.buildAlertMessageNoGps(requireActivity())
//            }
//        } catch (e: Exception) {
//            Toast.makeText(context, "Location Exception :${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }

//    private fun getCurrentLocationFromFusedProvider(zoom: Boolean) {
//        try {
//            enableZoom = zoom
//
//            locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
//                .setWaitForAccurateLocation(false)
//                .setMinUpdateIntervalMillis(1500)
//                .setMaxUpdateDelayMillis(3000)
//                .build()
//
//            if (ActivityCompat.checkSelfPermission(
//                    requireContext(),
//                    Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
//                    requireContext(),
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                return
//            }
//
//            fusedLocationClient.requestLocationUpdates(
//                locationRequest,
//                locationCallback,
//                Looper.getMainLooper()
//            )
//
//        } catch (e: Exception) {
//            Toast.makeText(
//                requireContext(),
//                "Current Location Exception :${e.message}",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//    }

//    private fun handleLocationResult(locationResult: LocationResult) {
//        for (location in locationResult.locations) {
//            val locationAccuracy = location.accuracy.roundToInt()
//
//            val meterAccuracy = sharedPreferences.getInt(
//                Constants.SHARED_PREF_METER_ACCURACY,
//                Constants.SHARED_PREF_DEFAULT_ACCURACY
//            )
//
//            val accuracy = if (enableZoom) {
//                meterAccuracy
//            } else {
//                Constants.locationMediumAccuracy
//            }
//
//            Log.d("TAG", locationAccuracy.toString())
//            Log.d("TAG", location.provider.toString())
//
//            if (locationAccuracy < accuracy) {
//                if (Build.VERSION.SDK_INT < 31) {
//                    @Suppress("DEPRECATION")
//                    if (!location.isFromMockProvider) {
//                        Utility.dismissProgressAlertDialog()
//                        if (!enableZoom) {
//                            fusedLocationClient.removeLocationUpdates(locationCallback)
//                        }
//                        setLocation(location, enableZoom)
//                    } else {
//                        Utility.dismissProgressAlertDialog()
//                        fusedLocationClient.removeLocationUpdates(locationCallback)
//                        Utility.exitApplication(
//                            "Warning!",
//                            "Please disable mock/fake location. The application will exit now.",
//                            requireActivity()
//                        )
//                    }
//                } else {
//                    if (!location.isMock) {
//                        Utility.dismissProgressAlertDialog()
//                        if (!enableZoom) {
//                            fusedLocationClient.removeLocationUpdates(locationCallback)
//                        }
//                        setLocation(location, enableZoom)
//                    } else {
//                        Utility.dismissProgressAlertDialog()
//                        fusedLocationClient.removeLocationUpdates(locationCallback)
//                        Utility.exitApplication(
//                            "Warning!",
//                            "Please disable mock/fake location. The application will exit now.",
//                            requireActivity()
//                        )
//                    }
//                }
//            }
//        }
//    }

    override fun onPause() {
        _binding?.parcelMapview?.pause()
        super.onPause()
        try {
            LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(refreshReceiver)
        } catch (e: Exception) {
            Log.e("FragmentMap", "Error unregistering receiver: ${e.message}")
        }
        stopLocationUpdates()
        stopLoadingParcels()
    }

    private fun refreshMapData() {
        Log.d("FragmentMap", "Refreshing map data...")
        viewLifecycleOwner.lifecycleScope.launch {
            surveyParcelsGraphics?.graphics?.clear()
            surveyLabelGraphics?.graphics?.clear()
            delay(300)
            loadMap(ids, ids.isNotEmpty())
        }
    }



//    private fun setLocation(location: Location, enableZoom: Boolean) {
//        stopLocationUpdates()
//
//        drawMarker(
//            location.latitude.toString(),
//            location.longitude.toString(),
//            enableZoom
//        )
//    }

//    private fun drawMarker(lat: String, lng: String, enableZoom: Boolean) {
//        try {
//            val latitude = lat.toDoubleOrNull()
//            val longitude = lng.toDoubleOrNull()
//
//            if (latitude == null || longitude == null ||
//                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
//            ) {
//                Log.e("TAG", "Invalid coordinates: lat=$latitude, lng=$longitude")
//                return
//            }
//
//            val location = Point(longitude, latitude, SpatialReferences.getWgs84())
//
//            val markerSymbol = SimpleMarkerSymbol(
//                SimpleMarkerSymbol.Style.CIRCLE,
//                ContextCompat.getColor(context, R.color.current_location),
//                22f
//            ).apply {
//                outline = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2f)
//            }
//
//            if (!binding.parcelMapview.graphicsOverlays.contains(currentLocationGraphicOverlay)) {
//                binding.parcelMapview.graphicsOverlays.add(currentLocationGraphicOverlay)
//            }
//
//            currentLocationGraphicOverlay.graphics.clear()
//            currentLocationGraphicOverlay.graphics.add(Graphic(location, markerSymbol))
//
//            if (enableZoom) {
//                binding.parcelMapview.setViewpointAsync(Viewpoint(location, 1000.0))
//            }
//
//            Log.d("TAG", "Marker drawn at lat=$latitude, lng=$longitude")
//
//        } catch (e: Exception) {
//            Log.e("TAG", "drawMarker error: ${e.message}", e)
//        }
//    }

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
            // Handle split mode touches
            if (isSplitMode) {
                handleSplitModeTouch(e)
                return true
            }

            // Handle task assign mode touches
            if (isTaskAssignMode) {
                handleTaskAssignModeTouch(e)
                return true
            }

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val graphic = result.graphics[0]

                        mCallOut.dismiss()

                        showSurveyedCallOutNew(graphic, screenPoint)
                    }
                } catch (ex: Exception) {
                    Log.e("IdentifyTouch", "Error: ${ex.message}")
                }
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            super.onLongPress(e)

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val graphic = result.graphics[0]

                        val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1

                        if (surveyStatus == 2) {
                            // Parcel is surveyed - don't allow splitting
                            Toast.makeText(
                                context,
                                "Surveyed parcels cannot be split",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@addDoneListener
                        }

                        mCallOut.dismiss()

                        enterSplitMode(graphic)

                        Toast.makeText(
                            context,
                            "Split mode enabled for parcel ${graphic.attributes["parcel_no"]}",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "No parcel found at this location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (ex: Exception) {
                    Log.e("LongPress", "Error identifying parcel: ${ex.message}")
                    Toast.makeText(
                        context,
                        "Error selecting parcel for split",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun handleSplitModeTouch(e: MotionEvent) {
            if (!isSplitMode || splitTargetGraphic == null) return
            if (!::splitOverlay.isInitialized) return

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())
            val mapPoint = mMapView.screenToLocation(screenPoint)
            if (splitPoints.isNotEmpty() && splitPoints.last() == mapPoint) return

            splitPoints.add(mapPoint)
            updateSplitLineVisual()

            Toast.makeText(
                context,
                "Added point ${splitPoints.size}. Tap to add more points or Apply Split.",
                Toast.LENGTH_SHORT
            ).show()
        }

        private fun handleTaskAssignModeTouch(e: MotionEvent) {
            if (!isTaskAssignMode) return

            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)

            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val selectedGraphic = result.graphics[0]
                        val parcelId = selectedGraphic.attributes["parcel_id"] as? Long
                            ?: return@addDoneListener

                        if (selectedTaskParcels.containsKey(parcelId)) {
                            // Unselect
                            selectedTaskParcels.remove(parcelId)

                            // Restore original symbol
                            val surveyStatus =
                                selectedGraphic.attributes["surveyStatusCode"] as? Int ?: 1
                            selectedGraphic.symbol = getSymbolForSurveyStatus(surveyStatus)

                            Toast.makeText(
                                context,
                                "Parcel deselected (${selectedTaskParcels.size} selected)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Select
                            val parcelInfo = ParcelInfo(
                                parcelId = parcelId,
                                parcelNo = selectedGraphic.attributes["parcel_no"].toString(),
                                subParcelNo = selectedGraphic.attributes["sub_parcel_no"].toString(),
                                area = selectedGraphic.attributes["area"].toString(),
                                khewatInfo = selectedGraphic.attributes["khewatInfo"].toString(),
                                unitId = selectedGraphic.attributes["unit_id"]?.toString()
                                    ?.toLongOrNull() ?: 0L,
                                groupId = selectedGraphic.attributes["group_id"]?.toString()
                                    ?.toLongOrNull() ?: 0L
                            )

                            selectedTaskParcels[parcelId] = parcelInfo

                            // Highlight selected parcel
                            selectedGraphic.symbol = SimpleFillSymbol(
                                SimpleFillSymbol.Style.SOLID,
                                Color.argb(100, 0, 150, 255),
                                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3f)
                            )

                            Toast.makeText(
                                context,
                                "Parcel selected (${selectedTaskParcels.size} total)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        updateTaskAssignSelectionCount()
                    }
                } catch (e: Exception) {
                    Log.e("TaskAssign", "Error selecting parcel: ${e.message}")
                }
            }
        }
    }

    private fun getSymbolForSurveyStatus(status: Int): SimpleFillSymbol {
        return when (status) {
            1 -> unSurveyedBlocks
            2 -> surveyedBlocks
            3 -> revisitBlocks
            4 -> lockedBlocks
            else -> unSurveyedBlocks
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showSurveyedCallOutNew(
        graphics: Graphic,
        screenPoint: android.graphics.Point
    ) {
        val inflater = this.layoutInflater
        dialogView = inflater.inflate(R.layout.cardview_map_info_new, null)

        val attr = graphics.attributes

        tvParcelNo = dialogView.findViewById(R.id.tv_parcel_no_value)
        tvParcelNoUni = dialogView.findViewById(R.id.tv_parcel_no_uni_value)
        val tvParcelArea = dialogView.findViewById<TextView>(R.id.tv_parcel_area_value)
        val lActionButtons = dialogView.findViewById<LinearLayout>(R.id.layout_action_buttons)
        val lParcel = dialogView.findViewById<LinearLayout>(R.id.layout_parcel)
        val btnStartSurvey = dialogView.findViewById<Button>(R.id.btn_start_survey)
        val btnStartTaskAssign = dialogView.findViewById<Button>(R.id.btn_start_task)
        val btnRevisitSurvey = dialogView.findViewById<Button>(R.id.btn_revisit_survey)
        val btnRetakePicturesSurvey =
            dialogView.findViewById<Button>(R.id.btn_retake_pictures_survey)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

//        val btnHarvested = dialogView.findViewById<Button>(R.id.btn_Harvested)
        val btnSolar = dialogView.findViewById<Button>(R.id.btn_solar_panel)
        val btnDrone = dialogView.findViewById<Button>(R.id.btn_drone_spray)
        val btnInputs = dialogView.findViewById<Button>(R.id.btn_inputs)

        val delete = dialogView.findViewById<ImageView>(R.id.delete)
        val mapLocation = dialogView.findViewById<ImageView>(R.id.mapLocaton)
        val directions = dialogView.findViewById<ImageView>(R.id.directions)

        val lSplitParcel = dialogView.findViewById<LinearLayout>(R.id.layout_split_parcel)
        val etSplitParcel = dialogView.findViewById<EditText>(R.id.et_split_parcel)

        val lMergeParcel = dialogView.findViewById<LinearLayout>(R.id.layout_merge_parcel)
        tvMergeParcel = dialogView.findViewById(R.id.tv_merge_parcel)
        tvMergeParcelHi = dialogView.findViewById(R.id.tv_merge_parcel_hi)

        val rgParcel = dialogView.findViewById<RadioGroup>(R.id.rg_parcel)

        btnSolar.setOnClickListener {
            IntentUtil.startActivity(context,SolarPanelActivity::class.java)
        }

        btnDrone.setOnClickListener {
            IntentUtil.startActivity(context,DroneSprayActivity::class.java)
        }

        btnInputs.setOnClickListener {
            IntentUtil.startActivity(context,InputItemsActivity::class.java)
        }

        rgParcel.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_same -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.GONE
                }

//                R.id.rb_split -> {
//                    lSplitParcel.visibility = View.VISIBLE
//                    lMergeParcel.visibility = View.GONE
//                }

                R.id.rb_merge -> {
                    lSplitParcel.visibility = View.GONE
                    lMergeParcel.visibility = View.VISIBLE
                }
            }
        }

        tvParcelNo.text = attr["parcel_no"].toString()
        tvParcelNoUni.text = attr["khewatInfo"].toString()
        val areaSqFt = attr["area"].toString().toDoubleOrNull() ?: 0.0
        val areaKanal = areaSqFt / 5445.0
        tvParcelArea.text = String.format(Locale.US, "%.2f Kanal", areaKanal)

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
            }

            else -> {
            }
        }

        val parcelId = attr["parcel_id"]?.toString()?.toLongOrNull() ?: 0L

//        fun handleHarvestedClick(parcelId: Long, attributes: Map<String, Any>) {
//            viewLifecycleOwner.lifecycleScope.launch {
//                try {
//                    val parcelNo = attributes["parcel_no"]?.toString() ?: ""
//                    val subParcelNo = attributes["sub_parcel_no"]?.toString() ?: ""
//
//                    val parcel = withContext(Dispatchers.IO) {
//                        database.activeParcelDao().getParcelById(parcelId)
//                    }
//
//                    if (parcel == null) {
//                        Toast.makeText(context, "Parcel not found in database", Toast.LENGTH_SHORT)
//                            .show()
//                        return@launch
//                    }
//
//                    if (parcel.surveyStatusCode != 2) {
//                        Toast.makeText(
//                            context,
//                            "Only surveyed parcels can be harvested",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                        return@launch
//                    }
//
//                    withContext(Dispatchers.Main) {
//                        for (graphic in surveyParcelsGraphics.graphics) {
//                            val graphicParcelId = graphic.attributes["parcel_id"] as? Long
//
//                            if (graphicParcelId == parcelId) {
//                                val harvestedSymbol = SimpleFillSymbol(
//                                    SimpleFillSymbol.Style.SOLID,
//                                    Color.WHITE,
//                                    SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLACK, 2f)
//                                )
//
//                                graphic.symbol = harvestedSymbol
//                                graphic.attributes["isHarvested"] = true
//
//                                for (labelGraphic in surveyLabelGraphics.graphics) {
//                                    val labelParcelId =
//                                        labelGraphic.attributes["parcel_id"] as? Long
//
//                                    if (labelParcelId == parcelId) {
//                                        val textSymbol = labelGraphic.symbol as? TextSymbol
//                                        textSymbol?.let {
//                                            val updatedTextSymbol = TextSymbol().apply {
//                                                text = it.text
//                                                size = 10f
//                                                color = Color.BLACK
//                                                horizontalAlignment =
//                                                    TextSymbol.HorizontalAlignment.CENTER
//                                                verticalAlignment =
//                                                    TextSymbol.VerticalAlignment.MIDDLE
//                                                haloColor = Color.WHITE
//                                                haloWidth = 1f
//                                                fontWeight = TextSymbol.FontWeight.BOLD
//                                            }
//                                            labelGraphic.symbol = updatedTextSymbol
//                                            labelGraphic.attributes["isHarvested"] = true
//                                        }
//                                        break
//                                    }
//                                }
//                                break
//                            }
//                        }
//
//                        saveHarvestedStatusToPreferences(parcelId, true)
//
//                        closeCallOut()
//
//                        Toast.makeText(
//                            context,
//                            "Parcel $parcelNo marked as harvested",
//                            Toast.LENGTH_SHORT
//                        ).show()
//                    }
//
//                    Log.d(
//                        "Harvested",
//                        "Parcel $parcelNo (ID: $parcelId) marked as harvested and saved"
//                    )
//
//                } catch (e: Exception) {
//                    Log.e("Harvested", "Error in harvested action: ${e.message}", e)
//                    Toast.makeText(
//                        context,
//                        "Error processing harvested action: ${e.message}",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//        }

//        btnHarvested.setOnClickListener {
//            handleHarvestedClick(parcelId, attr)
//        }

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

            for (graphic in surveyParcelsGraphics.graphics) {
                graphic.symbol = defaultParcelSymbol
            }

            val baseParcelId = graphics.attributes["parcel_id"].toString()

            graphics.symbol = SimpleFillSymbol(
                SimpleFillSymbol.Style.SOLID,
                Color.argb(100, 255, 140, 0),
                SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 3f)
            )

            val touchListener =
                object :
                    DefaultMapViewOnTouchListener(requireContext(), binding.parcelMapview) {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!isMergeMode) return super.onSingleTapConfirmed(e)

                        val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

                        val identifyFuture =
                            binding.parcelMapview.identifyGraphicsOverlayAsync(
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
                                        selectedMergeParcels.remove(parcelId)
                                        selectedGraphic.symbol = defaultParcelSymbol
                                        Toast.makeText(
                                            requireContext(),
                                            "Parcel unselected",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
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

                                    val parcelNos =
                                        selectedMergeParcels.values.joinToString(", ")
                                    val parcelIds =
                                        selectedMergeParcels.keys.joinToString(", ")

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

        // Task Assign Button - Show Selection Dialog
        btnStartTaskAssign.setOnClickListener {
            try {
                Log.d("TaskAssign", "=== Button Clicked ===")
                showTaskAssignSelectionDialog(attr)
            } catch (e: Exception) {
                Log.e("TaskAssign", "General Exception: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnStartSurvey.setOnClickListener { view: View? ->
            val radioButton: RadioButton =
                dialogView.findViewById(rgParcel.checkedRadioButtonId)

            viewModel.parcelOperation = radioButton.text.toString()
            viewModel.parcelId = attr["parcel_id"].toString().toLong()
            viewModel.parcelNo = attr["parcel_no"].toString().toLong()
            viewModel.subParcelNo = attr["sub_parcel_no"].toString()

            when (rgParcel.checkedRadioButtonId) {
                R.id.rb_same -> {
                    Log.d("TaskAssign", "=== RB_SAME OPERATION STARTED ===")

                    viewModel.parcelOperationValue = ""
                    viewModel.imageTaken = 0
                    viewModel.discrepancyPicturePath = ""

                    Log.d("TaskAssign", "All attributes in graphic:")
                    attr.forEach { (key, value) ->
                        Log.d("TaskAssign", "  $key = $value")
                    }

                    val parcelId = attr["parcel_id"].toString().toLong()
                    Log.d("TaskAssign", "parcelId: $parcelId")

                    val parcelNo = attr["parcel_no"].toString()
                    Log.d("TaskAssign", "parcelNo: $parcelNo")

                    val subParcelNo = attr["sub_parcel_no"].toString()
                    Log.d("TaskAssign", "subParcelNo: $subParcelNo")

                    val area = attr["area"].toString()
                    Log.d("TaskAssign", "area: $area")

                    val khewatInfo = attr["khewatInfo"].toString()
                    Log.d("TaskAssign", "khewatInfo: $khewatInfo")

                    val parcelOperation = viewModel.parcelOperation
                    Log.d("TaskAssign", "parcelOperation: $parcelOperation")

                    val parcelOperationValue = viewModel.parcelOperationValue
                    Log.d("TaskAssign", "parcelOperationValue: $parcelOperationValue")

                    val parcelOperationValueHi = tvMergeParcelHi.text.toString().trim()
                    Log.d("TaskAssign", "parcelOperationValueHi: $parcelOperationValueHi")

                    val unitIdRaw = attr["unit_id"]
                    Log.d(
                        "TaskAssign",
                        "unit_id (raw): $unitIdRaw (Type: ${unitIdRaw?.javaClass?.simpleName})"
                    )

                    val groupIdRaw = attr["group_id"]
                    Log.d(
                        "TaskAssign",
                        "group_id (raw): $groupIdRaw (Type: ${groupIdRaw?.javaClass?.simpleName})"
                    )

                    val unitId = try {
                        attr["unit_id"]?.toString()?.toLongOrNull() ?: 0L
                    } catch (e: Exception) {
                        Log.e("TaskAssign", "Error converting unit_id: ${e.message}")
                        0L
                    }
                    Log.d("TaskAssign", "unitId (converted): $unitId")

                    val groupId = try {
                        attr["group_id"]?.toString()?.toLongOrNull() ?: 0L
                    } catch (e: Exception) {
                        Log.e("TaskAssign", "Error converting group_id: ${e.message}")
                        0L
                    }
                    Log.d("TaskAssign", "groupId (converted): $groupId")

                    val context = requireContext()
                    val intent = Intent(context, SurveyActivity::class.java).apply {
                        putExtra("parcelId", parcelId)
                        putExtra("parcelNo", parcelNo)
                        putExtra("subParcelNo", subParcelNo)
                        putExtra("parcelArea", area)
                        putExtra("khewatInfo", khewatInfo)
                        putExtra("parcelOperation", parcelOperation)
                        putExtra("parcelOperationValue", parcelOperationValue)
                        putExtra("parcelOperationValueHi", parcelOperationValueHi)
                        putExtra("unitId", unitId)
                        putExtra("groupId", groupId)
                    }

                    Log.d("TaskAssign", "Intent extras being passed:")
                    Log.d("TaskAssign", "  parcelId: $parcelId")
                    Log.d("TaskAssign", "  parcelNo: $parcelNo")
                    Log.d("TaskAssign", "  subParcelNo: $subParcelNo")
                    Log.d("TaskAssign", "  parcelArea: $area")
                    Log.d("TaskAssign", "  khewatInfo: $khewatInfo")
                    Log.d("TaskAssign", "  parcelOperation: $parcelOperation")
                    Log.d("TaskAssign", "  parcelOperationValue: $parcelOperationValue")
                    Log.d("TaskAssign", "  parcelOperationValueHi: $parcelOperationValueHi")
                    Log.d("TaskAssign", "  unitId: $unitId")
                    Log.d("TaskAssign", "  groupId: $groupId")

                    Log.d("TaskAssign", "Starting SurveyActivity...")
                    startActivity(intent)
//                    dialog.dismiss()
                    Log.d("TaskAssign", "=== RB_SAME OPERATION COMPLETED ===")
                }

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
//                    val bundle = Bundle().apply {
//                        putLong("parcelId", attr["parcel_id"].toString().toLong())
//                        putString("parcelNo", attr["parcel_no"].toString())
//                        putString("subParcelNo", attr["sub_parcel_no"].toString())
//                        putString("parcelArea", attr["area"].toString())
//                        putString("khewatInfo", attr["khewatInfo"].toString())
//                        putString("parcelOperation", viewModel.parcelOperation)
//                        putString("parcelOperationValue", viewModel.parcelOperationValue)
//                        putString(
//                            "parcelOperationValueHi",
//                            tvMergeParcelHi.text.toString().trim()
//                        )
//                        putLong("unitId", attr["unit_id"].toString().toLong())
//                        putLong("groupId", attr["group_id"].toString().toLong())
//                    }
//
//                    findNavController().navigate(
//                        R.id.action_fragmentMap_to_fragmentSubParcelList,
//                        bundle
//                    )
////                    dialog.dismiss()
//                }

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
                        putExtra(
                            "parcelOperationValueHi",
                            tvMergeParcelHi.text.toString().trim()
                        )
                        putExtra("unitId", attr["unit_id"].toString().toLong())
                        putExtra("groupId", attr["group_id"].toString().toLong())
                    }
                    startActivity(intent)
//                    dialog.dismiss()
                }
            }
        }

        btnRevisitSurvey.setOnClickListener {
            graphicCentoid = graphics

            closeCallOut()

            if (checkPermission()) {
                if (Utility.checkGPS(requireActivity())) {
                    Utility.showProgressAlertDialog(
                        context,
                        "Please wait, fetching location..."
                    )
//                    getCurrentLocation()
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
                emptyList()
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

                        action = R.id.action_fragmentMap_to_fragmentFormRemarks
                    }

                    else -> {
                        viewModel.rejectedSubParcelsList.clear()

                        val rejectedSubParcel = arrayListOf<RejectedSubParcel>()
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
                    Toast.makeText(context, "Action Undefined", Toast.LENGTH_SHORT).show()
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
                                            survey.parcelOperationValue.split(",")
                                                .toMutableList()
                                        for (parcelNo in parcelNos) {

                                            val newStatusId =
                                                database.parcelDao().getNewStatusId(
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
                                        val newStatusId =
                                            database.parcelDao().getNewStatusId(
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
                GeometryEngine.project(
                    mapPoint,
                    SpatialReferences.getWebMercator()
                ) as Point

            val geoPoint =
                GeometryEngine.project(
                    projectedPoint,
                    SpatialReferences.getWgs84()
                ) as Point

            val latitude = geoPoint.y
            val longitude = geoPoint.x

            println("latitude77=$latitude")
            println("longitude77=$longitude")

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
                GeometryEngine.project(
                    mapPoint,
                    SpatialReferences.getWebMercator()
                ) as Point

            val geoPoint =
                GeometryEngine.project(
                    projectedPoint,
                    SpatialReferences.getWgs84()
                ) as Point

            val latitude = geoPoint.y
            val longitude = geoPoint.x

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
        mCallOut.location = mapPoint
        mCallOut.content = dialogView
        mCallOut.show()
    }

    // Task Assign Selection Dialog
    private fun showTaskAssignSelectionDialog(currentParcelAttr: Map<String, Any>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_task_assign_selection, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Make dialog background transparent to show custom rounded corners
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Single Parcel Card Click
        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardSingleParcel)
            .setOnClickListener {
                dialog.dismiss()
                startSingleParcelTaskAssign(currentParcelAttr)

                // Optional: Add haptic feedback
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }

        // Multiple Parcels Card Click
        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardMultipleParcels)
            .setOnClickListener {
                dialog.dismiss()
                enterMultipleParcelTaskAssignMode(currentParcelAttr)

                // Optional: Add haptic feedback
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }

        // Cancel Button Click
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Optional: Add enter animation
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
    }

    private fun startSingleParcelTaskAssign(attr: Map<String, Any>) {
        val context = requireContext()
        val intent = Intent(context, TaskAssignActivity::class.java).apply {
            putExtra("parcelId", attr["parcel_id"].toString().toLong())
            putExtra("parcelNo", attr["parcel_no"].toString())
            putExtra("subParcelNo", attr["sub_parcel_no"].toString())
            putExtra("parcelArea", attr["area"].toString())
            putExtra("khewatInfo", attr["khewatInfo"].toString())
            putExtra("unitId", attr["unit_id"]?.toString()?.toLongOrNull() ?: 0L)
            putExtra("groupId", attr["group_id"]?.toString()?.toLongOrNull() ?: 0L)
            putExtra("isMultipleParcel", false)
        }

        Log.d("TaskAssign", "Starting single parcel task assignment")
        startActivity(intent)
        closeCallOut()
    }

    private fun enterMultipleParcelTaskAssignMode(baseParcelAttr: Map<String, Any>) {
        isTaskAssignMode = true
        selectedTaskParcels.clear()

        closeCallOut()

        binding.taskAssignControlBar.visibility = View.VISIBLE
        dialogView.findViewById<View>(R.id.card_root)?.visibility = View.GONE

        Toast.makeText(
            requireContext(),
            "Tap on parcels to select for task assignment",
            Toast.LENGTH_LONG
        ).show()

        val baseParcelId = baseParcelAttr["parcel_id"].toString().toLong()
        val baseParcelInfo = ParcelInfo(
            parcelId = baseParcelId,
            parcelNo = baseParcelAttr["parcel_no"].toString(),
            subParcelNo = baseParcelAttr["sub_parcel_no"].toString(),
            area = baseParcelAttr["area"].toString(),
            khewatInfo = baseParcelAttr["khewatInfo"].toString(),
            unitId = baseParcelAttr["unit_id"]?.toString()?.toLongOrNull() ?: 0L,
            groupId = baseParcelAttr["group_id"]?.toString()?.toLongOrNull() ?: 0L
        )

        selectedTaskParcels[baseParcelId] = baseParcelInfo

        for (graphic in surveyParcelsGraphics.graphics) {
            if (graphic.attributes["parcel_id"] == baseParcelId) {
                graphic.symbol = SimpleFillSymbol(
                    SimpleFillSymbol.Style.SOLID,
                    Color.argb(100, 255, 140, 0),
                    SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.BLUE, 3f)
                )
                break
            }
        }

        updateTaskAssignSelectionCount()

        try {
            IdentifyFeatureLayerTouchListener(
                context,
                binding.parcelMapview,
                this@FragmentMap.surveyParcelsGraphics
            ).also { binding.parcelMapview.onTouchListener = it }
        } catch (e: Exception) {
            Log.e("TaskAssign", "Error setting touch listener: ${e.message}")
        }
    }

    private fun saveHarvestedStatusToPreferences(parcelId: Long, isHarvested: Boolean) {
        try {
            val currentSet =
                sharedPreferences.getStringSet("harvested_parcels", emptySet()) ?: emptySet()
            Log.d("Harvested", "Current harvested parcels before save: $currentSet")

            val harvestedParcels = currentSet.toMutableSet()

            if (isHarvested) {
                harvestedParcels.add(parcelId.toString())
                Log.d("Harvested", "Adding parcel ID: $parcelId")
            } else {
                harvestedParcels.remove(parcelId.toString())
                Log.d("Harvested", "Removing parcel ID: $parcelId")
            }

            Log.d("Harvested", "New harvested parcels set: $harvestedParcels")

            val success = sharedPreferences.edit()
                .putStringSet("harvested_parcels", harvestedParcels)
                .commit()

            if (success) {
                Log.d("Harvested", "Successfully saved harvested status for parcel ID: $parcelId")
            } else {
                Log.e("Harvested", "Failed to save harvested status for parcel ID: $parcelId")
            }

            val verifySet = sharedPreferences.getStringSet("harvested_parcels", emptySet())
            Log.d("Harvested", "Verification - Harvested parcels after save: $verifySet")

        } catch (e: Exception) {
            Log.e("Harvested", "Error saving harvested status: ${e.message}", e)
        }
    }

    private fun getHarvestedParcelsFromPreferences(): Set<String> {
        try {
            val harvestedParcels =
                sharedPreferences.getStringSet("harvested_parcels", emptySet()) ?: emptySet()
            Log.d(
                "Harvested",
                "Retrieved ${harvestedParcels.size} harvested parcels: $harvestedParcels"
            )
            return harvestedParcels
        } catch (e: Exception) {
            Log.e("Harvested", "Error getting harvested parcels: ${e.message}", e)
            return emptySet()
        }
    }

    private fun isParcelHarvested(parcelId: Long): Boolean {
        val harvestedParcels = getHarvestedParcelsFromPreferences()
        val isHarvested = harvestedParcels.contains(parcelId.toString())
        Log.d("Harvested", "Checking parcel ID $parcelId: isHarvested = $isHarvested")
        return isHarvested
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
                    0, 1 -> 2 * 60 * 60 * 1000L
                    else -> {
                        val dateTimeFormatter =
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
                        val providedDateTime =
                            LocalDateTime.parse(providedTime, dateTimeFormatter)
                        val nextVisitDateTime =
                            providedDateTime.toLocalDate().plusDays(1).atStartOfDay()
                        Duration.between(providedDateTime, nextVisitDateTime)
                            .toMillis() + 60 * 1000
                    }
                }

                val remainingTime =
                    calculateRemainingTime(
                        currentMobileTime,
                        providedTime,
                        restrictionMillis
                    )

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
                        message =
                            "Please go for this Survey after:\n$remainingMinutes minutes"
                    }

                    val builder = AlertDialog.Builder(requireActivity())
                        .setTitle("Info!")
                        .setCancelable(false)
                        .setMessage(message)
                        .setPositiveButton("Ok", null)

                    val dialog = builder.create()
                    dialog.show()

                    val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                    positiveButton.textSize = 16f
                    positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

                    negativeButton.textSize = 16f
                    negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                }
            } else {
                val builder = AlertDialog.Builder(requireActivity())
                    .setTitle("Alert!")
                    .setCancelable(false)
                    .setMessage("Please try again after few minutes.")
                    .setPositiveButton("Ok", null)

                val dialog = builder.create()
                dialog.show()

                val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)

                positiveButton.textSize = 16f
                positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)

                negativeButton.textSize = 16f
                negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
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
        setHasOptionsMenu(true)

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
//        locationCallback = object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult) {
//                handleLocationResult(locationResult)
//            }
//        }

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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_fragment_map, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Back button
                val intent = Intent(requireContext(), MenuActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                requireActivity().finish()
                true
            }
            R.id.action_refresh_map -> {
                // Refresh button (same as your iv_reset functionality)
                closeCallOut()
                viewLifecycleOwner.lifecycleScope.launch {
                    ids.clear()
                    enableNewPoint = true
                    loadMap(ids, false)
                }
                Toast.makeText(requireContext(), "Refreshing map...", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

//    private fun getCurrentLocation() {
//        try {
//            ls = object : LocationListener {
//                override fun onProviderEnabled(provider: String) {}
//                override fun onProviderDisabled(provider: String) {}
//
//                @Deprecated("Deprecated in Java")
//                override fun onStatusChanged(
//                    provider: String?,
//                    status: Int,
//                    extras: Bundle?
//                ) {
//                }
//
//                override fun onLocationChanged(location: Location) {
//
//                    viewModel.currentLocation =
//                        Utility.convertGpsTimeToString(location.time)
//
//                    if (Build.VERSION.SDK_INT < 31) {
//                        if (!location.isFromMockProvider) {
//                            Utility.dismissProgressAlertDialog()
//                            ls?.let { lm?.removeUpdates(it) }
//                            reVisitValidation(location)
//                        } else {
//                            Utility.dismissProgressAlertDialog()
//                            ls?.let { lm?.removeUpdates(it) }
//                            Utility.exitApplication(
//                                "Warning!",
//                                "Please disable mock/fake location. The application will exit now.",
//                                requireActivity()
//                            )
//                        }
//                    } else {
//                        if (!location.isMock) {
//                            Utility.dismissProgressAlertDialog()
//                            ls?.let { lm?.removeUpdates(it) }
//                            reVisitValidation(location)
//                        } else {
//                            Utility.dismissProgressAlertDialog()
//                            ls?.let { lm?.removeUpdates(it) }
//                            Utility.exitApplication(
//                                "Warning!",
//                                "Please disable mock/fake location. The application will exit now.",
//                                requireActivity()
//                            )
//                        }
//                    }
//                }
//            }
//            if (ActivityCompat.checkSelfPermission(
//                    context, Manifest.permission.ACCESS_FINE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
//                    context, Manifest.permission.ACCESS_COARSE_LOCATION
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                return
//            }
//            ls?.let {
//                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0f, it)
//            }
//        } catch (e: Exception) {
//            Toast.makeText(
//                context,
//                "Current Location Exception :${e.message}",
//                Toast.LENGTH_SHORT
//            )
//                .show()
//        }
//    }
}