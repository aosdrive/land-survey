package pk.gop.pulse.katchiAbadi.ui.fragments.not_at_home

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.ui.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.FragmentNahPropertyDetailsBinding
import pk.gop.pulse.katchiAbadi.presentation.not_at_home.SharedNAHViewModel
import javax.inject.Inject

@AndroidEntryPoint
class FragmentNAHPropertyDetails : Fragment() {
    private val viewModel: SharedNAHViewModel by activityViewModels()

    private var _binding: FragmentNahPropertyDetailsBinding? = null

    @Inject
    lateinit var database: AppDatabase

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNahPropertyDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

        binding.apply {

            val intent = activity?.intent
            val bundle = intent?.getBundleExtra("bundle_data")
            if (bundle != null) {
                val parcelNo = bundle.getLong("parcelNo")
                val uniqueId = bundle.getString("uniqueId") as String

                viewLifecycleOwner.lifecycleScope.launch {

                    if (viewModel.surveyFormCounter < 1) {
                        val item = database.notAtHomeSurveyFormDao()
                            .getSurveyForm(parcelNo, uniqueId)

                        viewModel.createFormInstance(item)
                    }

                    if (viewModel.parcelOperation == "Split") {
                        tvDetails.text =
                            "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter}/${viewModel.parcelOperationValue})"
                    } else {
                        tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
                    }

                    if (viewModel.surveyStatus != Constants.Survey_New_Unit) {
                        etPropertyNumber.setText(viewModel.propertyNumber)

                        val areaParts = viewModel.area.split("-")

                        etAreaKanal.setText(areaParts[0])
                        etAreaMarla.setText(areaParts[1])
                        etAreaSfeet.setText(areaParts[2])

                        tvCalculatedArea.text = "Calculated Area:${viewModel.area}"

                        when (viewModel.interviewStatus) {
                            requireContext().getString(R.string.present_respondent_status) -> {
                                spnCategory.setSelection(0)
                            }

                            requireContext().getString(R.string.declined_respondent_status) -> {
                                spnCategory.setSelection(1)
                            }

                            requireContext().getString(R.string.not_present_respondent_status) -> {
                                spnCategory.setSelection(2)
                            }

                            requireContext().getString(R.string.empty_plot_status) -> {
                                spnCategory.setSelection(3)
                            }
                        }

                        if (viewModel.interviewStatus == "Respondent Present") {
                            etName.setText(viewModel.name)
                            etFatherHusbandName.setText(viewModel.fname)
                            etCnicNo.setText(viewModel.cnic)
                            etMobileNo.setText(viewModel.mobile)
                            spnRespondentRelationStatus.setSelection(viewModel.mobileSourcePosition)
                            etOtherRespondentRelation.setText(viewModel.mobileOtherSource)

                            when (viewModel.gender) {
                                "Male" -> rbGenderMale.isChecked = true
                                "Female" -> rbGenderFemale.isChecked = true
                                "Transgender" -> rgGenderTransgender.isChecked = true
                                else -> {}
                            }
                        } else {
                            etName.setText(viewModel.name)
                            etFatherHusbandName.setText(viewModel.fname)
                        }

                    } else {
                        etPropertyNumber.setText(viewModel.propertyNumber)

                        val areaParts = viewModel.area.split("-")

                        etAreaKanal.setText(areaParts[0])
                        etAreaMarla.setText(areaParts[1])
                        etAreaSfeet.setText(areaParts[2])

                        tvCalculatedArea.text = "Calculated Area:${viewModel.area}"
                        rbGenderMale.isChecked = true
                    }

                }

            }

            if (etMobileNo.text.toString().isEmpty()) {
                val spannable = SpannableString(Constants.fixedText)
                etMobileNo.setText(spannable)
            }

            etMobileNo.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s != null) {
                        // Check if the text doesn't start with the fixed digits
                        if (!s.toString().startsWith(Constants.fixedText)) {
                            // Set the text to the fixed digits
                            etMobileNo.setText(SpannableString(Constants.fixedText))
                            // Move the cursor to the end of the text
                            etMobileNo.setSelection(Constants.fixedText.length)
                        }
                    }
                }
            })

            spnCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(

                    adapterView: AdapterView<*>?,
                    view: View?,
                    pos: Int,
                    l: Long
                ) {
                    try {

                        if (viewModel.interviewStatus != spnCategory.selectedItem.toString()) {

                            val builder = AlertDialog.Builder(requireContext())
                                .setTitle("Alert!")
                                .setCancelable(false)
                                .setMessage("This action will reset the current form.\n\nDo you really want to change the selection to ${spnCategory.selectedItem}?")
                                .setPositiveButton("Yes") { _, _ ->

                                    if (pos == 0) {
                                        cvOwner2.visibility = View.VISIBLE
                                        tvPageNumber.text = "1/3"
                                    } else {
                                        cvOwner2.visibility = View.GONE
                                        tvPageNumber.text = "1/2"
                                    }
                                    viewModel.resetValues()

                                    viewModel.interviewStatus = spnCategory.selectedItem.toString()
                                }
                                .setNegativeButton("No") { _, _ ->

                                    when (viewModel.interviewStatus) {
                                        requireContext().getString(R.string.present_respondent_status) -> {
                                            spnCategory.setSelection(0)
                                        }

                                        requireContext().getString(R.string.declined_respondent_status) -> {
                                            spnCategory.setSelection(1)
                                        }

                                        requireContext().getString(R.string.not_present_respondent_status) -> {
                                            spnCategory.setSelection(2)
                                        }
                                    }
                                }
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
                            if (pos == 0) {
                                cvOwner2.visibility = View.VISIBLE
                                tvPageNumber.text = "1/3"
                            } else {
                                cvOwner2.visibility = View.GONE
                                tvPageNumber.text = "1/2"
                            }
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {}
            }

            spnRespondentRelationStatus.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        adapterView: AdapterView<*>?,
                        view: View?,
                        pos: Int,
                        l: Long
                    ) {
                        try {
                            if (spnRespondentRelationStatus.selectedItem.toString() == "Other") {
                                trOtherRespondentRelationCaption.visibility = View.VISIBLE
                                trOtherRespondentRelation.visibility = View.VISIBLE
                            } else {
                                trOtherRespondentRelationCaption.visibility = View.GONE
                                trOtherRespondentRelation.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                }

            btnNext.setOnClickListener {

                if (etPropertyNumber.text.toString().trim().isEmpty()) {
                    etPropertyNumber.apply {
                        setText("")
                        error = "Property Number cannot be empty"
                        requestFocus()
                    }
                    return@setOnClickListener
                }

                if (etAreaKanal.text.toString().trim().isEmpty()) {
                    etAreaKanal.apply {
                        setText("")
                        error = "Kanal cannot be empty"
                        requestFocus()
                    }
                    return@setOnClickListener
                }

                if (etAreaMarla.text.toString().trim().isEmpty()) {
                    etAreaMarla.apply {
                        setText("")
                        error = "Marla cannot be empty"
                        requestFocus()
                    }
                    return@setOnClickListener
                }

                if (etAreaSfeet.text.toString().trim().isEmpty()) {
                    etAreaSfeet.apply {
                        setText("")
                        error = "Square Feet cannot be empty"
                        requestFocus()
                    }
                    return@setOnClickListener
                }

                if (etAreaMarla.text.toString().trim().toInt() >= 20) {
                    etAreaMarla.apply {
                        error = "Marla value is not valid"
                        requestFocus()
                    }
                    return@setOnClickListener
                }

                if (etAreaSfeet.text.toString().trim().toInt() >= 272) {
                    etAreaSfeet.apply {
                        error = "Square Feet value is not valid"
                        requestFocus()
                    }
                    return@setOnClickListener
                }

                if (etAreaKanal.text.toString().toInt() == 0 && etAreaMarla.text.toString()
                        .toInt() == 0
                    && etAreaSfeet.text.toString().toInt() == 0
                ) {
                    Toast.makeText(context, "Enter valid area value", Toast.LENGTH_LONG).show()
                    return@setOnClickListener

                }

                if (spnCategory.selectedItemPosition == 0) {
                    if (etName.text.toString().trim().isEmpty()) {
                        etName.apply {
                            setText("")
                            error = "Name cannot be empty"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (!Utility.isMinLengthAndNoDuplicates(etName, 3)) {
                        etName.apply {
                            error = "Enter Valid Name"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etFatherHusbandName.text.toString().trim().isEmpty()) {
                        etFatherHusbandName.apply {
                            setText("")
                            error = "Father/Husband Name cannot be empty"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (!Utility.isMinLengthAndNoDuplicates(etFatherHusbandName, 3)) {
                        etFatherHusbandName.apply {
                            error = "Enter Valid Father/Husband Name"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etCnicNo.text.toString().trim().isEmpty()) {
                        etCnicNo.apply {
                            setText("")
                            error = "CNIC number cannot be empty"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etCnicNo.text.toString().trim().length != 13) {
                        etCnicNo.apply {
                            error = "Enter a valid CNIC number"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (Utility.hasSingleDigit(etCnicNo.text.toString().trim())) {
                        etCnicNo.apply {
                            error = "Enter a valid CNIC number"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etMobileNo.text.toString().trim().isEmpty()) {
                        etMobileNo.apply {
                            setText("")
                            error = "Mobile number cannot be empty"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etMobileNo.text.toString().trim().length != 11) {
                        etMobileNo.apply {
                            error = "Enter valid Mobile number"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (etMobileNo.text.toString().trim().contains("03000000000")) {
                        etMobileNo.apply {
                            error = "Enter valid Mobile number"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    // Remove the first two digits of the mobile number
                    val mobileNumber = etMobileNo.text.toString().trim().let {
                        if (it.length > 2 && it.startsWith("03")) it.substring(2) else it
                    }

                    if (Utility.hasSingleDigit(mobileNumber)) {
                        etMobileNo.apply {
                            error = "Enter a valid Mobile number"
                            requestFocus()
                        }
                        return@setOnClickListener
                    }

                    if (spnRespondentRelationStatus.selectedItemPosition == 0) {
                        Toast.makeText(context, "Enter Select Respondent Status", Toast.LENGTH_LONG)
                            .show()
                        return@setOnClickListener
                    }

                    if (spnRespondentRelationStatus.selectedItem.toString() == "Other" && etOtherRespondentRelation.visibility == View.VISIBLE) {

                        if (etOtherRespondentRelation.text.toString().trim().isEmpty()) {
                            etOtherRespondentRelation.apply {
                                setText("")
                                error = "Field cannot be empty"
                                requestFocus()
                            }
                            return@setOnClickListener
                        }

                        if (!Utility.isMinLengthAndNoDuplicates(etOtherRespondentRelation, 4)) {
                            etOtherRespondentRelation.apply {
                                error = "Enter valid input value"
                                requestFocus()
                            }
                            return@setOnClickListener
                        }
                    }

                }

                viewModel.propertyNumber = etPropertyNumber.text.toString().trim()
                viewModel.area =
                    "${etAreaKanal.text.toString().toInt()}-${
                        etAreaMarla.text.toString().toInt()
                    }-${etAreaSfeet.text.toString().toInt()}"

                viewModel.interviewStatus = spnCategory.selectedItem.toString()

                if (spnCategory.selectedItemPosition == 0) {
                    val radioButton =
                        binding.root.findViewById(rgGender.checkedRadioButtonId) as RadioButton

                    viewModel.name = etName.text.toString().trim()
                    viewModel.fname = etFatherHusbandName.text.toString().trim()
                    viewModel.gender = radioButton.text.toString()
                    viewModel.mobile = etMobileNo.text.toString().trim()

                    viewModel.cnic = etCnicNo.text.toString().trim()
                    viewModel.mobileSource = spnRespondentRelationStatus.selectedItem.toString()
                    viewModel.mobileSourcePosition = spnRespondentRelationStatus.selectedItemPosition
                    viewModel.mobileOtherSource = etOtherRespondentRelation.text.toString().trim()

                }

                if (spnCategory.selectedItemPosition != 0) {
                    if (isAdded) {
                        findNavController().navigate(R.id.action_fragmentFormNAHPropertyDetails_to_fragmentNAHRemarks)
                    }
                } else {
                    if (isAdded) {
                        findNavController().navigate(R.id.action_fragmentNAHPropertyDetails_to_fragmentNAHOccupancyDetails)
                    }
                }
            }

            btnPrevious.setOnClickListener {
                showAlertDialog()
            }

//            ivCnicInfo.setOnClickListener {
//                val builder = AlertDialog.Builder(requireContext())
//                    .setTitle("Info!")
//                    .setCancelable(false)
//                    .setMessage("If CNIC number is not available, then enter 0000000000000.")
//                    .setPositiveButton("Ok", null)
//                // Create the AlertDialog object
//                val dialog = builder.create()
//                dialog.show()
//
//                // Get the buttons from the dialog
//                val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
//                val negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
//
//                // Set button text size and style
//                positiveButton.textSize =
//                    16f // Change the size according to your preference
//                positiveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
//
//                negativeButton.textSize =
//                    16f // Change the size according to your preference
//                negativeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD) // Make the text bold
//            }

        }
    }

    override fun onPause() {
        super.onPause()
        // Saving state while the view is paused or moved back to   the previous view
        savingCurrentState()
    }

    private fun savingCurrentState() {
        binding.apply {
            viewModel.propertyNumber = etPropertyNumber.text.toString().trim()

            var kanal = ""
            var marla = ""
            var sqfeet = ""

            if (etAreaKanal.text.toString() != "") {
                kanal = etAreaKanal.text.toString().trim()
            }

            if (etAreaMarla.text.toString() != "") {
                marla = etAreaMarla.text.toString().trim()
            }

            if (etAreaSfeet.text.toString() != "") {
                sqfeet = etAreaSfeet.text.toString().trim()
            }

            viewModel.area = "$kanal-$marla-$sqfeet"

            viewModel.interviewStatus = spnCategory.selectedItem.toString()

            if (spnCategory.selectedItemPosition == 0) {
                val radioButton: RadioButton =
                    binding.root.findViewById(rgGender.checkedRadioButtonId)

                viewModel.name = etName.text.toString().trim()
                viewModel.fname = etFatherHusbandName.text.toString().trim()
                viewModel.gender = radioButton.text.toString()
                viewModel.mobile = etMobileNo.text.toString().trim()

                viewModel.cnic = etCnicNo.text.toString().trim()
                viewModel.mobileSource = spnRespondentRelationStatus.selectedItem.toString()
                viewModel.mobileSourcePosition = spnRespondentRelationStatus.selectedItemPosition
                viewModel.mobileOtherSource = etOtherRespondentRelation.text.toString().trim()

            }
        }
    }

    private fun showAlertDialog() {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Alert!")
            .setCancelable(false)
            .setMessage("Are you sure you want to go back? Please note that the form will be reset, if you proceed to leave")
            .setPositiveButton("Proceed") { _, _ ->
                viewModel.resetValues()
                if (viewModel.parcelOperation == "Split") {
                    if (isAdded) {
                        findNavController().popBackStack()
                    }
                } else {
                    Intent(context, MenuActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(this)
                        requireActivity().finish()
                        Utility.dismissProgressAlertDialog()
                    }
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val builder = AlertDialog.Builder(requireContext())
                    .setTitle("Alert!")
                    .setCancelable(false)
                    .setMessage("Are you sure you want to exit the form? Please note that any unsaved data will be lost if you proceed to leave")
                    .setPositiveButton("Proceed") { _, _ ->
                        if (viewModel.parcelOperation == "Split") {
                            if (isAdded) {
                                findNavController().popBackStack()
                            }
                        } else {
                            Intent(context, MenuActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(this)
                                requireActivity().finish()
                                Utility.dismissProgressAlertDialog()
                            }
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
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

}