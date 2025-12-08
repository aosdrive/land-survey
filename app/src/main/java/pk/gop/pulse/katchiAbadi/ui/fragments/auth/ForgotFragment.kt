package pk.gop.pulse.katchiAbadi.ui.fragments.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.BuildConfig
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.FragmentForgotPasswordBinding
import pk.gop.pulse.katchiAbadi.presentation.login.ForgotViewModel

@AndroidEntryPoint
class ForgotFragment : Fragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ForgotViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            btnSubmit.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    val cnic = getCNIC()?.trim()
                    cnic?.let {
                        viewModel.forgotPassword(cnic)
                    }
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.forgotPassword.collect {
                when (it) {
                    is Resource.Loading -> {
                        binding.btnSubmit.startAnimation()
                    }

                    is Resource.Success -> {

                        Toast.makeText(
                            activity,
                            "OTP has been sent to your registered mobile and email",
                            Toast.LENGTH_LONG
                        ).show()

                        binding.btnSubmit.revertAnimation()
                        val email = binding.etUsername.text.toString().trim()
                        val action =
                            ForgotFragmentDirections.actionForgotFragmentToOtpVerificationFragment(
                                cnic = email,
                            )
                        if (isAdded) {
                            findNavController().navigate(action)
                        }
                    }

                    is Resource.Error -> {
                        Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show()
                        binding.btnSubmit.revertAnimation()
                    }

                    else -> Unit

                }
            }
        }
    }


    private fun getCNIC(): String? {
        val cnic = binding.etUsername.text.toString().trim()

        if (cnic.isEmpty()) {
            binding.etUsername.apply {
                error = resources.getString(R.string.error_username_field_empty)
                requestFocus()
            }
            return null
        }

        if (cnic.length != 13) {
            binding.etUsername.apply {
                error = resources.getString(R.string.not_a_valid_cnic)
                requestFocus()
            }
            return null
        }

        return cnic
    }


}

