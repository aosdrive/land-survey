package pk.gop.pulse.katchiAbadi.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidationUseCase
import javax.inject.Inject

@HiltViewModel
class ForgotViewModel @Inject constructor(
    private val validationUseCase: ValidationUseCase
) : ViewModel() {

    private val _forgotPassword =
        MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val forgotPassword = _forgotPassword.asStateFlow()

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _forgotPassword.emit(Resource.Loading())
            when (val validationResult = validationUseCase.forgotPasswordUseCase(email)) {
                is Resource.Success -> {
                    _forgotPassword.emit(Resource.Success(Unit))
                }

                is Resource.Error -> {
                    _forgotPassword.emit(Resource.Error(validationResult.message.toString()))
                }

                else -> {}
            }

        }
    }

    override fun onCleared() {
        viewModelJob.cancel()
        super.onCleared()
    }
}