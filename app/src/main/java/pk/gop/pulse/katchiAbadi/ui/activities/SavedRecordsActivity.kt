package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.adapter.SavedAdapter
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SavedItemClickListener
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.ActivitySavedRecordsBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails
import pk.gop.pulse.katchiAbadi.presentation.saved.SavedViewModel
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import javax.inject.Inject

@AndroidEntryPoint
class SavedRecordsActivity : AppCompatActivity(),
    SavedItemClickListener {

    private val viewModel: SavedViewModel by viewModels()
    private val savedAdapter = SavedAdapter(this)
    private lateinit var context: Context
    private lateinit var binding: ActivitySavedRecordsBinding

    @Inject
    lateinit var database: AppDatabase

    private var uploadType: String = Constants.UPLOAD_SINGLE_RECORD

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySavedRecordsBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        database.surveyFormDao().liveTotalPendingCount().observe(this) { totalPendingRecords ->
            if (totalPendingRecords > 0) {
                binding.tvDetails.text = "Total Count: $totalPendingRecords"
                binding.tvDetails.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
                binding.noRecordLayout.visibility = View.GONE
                binding.noRecordText.visibility = View.GONE
            } else {
                binding.tvDetails.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
                binding.noRecordLayout.visibility = View.VISIBLE
                binding.noRecordText.visibility = View.VISIBLE
            }
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SavedRecordsActivity)
            adapter = savedAdapter
        }

        viewModel.surveys.observe(this) { surveys ->
            // Update your UI or adapter with the new list of surveys
            savedAdapter.submitList(surveys)
        }

        lifecycleScope.launch {
            viewModel.deleted.collect {
                when (it) {
                    is Resource.Loading -> {
                        Utility.showProgressAlertDialog(context, "Data deleting...")
                    }

                    is Resource.Success -> {
                        Utility.dismissProgressAlertDialog()
                        ToastUtil.showShort(
                            context,
                            "Record Deleted"
                        )
                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()
                        Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                    }

                    else -> {}

                }
            }
        }

        lifecycleScope.launch {
            viewModel.uploaded.collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        if (uploadType.equals(Constants.UPLOAD_SINGLE_RECORD, ignoreCase = true)) {
                            Utility.showProgressAlertDialog(context, "Data uploading...")
                        }
                    }

                    is Resource.Success -> {

                        if (uploadType.equals(Constants.UPLOAD_SINGLE_RECORD, ignoreCase = true)) {
                            Utility.dismissProgressAlertDialog()
                            ToastUtil.showShort(
                                context,
                                "Data Uploaded Successfully"
                            )
                        } else {
                            postAllSavedData()
                        }

                    }

                    is Resource.Error -> {
                        Utility.dismissProgressAlertDialog()

                        result.message?.let { msg ->
                            if (msg.contains("401")) {
                                sharedPreferences.edit()
                                    .putInt(
                                        Constants.SHARED_PREF_LOGIN_STATUS,
                                        Constants.LOGIN_STATUS_INACTIVE
                                    )
                                    .putString(
                                        Constants.SHARED_PREF_USER_NAME,
                                        Constants.SHARED_PREF_DEFAULT_STRING
                                    )
                                    .apply()

                                Intent(this@SavedRecordsActivity, AuthActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    startActivity(this)
                                    finish()
                                }

                                // Session has expired
                                ToastUtil.showShort(
                                    this@SavedRecordsActivity,
                                    "Session expired"
                                )

                            } else {
                                ToastUtil.showShort(
                                    context,
                                    msg
                                )
                            }
                        }

                    }

                    else -> {
                        // Handle other cases if needed
                    }
                }
            }
        }
    }

    override fun onUploadItemClicked(survey: SurveyMergeDetails, uploadButton: Button) {
        // Disable the upload button
        uploadButton.isEnabled = false

        uploadType = Constants.UPLOAD_SINGLE_RECORD

        postRecord(survey, uploadButton)
    }

    private fun postRecord(survey: SurveyMergeDetails, uploadButton: Button?) {
        if (Utility.checkInternetConnection(this)) {
            viewModel.postData(survey, uploadButton)
        } else {
            Utility.dialog(
                context,
                "Please make sure you are connected to the internet and try again.",
                "No Internet!"
            )
            // Re-enable the button if there's no internet connection
            uploadButton?.isEnabled = true
        }
    }

    override fun onDeleteItemClicked(survey: SurveyMergeDetails) {
        val builder = AlertDialog.Builder(this)
            .setTitle("Confirm!")
            .setCancelable(false)
            .setMessage("Are you sure, you want to delete this record.")
            .setPositiveButton("Proceed") { _, _ ->
                viewModel.deleteData(survey)
            }
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

    override fun onViewItemClicked(survey: SurveyMergeDetails) {
        Intent(context, ViewRecordActivity::class.java).apply {
            val bundle = Bundle()
            bundle.putLong("parcelNo", survey.parcelNo)
            bundle.putString("uniqueId", survey.uniqueId)
            putExtra("bundle_data", bundle)
            startActivity(this)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.upload_all_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.upload_all -> {

                if (Utility.checkInternetConnection(this@SavedRecordsActivity)) {

                    lifecycleScope.launch {
                        val totalPendingRecords = database.surveyFormDao().totalPendingCount()
                        if (totalPendingRecords > 0) {

                            val builder = AlertDialog.Builder(this@SavedRecordsActivity)
                                .setTitle("Confirm!")
                                .setCancelable(false)
                                .setMessage("Are you sure, you want to upload all records.")
                                .setPositiveButton("Proceed") { _, _ ->
                                    // Show the progress dialog at the start
                                    Utility.showProgressAlertDialog(context, "Data uploading...")
                                    postAllSavedData()
                                }
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
                        } else {
                            ToastUtil.showShort(
                                context,
                                "No record found."
                            )
                        }
                    }
                } else {

                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun postAllSavedData() {

        lifecycleScope.launch {
            try {
                // Fetch a single survey record from the database
                val survey = database.surveyFormDao().getListSavedRecordsDetailsByLimit()

                // Set the upload type to UPLOAD_ALL
                uploadType = Constants.UPLOAD_ALL

                // Directly upload the record
                postRecord(survey, null)

            } catch (e: Exception) {
                // Handle exceptions during the database operation or upload
                e.printStackTrace()
                // Dismiss the progress dialog in case of an error
                Utility.dismissProgressAlertDialog()
            }
        }
    }


}


