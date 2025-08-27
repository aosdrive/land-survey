package pk.gop.pulse.katchiAbadi.fragments.not_at_home

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import pk.gop.pulse.katchiAbadi.R
import dagger.hilt.android.AndroidEntryPoint
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.FragmentNahOwnershipStatusBinding
import pk.gop.pulse.katchiAbadi.presentation.not_at_home.SharedNAHViewModel

@AndroidEntryPoint
class FragmentNAHOwnershipStatus : Fragment(), RadioGroup.OnCheckedChangeListener {
    private val viewModel: SharedNAHViewModel by activityViewModels()

    private var _binding: FragmentNahOwnershipStatusBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentNahOwnershipStatusBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onResume() {
        super.onResume()

        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }


        binding.apply {

            if (viewModel.parcelOperation == "Split") {
                tvDetails.text =
                    "Parcel No: ${viewModel.parcelNo} (${viewModel.surveyFormCounter}/${viewModel.parcelOperationValue})"
            } else {
                tvDetails.text = "Parcel No: ${viewModel.parcelNo}"
            }

            when (viewModel.ownershipType) {
                "First owner" -> rbOwnershipSelfOwner.isChecked = true
                "Inherited" -> rbOwnershipInheritance.isChecked = true
                "Buyer" -> rbOwnershipBuyer.isChecked = true
                "Other" -> {
                    rbOwnershipOther.isChecked = true
                    trOtherOwnershipCaption.visibility = View.VISIBLE
                    trOtherOwnershipQuestion.visibility = View.VISIBLE
                }
            }

            etOtherOwnershipType.setText(viewModel.ownershipOtherType)

            rgOwnershipType.setOnCheckedChangeListener(this@FragmentNAHOwnershipStatus)

            btnNext.setOnClickListener {

                when (rgOwnershipType.checkedRadioButtonId) {
                    R.id.rb_ownership_other -> {
                        if (etOtherOwnershipType.text.toString().trim().isEmpty()) {
                            etOtherOwnershipType.apply {
                                setText("")
                                error = "Field cannot be empty"
                                requestFocus()
                            }
                            return@setOnClickListener
                        }

                        if (!Utility.isMinLengthAndNoDuplicates(etOtherOwnershipType, 4)) {
                            etOtherOwnershipType.apply {
                                error = "Enter Valid input value"
                                requestFocus()
                            }
                            return@setOnClickListener
                        }
                    }

                    else -> {
                        etOtherOwnershipType.apply {
                            setText("")
                        }
                    }
                }

                val radioButton: RadioButton =
                    binding.root.findViewById(rgOwnershipType.checkedRadioButtonId)

                viewModel.ownershipType = radioButton.text.toString()
                viewModel.ownershipOtherType = etOtherOwnershipType.text.toString().trim()

                if (isAdded) {
//                    findNavController().navigate(R.id.action_fragmentFormNAHOwnershipStatus_to_fragmentNAHOccupancyDetails)
                }
            }

            btnPrevious.setOnClickListener {
                val radioButton: RadioButton =
                    binding.root.findViewById(rgOwnershipType.checkedRadioButtonId)

                viewModel.ownershipType = radioButton.text.toString()
                viewModel.ownershipOtherType = etOtherOwnershipType.text.toString().trim()
                if (isAdded) {
                    findNavController().popBackStack()
                }
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onPause() {
        super.onPause()
        // Saving state while the view is paused or moved back to   the previous view
        savingCurrentState()
    }

    private fun savingCurrentState() {
        binding.apply {
            val radioButton: RadioButton =
                binding.root.findViewById(rgOwnershipType.checkedRadioButtonId)

            viewModel.ownershipType = radioButton.text.toString()
            viewModel.ownershipOtherType = etOtherOwnershipType.text.toString().trim()
        }
    }

    override fun onCheckedChanged(group: RadioGroup?, checkedId: Int) {
        when (checkedId) {
            R.id.rb_ownership_other -> {
                binding.trOtherOwnershipCaption.visibility = View.VISIBLE
                binding.trOtherOwnershipQuestion.visibility = View.VISIBLE
            }

            else -> {
                binding.trOtherOwnershipCaption.visibility = View.GONE
                binding.trOtherOwnershipQuestion.visibility = View.GONE
            }
        }
    }
}