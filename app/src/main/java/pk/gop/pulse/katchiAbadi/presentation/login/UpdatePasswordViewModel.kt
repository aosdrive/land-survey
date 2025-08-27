package pk.gop.pulse.katchiAbadi.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.remote.response.UpdatePasswordDto
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.ValidationUseCase
import javax.inject.Inject

@HiltViewModel
class UpdatePasswordViewModel @Inject constructor(
    private val validationUseCase: ValidationUseCase
) : ViewModel() {

    private val _updatePassword = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val updatePassword = _updatePassword.asStateFlow()

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    fun updatePassword(cnic: String, password: String) {
        viewModelScope.launch {
            _updatePassword.emit(Resource.Loading())
            when (val validationResult = validationUseCase.updatePasswordUseCase(cnic, password)) {
                is Resource.Success -> {
                        _updatePassword.emit(Resource.Success(Unit))
                }

                is Resource.Error -> {
                    _updatePassword.emit(Resource.Error(validationResult.message.toString()))
                }

                else -> {}
            }

        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}