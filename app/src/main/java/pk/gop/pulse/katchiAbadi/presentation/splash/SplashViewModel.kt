package pk.gop.pulse.katchiAbadi.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pk.gop.pulse.katchiAbadi.common.Constants.SPLASH_SCREEN_DURATION
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.domain.use_case.auth.AuthenticateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authenticateUseCase: AuthenticateUseCase
) : ViewModel() {

    private val _navigate = MutableSharedFlow<Boolean>()
    val navigate = _navigate.asSharedFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            when (authenticateUseCase()) {
                is Resource.Success -> {
                    _navigate.emit(
                        true
                    )
                }

                is Resource.Error -> {
                    _navigate.emit(
                        false
                    )
                }

                else -> {}
            }

            delay(SPLASH_SCREEN_DURATION)
            _isLoading.value = false
        }
    }
}