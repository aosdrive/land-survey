package pk.gop.pulse.katchiAbadi.ui.activities

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.esri.arcgisruntime.geometry.AreaUnit
import com.esri.arcgisruntime.geometry.AreaUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.Polygon
import com.esri.arcgisruntime.geometry.SpatialReferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.adapter.ViewRecordAdapter
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.common.ViewRecordClickListener
import pk.gop.pulse.katchiAbadi.databinding.ActivityViewRecordBinding
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonPost
import pk.gop.pulse.katchiAbadi.presentation.saved.SavedViewModel
import pk.gop.pulse.katchiAbadi.presentation.survey_list.NewSurveyViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class ViewRecordActivity : AppCompatActivity(), ViewRecordClickListener {

    private val savedViewModel: SavedViewModel by viewModels()
    private val newSurveyViewModel: NewSurveyViewModel by viewModels()

    private val savedAdapter = ViewRecordAdapter(this)

    private lateinit var context: Context
    private lateinit var binding: ActivityViewRecordBinding

    private var isNewSurvey = false

    private val wgs84 by lazy { SpatialReferences.getWgs84() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewRecordBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this

        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        val parcelNo = intent.getStringExtra("parcelNo")
        val uniqueId = intent.getLongExtra("uniqueId", 0L)

        if (parcelNo != null && uniqueId > 0) {
            // ===== NEW survey flow =====
            isNewSurvey = true
            binding.scrollContent.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.tvDetails.visibility = View.GONE
            loadNewSurveyData(uniqueId)
        } else {
            // ===== OLD survey flow (unchanged) =====
            isNewSurvey = false
            binding.scrollContent.visibility = View.GONE
            binding.tvDetails.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.VISIBLE
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = savedAdapter

            val bundle = intent?.getBundleExtra("bundle_data")
            if (bundle != null) {
                val oldParcelNo = bundle.getLong("parcelNo")
                val oldUniqueId = bundle.getString("uniqueId")
                binding.tvDetails.text = "Parcel No: $oldParcelNo"
                oldUniqueId?.let { savedViewModel.viewRecord(oldParcelNo, it) }
            }
            observeOldViewModel()
        }
    }

    // =========================================================
    //  NEW SURVEY DISPLAY (single card)
    // =========================================================
    private fun loadNewSurveyData(uniqueId: Long) {
        lifecycleScope.launch {
            try {
                val survey = newSurveyViewModel.getSurveyById(uniqueId)
                if (survey == null) {
                    showEmpty()
                    return@launch
                }

                bindHeader(survey)
                bindMetrics(survey)
                bindSurveyDetails(survey)
                loadOwners(survey.pkId)
                loadImages(survey)

                binding.noRecordText.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("ViewRecord", "Error loading new survey: ${e.message}", e)
                ToastUtil.showShort(context, "Error loading data: ${e.message}")
            }
        }
    }

    private fun showEmpty() {
        binding.scrollContent.visibility = View.GONE
        binding.noRecordText.visibility = View.VISIBLE
        binding.noRecordText.text = "No record found"
    }

    private fun bindHeader(survey: NewSurveyNewEntity) {
        val sub = survey.subParcelNo
        val title = if (sub.isNotBlank() && sub != "0") {
            "${survey.parcelNo} / $sub"
        } else {
            survey.parcelNo
        }
        binding.tvParcelNo.text = title

        val op = survey.parcelOperation
        binding.tvStatus.text = if (op.isNotBlank()) op else "Surveyed"
    }

    private fun bindMetrics(survey: NewSurveyNewEntity) {
        binding.tvYear.text = survey.year.ifBlank { "—" }

        // Area: merge ho to total, warna single
        binding.tvArea.text = "Calculating..."
        lifecycleScope.launch {
            val areaText = calculateAreaForSurvey(survey)
            binding.tvArea.text = areaText
        }
    }

    // ✅ NAYA: survey ke operation ke hisaab se area (merge = total)
    private suspend fun calculateAreaForSurvey(survey: NewSurveyNewEntity): String {
        return try {
            // Saare parcel IDs jama karein jinka area add karna hai
            val parcelIds = mutableListOf<Long>()
            parcelIds.add(survey.parcelId)

            if (survey.parcelOperation.equals("Merge", ignoreCase = true) &&
                survey.parcelOperationValue.isNotBlank()
            ) {
                val mergedIds = survey.parcelOperationValue
                    .split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                // duplicate na ho (main parcel pehle se add hai)
                mergedIds.forEach { if (it !in parcelIds) parcelIds.add(it) }
            }

            // Har parcel ka area nikaal kar jamaa karein
            var totalAcres = 0.0
            var anyValid = false
            for (id in parcelIds) {
                val acres = areaAcresForParcel(id)
                if (acres != null) {
                    totalAcres += acres
                    anyValid = true
                }
            }

            if (!anyValid) "—" else String.format(Locale.US, "%.2f Acres", totalAcres)
        } catch (e: Exception) {
            Log.e("ViewRecord", "Merge area calc error: ${e.message}", e)
            "—"
        }
    }


    // ✅ NAYA: ek parcel ka area Acres me (Double?) — null agar geometry na mile
    private suspend fun areaAcresForParcel(parcelId: Long): Double? {
        return try {
            val parcel = newSurveyViewModel.getActiveParcelById(parcelId)
            val geom = parcel?.geomWKT
            if (geom.isNullOrBlank()) return null

            withContext(Dispatchers.Default) {
                val polygon: Polygon? = when {
                    geom.contains("MULTIPOLYGON") ->
                        Utility.getMultiPolygonFromString(geom, wgs84)?.firstOrNull()
                    geom.contains("POLYGON ((") ->
                        Utility.getPolygonFromString(geom, wgs84)
                    geom.contains("POLYGON") ->
                        Utility.getPolyFromString(geom, wgs84)
                    else -> null
                }

                if (polygon == null || polygon.isEmpty) {
                    null
                } else {
                    val sqFt = GeometryEngine.areaGeodetic(
                        polygon,
                        AreaUnit(AreaUnitId.SQUARE_FEET),
                        GeodeticCurveType.NORMAL_SECTION
                    )
                    sqFt / 43560.0
                }
            }
        } catch (e: Exception) {
            Log.e("ViewRecord", "Area calc error for parcel $parcelId: ${e.message}", e)
            null
        }
    }

    private suspend fun calculateArea(parcelId: Long): String {
        return try {
            val parcel = newSurveyViewModel.getActiveParcelById(parcelId)
            val geom = parcel?.geomWKT
            if (geom.isNullOrBlank()) return "—"

            withContext(Dispatchers.Default) {
                val polygon: Polygon? = when {
                    geom.contains("MULTIPOLYGON") ->
                        Utility.getMultiPolygonFromString(geom, wgs84)?.firstOrNull()
                    geom.contains("POLYGON ((") ->
                        Utility.getPolygonFromString(geom, wgs84)
                    geom.contains("POLYGON") ->
                        Utility.getPolyFromString(geom, wgs84)
                    else -> null
                }

                if (polygon == null || polygon.isEmpty) {
                    "—"
                } else {
                    val sqFt = GeometryEngine.areaGeodetic(
                        polygon,
                        AreaUnit(AreaUnitId.SQUARE_FEET),
                        GeodeticCurveType.NORMAL_SECTION
                    )
                    val acres = sqFt / 43560.0
                    String.format(Locale.US, "%.2f Acres", acres)
                }
            }
        } catch (e: Exception) {
            Log.e("ViewRecord", "Area calc error: ${e.message}", e)
            "—"
        }
    }

    private fun bindSurveyDetails(survey: NewSurveyNewEntity) {
        val rows = linkedMapOf<String, String?>(
            "Property Type" to survey.propertyType,
            "Parcel Ownership" to survey.ownershipStatus,
            "Crop" to survey.crop,
            "Crop Type" to survey.cropType,
            "Variety" to survey.variety,
            "Sowing Date" to survey.sowingDate,
            "Remarks" to survey.remarks
        )
        addKvRows(binding.containerSurvey, rows)
    }

    private fun loadOwners(surveyId: Long) {
        lifecycleScope.launch {
            try {
                val persons = newSurveyViewModel.getPersonsForSurvey(surveyId)
                binding.containerOwners.removeAllViews()
                if (persons.isEmpty()) {
                    binding.tvOwnersLabel.visibility = View.GONE
                    return@launch
                }
                binding.tvOwnersLabel.text =
                    if (persons.size > 1) "Owner Information (${persons.size})" else "Owner Information"

                persons.forEach { p -> addOwnerBlock(p) }
            } catch (e: Exception) {
                Log.e("ViewRecord", "Error loading owners: ${e.message}", e)
                binding.tvOwnersLabel.visibility = View.GONE
            }
        }
    }

    private fun addOwnerBlock(p: SurveyPersonPost) {
        val block = layoutInflater.inflate(R.layout.item_owner_block, binding.containerOwners, false)

        val fullName = listOf(p.firstName, p.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Unnamed" }

        block.findViewById<TextView>(R.id.tvOwnerName).text = fullName

        // Avatar initials
        val initials = buildString {
            p.firstName.firstOrNull()?.let { append(it.uppercaseChar()) }
            p.lastName.firstOrNull()?.let { append(it.uppercaseChar()) }
        }.ifBlank { "?" }
        block.findViewById<TextView>(R.id.tvAvatar).text = initials

        // Subtitle: ownership type / gender
        val subParts = listOf(p.ownershipType, p.gender).filter { it.isNotBlank() }
        val subView = block.findViewById<TextView>(R.id.tvOwnerSub)
        if (subParts.isEmpty()) {
            subView.visibility = View.GONE
        } else {
            subView.text = subParts.joinToString(" • ")
        }

        val container = block.findViewById<LinearLayout>(R.id.containerOwnerFields)
        val rows = linkedMapOf<String, String?>(
            "Last Name" to p.lastName,
            "Relation" to p.relation,
            "CNIC" to p.nic,
            "Mobile Number" to p.mobile,
            "Address" to p.address,
            "Ownership Type" to p.ownershipType,
            "Gender" to p.gender,
            "Grower Code" to p.growerCode
        )
        addKvRows(container, rows)

        binding.containerOwners.addView(block)
    }

    private fun loadImages(survey: NewSurveyNewEntity) {
        lifecycleScope.launch {
            try {
                val images = newSurveyViewModel.getImagesBySurvey(survey.pkId)

                // Parcel pics = type == "Property"
                val parcelPics = images.filter {
                    it.type?.equals("Property", ignoreCase = true) == true
                }
                // Agar koi "Property" type na ho, to saari images dikha do
                val showPics = if (parcelPics.isNotEmpty()) parcelPics else images

                bindParcelPics(showPics)
                bindFarmerPic(survey.farmerProfilePath)

                val anyImage = showPics.isNotEmpty() || !survey.farmerProfilePath.isNullOrBlank()
                binding.tvImagesLabel.visibility = if (anyImage) View.VISIBLE else View.GONE
                binding.dividerImages.visibility = if (anyImage) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e("ViewRecord", "Error loading images: ${e.message}", e)
                binding.tvImagesLabel.visibility = View.GONE
                binding.dividerImages.visibility = View.GONE
            }
        }
    }

    private fun bindParcelPics(pics: List<SurveyImage>) {
        binding.rowParcelPics.removeAllViews()
        if (pics.isEmpty()) {
            binding.tvParcelPicLabel.visibility = View.GONE
            binding.scrollParcelPics.visibility = View.GONE
            return
        }
        binding.tvParcelPicLabel.visibility = View.VISIBLE
        binding.scrollParcelPics.visibility = View.VISIBLE

        pics.forEach { img ->
            val v = layoutInflater.inflate(R.layout.item_thumb, binding.rowParcelPics, false)
            val iv = v.findViewById<ImageView>(R.id.ivThumb)
            v.findViewById<TextView>(R.id.tvCaption).text = img.type ?: "Parcel Pic"
            loadThumbInto(iv, img.uri)
            v.setOnClickListener { showFullImage(img.uri) }
            binding.rowParcelPics.addView(v)
        }
    }

    private fun bindFarmerPic(path: String?) {
        if (path.isNullOrBlank() || !File(path).exists()) {
            binding.tvFarmerPicLabel.visibility = View.GONE
            binding.ivFarmerPic.visibility = View.GONE
            return
        }
        binding.tvFarmerPicLabel.visibility = View.VISIBLE
        binding.ivFarmerPic.visibility = View.VISIBLE
        loadThumbInto(binding.ivFarmerPic, path)
        binding.ivFarmerPic.setOnClickListener { showFullImage(path) }
    }

    // ---- helpers ----
    private fun addKvRows(container: LinearLayout, rows: Map<String, String?>) {
        container.removeAllViews()
        rows.forEach { (key, value) ->
            val v = value?.trim().orEmpty()
            if (v.isEmpty()) return@forEach
            val row = layoutInflater.inflate(R.layout.item_kv_row, container, false)
            row.findViewById<TextView>(R.id.tvKey).text = key
            row.findViewById<TextView>(R.id.tvValue).text = v
            container.addView(row)
        }
    }

    private fun loadThumbInto(iv: ImageView, path: String?) {
        if (path.isNullOrBlank()) return
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    when {
                        path.startsWith("content") ->
                            contentResolver.openInputStream(Uri.parse(path))
                                ?.use { BitmapFactory.decodeStream(it, null, opts) }
                        else -> BitmapFactory.decodeFile(path.removePrefix("file://"), opts)
                    }
                } catch (e: Exception) { null }
            }
            if (bmp != null) iv.setImageBitmap(bmp)
        }
    }

    private fun showFullImage(path: String?) {
        if (path.isNullOrBlank()) return
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    when {
                        path.startsWith("content") ->
                            contentResolver.openInputStream(Uri.parse(path))
                                ?.use { BitmapFactory.decodeStream(it) }
                        else -> BitmapFactory.decodeFile(path.removePrefix("file://"))
                    }
                } catch (e: Exception) { null }
            } ?: run {
                ToastUtil.showShort(context, "Image not available")
                return@launch
            }
            val iv = ImageView(context).apply {
                setImageBitmap(bmp)
                adjustViewBounds = true
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                setContentView(iv)
                iv.setOnClickListener { dismiss() }
                show()
            }
        }
    }

    // =========================================================
    //  OLD SURVEY (unchanged behaviour)
    // =========================================================
    private fun observeOldViewModel() {
        lifecycleScope.launch {
            savedViewModel.viewRecord.collect {
                when (it) {
                    is Resource.Loading ->
                        Utility.showProgressAlertDialog(context, "Data Loading...")

                    is Resource.Success -> {
                        Utility.dismissProgressAlertDialog()
                        if (it.data != null) {
                            savedAdapter.submitList(it.data)
                            binding.recyclerView.visibility = View.VISIBLE
                            binding.noRecordText.visibility = View.GONE
                        } else {
                            binding.recyclerView.visibility = View.GONE
                            binding.noRecordText.visibility = View.VISIBLE
                        }
                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()
                        if (it.message == "No record found") {
                            binding.recyclerView.visibility = View.GONE
                            binding.noRecordText.visibility = View.VISIBLE
                        } else {
                            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    override fun onViewImagesClicked(survey: SurveyFormEntity) {
        if (survey.picturesList.isNotEmpty()) {
            try {
                val builder = StrictMode.VmPolicy.Builder()
                StrictMode.setVmPolicy(builder.build())
                val imagePathList = ArrayList<pk.gop.pulse.katchiAbadi.common.ImageDetails>()

                val jsonObject = JSONObject(survey.picturesList)
                val jsonArray = jsonObject.getJSONArray("pictures")

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val path = item.getString("path")
                    var type = item.getString("picture_type")
                    if (type == "Other") {
                        val otherType = item.getString("picture_other_type")
                        type += " ($otherType)"
                    }
                    imagePathList.add(
                        pk.gop.pulse.katchiAbadi.common.ImageDetails(type = type, path = path)
                    )
                }

                val intent = Intent(this, OfflineViewpagerActivity::class.java)
                val bundle = Bundle()
                bundle.putSerializable("imagePathList", imagePathList)
                intent.putExtra("bundle_data", bundle)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtil.showShort(context, "Error while showing images")
            }
        } else {
            Utility.dialog(context, "Images cannot be viewed.", "Alert!")
        }
    }
}