package pk.gop.pulse.katchiAbadi.ui.fragments.auth

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.FragmentOtpBinding
import pk.gop.pulse.katchiAbadi.presentation.login.OtpVerificationViewModel
import java.util.Locale
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class OtpVerificationFragment : Fragment() {
    private var _binding: FragmentOtpBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OtpVerificationViewModel by viewModels()

    private val args: OtpVerificationFragmentArgs by navArgs()
    private val cnic: String by lazy { args.cnic }

    private var countDownTimer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // Cancel the countdown timer when the view is destroyed
        countDownTimer?.cancel()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            btnSubmit.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    val otp = getOTP()?.trim()
                    otp?.let {
                        viewModel.otpVerification(cnic, otp.toString().toInt())
                    }
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }

            tvResendCode.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    cnic.let {
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
            viewModel.otpVerification.collect {
                when (it) {
                    is Resource.Loading -> {
                        binding.btnSubmit.startAnimation()
                    }

                    is Resource.Success -> {
                        binding.btnSubmit.revertAnimation()

                        Toast.makeText(
                            activity,
                            "OTP is verified, please update your password",
                            Toast.LENGTH_LONG
                        ).show()

                        val action =
                            OtpVerificationFragmentDirections.actionOtpVerificationFragmentToUpdateFragment(
                                cnic = cnic,
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.forgotPassword.collect {
                when (it) {
                    is Resource.Loading -> {
                        binding.btnSubmit.startAnimation()
                    }

                    is Resource.Success -> {
                        binding.btnSubmit.revertAnimation()
                        screenNavigation()
                        Toast.makeText(
                            activity,
                            "OTP has been sent to your registered mobile and email",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is Resource.Error -> {
                        binding.btnSubmit.revertAnimation()
                        Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show()
                    }

                    else -> Unit

                }
            }
        }

        screenNavigation()
    }

    private fun screenNavigation() {
        val countdownDuration = 180_000L // 3 minutes in milliseconds
        val interval = 1_000L // 1 second in milliseconds

        binding.apply {
            tvResendCode.visibility = View.GONE
            tvTimer.visibility = View.VISIBLE
            startCountdown(countdownDuration, interval)
        }
    }

    private fun startCountdown(duration: Long, interval: Long) {
        countDownTimer = object : CountDownTimer(duration, interval) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                binding.tvTimer.text =
                    String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.tvResendCode.visibility = View.VISIBLE
                binding.tvTimer.visibility = View.GONE
            }
        }.start()
    }


    private fun getOTP(): String? {
        val otp = binding.etOtp.text.toString().trim()

        if (otp.isEmpty()) {
            binding.etOtp.apply {
                error = resources.getString(R.string.error_otp_field_empty)
                requestFocus()
            }
            return null
        }

        if (otp.length != 4) {
            binding.etOtp.apply {
                error = resources.getString(R.string.not_a_valid_otp)
                requestFocus()
            }
            return null
        }

        return otp
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAdded) {
                    findNavController().navigate(R.id.action_otpVerificationFragment_to_loginFragment)
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }


}

