package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.adapter.SurveyActivityAdapter
import pk.gop.pulse.katchiAbadi.common.BaseClass
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Results
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.ActivitySurveyListBinding
import pk.gop.pulse.katchiAbadi.presentation.survey_list.SurveyViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SurveyListActivity : BaseClass(), RadioGroup.OnCheckedChangeListener {


    private val viewModel: SurveyViewModel by viewModels()
    private val surveyAdapter = SurveyActivityAdapter()
    private lateinit var context: Context

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var database: AppDatabase


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySurveyListBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SurveyListActivity)
            adapter = surveyAdapter
        }

        lifecycleScope.launch {
            database.surveyDao().updateAllSurveyStatus()
        }

        // Observe the survey data
        viewModel.surveysState.onEach { result ->
            when (result) {
                is Results.Loading -> {
                    Utility.showProgressAlertDialog(context, "Please wait...")
                }

                is Results.Success -> {
                    // Handle and display survey data in your UI
                    val surveys = result.data

                    val name = sharedPreferences.getString(
                        Constants.SHARED_PREF_USER_SELECTED_AREA_NAME,
                        Constants.SHARED_PREF_DEFAULT_STRING
                    )

                    if (surveys.isEmpty()) {
                        binding.tvHint.visibility = View.VISIBLE

                        if (viewModel.isSearched) {
                            binding.layoutSearch.visibility = View.GONE
                        }
                        binding.recyclerView.visibility = View.GONE

                        binding.tvHeader.text = "$name\n(Total Count: 0)"
                    } else {
                        binding.tvHint.visibility = View.GONE
                        binding.layoutSearch.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.VISIBLE

                        binding.tvHeader.text = "$name\n(Total Count: ${surveys.size})"

                        surveyAdapter.submitList(surveys)
                    }

                    Utility.dismissProgressAlertDialog()
                }

                is Results.Error -> {
                    Utility.dismissProgressAlertDialog()
                    Toast.makeText(context, result.exception.message, Toast.LENGTH_LONG).show()
                }
            }
        }.launchIn(lifecycleScope)

        binding.rgSurveyFilter.setOnCheckedChangeListener(this)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // This method is called before the text is changed
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    val enteredText = "$s"
                    viewModel.getFilterList(enteredText)
                }

            }

            override fun afterTextChanged(s: Editable?) {
                // This method is called after the text has changed
            }
        })

    }

    override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
        when (checkedId) {
            R.id.rb_all -> {
                // "All" RadioButton clicked
                // Implement your logic here
            }

            R.id.rb_available -> {
                // "Available" RadioButton clicked
                // Implement your logic here
            }

            R.id.rb_attached -> {
                // "Attached" RadioButton clicked
                // Implement your logic here
            }
        }
    }
}


