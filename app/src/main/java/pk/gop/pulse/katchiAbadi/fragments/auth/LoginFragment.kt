package pk.gop.pulse.katchiAbadi.fragments.auth

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.Utility
import pk.gop.pulse.katchiAbadi.databinding.FragmentLoginBinding
import pk.gop.pulse.katchiAbadi.presentation.login.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.activities.MenuActivity
import pk.gop.pulse.katchiAbadi.common.Constants
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            btnLogin.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    val cnic = getCNIC()?.trim()
                    cnic?.let {
                        val password = getPassword()
                        password?.let {
                            viewModel.login(cnic, password)
                        }
                    }
                } else {
                    Utility.dialog(
                        context,
                        "Please make sure you are connected to the internet and try again.",
                        "No Internet!"
                    )
                }
            }

            tvForgotPassword.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    if (isAdded) {
                        findNavController().navigate(R.id.action_loginFragment_to_forgotFragment)
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
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.login.collect { it ->
                    when (it) {
                        is Resource.Loading -> {
                            binding.btnLogin.startAnimation()
                        }

//                        is Resource.Success -> {
//                            binding.btnLogin.revertAnimation()
//
//                            it.data?.let {
//                                if (it.changePassword) {
//
//                                    Toast.makeText(
//                                        activity,
//                                        "OTP has been sent to your registered mobile and email",
//                                        Toast.LENGTH_LONG
//                                    ).show()
//
//                                    val action =
//                                        LoginFragmentDirections.actionLoginFragmentToOtpVerificationFragment(
//                                            cnic = it.cnic,
//                                        )
//
//                                    findNavController().navigate(action)
//
//                                } else {
//
//                                    sharedPreferences.edit()
//                                        .putInt(
//                                            Constants.SHARED_PREF_LOGIN_STATUS,
//                                            Constants.LOGIN_STATUS_ACTIVE
//                                        )
//                                        .putLong(Constants.SHARED_PREF_USER_ID, it.userID)
//                                        .putString(Constants.SHARED_PREF_TOKEN, it.token)
//                                        .putString(Constants.SHARED_PREF_USER_CNIC, it.cnic)
//                                        .putString(Constants.SHARED_PREF_USER_NAME, it.name)
//                                        .putLong(
//                                            Constants.SHARED_PREF_USER_ASSIGNED_MOUZA,
//                                            it.mauzaId
//                                        )
//                                        .putString(
//                                            Constants.SHARED_PREF_USER_ASSIGNED_MOUZA_NAME,
//                                            it.mauzaName
//                                        )
//                                        .apply()
//
//                                    Intent(requireActivity(), MenuActivity::class.java).apply {
//                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
//                                        startActivity(this)
//                                        requireActivity().finish()
//                                    }
//                                }
//                            } ?: Toast.makeText(
//                                activity,
//                                "Data not found, contact administration",
//                                Toast.LENGTH_LONG
//                            ).show()
//                            binding.btnLogin.revertAnimation()
//
//                        }

                        is Resource.Success -> {
                            binding.btnLogin.revertAnimation()
                            it.data?.let { loginData ->
                                sharedPreferences.edit()
                                    .putInt(Constants.SHARED_PREF_LOGIN_STATUS, Constants.LOGIN_STATUS_ACTIVE)
                                    .putLong(Constants.SHARED_PREF_USER_ID, loginData.userId)
                                    .putString(Constants.SHARED_PREF_TOKEN, loginData.token)
                                    .putString(Constants.SHARED_PREF_USER_NAME, loginData.name)
                                    .apply()

                                Intent(requireActivity(), MenuActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(this)
                                    requireActivity().finish()
                                }

                            } ?: Toast.makeText(activity, "Login failed: no user data", Toast.LENGTH_LONG).show()
                        }


                        is Resource.Error -> {
                            Toast.makeText(activity, it.message, Toast.LENGTH_LONG).show()
                            binding.btnLogin.revertAnimation()
                        }

                        else -> Unit

                    }
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

    private fun getPassword(): String? {
        val password = binding.etPassword.text.toString()

        if (password.isEmpty()) {
            binding.etPassword.apply {
                error = resources.getString(R.string.error_password_field_empty)
                requestFocus()
            }
            return null
        }

        if (password.length < 8) {
            binding.etPassword.apply {
                error = resources.getString(R.string.invalid_password)
                requestFocus()
            }
            return null
        }
        return password
    }
}