package pk.gop.pulse.katchiAbadi.fragments.form

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
import android.os.Environment
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
import android.widget.PopupMenu
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
import com.esri.arcgisruntime.geometry.PointCollection
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.PolygonBuilder
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
import pk.gop.pulse.katchiAbadi.data.repository.NewSurveyRepositoryImpl
import pk.gop.pulse.katchiAbadi.databinding.FragmentMapBinding
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.SurveyStatusCodes
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutionException
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
        // Add split mode setup
        setupSplitOverlay()
        setupSplitModeListeners()

    }

    private fun setupSplitOverlay() {
        splitOverlay = GraphicsOverlay()
        binding.parcelMapview.graphicsOverlays.add(splitOverlay)
    }

    // Add split mode button listener in your setupEditModeListeners method
    private fun setupSplitModeListeners() {
        binding.fabSplitMode.setOnClickListener {
            toggleSplitMode()
        }

        binding.btnApplySplit.setOnClickListener {
            applySplit()
        }

        binding.btnCancelSplit.setOnClickListener {
            exitSplitMode()
        }
    }

    // Toggle split mode
    private fun toggleSplitMode() {
        if (isSplitMode) {
            exitSplitMode()
        } else {
            if (selectedParcelGraphics.isNotEmpty()) {
                enterSplitMode(selectedParcelGraphics.first())
            } else {
                Toast.makeText(context, "Please select a parcel to split", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // Enter split mode
    // Updated enterSplitMode method to ensure proper highlighting
    private fun enterSplitMode(graphic: Graphic) {
        if (isSplitMode) {
            exitSplitMode()
        }

        isSplitMode = true
        splitTargetGraphic = graphic
        splitPoints.clear()

        // Add the graphic to selectedParcelGraphics if not already there
        if (!selectedParcelGraphics.contains(graphic)) {
            selectedParcelGraphics.add(graphic)
        }

        // Highlight the target parcel with green color to show selection
        val splitHighlightSymbol = SimpleFillSymbol(
            SimpleFillSymbol.Style.SOLID,
            Color.argb(150, 0, 255, 0), // Green highlight with transparency
            SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 4f) // Green border
        )
        graphic.symbol = splitHighlightSymbol

        // Show split mode UI
        showSplitModeUI()

        Toast.makeText(
            context,
            "Split Mode: Tap points to draw a line across the parcel",
            Toast.LENGTH_LONG
        ).show()
    }

    // Exit split mode
    private fun exitSplitMode() {
        if (!isSplitMode) return

        isSplitMode = false
        splitPoints.clear()
        splitLine = null

        // Restore original symbol for target parcel
        splitTargetGraphic?.let { graphic ->
            val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
            graphic.symbol = getSymbolForSurveyStatus(surveyStatus)
        }

        // Clear split graphics
        splitOverlay.graphics.clear()

        // Hide split mode UI
        hideSplitModeUI()

        splitTargetGraphic = null
    }

    // Show split mode UI
    private fun showSplitModeUI() {
        binding.layoutSplitControls.visibility = View.VISIBLE
        binding.btnApplySplit.visibility = View.VISIBLE
        binding.btnCancelSplit.visibility = View.VISIBLE

        // Hide other UI elements that might interfere
        binding.mergeControlBar.visibility = View.GONE

        // Optionally change FAB icon to indicate split mode is active
        // binding.fabSplitMode.setImageResource(R.drawable.ic_close)
    }

    // Hide split mode UI
    private fun hideSplitModeUI() {
        binding.layoutSplitControls.visibility = View.GONE
        binding.btnApplySplit.visibility = View.GONE
        binding.btnCancelSplit.visibility = View.GONE

        // Restore FAB icon
        // binding.fabSplitMode.setImageResource(R.drawable.ic_split)
    }

    // Apply the split operation
    // Updated applySplit method with better error handling
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
                // Show progress
                Toast.makeText(context, "Processing split...", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    // Create the split line geometry
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

                    // Perform the split operation
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
                                // Success case
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

    // Improved createSplitLineGeometry method
    private fun createSplitLineGeometry(): Polyline? {
        try {
            if (splitPoints.size < 2) {
                Log.e("ParcelSplit", "Insufficient points for line creation: ${splitPoints.size}")
                return null
            }

            // Use the spatial reference from the selected graphic
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

            // Add all split points
            for (point in splitPoints) {
                // Ensure point has correct spatial reference
                val projectedPoint = if (point.spatialReference != spatialRef) {
                    GeometryEngine.project(point, spatialRef) as Point
                } else {
                    point
                }
                builder.addPoint(projectedPoint)
            }

            val line = builder.toGeometry()

            // Validate the created line
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

    // Perform polygon split using the line
    private fun performPolygonSplit(
        polygon: Polygon,
        splitLine: Polyline
    ): List<Polygon> {
        try {
            // Validate inputs
            if (polygon.isEmpty || splitLine.isEmpty) {
                Log.e("ParcelSplit", "Empty geometry provided")
                return emptyList()
            }

            // Ensure both geometries have the same spatial reference
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

            // Validate that the line actually intersects the polygon
            if (!GeometryEngine.intersects(polygon, projectedLine)) {
                Log.e("ParcelSplit", "Split line does not intersect the polygon")
                return emptyList()
            }

            // Extend the line to ensure it completely crosses the polygon
            val extendedLine = extendLineToPolygonBounds(projectedLine, polygon)

            // Validate extended line
            if (extendedLine.isEmpty) {
                Log.e("ParcelSplit", "Failed to create extended line")
                return emptyList()
            }

            // Perform the cut operation with additional validation
            val cutResult = try {
                GeometryEngine.cut(polygon, extendedLine)
            } catch (e: Exception) {
                Log.e("ParcelSplit", "GeometryEngine.cut failed: ${e.message}")
                // Try alternative approach with buffer
                return performPolygonSplitWithBuffer(polygon, extendedLine)
            }

            // Filter and validate results
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

    // Alternative split method using buffer approach
    private fun performPolygonSplitWithBuffer(
        polygon: Polygon,
        splitLine: Polyline
    ): List<Polygon> {
        try {
            // Create a small buffer around the line to create a cutting polygon
            val bufferDistance = 0.00001 // Very small buffer in coordinate units
            val bufferPolygon = GeometryEngine.buffer(splitLine, bufferDistance) as? Polygon
                ?: return emptyList()

            // Use difference operation to split
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


    // Improved extendLineToPolygonBounds method
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

            // Calculate direction vector and normalize it
            val dx = endPoint.x - startPoint.x
            val dy = endPoint.y - startPoint.y
            val length = kotlin.math.sqrt(dx * dx + dy * dy)

            if (length == 0.0) {
                Log.e("ParcelSplit", "Zero-length line cannot be extended")
                return line
            }

            val normalizedDx = dx / length
            val normalizedDy = dy / length

            // Get polygon bounds with sufficient extension
            val envelope = polygon.extent
            val maxDimension = maxOf(envelope.width, envelope.height)
            val extensionDistance = maxDimension * 2.0 // Extend by 2x the polygon size

            // Create extended points
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

            // Build the extended line
            val builder = PolylineBuilder(line.spatialReference)
            builder.addPoint(extendedStart)

            // Add all original points
            for (i in 0 until part.pointCount) {
                builder.addPoint(part.getPoint(i))
            }

            builder.addPoint(extendedEnd)

            val extendedLine = builder.toGeometry()

            // Validate the extended line
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


    // Updated createSplitParcels method with better debugging and refresh
// OPTION 1: Keep it simple - let Room handle everything
    private fun createSplitParcels(originalGraphic: Graphic, splitPolygons: List<Polygon>) {
        val originalAttributes = originalGraphic.attributes
        val originalParcelNo = originalAttributes["parcel_no"]?.toString()?.toLongOrNull() ?: return
        val originalSubParcelNo = originalAttributes["sub_parcel_no"]?.toString() ?: ""

        try {
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        // ✅ FIXED: Get the correct original parcel ID from graphic attributes
                        val originalParcelId = originalAttributes["parcel_id"] as? Long ?: return@withContext
                        if (originalParcelId != null) {
                            cleanupOriginalGraphicsAfterSplit(originalParcelId)
                        }

                        Log.d("SPLIT_DEBUG", "Original parcel ID from graphic: $originalParcelId")

                        // Get the original parcel from database using the ID
                        val originalParcel = database.activeParcelDao().getParcelById(originalParcelId)

                        if (originalParcel == null) {
                            Log.e("SPLIT_DEBUG", "Original parcel not found in database with ID: $originalParcelId")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Original parcel not found", Toast.LENGTH_SHORT).show()
                            }
                            return@withContext
                        }

                        Log.d("SPLIT_DEBUG", "Found original parcel: ID=${originalParcel.id}, PKID=${originalParcel.pkid}, ParcelNo=${originalParcel.parcelNo}")

                        // ✅ FIXED: Deactivate using the correct ID (not pkid)
                        database.activeParcelDao().updateParcelActivationStatus(originalParcel.id, false)
                        Log.d("ParcelSplit", "Deactivated original parcel ID: ${originalParcel.id}")

                        // Generate the maximum ID for new unique IDs
                        val maxId = database.activeParcelDao().getMaxParcelId() ?: 0L

                        // Create new split parcels with UNIQUE IDs
                        val newParcels = mutableListOf<ActiveParcelEntity>()

                        for (i in splitPolygons.indices) {
                            // Generate sub-parcel numbering
                            val newSubParcelNo = if (originalSubParcelNo.isBlank() || originalSubParcelNo == "0") {
                                (i + 1).toString()
                            } else {
                                "${originalSubParcelNo}_${i + 1}"
                            }

                            // Create geometry and centroid
                            val newGeomWKT = convertPolygonToWkt(splitPolygons[i])
                            if (!validateWkt(newGeomWKT)) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Invalid geometry for split parcel ${i + 1}", Toast.LENGTH_LONG).show()
                                }
                                return@withContext
                            }

                            val newCentroid = splitPolygons[i].extent.center
                            val centroidWKT = String.format("POINT(%.8f %.8f)", newCentroid.x, newCentroid.y)

                            // ✅ Generate unique IDs for split parcels
                            val newUniqueId = maxId + i + 1

                            // Create new parcel with UNIQUE id
                            val newParcel = originalParcel.copy(
                                pkid = 0, // Let Room auto-generate
                                id = newUniqueId, // ✅ Each split parcel gets unique ID
                                parcelNo = originalParcelNo,
                                subParcelNo = newSubParcelNo,
                                geomWKT = newGeomWKT,
                                centroid = centroidWKT,
                                surveyStatusCode = 1, // Reset to unsurveyed
                                surveyId = null, // ✅ Clear survey data - each split parcel needs separate survey
                                isActivate = true,
                            )

                            newParcels.add(newParcel)
                            Log.d("SPLIT_DEBUG", "Created split parcel: ID=${newUniqueId}, ParcelNo=$originalParcelNo, SubParcel=$newSubParcelNo")
                        }

                        // Insert the new parcels
                        database.activeParcelDao().insertActiveParcels(newParcels)

                        // ✅ VERIFICATION: Check that split was successful
                        val verificationCount = database.activeParcelDao().countActiveParcelsByNumber(
                            originalParcelNo,
                            originalParcel.mauzaId,
                            originalParcel.areaAssigned
                        )
                        Log.d("ParcelSplit", "After split - Active parcels with number $originalParcelNo: $verificationCount")

                        Log.d("ParcelSplit", "Successfully split parcel $originalParcelNo into ${splitPolygons.size} parts with unique IDs")

                    } catch (e: Exception) {
                        Log.e("ParcelSplit", "Database error: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        return@withContext
                    }
                }

                // Update UI
                withContext(Dispatchers.Main) {
                    try {
                        surveyParcelsGraphics.graphics.remove(originalGraphic)
                        selectedParcelGraphics.remove(originalGraphic)

                        Toast.makeText(
                            context,
                            "Parcel $originalParcelNo split into ${splitPolygons.size} parts",
                            Toast.LENGTH_SHORT
                        ).show()

                        refreshMapDisplay()

                    } catch (e: Exception) {
                        Log.e("ParcelSplit", "UI update error: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ParcelSplit", "Error in createSplitParcels: ${e.message}", e)
        }
    }
    private fun validateWkt(wkt: String?): Boolean {
        if (wkt.isNullOrBlank()) return false

        // Basic checks
        return when {
            wkt.startsWith("POLYGON", ignoreCase = true) && wkt.contains("((") -> true
            wkt.startsWith("POINT", ignoreCase = true) && wkt.contains("(") -> true
            wkt.startsWith("MULTIPOLYGON", ignoreCase = true) && wkt.contains("(((") -> true
            else -> false
        }
    }

    private fun cleanupOriginalGraphicsAfterSplit(originalParcelId: Long) {
        // Remove the old parcel's symbols from the maps
        originalGraphicSymbols.remove(originalParcelId)
        originalLabelGraphics.remove(originalParcelId)

        Log.d("CLEANUP_DEBUG", "Removed original symbols for parcel ID: $originalParcelId")
    }


        // Helper function to convert Polygon to WKT for older SDK versions
    private fun convertPolygonToWkt(polygon: Polygon): String {
        val stringBuilder = StringBuilder("POLYGON(")

        // Get exterior ring
        val exteriorRing = polygon.parts[0]
        stringBuilder.append("(")

        for (i in 0 until exteriorRing.pointCount) {
            val point = exteriorRing.getPoint(i)
            if (i > 0) stringBuilder.append(",")
            stringBuilder.append("${point.x} ${point.y}")
        }

        // Close the ring if not already closed
        val firstPoint = exteriorRing.getPoint(0)
        val lastPoint = exteriorRing.getPoint(exteriorRing.pointCount - 1)
        if (firstPoint.x != lastPoint.x || firstPoint.y != lastPoint.y) {
            stringBuilder.append(",${firstPoint.x} ${firstPoint.y}")
        }

        stringBuilder.append(")")

        // Add interior rings (holes) if any
        for (i in 1 until polygon.parts.size) {
            stringBuilder.append(",(")
            val interiorRing = polygon.parts[i]

            for (j in 0 until interiorRing.pointCount) {
                val point = interiorRing.getPoint(j)
                if (j > 0) stringBuilder.append(",")
                stringBuilder.append("${point.x} ${point.y}")
            }

            // Close the ring if not already closed
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

    // Update split line visual
    private fun updateSplitLineVisual() {
        if (!::splitOverlay.isInitialized) return

        // Clear all graphics first to avoid duplicates
        splitOverlay.graphics.clear()

        // Draw the line if we have at least 2 points
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

        // Draw point markers for all split points
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

    // Fixed refreshMapDisplay method to properly show split parcels
    private fun refreshMapDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("MapRefresh", "Starting map refresh...")

                withContext(Dispatchers.Main) {
                    // Clear current graphics first
                    surveyParcelsGraphics.graphics.clear()
                    surveyLabelGraphics.graphics.clear()
                    selectedParcelGraphics.clear()

                    // Also clear any split graphics if they exist
                    if (::splitOverlay.isInitialized) {
                        splitOverlay.graphics.clear()
                    }
                }

                // Small delay to ensure graphics are cleared
                kotlinx.coroutines.delay(200)

                // Stop any existing loading job
                stopLoadingParcels()

                withContext(Dispatchers.Main) {
                    // Force reload from database - don't preserve IDs to show all split parcels
                    val currentShowLabels =
                        binding.parcelMapview.graphicsOverlays.contains(surveyLabelGraphics)

                    Log.d("MapRefresh", "Reloading map with current filter")
                    Log.d("MapRefresh", "Current IDs: ${ids.size}, ShowLabels: $currentShowLabels")

                    // Clear the current filter temporarily to reload all parcels in the area
                    val originalIds = ArrayList(ids) // Save original filter

                    // Load all parcels to show the split results
                    ids.clear()
                    loadMap(ids, currentShowLabels)

                    // Optionally restore the original filter after a delay
                    viewLifecycleOwner.lifecycleScope.launch {
                        kotlinx.coroutines.delay(1000) // Wait for map to load
                        // You can choose to restore the filter or keep it cleared
                        // ids.addAll(originalIds)
                    }
                }

                Log.d("MapRefresh", "Map refresh completed")

            } catch (e: Exception) {
                Log.e("MapRefresh", "Error refreshing map display: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Error refreshing map: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun deleteParcel(graphic: Graphic) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val parcelId = graphic.attributes["parcel_id"] as? Long ?: return@launch

                // Remove from database
//                database.activeParcelDao().deleteParcel(parcelId)

                // Remove from graphics overlay
                surveyParcelsGraphics.graphics.remove(graphic)

                // Remove from selected list if present
                selectedParcelGraphics.remove(graphic)

                Toast.makeText(context, "Parcel deleted successfully", Toast.LENGTH_SHORT).show()

                // Refresh map display
                refreshMapDisplay()

            } catch (e: Exception) {
                Toast.makeText(context, "Error deleting parcel: ${e.message}", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    // Additional validation method to check split line quality

    override fun onResume() {
        super.onResume()

        // Register broadcast receiver
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

            // Add split overlay setup
            setupSplitOverlay()
            // Add split mode listeners
            setupSplitModeListeners()

            // Update touch listener to support split mode
            try {
                IdentifyFeatureLayerTouchListener(
                    context,
                    parcelMapview,
                    this@FragmentMap.surveyParcelsGraphics
                ).also { parcelMapview.onTouchListener = it }
            } catch (e: Exception) {
                Toast.makeText(context, "Restart the map screen.", Toast.LENGTH_LONG).show()
            }

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
                        database.activeParcelDao().getActiveParcelsByMauzaAndArea(mauzaId, areaName)

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

                // Replace this section in your loadMapTiles method:

                val parcels = if (ids.isNotEmpty()) {
                    val searchResults = database.activeParcelDao().searchParcels(ids)
                    searchResults.filter { it.isActivate } // Only show active parcels
                } else {
                    database.activeParcelDao().getActiveParcelsByMauzaAndArea(mauzaId, areaName)
                }

                Log.d("LoadMap", "Found ${parcels.size} parcels to display")
                val polygonsList = mutableListOf<Polygon>()
                val gson = Gson()

                var processedCount = 0
                var skipCount = 0

                for (parcel in parcels) {
                    try {
                        val parcelGeom = parcel.geomWKT

                        // Skip empty or null geometries
                        if (parcelGeom.isNullOrBlank()) {
                            Log.w(
                                "LoadMap",
                                "Skipping parcel ${parcel.parcelNo}${parcel.subParcelNo} with empty geometry"
                            )
                            skipCount++
                            continue
                        }

                        // Use improved safe parsing method
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

                // Continue with the rest of your existing code for map setup...
                // [Rest of the existing method remains the same]

                // Union all polygons into a single geometry
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

                // Calculate counts
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
                    binding.tvLcckedParcelCount.text = "($lockedCount)"
                    binding.tvRevisitParcelCount.text = "($rejectedCount)"

                    // Initialize TileManager and continue with map setup
                    val tileManager = TileManager(requireContext())

                    // Define Levels of Detail (LODs)
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

                    val maxZoomLevel = sharedPreferences.getInt(
                        Constants.SHARED_PREF_MAP_MAX_SCALE,
                        Constants.SHARED_PREF_DEFAULT_MAX_SCALE
                    )

                    // Create CustomTileLayer
                    val customTileLayer = CustomTileLayer(
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
                        Toast.makeText(context, "Restart the map screen.", Toast.LENGTH_LONG).show()
                    }

                    progressBar.visibility = View.GONE
                    layoutInfo.visibility = View.VISIBLE
                    layoutRejected.visibility = View.VISIBLE
                    fab.visibility = View.VISIBLE
                    parcelMapview.visibility = View.VISIBLE
                    ivReset.visibility = View.VISIBLE
                    ivSearch.visibility = View.GONE

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
        attr["pkid"] = parcel.pkid  // Critical: Add pkid for proper identification
        attr["parcel_no"] = parcel.parcelNo
        attr["sub_parcel_no"] = parcel.subParcelNo
        attr["surveyStatusCode"] = parcel.surveyStatusCode
        attr["area"] = area
        attr["geomWKT"] = parcel.geomWKT
        attr["centroid"] = parcel.centroid
        attr["isRejected"] = isRejected

        // Create label text that shows parcel number with sub-parcel info
//        val labelText = if (parcel.subParcelNo.isNullOrBlank() || parcel.subParcelNo == "0") {
//            "${parcel.parcelNo}\n${parcel.khewatInfo ?: ""}"
//        } else {
//            "${parcel.parcelNo}${parcel.subParcelNo}\n${parcel.khewatInfo ?: ""}"
//        }
        val labelText = "${parcel.parcelNo}\n${parcel.khewatInfo ?: ""}"

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

        val labelGraphic = Graphic(myPolygonCenterLatLon, polyLabelSymbol)


        val parcelId = parcel.pkid

// Save original symbols
        originalGraphicSymbols[parcelId] = symbol
        originalLabelGraphics[parcelId] = labelGraphic

        val attrLabel = labelGraphic.attributes
        attr["parcel_id"] = parcel.id
        attrLabel["pkid"] = parcel.pkid
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


        // 🔑 Now fetch grower codes asynchronously and update label text
        if (parcel.surveyStatusCode == 2) {
            CoroutineScope(Dispatchers.IO).launch {
                val codes = newSurveyRepository.getGrowerCodesForParcel(parcel.id)
                val growerText = if (codes.isNotEmpty()) {
                    "${codes.joinToString(", ")}"
                } else {
                    "N/A"
                }
                withContext(Dispatchers.Main) {
                    // Update with proper parcel numbering
                    val updatedLabelText = "${parcel.parcelNo}\n${parcel.khewatInfo ?: ""}\n$growerText"
                    (labelGraphic.symbol as? TextSymbol)?.text = updatedLabelText

                    // Force refresh the label
                    surveyLabelGraphics.graphics.remove(labelGraphic)
                    surveyLabelGraphics.graphics.add(labelGraphic)
                }
            }
        }
    }

    // ✅ FIXED: Updated restoreOriginalGraphics to handle split parcels correctly
    private fun restoreOriginalGraphics() {
        Log.d("RESTORE_DEBUG", "Starting graphics restoration...")

        // Clear merge highlights and restore original symbols
        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue

            Log.d("RESTORE_DEBUG", "Processing graphic with parcel_id: $parcelId")

            // Check if we have an original symbol for this ID
            val originalSymbol = originalGraphicSymbols[parcelId]
            if (originalSymbol != null) {
                graphic.symbol = originalSymbol
                Log.d("RESTORE_DEBUG", "Restored symbol for parcel_id: $parcelId")
            } else {
                // ✅ CRITICAL FIX: If no original symbol, determine symbol based on survey status
                val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1
                val correctSymbol = getSymbolForSurveyStatus(surveyStatus)
                graphic.symbol = correctSymbol

                // Update the original symbols map for future use
                originalGraphicSymbols[parcelId] = correctSymbol

                Log.d("RESTORE_DEBUG", "Generated new symbol for parcel_id: $parcelId, status: $surveyStatus")
            }
        }

        // ✅ FIXED: Restore labels properly
        surveyLabelGraphics.graphics.clear()

        // Add back original labels that still exist in the map
        for (graphic in surveyParcelsGraphics.graphics) {
            val parcelId = graphic.attributes["parcel_id"] as? Long ?: continue
            val originalLabel = originalLabelGraphics[parcelId]

            if (originalLabel != null) {
                surveyLabelGraphics.graphics.add(originalLabel)
                Log.d("RESTORE_DEBUG", "Restored label for parcel_id: $parcelId")
            } else {
                // ✅ Create a new label if original doesn't exist (for split parcels)
                val parcelNo = graphic.attributes["parcel_no"]?.toString() ?: ""
                val subParcelNo = graphic.attributes["sub_parcel_no"]?.toString() ?: ""
                val khewatInfo = graphic.attributes["khewatInfo"]?.toString() ?: ""
                val surveyStatus = graphic.attributes["surveyStatusCode"] as? Int ?: 1

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

                val polyLabelSymbol = TextSymbol().apply {
                    text = "$displayText\n$khewatInfo"
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

                // Copy attributes to new label
                val newLabelAttrs = newLabel.attributes
                graphic.attributes.forEach { (key, value) ->
                    newLabelAttrs[key] = value
                }

                surveyLabelGraphics.graphics.add(newLabel)
                originalLabelGraphics[parcelId] = newLabel

                Log.d("RESTORE_DEBUG", "Created new label for parcel_id: $parcelId")
            }
        }

        Log.d("RESTORE_DEBUG", "Graphics restoration completed")
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
            // Reload map with current IDs
            loadMap(ids, ids.isNotEmpty())
        }
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
            if (isSplitMode) {
                handleSplitModeTouch(e)
                return true
            }
            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val graphic = result.graphics[0]

                        // 🔹 Close any previous callout first
                        mCallOut.dismiss()

                        // 🔹 Show survey dialog only once
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

            // Convert touch event to screen point
            val screenPoint = android.graphics.Point(e.x.toInt(), e.y.toInt())

            // Identify which parcel was long-pressed
            val identifyFuture = mMapView.identifyGraphicsOverlayAsync(go, screenPoint, 10.0, false)
            identifyFuture.addDoneListener {
                try {
                    val result = identifyFuture.get()
                    if (result.graphics.isNotEmpty()) {
                        val graphic = result.graphics[0]

                        // Close any existing callout
                        mCallOut.dismiss()

                        // Enter split mode with the selected parcel
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
// Avoid adding identical consecutive points
            if (splitPoints.isNotEmpty() && splitPoints.last() == mapPoint) return

            // Add point to split line
            splitPoints.add(mapPoint)
            // Update split line visual
            updateSplitLineVisual()

            Toast.makeText(
                context,
                "Added point ${splitPoints.size}. Tap to add more points or Apply Split.",
                Toast.LENGTH_SHORT
            ).show()
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
//        val dialogView: View = inflater.inflate(R.layout.cardview_map_info_new, null)

//        val tableLayout = dialogView.findViewById<TableLayout>(R.id.table_selected_parcels)
//        val selectedGraphic = graphicsList.firstOrNull()
//        if (selectedGraphic == null) {
//            Toast.makeText(context, "No graphic data found.", Toast.LENGTH_SHORT).show()
//            return
//        }

        val attr = graphics.attributes

//        val attr = selectedGraphic.attributes
//
//        val parcelInfoList = graphicsList.map { graphic ->
//            val attr = graphic.attributes
//
//            ParcelInfoModel(
//                parcelNo = attr["parcel_no"]?.toString() ?: "N/A",
//                subParcelNo = attr["sub_parcel_no"]?.toString() ?: "N/A",
//                parcelArea = attr["area"]?.toString() ?: "0",
//                parcelId = attr["parcel_id"]?.toString()?.toLongOrNull() ?: 0L,
//                khewatInfo = attr["khewatInfo"]?.toString() ?: "N/A"
//            )
//        }

//        for (parcel in parcelInfoList) {
//            val row = TableRow(requireContext())
//
//            val parcelNoView = TextView(requireContext())
//            parcelNoView.text = parcel.parcelNo
//            parcelNoView.setPadding(8, 8, 8, 8)
//
//            val khewatView = TextView(requireContext())
//            khewatView.text = parcel.khewatInfo
//            khewatView.setPadding(8, 8, 8, 8)
//
//            val areaView = TextView(requireContext())
//            areaView.text = "${parcel.parcelArea} Sq. Ft."
//            areaView.setPadding(8, 8, 8, 8)
//
//            row.addView(parcelNoView)
//            row.addView(khewatView)
//            row.addView(areaView)
//
//            tableLayout.addView(row)
//        }

        tvParcelNo = dialogView.findViewById<TextView>(R.id.tv_parcel_no_value)
        tvParcelNoUni = dialogView.findViewById<TextView>(R.id.tv_parcel_no_uni_value)
        val tvParcelArea = dialogView.findViewById<TextView>(R.id.tv_parcel_area_value)
        val lActionButtons =
            dialogView.findViewById<LinearLayout>(R.id.layout_action_buttons)
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
//        tvParcelArea.text = "${attr["area"]} Sq. Ft."
        val areaSqFt = attr["area"].toString().toDoubleOrNull() ?: 0.0
        val areaAcre = areaSqFt / 43560.0
        tvParcelArea.text = String.format(Locale.US, "%.2f Acres", areaAcre)


        // Ensure MapView has a valid map
        if (binding.parcelMapview.map == null) {
            Toast.makeText(context, "MapView does not have a valid map", Toast.LENGTH_LONG)
                .show()
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
                        putExtra(
                            "parcelOperationValueHi",
                            tvMergeParcelHi.text.toString().trim()
                        )
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
                        putString(
                            "parcelOperationValueHi",
                            tvMergeParcelHi.text.toString().trim()
                        )
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
                        putExtra(
                            "parcelOperationValueHi",
                            tvMergeParcelHi.text.toString().trim()
                        )
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
                    Utility.showProgressAlertDialog(
                        context,
                        "Please wait, fetching location..."
                    )
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

            // Convert the projected point to a geographic point
            val geoPoint =
                GeometryEngine.project(
                    projectedPoint,
                    SpatialReferences.getWgs84()
                ) as Point

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
                GeometryEngine.project(
                    mapPoint,
                    SpatialReferences.getWebMercator()
                ) as Point

            // Convert the projected point to a geographic point
            val geoPoint =
                GeometryEngine.project(
                    projectedPoint,
                    SpatialReferences.getWgs84()
                ) as Point

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

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(requireContext())
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
                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?
                ) {
                }

                override fun onLocationChanged(location: Location) {

                    viewModel.currentLocation =
                        Utility.convertGpsTimeToString(location.time)

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
            Toast.makeText(
                context,
                "Current Location Exception :${e.message}",
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }

}
