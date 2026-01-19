package pk.gop.pulse.katchiAbadi.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidateCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.data.remote.response.VersionCheckResponse
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidationUseCase
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val validationUseCase: ValidationUseCase
) : ViewModel() {

    // Version check state
    private val _versionCheck = MutableStateFlow<Resource<VersionCheckResponse>>(Resource.Unspecified())
    val versionCheck: StateFlow<Resource<VersionCheckResponse>> = _versionCheck.asStateFlow()

    // Login state
    private val _login = MutableStateFlow<Resource<LoginSurveyorResponse>>(Resource.Unspecified())
    val login = _login.asStateFlow()

    /**
     * Check app version against backend requirements
     * @param appVersion Current app version (e.g., "1.0.5")
     */
    fun checkAppVersion(appVersion: String) {
        viewModelScope.launch {
            _versionCheck.emit(Resource.Loading())
            try {
                when (val result = validationUseCase.checkAppVersionUseCase(appVersion)) {
                    is Resource.Success -> {
                        result.data?.let {
                            _versionCheck.emit(Resource.Success(it))
                        } ?: run {
                            _versionCheck.emit(Resource.Error("Version check response is null"))
                        }
                    }
                    is Resource.Error -> {
                        // If update is required, emit error with data
                        _versionCheck.emit(Resource.Error(
                            message = result.message ?: "Version check failed",
//                            data = result.data
                        ))
                    }
                    else -> {
                        _versionCheck.emit(Resource.Error("Unexpected version check result"))
                    }
                }
            } catch (e: Exception) {
                _versionCheck.emit(Resource.Error(e.message ?: "Version check failed"))
            }
        }
    }

    /**
     * Login user with credentials
     * @param email User's CNIC
     * @param password User's password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _login.emit(Resource.Loading())
            when (val validationResult = validationUseCase.validateCredentialsSur(email, password)) {
                is Resource.Success -> {
                    validationResult.data?.let {
                        _login.emit(Resource.Success(validationResult.data))
                    } ?: _login.emit(Resource.Error(message = "Response data is null"))
                }

                is Resource.Error -> {
                    _login.emit(Resource.Error(validationResult.message.toString()))
                }

                else -> {
                    _login.emit(Resource.Error("Unexpected login result"))
                }
            }
        }
    }

    /**
     * Reset version check state
     */
    fun resetVersionCheck() {
        _versionCheck.value = Resource.Unspecified()
    }

    /**
     * Reset login state
     */
    fun resetLogin() {
        _login.value = Resource.Unspecified()
    }
}