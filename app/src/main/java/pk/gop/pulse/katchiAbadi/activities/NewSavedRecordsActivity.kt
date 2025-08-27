package pk.gop.pulse.katchiAbadi.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.adapter.NewSurveyAdapter
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.ActivitySavedRecordsBinding
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.presentation.survey_list.NewSurveyViewModel

@AndroidEntryPoint
class NewSavedRecordsActivity : AppCompatActivity(), NewSurveyAdapter.OnItemClickListener {

    private val viewModel: NewSurveyViewModel by viewModels()
    private lateinit var binding: ActivitySavedRecordsBinding
    private lateinit var adapter: NewSurveyAdapter

    private var uploadType: String = Constants.UPLOAD_SINGLE_RECORD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Saved Records (New)"

        adapter = NewSurveyAdapter(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        observeViewModel()
    }


    private fun observeViewModel() {
        // Load the surveys with khewat info when activity starts


        lifecycleScope.launch {
            viewModel.surveysWithKhewat.collectLatest { surveysWithKhewat ->
                adapter.submitList(surveysWithKhewat)

                if (surveysWithKhewat.isNotEmpty()) {
                    binding.recyclerView.visibility = android.view.View.VISIBLE
                    binding.noRecordLayout.visibility = android.view.View.GONE
                    binding.tvDetails.text = "Total Count: ${surveysWithKhewat.size}"
                    binding.tvDetails.visibility = android.view.View.VISIBLE
                } else {
                    binding.recyclerView.visibility = android.view.View.GONE
                    binding.noRecordLayout.visibility = android.view.View.VISIBLE
                    binding.tvDetails.visibility = android.view.View.GONE
                }
            }
        }

        // Keep your existing upload and delete observers
        lifecycleScope.launch {
            viewModel.deleted.collect { result ->
                when (result) {
                    is Resource.Loading -> Utility.showProgressAlertDialog(
                        this@NewSavedRecordsActivity,
                        "Deleting..."
                    )

                    is Resource.Success -> {
                        Utility.dismissProgressAlertDialog()
                        Toast.makeText(
                            this@NewSavedRecordsActivity,
                            "Deleted successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()
                        Toast.makeText(
                            this@NewSavedRecordsActivity,
                            result.message ?: "Error deleting",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            viewModel.uploaded.collect { result ->
                when (result) {
                    is Resource.Loading -> Utility.showProgressAlertDialog(
                        this@NewSavedRecordsActivity,
                        "Uploading..."
                    )

                    is Resource.Success -> {
                        Utility.dismissProgressAlertDialog()
                        Toast.makeText(
                            this@NewSavedRecordsActivity,
                            "Uploaded successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (uploadType == Constants.UPLOAD_ALL) {
                            uploadNextSurvey()
                        }
                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()
                        Toast.makeText(
                            this@NewSavedRecordsActivity,
                            result.message ?: "Upload failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e("Error", result.message ?: "Unknown error")
                    }

                    else -> {}
                }
            }
        }
    }

    override fun onUploadClicked(survey: NewSurveyNewEntity) {

        uploadType = Constants.UPLOAD_SINGLE_RECORD
        if (Utility.checkInternetConnection(this)) {
            viewModel.uploadData(this, survey)
        } else {
            Utility.dialog(this, "Please connect to the internet and try again.", "No Internet")
        }
    }

    override fun onItemClicked(survey: NewSurveyNewEntity) {
        logSurveyData(survey)
        val intent = Intent(this, ViewRecordActivity::class.java).apply {
            putExtra("parcelNo", survey.parcelNo)
            putExtra("uniqueId", survey.pkId)
        }
        startActivity(intent)
    }

    override fun onDeleteClicked(survey: NewSurveyNewEntity) {
        AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage("Do you really want to delete this record?")
            .setPositiveButton("Yes") { _: DialogInterface, _: Int ->
                viewModel.deleteData(survey)
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.upload_all_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.upload_all) {
            if (Utility.checkInternetConnection(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Confirm Upload")
                    .setMessage("Upload all pending records?")
                    .setPositiveButton("Yes") { _, _ ->
                        uploadType = Constants.UPLOAD_ALL
                        uploadNextSurvey()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Utility.dialog(this, "Please connect to the internet first.", "No Internet")
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun uploadNextSurvey() {
        lifecycleScope.launch {
            val survey = viewModel.getOnePendingSurvey()
            if (survey != null) {
                viewModel.uploadData(this@NewSavedRecordsActivity, survey)
            } else {
                Utility.dismissProgressAlertDialog()
                Toast.makeText(
                    this@NewSavedRecordsActivity,
                    "All records uploaded",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun logSurveyData(survey: NewSurveyNewEntity) {
        Log.d("SurveyData", "=== SURVEY RECORD DATA ===")
        Log.d("SurveyData", "Primary Key ID: ${survey.pkId}")
        Log.d("SurveyData", "Property Type: ${survey.propertyType}")
        Log.d("SurveyData", "Ownership Status: ${survey.ownershipStatus}")
        Log.d("SurveyData", "Variety: ${survey.variety}")
        Log.d("SurveyData", "Crop Type: ${survey.cropType}")
        Log.d("SurveyData", "Crop: ${survey.crop}")
        Log.d("SurveyData", "Year: ${survey.year}")
        Log.d("SurveyData", "Area: ${survey.area}")
        Log.d("SurveyData", "Is Geometry Correct: ${survey.isGeometryCorrect}")
        Log.d("SurveyData", "Remarks: ${survey.remarks}")
        Log.d("SurveyData", "Mauza ID: ${survey.mauzaId}")
        Log.d("SurveyData", "Area Name: ${survey.areaName}")
        Log.d("SurveyData", "Parcel ID: ${survey.parcelId}")
        Log.d("SurveyData", "Parcel No: ${survey.parcelNo}")
        Log.d("SurveyData", "Sub Parcel No: ${survey.subParcelNo}")
        Log.d("SurveyData", "Status Bit: ${survey.statusBit}")
        Log.d("SurveyData", "=== END SURVEY DATA ===")
    }
}
