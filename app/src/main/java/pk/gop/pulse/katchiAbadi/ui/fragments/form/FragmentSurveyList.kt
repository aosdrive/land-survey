package pk.gop.pulse.katchiAbadi.ui.fragments.form

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.ui.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.adapter.SurveyAdapter
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Results
import pk.gop.pulse.katchiAbadi.common.SurveyItemClickListener
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.FragmentSurveyListBinding
import pk.gop.pulse.katchiAbadi.domain.model.SurveyEntity
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class FragmentSurveyList : Fragment(), RadioGroup.OnCheckedChangeListener, SurveyItemClickListener {

    private val viewModel: SharedFormViewModel by activityViewModels()
    private val surveyAdapter = SurveyAdapter(this)
    private lateinit var context: Context

    private var _binding: FragmentSurveyListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSurveyListBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onResume() {
        super.onResume()
        context = requireContext()

        binding.apply {

            etSearch.setText("")

            if (viewModel.parcelOperation == "Split") {
                if(viewModel.isRevisit == 1){
                    tvDetails.text =
                        "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter})"
                }else{
                    tvDetails.text =
                        "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter}/${viewModel.parcelOperationValue})"
                }
            } else {
                tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
            }

            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = surveyAdapter
            }

            rgSurveyFilter.setOnCheckedChangeListener(this@FragmentSurveyList)

            etSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s != null) {
                        val enteredText = s.toString()
                        viewModel.getFilterList(enteredText)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            btnAddNewUnit.setOnClickListener {

                val builder = AlertDialog.Builder(context).setTitle("Confirm!").setCancelable(false)
                    .setMessage("Are you sure, you want to add occupant?")
                    .setPositiveButton("Yes") { _, _ ->
                        viewModel.surveyStatus = Constants.Survey_New_Unit
                        navigateToNextScreen()
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
        }

        viewModel.fetchSurveys()

        // Observe the survey data
        viewModel.surveysState.onEach { result ->
            when (result) {
                is Results.Loading -> {
                    Utility.showProgressAlertDialog(context, "Please wait...")
                }

                is Results.Success -> {
                    // Handle and display survey data in your UI
                    val surveys = result.data

                    if (surveys.isEmpty()) {
                        binding.tvHint.visibility = View.VISIBLE
                        if (viewModel.isSearched) {
                            binding.layoutSearch.visibility = View.GONE
                        }
                        binding.recyclerView.visibility = View.GONE

                        binding.tvHeader.text = "Owner List\n(Total Count: 0)"
                    } else {
                        binding.tvHint.visibility = View.GONE
                        binding.layoutSearch.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.VISIBLE

                        binding.tvHeader.text = "Owner List\n(Total Count: ${surveys.size})"

                        surveyAdapter.submitList(surveys)
                    }

                    Utility.dismissProgressAlertDialog()
                }

                is Results.Error -> {
                    Utility.dismissProgressAlertDialog()
                    Toast.makeText(context, result.exception.message, Toast.LENGTH_LONG).show()
                }
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // Rest of your methods (onCheckedChanged, onSurveyItemClicked, onSaveInstanceState, onRestoreInstanceState) remain the same.

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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

    override fun onSurveyItemClicked(survey: SurveyEntity) {

        val propertyNumber = survey.propertyNo

        val partsName = survey.name.split(":$$:")

        val name = if (partsName.size == 1) {
            survey.name
                .split(":$$:")[0]
                .trim()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("[,;]+"), ",")
                .replace(Regex("[^a-zA-Z,.]"), " ")
                .replace(Regex(" ,"), ",")

        } else {
            survey.name
                .split(":$$:")
                .joinToString(", ") {
                    it.trim()
                        .replace(Regex("\\s+"), " ")
                        .replace(Regex("[,;]+"), ",")
                        .replace(Regex("[^a-zA-Z,.]"), "")
                        .replace(Regex(" ,"), ",")
                }
        }

        val fName = survey.fname
            .split(":$$:")[0]
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[,;]+"), ",")
            .replace(Regex("[^a-zA-Z,.]"), " ")
            .replace(Regex(" ,"), ",")

        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.survey_list_alert_dialog, null)

        val tvSurveyUnitNumber = dialogView.findViewById<TextView>(R.id.tv_survey_unit_number)
        val tvOwnerName = dialogView.findViewById<TextView>(R.id.tv_owner_name)
        val tvOwnerFatherName = dialogView.findViewById<TextView>(R.id.tv_owner_father_name)

        tvSurveyUnitNumber.text = propertyNumber
        tvOwnerName.text = name
        tvOwnerFatherName.text = fName

        val builder = AlertDialog.Builder(context).setView(dialogView).setTitle("Confirm!")
            .setCancelable(false)
            .setPositiveButton("Proceed") { _, _ ->

                viewModel.surveyStatus = Constants.Survey_SAME_Unit
                viewModel.surveyId = survey.propertyId
                survey.pkId?.let { viewModel.surveyPkId = it }

                viewModel.propertyNumber = propertyNumber.takeIf { it != "NA" } ?: ""

//                viewModel.name = name
//                viewModel.fname = fName

//                val firstCnic = survey.cnic.split(":$$:")[0].replace("-", "").replace("_", "")
//                viewModel.cnic = firstCnic.takeIf { it != "NA" && it.length == 13 } ?: ""

//                viewModel.gender = when (survey.gender.split(":$$:")[0]) {
//                    "1" -> "Male"
//                    "0" -> "Female"
//                    else -> "Transgender"
//                }

                viewModel.area = survey.area
//                viewModel.mobile = ""

                navigateToNextScreen()
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

    private fun navigateToNextScreen() {
        if (isAdded) {
            findNavController().navigate(R.id.action_fragmentSurveyList_to_fragmentFormPropertyDetails)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.parcelOperation != "Split") {
                    val builder = AlertDialog.Builder(requireActivity())
                        .setTitle("Alert!")
                        .setCancelable(false)
                        .setMessage("Are you sure you want to exit the form? Please note that any unsaved data will be lost if you proceed to leave")
                        .setPositiveButton("Exit") { _, _ ->
                            Intent(context, MenuActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(this)
                                requireActivity().finish()
                                Utility.dismissProgressAlertDialog()
                            }
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
                    remove()
                    if (isAdded) {
                        findNavController().popBackStack()
                    }
                }

            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }
}
