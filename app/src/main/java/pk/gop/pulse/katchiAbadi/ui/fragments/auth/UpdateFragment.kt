package pk.gop.pulse.katchiAbadi.ui.fragments.auth

import android.os.Bundle
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
import pk.gop.pulse.katchiAbadi.databinding.FragmentUpdatePasswordBinding
import pk.gop.pulse.katchiAbadi.presentation.login.UpdatePasswordViewModel

@AndroidEntryPoint
class UpdateFragment : Fragment() {
    private var _binding: FragmentUpdatePasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UpdatePasswordViewModel by viewModels()

    private val args: UpdateFragmentArgs by navArgs()
    private val cnic: String by lazy { args.cnic }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdatePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {

            tvFooter.text =
                tvFooter.text.toString().replace("Version", "Version ${Constants.VERSION_NAME}")

            btnSubmit.setOnClickListener {
                requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
                if (Utility.checkInternetConnection(requireContext())) {
                    val password = getPassword()?.trim()
                    password?.let {
                        viewModel.updatePassword(cnic, password)
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
            viewModel.updatePassword.collect {
                when (it) {
                    is Resource.Loading -> {
                        binding.btnSubmit.startAnimation()
                    }

                    is Resource.Success -> {
                        binding.btnSubmit.revertAnimation()
                        if (isAdded) {
                            findNavController().navigate(R.id.action_updateFragment_to_successFragment)
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


    private fun getPassword(): String? {
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

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

        if (password == "Pulse@123") {
            binding.etPassword.apply {
                error = resources.getString(R.string.default_password_error)
                requestFocus()
            }
            return null
        }

        if (!isPasswordValid(password)) {
            binding.etPassword.apply {
                error = resources.getString(R.string.incorrect_password)
                requestFocus()
            }
            return null
        }

        if (confirmPassword.isEmpty()) {
            binding.etConfirmPassword.apply {
                error = resources.getString(R.string.error_password_field_empty)
                requestFocus()
            }
            return null
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.apply {
                error = resources.getString(R.string.password_do_not_match)
                requestFocus()
            }
            return null
        }
        return password
    }

    private fun isPasswordValid(password: String): Boolean {
//        val passwordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")
        val passwordRegex =
            Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#\$%^&*()_+\\-={}\\[\\]:;\"<>,.?/~]).{8,}$")
        return passwordRegex.matches(password)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAdded) {
                    findNavController().navigate(R.id.action_updateFragment_to_loginFragment)
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().let { activity -> Utility.closeKeyBoard(activity) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}

