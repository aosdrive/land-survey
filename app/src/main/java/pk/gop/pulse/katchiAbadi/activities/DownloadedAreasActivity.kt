package pk.gop.pulse.katchiAbadi.activities

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.adapter.DownloadedAreasAdapter
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.DownloadType
import pk.gop.pulse.katchiAbadi.data.local.ActiveParcelDao
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.ActivityDownloadedAreasBinding
import java.io.File
import javax.inject.Inject
@AndroidEntryPoint
class DownloadedAreasActivity : AppCompatActivity(), DownloadedAreasItemClickListener {

    private val viewModel: ParcelViewModel by viewModels()
    private lateinit var downloadedAreasAdapter: DownloadedAreasAdapter
    private lateinit var binding: ActivityDownloadedAreasBinding
    private lateinit var context: Context

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadedAreasBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this

        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        downloadedAreasAdapter = DownloadedAreasAdapter(this)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DownloadedAreasActivity)
            adapter = downloadedAreasAdapter
        }

        lifecycleScope.launch {
            val mauzaId = sharedPreferences.getLong(
                Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID,
                Constants.SHARED_PREF_DEFAULT_INT.toLong()
            )

            val areaName = sharedPreferences.getString(
                Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name,
                Constants.SHARED_PREF_DEFAULT_STRING
            ).orEmpty()
            val totalPendingRecords = withContext(Dispatchers.IO) {
//                database.activeParcelDao().getDistinctParcelCount()
                database.activeParcelDao().getParcelsCountByMauzaAndArea(mauzaId,areaName )
            }

            if (totalPendingRecords > 0) {
                val allowedDownloadableAreas = sharedPreferences.getInt(
                    Constants.SHARED_PREF_ALLOWED_DOWNLOADABLE_AREAS,
                    Constants.SHARED_PREF_DEFAULT_DOWNLOADABLE_AREAS
                )
                binding.tvHeader.text =
                    "Downloaded Areas: $totalPendingRecords/$allowedDownloadableAreas"
                binding.tvHeader.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
                binding.noRecordLayout.visibility = View.GONE
                binding.noRecordText.visibility = View.GONE
            } else {
                binding.tvHeader.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
                binding.noRecordLayout.visibility = View.VISIBLE
                binding.noRecordText.visibility = View.VISIBLE

                sharedPreferences.edit()
                    .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, Constants.SHARED_PREF_DEFAULT_INT.toLong())
                    .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, Constants.SHARED_PREF_DEFAULT_STRING)
                    .putLong(Constants.SHARED_PREF_USER_SELECTED_AREA_ID, Constants.SHARED_PREF_DEFAULT_INT.toLong())
                    .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, Constants.SHARED_PREF_DEFAULT_STRING)
                    .apply()

                File(context.filesDir, "MapTiles").deleteRecursively()
            }

            // Load downloaded areas
            val downloadedAreaModels = withContext(Dispatchers.IO) {
                viewModel.getDownloadedAreas()
            }

//            val selectedItemId = getSelectedItem()
//            downloadedAreaModels.forEach {
//                it.isSelected = it.mauzaId == selectedItemId
//            }

            val selectedItemId = getSelectedMauzaId()
            val selectedAreaName = getSelectedAreaName()
            downloadedAreaModels.forEach {
                it.isSelected = it.mauzaId == selectedItemId && it.areaAssigned == selectedAreaName
            }


            downloadedAreasAdapter.setData(downloadedAreaModels)
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back or finish the activity
//                onBackPressedDispatcher.onBackPressed()
                startActivity(Intent(this, MenuActivity::class.java))
                finish()

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onDeleteItemClicked(survey: MauzaAreaEntry) {
        val builder = AlertDialog.Builder(this)
            .setTitle("Confirm!")
            .setCancelable(false)
            .setMessage("Are you sure, you want to delete this record?")
            .setPositiveButton("Proceed") { _, _ ->
                lifecycleScope.launch {
                    database.activeParcelDao().deleteParcelsByMauzaAndArea(survey.mauzaId, survey.areaAssigned)
                    //database.surveyDao().deleteAllSurveysWrtArea(survey.mauzaId)

                    val areaName = survey.areaAssigned
                    val mauzaId = survey.mauzaId
                    val sanitizedAreaName = areaName.replace(Regex("[^a-zA-Z0-9_]"), "_")
                    val folderKey = "mauza_${mauzaId}_area_${sanitizedAreaName}"

                    val mapFolder = when (Constants.MAP_DOWNLOAD_TYPE) {
                        DownloadType.TPK -> "MapTpk/${survey.mauzaId}"
                        DownloadType.TILES -> "MapTiles/${folderKey}"
                    }
                    File(context.filesDir, mapFolder).deleteRecursively()

                    downloadedAreasAdapter.deleteItem(survey, sharedPreferences)
                }
            }
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            textSize = 16f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
    }

    override fun onSelectItemClicked(survey: MauzaAreaEntry) {
        saveSelectedItem(survey)
    }

//    private fun getSelectedItem(): Long {
//        return sharedPreferences.getLong(
//            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
//            Constants.SHARED_PREF_DEFAULT_INT.toLong()
//        )
//    }

    private fun getSelectedMauzaId(): Long {
        return sharedPreferences.getLong(
            Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID,
            Constants.SHARED_PREF_DEFAULT_INT.toLong()
        )
    }

    private fun getSelectedAreaName(): String {
        return sharedPreferences.getString(
            Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
            Constants.SHARED_PREF_DEFAULT_STRING
        ) ?: ""
    }


    private fun saveSelectedItem(survey: MauzaAreaEntry) {
        sharedPreferences.edit()
            .putLong(Constants.SHARED_PREF_USER_DOWNLOADED_MAUZA_ID, survey.mauzaId)
            .putLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, survey.mauzaId)
            .putString(Constants.SHARED_PREF_USER_SELECTED_MAUZA_NAME, survey.mauzaName)
            .putLong(Constants.SHARED_PREF_USER_SELECTED_AREA_ID, survey.mauzaId)
            .putString(Constants.SHARED_PREF_USER_DOWNLOADED_AREA_Name, survey.areaAssigned)
            .putString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, survey.areaAssigned)
            .apply()



    }

}



interface DownloadedAreasItemClickListener {
    fun onDeleteItemClicked(survey: MauzaAreaEntry)
    fun onSelectItemClicked(survey: MauzaAreaEntry)
}



data class MauzaAreaEntry(
    val mauzaId: Long,
    val mauzaName: String,
    val areaAssigned: String,
    var isSelected: Boolean = false
)


@HiltViewModel
class ParcelViewModel @Inject constructor(
    private val database: AppDatabase
) : ViewModel() {

    suspend fun getDownloadedAreas(): List<MauzaAreaEntry> {
        return database.activeParcelDao().getDistinctDownloadedAreas()
            .map {
                MauzaAreaEntry(
                    mauzaId = it.mauzaId,
                    mauzaName = it.mauzaName,
                    areaAssigned = it.areaAssigned,
                    isSelected = false
                )
            }
    }
}
