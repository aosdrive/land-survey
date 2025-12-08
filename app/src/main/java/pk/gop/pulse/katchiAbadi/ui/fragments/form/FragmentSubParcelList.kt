package pk.gop.pulse.katchiAbadi.ui.fragments.form

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.ui.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.ui.activities.SurveyActivity
import pk.gop.pulse.katchiAbadi.adapter.SubParcelAdapter
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SubParcel
import pk.gop.pulse.katchiAbadi.common.SubParcelItemClickListener
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.FragmentSubParcelListBinding
import pk.gop.pulse.katchiAbadi.presentation.form.SharedFormViewModel
import javax.inject.Inject

@AndroidEntryPoint
class FragmentSubParcelList : Fragment(), SubParcelItemClickListener {

    private val viewModel: SharedFormViewModel by activityViewModels()
    private lateinit var context: Context

    private var _binding: FragmentSubParcelListBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var database: AppDatabase
    // Declare your adapter as a property of the fragment
    private lateinit var subParcelAdapter: SubParcelAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Initialize your adapter here, after the fragment is attached
        subParcelAdapter = SubParcelAdapter(context, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubParcelListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()

        binding.apply {

            tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
            tvHeader.text = "Total Sub-Parcels (${viewModel.parcelOperationValue})"

            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = subParcelAdapter
            }

            //subParcelAdapter.submitList(viewModel.subParcelList)
//            viewLifecycleOwner.lifecycleScope.launch {
//                val updatedList = viewModel.subParcelList.map { subParcel ->
//                    val isSurveyed = database.tempSurveyLogDao()
//                        .isSubParcelSurveyed(viewModel.parcelId, subParcel.id.toString())
//                    Log.d("SubParcelListDebug", "Checking subParcel id=${subParcel.id}, surveyed=$isSurveyed")
//                    subParcel.copy(isFormFilled = isSurveyed)
//                }
//                Log.d("SubParcelListDebug", "Updated list prepared: $updatedList")
//                subParcelAdapter.submitList(updatedList)
//            }



//            btnSave.setOnClickListener {
//                if (!Utility.checkTimeZone(requireContext())) return@setOnClickListener
//
//                viewModel.saveAllData(viewModel.parcelNo)
//            }
            btnSave.setOnClickListener {
                if (!Utility.checkTimeZone(requireContext())) return@setOnClickListener

                lifecycleScope.launch {
                    // Clear all temp logs
                    database.tempSurveyLogDao().clearAllLogs()

                    // Save other data if you want
                   // viewModel.saveAllData(viewModel.parcelNo)

                    // Navigate to Map Fragment
                    // If you use Navigation Component:
                    findNavController().navigate(R.id.action_fragmentSubParcelList_to_fragmentMap)

                    // Or if MapFragment is your activity, you can start it like:
                    // val intent = Intent(requireContext(), MapActivity::class.java)
                    // startActivity(intent)
                    // requireActivity().finish()
                }
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
//                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
//                            startActivity(this)
//                            requireActivity().finish()
//                            Utility.dismissProgressAlertDialog()
                            lifecycleScope.launch {
                                // Clear all temp logs
                                database.tempSurveyLogDao().clearAllLogs()

                                // Save other data if you want
                                // viewModel.saveAllData(viewModel.parcelNo)

                                // Navigate to Map Fragment
                                // If you use Navigation Component:
                                findNavController().navigate(R.id.action_fragmentSubParcelList_to_fragmentMap)

                                // Or if MapFragment is your activity, you can start it like:
                                // val intent = Intent(requireContext(), MapActivity::class.java)
                                // startActivity(intent)
                                // requireActivity().finish()
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


//    override fun onSelectItemClicked(item: SubParcel) {
//        viewModel.surveyFormCounter = item.id
//        if (isAdded) {
//            findNavController().navigate(R.id.action_fragmentSubParcelList_to_fragmentSurveyList)
//        }
//    }

    override fun onSelectItemClicked(item: SubParcel) {
        viewModel.surveyFormCounter = item.id

        val args = arguments
        val intent = Intent(requireContext(), SurveyActivity::class.java).apply {
            putExtra("parcelId", args?.getLong("parcelId") ?: 0L)
            putExtra("parcelNo", args?.getString("parcelNo") ?: "")
            putExtra("subParcelNo", item.id.toString()) // each sub-parcel has its own number
            putExtra("parcelArea", args?.getString("parcelArea") ?: "")
            putExtra("khewatInfo", args?.getString("khewatInfo") ?: "")
            putExtra("parcelOperation", args?.getString("parcelOperation") ?: "")
            putExtra("parcelOperationValue", args?.getString("parcelOperationValue") ?: "")
            putExtra("parcelOperationValueHi", args?.getString("parcelOperationValueHi") ?: "")
        }
        startActivity(intent)
    }


    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

        // Reload the list with updated isFormFilled from DB every time fragment resumes
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedList = viewModel.subParcelList.map { subParcel ->
                val isSurveyed = database.tempSurveyLogDao()
                    .isSubParcelSurveyed(viewModel.parcelId, subParcel.id.toString())
                Log.d("SubParcelListDebug", "Checking subParcel id=${subParcel.id}, surveyed=$isSurveyed")
                subParcel.copy(isFormFilled = isSurveyed)
            }
            Log.d("SubParcelListDebug", "Updated list submitted in onResume")
            subParcelAdapter.submitList(updatedList)

            // Enable save button only if all subparcels are filled
          //  binding.btnSave.isEnabled = updatedList.all { it.isFormFilled }
        }

//        binding.apply {
//            btnSave.isEnabled = viewModel.subParcelList.all { it.isFormFilled }
//        }
    }
}
