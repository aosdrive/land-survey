package pk.gop.pulse.katchiAbadi.presentation.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.remote.response.MouzaAssignedDto
import pk.gop.pulse.katchiAbadi.domain.use_case.menu.MenuDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.common.ResourceSealed
import pk.gop.pulse.katchiAbadi.data.remote.response.Info
import pk.gop.pulse.katchiAbadi.data.remote.response.MauzaDetail
import pk.gop.pulse.katchiAbadi.data.remote.response.Settings
import javax.inject.Inject

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuDataUseCase: MenuDataUseCase
) : ViewModel() {

    private val _sync = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val sync = _sync.asStateFlow()

    private val _mouza = MutableStateFlow<ResourceSealed<MouzaAssignedDto, Info>>(ResourceSealed.Unspecified())
    val mouza = _mouza.asStateFlow()
    private val _mauzaNew = MutableStateFlow<ResourceSealed<List<MauzaDetail>, Settings>>(ResourceSealed.Unspecified())
    val mauzaNew = _mauzaNew.asStateFlow()


    fun syncData(mauzaId: Long, abadiId: Long, mauzaName: String, abadiName: String) {
        viewModelScope.launch {
            _sync.emit(Resource.Loading())
            when (val validationResult =
                menuDataUseCase.getSyncDataUseCase(mauzaId, abadiId, mauzaName, abadiName)) {
                is Resource.Success -> {

                    _sync.emit(Resource.Success(Unit))
                }

                is Resource.Error -> {
                    _sync.emit(Resource.Error(validationResult.message.toString()))
                }

                else -> {}
            }
        }
    }

    fun mouzaData() {
        viewModelScope.launch {
            _mouza.emit(ResourceSealed.Loading())
            when (val validationResult = menuDataUseCase.getMouzaDataUseCase()) {
                is ResourceSealed.Success -> {
                    validationResult.data?.let {
                        _mouza.emit(ResourceSealed.Success(validationResult.data, validationResult.info))
                    } ?: _mouza.emit(ResourceSealed.Error(message = "Response data is null"))
                }

                is ResourceSealed.Error -> {
                    _mouza.emit(ResourceSealed.Error(validationResult.message.toString()))
                }

                else -> {}
            }
        }
    }

    fun mauzaNewData() {
        viewModelScope.launch {
            _mauzaNew.emit(ResourceSealed.Loading())
            when (val result = menuDataUseCase.fetchMauzaSyncUseCase()) {
                is ResourceSealed.Success -> {
                    result.data?.let {
                        _mauzaNew.emit(ResourceSealed.Success(result.data, result.info))
                    } ?: _mauzaNew.emit(ResourceSealed.Error("Mauza list is empty"))
                }

                is ResourceSealed.Error -> {
                    _mauzaNew.emit(ResourceSealed.Error(result.message ?: "Unknown error"))
                }

                else -> {}
            }
        }
    }

}