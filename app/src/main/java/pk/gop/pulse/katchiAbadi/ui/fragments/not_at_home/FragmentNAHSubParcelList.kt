package pk.gop.pulse.katchiAbadi.ui.fragments.not_at_home

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import pk.gop.pulse.katchiAbadi.adapter.NAHSubParcelAdapter
import pk.gop.pulse.katchiAbadi.common.NAHSubParcelItemClickListener
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.databinding.FragmentNahSubParcelListBinding
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.presentation.not_at_home.SharedNAHViewModel
import javax.inject.Inject

@AndroidEntryPoint
class FragmentNAHSubParcelList : Fragment(), NAHSubParcelItemClickListener {

    private val viewModel: SharedNAHViewModel by activityViewModels()
    private lateinit var context: Context

    private var _binding: FragmentNahSubParcelListBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var database: AppDatabase

    // Declare your adapter as a property of the fragment
    private lateinit var nAHSubParcelAdapter: NAHSubParcelAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Initialize your adapter here, after the fragment is attached
        nAHSubParcelAdapter = NAHSubParcelAdapter(context, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNahSubParcelListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        context = requireContext()

        binding.apply {

            val intent = activity?.intent
            val bundle = intent?.getBundleExtra("bundle_data")
            if (bundle != null) {
                val parcelNo = bundle.getLong("parcelNo")
                val uniqueId = bundle.getString("uniqueId") as String

                viewLifecycleOwner.lifecycleScope.launch {
                    val items =
                        database.notAtHomeSurveyFormDao().getAllNAHForm(parcelNo, uniqueId)

                    tvDetails.text =
                        getString(R.string.parcel_id_dynamic, items[0].parcelNo.toString())
                    tvHeader.text =
                        getString(R.string.total_sub_parcels, items[0].parcelOperationValue)

                    recyclerView.apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = nAHSubParcelAdapter
                    }

                    nAHSubParcelAdapter.submitList(items)

                }
            }

        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = activity?.intent
        val bundle = intent?.getBundleExtra("bundle_data")
        if (bundle != null) {
            val parcelOperation = bundle.getString("parcelOperation") as String

            if (parcelOperation != "Split") {
                if (isAdded) {
                    findNavController().navigate(R.id.action_fragmentNAHSubParcelList_to_fragmentNAHPropertyDetails)
                }
            }

        }

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


    override fun onSelectItemClicked(item: NotAtHomeSurveyFormEntity) {

        viewModel.createSplitFormInstance(item)

        viewModel.surveyFormCounter = item.subParcelId
        if (isAdded) {
            findNavController().navigate(R.id.action_fragmentNAHSubParcelList_to_fragmentNAHPropertyDetails)
        }
    }

}
