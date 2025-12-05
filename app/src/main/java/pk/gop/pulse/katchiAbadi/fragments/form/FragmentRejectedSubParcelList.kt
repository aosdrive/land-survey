package pk.gop.pulse.katchiAbadi.fragments.form

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.ui.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.adapter.RejectedSubParcelAdapter
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcel
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.RejectedSubParcelItemClickListener
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.FragmentRejectedSubParcelListBinding
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel

@AndroidEntryPoint
class FragmentRejectedSubParcelList : Fragment(), RejectedSubParcelItemClickListener {

    private val viewModel: SharedFormViewModel by activityViewModels()
    private lateinit var context: Context

    private var _binding: FragmentRejectedSubParcelListBinding? = null
    private val binding get() = _binding!!

    // Declare your adapter as a property of the fragment
    private lateinit var rejectedSubParcelAdapter: RejectedSubParcelAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Initialize your adapter here, after the fragment is attached
        rejectedSubParcelAdapter = RejectedSubParcelAdapter(context, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRejectedSubParcelListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()

        binding.apply {

            tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
            tvHeader.text = "Revisit Sub-Parcels (${viewModel.parcelOperationValue})"

            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = rejectedSubParcelAdapter
            }

            rejectedSubParcelAdapter.submitList(viewModel.rejectedSubParcelsList)

            btnSave.setOnClickListener {
                if (!Utility.checkTimeZone(requireContext())) return@setOnClickListener

                viewModel.saveAllData(viewModel.parcelNo)
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.saveAllForm.collect {
                    when (it) {
                        is Resource.Loading -> {
                            Utility.showProgressAlertDialog(
                                requireContext(),
                                "Please wait..."
                            )
                        }

                        is Resource.Success -> {
                            Utility.dismissProgressAlertDialog()

                            Intent(requireActivity(), MenuActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(this)
                                requireActivity().finish()
                            }
                            Toast.makeText(
                                context,
                                "Data saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                        }

                        is Resource.Error -> {
                            Utility.dismissProgressAlertDialog()
                            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                        }

                        else -> Unit

                    }
                }
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onSelectItemClicked(item: RejectedSubParcel) {
        val gson = Gson()
        viewModel.surveyFormCounter = item.id
        if (isAdded) {
            viewModel.parcelOperation = "Split"
            viewModel.subParcelsStatusList = gson.toJson(item)

            if (item.subParcelNoAction == "Revisit") {
                viewModel.newStatusId = 12
                findNavController().navigate(R.id.action_fragmentRejectedSubParcelList_to_fragmentSurveyList)
            } else {
                viewModel.newStatusId = 11
                findNavController().navigate(R.id.action_fragmentRejectedSubParcelList_to_fragmentFormRemarks)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

        binding.apply {
            btnSave.isEnabled = viewModel.rejectedSubParcelsList.all { it.isFormFilled }
        }
    }
}
