package pk.gop.pulse.katchiAbadi.ui.fragments.not_at_home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.ExtraFloors
import pk.gop.pulse.katchiAbadi.common.Utility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.common.ExtraFloorsInterface
import pk.gop.pulse.katchiAbadi.databinding.FragmentNahOccupancyDetailsBinding
import pk.gop.pulse.katchiAbadi.presentation.not_at_home.SharedNAHViewModel

@AndroidEntryPoint
class FragmentNAHOccupancyDetails : Fragment(), ExtraFloorsInterface {

    private val viewModel: SharedNAHViewModel by activityViewModels()

    private var _binding: FragmentNahOccupancyDetailsBinding? = null
    private val binding get() = _binding!!

    private var extraFloorsList = arrayListOf<ExtraFloors>()
    private lateinit var floorInflater: LayoutInflater

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNahOccupancyDetailsBinding.inflate(inflater, container, false)
        floorInflater =
            requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        extraFloorsList = viewModel.extraFloorsList
        return binding.root
    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }

        extraFloorsList = viewModel.extraFloorsList

        binding.apply {

            if (viewModel.parcelOperation == "Split") {
                tvDetails.text =
                    "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter}/${viewModel.parcelOperationValue})"
            } else {
                tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
            }

            addFloorBtn.setOnClickListener {
                extraFloor()
            }

            btnNext.setOnClickListener {

                for (floor in extraFloorsList) {
                    for (partition in floor.extraPartitionsList) {

                        when (partition.landuseType.split("_")[0]) {
                            "Commercial" -> {
                                val commercialActivityType = partition.commercialActivityType

                                val tLayout = partition.view?.findViewById<TableLayout>(R.id.tlayout)
                                val tRow = tLayout?.getChildAt(3) as TableRow

                                val listOfEditText = arrayListOf<AutoCompleteTextView>()

                                for (a in 0 until tRow.childCount) {
                                    if (tRow.getChildAt(a) is LinearLayout) {
                                        val lLayout = tRow.getChildAt(a) as LinearLayout
                                        val et = lLayout.getChildAt(a) as AutoCompleteTextView
                                        listOfEditText.add(et)
                                    }
                                }

                                for (commercialActivityTypeEditText in listOfEditText) {
                                    if (commercialActivityType.trim().isEmpty()) {
                                        commercialActivityTypeEditText.apply {
                                            setText("")
                                            error = "Commercial Activity Type cannot be empty"
                                            requestFocus()
                                        }
                                        return@setOnClickListener
                                    }

                                    if (!Utility.isMinLengthAndNoDuplicates(
                                            commercialActivityTypeEditText,
                                            4
                                        )
                                    ) {
                                        commercialActivityTypeEditText.apply {
                                            error = "Enter Valid input value"
                                            requestFocus()
                                        }
                                        return@setOnClickListener
                                    }
                                }

                            }

                            else -> {
                            }
                        }

                    }
                }

                navigateToNextScreen()

            }

            btnPrevious.setOnClickListener {
                if (isAdded) {
                    findNavController().popBackStack()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.apply {
                if (viewModel.extraFloorsList.isNotEmpty()) {
                    progressBar.visibility = View.VISIBLE
                    delay(10)

                    extraFloorsList = viewModel.extraFloorsList

                    layoutFloorContainer.removeAllViews()
                    // Recreate dynamic layouts based on extraFloorsList
                    for (extraFloor in extraFloorsList) {
                        extraFloor.inflate(
                            this@FragmentNAHOccupancyDetails,
                            this@FragmentNAHOccupancyDetails,
                            layoutInflater,
                            layoutFloorContainer,
                            extraFloorsList,
                            true
                        )
                    }
                    makeAddMoreButtonInvisibleIfLimitReached()
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                    extraFloor()
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun navigateToNextScreen() {
        if (isAdded) {
            findNavController().navigate(R.id.action_fragmentNAHOccupancyDetails_to_fragmentNAHRemarks)
        }
    }


    override fun onPause() {
        super.onPause()
        viewModel.extraFloorsList = extraFloorsList
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun extraFloor() {
        if (binding.layoutFloorContainer.visibility == View.GONE) {
            binding.layoutFloorContainer.visibility = View.VISIBLE
        }
        val extraFloor = ExtraFloors(extraFloorsList.size)
        extraFloor.inflate(
            this,
            this,
            floorInflater,
            binding.layoutFloorContainer,
            extraFloorsList,
            false
        )
        extraFloorsList.add(extraFloor)
        makeAddMoreButtonInvisibleIfLimitReached()

        // Save to ViewModel
        viewModel.extraFloorsList = extraFloorsList
    }

    private fun makeAddMoreButtonInvisibleIfLimitReached() {
        binding.layoutMainIncluded.visibility = View.VISIBLE
        if (binding.layoutFloorContainer.childCount == Constants.maxNumberOfFloors) {
            makeAddMoreButtonInvisible()
        } else {
            makeAddMoreButtonVisible()
        }
    }

    private fun makeAddMoreButtonInvisible() {
        binding.addFloorBtn.visibility = View.GONE
    }

    override fun makeAddMoreButtonVisible() {
        binding.addFloorBtn.visibility = View.VISIBLE
    }
}