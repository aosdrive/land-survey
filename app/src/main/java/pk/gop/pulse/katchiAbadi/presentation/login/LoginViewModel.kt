package pk.gop.pulse.katchiAbadi.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidateCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginDto
import pk.gop.pulse.katchiAbadi.data.remote.response.LoginSurveyorResponse
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidationUseCase
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val validationUseCase: ValidationUseCase
) : ViewModel() {

    private val _login = MutableStateFlow<Resource<LoginSurveyorResponse>>(Resource.Unspecified())
    val login = _login.asStateFlow()

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

                else -> {}
            }

        }
    }
}