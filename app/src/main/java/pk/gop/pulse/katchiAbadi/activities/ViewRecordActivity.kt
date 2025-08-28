package pk.gop.pulse.katchiAbadi.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject
import pk.gop.pulse.katchiAbadi.adapter.ViewRecordAdapter
import pk.gop.pulse.katchiAbadi.adapter.NewSurveyViewRecordAdapter
import pk.gop.pulse.katchiAbadi.common.ImageDetails
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.common.ViewRecordClickListener
import pk.gop.pulse.katchiAbadi.databinding.ActivityViewRecordBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.presentation.saved.SavedViewModel
import pk.gop.pulse.katchiAbadi.presentation.survey_list.NewSurveyViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil

@AndroidEntryPoint
class ViewRecordActivity : AppCompatActivity(), ViewRecordClickListener {

    private val savedViewModel: SavedViewModel by viewModels()
    private val newSurveyViewModel: NewSurveyViewModel by viewModels()

    private val savedAdapter = ViewRecordAdapter(this)
    private val newSurveyAdapter = NewSurveyViewRecordAdapter()

    private lateinit var context: Context
    private lateinit var binding: ActivityViewRecordBinding

    private var isNewSurvey = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewRecordBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this

        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        // Check if it's coming from NewSavedRecordsActivity
        val parcelNo = intent.getStringExtra("parcelNo")
        val uniqueId = intent.getLongExtra("uniqueId", 0L)

        if (parcelNo != null && uniqueId > 0) {
            // New survey data
            isNewSurvey = true
            binding.tvDetails.text = "Parcel No: $parcelNo"
            loadNewSurveyData(uniqueId)
        } else {
            // Old survey data (existing functionality)
            val bundle = intent?.getBundleExtra("bundle_data")
            if (bundle != null) {
                val oldParcelNo = bundle.getLong("parcelNo")
                val oldUniqueId = bundle.getString("uniqueId")

                binding.tvDetails.text = "Parcel No: $oldParcelNo"
                oldUniqueId?.let { savedViewModel.viewRecord(oldParcelNo, it) }
            }
        }

        setupRecyclerView()
        observeViewModels()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this@ViewRecordActivity)

        if (isNewSurvey) {
            binding.recyclerView.adapter = newSurveyAdapter
        } else {
            binding.recyclerView.adapter = savedAdapter
        }
    }

    private fun loadNewSurveyData(uniqueId: Long) {
        lifecycleScope.launch {
            try {
                val survey = newSurveyViewModel.getSurveyById(uniqueId)
                if (survey != null) {
                    displayNewSurveyData(survey)
                } else {
                    binding.recyclerView.visibility = View.GONE
                    binding.noRecordText.visibility = View.VISIBLE
                    binding.noRecordText.text = "No record found"
                }
            } catch (e: Exception) {
                ToastUtil.showShort(
                    context,
                    "Error loading data: ${e.message}"
                )
            }
        }
    }

    private fun displayNewSurveyData(survey: NewSurveyNewEntity) {
        // Convert NewSurveyNewEntity to display format
        val displayData = mutableListOf<Pair<String, String>>()
//        displayData.add("Survey Information" to "")
        displayData.add("Primary Key ID" to survey.pkId.toString())
        displayData.add("Property Type" to survey.propertyType)
        displayData.add("Ownership Status" to survey.ownershipStatus)
        displayData.add("Variety" to survey.variety)
        displayData.add("Crop Type" to survey.cropType)
        displayData.add("Crop" to survey.crop)
        displayData.add("Year" to survey.year)
        displayData.add("Area" to survey.area)
//        displayData.add("Is Geometry Correct" to survey.isGeometryCorrect.toString())
//        displayData.add("Remarks" to survey.remarks)
//        displayData.add("Mauza ID" to survey.mauzaId.toString())
//        displayData.add("Area Name" to survey.areaName)
//        displayData.add("Parcel ID" to survey.parcelId.toString())
//        displayData.add("Parcel No" to survey.parcelNo)
//        displayData.add("Sub Parcel No" to survey.subParcelNo)
//        displayData.add("Status Bit" to survey.statusBit.toString())


        // 🔹 Fetch persons for this survey
        lifecycleScope.launch {
            try {
                val persons =
                    newSurveyViewModel.getPersonsForSurvey(survey.pkId) // <-- implement this in ViewModel
                Log.e("PersonData", "displayNewSurveyData: $persons")
                if (persons.isNotEmpty()) {
                    persons.forEachIndexed { index, person ->
//                        displayData.add("=== Persons Information ===" to "")
                        displayData.add("First Name" to person.firstName)
                        displayData.add("Last Name" to person.lastName)
                        displayData.add("Gender" to person.gender)
                        displayData.add("Relation" to person.relation)
                        displayData.add("Religion" to person.religion)
                        displayData.add("Mobile" to person.mobile)
                        displayData.add("NIC" to person.nic)
                        displayData.add("Grower Code" to person.growerCode)
                        displayData.add("Person Area" to person.personArea)
                        displayData.add("Ownership Type" to person.ownershipType)
                        displayData.add("Mauza Name" to person.mauzaName)
                    }
                }
                newSurveyAdapter.submitList(displayData)
                binding.recyclerView.visibility = View.VISIBLE
                binding.noRecordText.visibility = View.GONE
            } catch (e: Exception) {
                ToastUtil.showShort(
                    context,
                    "Error loading persons: ${e.message}"
                )
            }
        }
    }

    private fun observeViewModels() {
        // Observe old survey data
        lifecycleScope.launch {
            savedViewModel.viewRecord.collect {
                when (it) {
                    is Resource.Loading -> {
                        Utility.showProgressAlertDialog(context, "Data Loading...")
                    }

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
                val imagePathList = ArrayList<ImageDetails>()

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

                    val image = ImageDetails(type = type, path = path)
                    imagePathList.add(image)
                }

                val intent = Intent(this, OfflineViewpagerActivity::class.java)
                val bundle = Bundle()
                bundle.putSerializable("imagePathList", imagePathList)
                intent.putExtra("bundle_data", bundle)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtil.showShort(
                    context,
                    "Error while showing images"
                )
            }
        } else {
            Utility.dialog(context, "Images cannot be viewed.", "Alert!")
        }
    }
}