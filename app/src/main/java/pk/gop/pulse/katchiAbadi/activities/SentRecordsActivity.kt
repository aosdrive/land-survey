package pk.gop.pulse.katchiAbadi.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import pk.gop.pulse.katchiAbadi.adapter.SentAdapter
import pk.gop.pulse.katchiAbadi.common.SavedItemClickListener
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.ActivitySavedRecordsBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails
import javax.inject.Inject


@AndroidEntryPoint
class SentRecordsActivity : AppCompatActivity(),
    SavedItemClickListener {

    private val sentAdapter = SentAdapter(this)
    private lateinit var context: Context
    private lateinit var binding: ActivitySavedRecordsBinding

    @Inject
    lateinit var database: AppDatabase

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

        database.surveyFormDao().liveTotalSentCount().observe(this) { totalPendingRecords ->
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
            layoutManager = LinearLayoutManager(this@SentRecordsActivity)
            adapter = sentAdapter
        }

        database.surveyFormDao().getSentRecordsDetails().observe(this) { surveys ->
            // Update your UI or adapter with the new list of surveys
            sentAdapter.submitList(surveys)
        }

    }

    override fun onUploadItemClicked(survey: SurveyMergeDetails, uploadButton: Button) {
//        uploadType = Constants.UPLOAD_SINGLE_RECORD
//
//        if (Utility.checkInternetConnection(this)) {
//            viewModel.postData(survey)
//        } else {
//            Utility.dialog(
//                context,
//                "Please make sure you are connected to the internet and try again.",
//                "No Internet!"
//            )
//        }
    }

    override fun onDeleteItemClicked(survey: SurveyMergeDetails) {
//        val builder = AlertDialog.Builder(this)
//            .setTitle("Confirm!")
//            .setCancelable(false)
//            .setMessage("Are you sure, you want to delete this record.")
//            .setPositiveButton("Proceed") { _, _ ->
//                viewModel.deleteData(survey)
//            }
//            .setNegativeButton("Cancel", null)
//
//        // Create the AlertDialog object
//        val dialog = builder.create()
//        dialog.show()
//
//        // Get the buttons from the dialog
//        val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
//        val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
//
//        // Set button text size and style
//        positiveButton.textSize =
//            16f // Change the size according to your preference
//        positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
//
//        negativeButton.textSize =
//            16f // Change the size according to your preference
//        negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
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

}


