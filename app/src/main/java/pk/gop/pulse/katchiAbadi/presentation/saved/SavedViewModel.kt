package pk.gop.pulse.katchiAbadi.presentation.saved

import android.util.Log
import android.widget.Button
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails
import pk.gop.pulse.katchiAbadi.domain.use_case.saved.SavedDataUseCase
import javax.inject.Inject

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val savedDataUseCase: SavedDataUseCase
) : ViewModel() {

    private val uploadMutex = Mutex()

    val surveys: LiveData<List<SurveyMergeDetails>> =
        savedDataUseCase.getAllSavedFormsUseCase()

    private val _deleted = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val deleted = _deleted.asStateFlow()

    private val _uploaded = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val uploaded = _uploaded.asStateFlow()

    private val viewModelJob = Job()
    private val viewModelScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    private val _viewRecord =
        MutableStateFlow<Resource<List<SurveyFormEntity>>>(Resource.Unspecified())
    val viewRecord = _viewRecord.asStateFlow()

    fun deleteData(survey: SurveyMergeDetails) {
        viewModelScope.launch {
            uploadMutex.withLock {
                _deleted.emit(Resource.Loading())
                when (val validationResult =
                    savedDataUseCase.deleteSavedRecordUseCase(survey)) {
                    is Resource.Success -> {
                        _deleted.emit(Resource.Success(Unit))
                    }

                    is Resource.Error -> {
                        _deleted.emit(Resource.Error(validationResult.message.toString()))
                    }

                    else -> {}
                }
            }
        }
    }

    fun postData(survey: SurveyMergeDetails, uploadButton: Button?) {
        Log.d("TAG", "Attempting to upload record with ID: ${survey.parcelNo}")

        viewModelScope.launch {

            uploadMutex.withLock {

                _uploaded.emit(Resource.Loading())

                when (val validationResult =
                    savedDataUseCase.postSavedRecordUseCase(survey.parcelNo, survey.uniqueId)) {
                    is Resource.Success -> {
                        _uploaded.emit(Resource.Success(Unit))
                        // Re-enable the button in the UI thread
                        withContext(Dispatchers.Main) {
                            uploadButton?.isEnabled = true
                        }
                    }

                    is Resource.Error -> {
                        _uploaded.emit(Resource.Error(validationResult.message.toString()))
                        // Re-enable the button in the UI thread
                        withContext(Dispatchers.Main) {
                            uploadButton?.isEnabled = true
                        }
                    }

                    else -> {}
                }
            }
        }
    }

//    fun postAllData() {
//
//        viewModelScope.launch {
//
//            uploadMutex.withLock {
//
//                _uploaded.emit(Resource.Loading())
//
//                when (val validationResult =
//                    savedDataUseCase.postAllSavedRecordUseCase()) {
//                    is Resource.Success -> {
//                        _uploaded.emit(Resource.Success(Unit))
//                    }
//
//                    is Resource.Error -> {
//                        _uploaded.emit(Resource.Error(validationResult.message.toString()))
//                    }
//
//                    else -> {}
//                }
//            }
//        }
//    }


    fun viewRecord(parcelNo: Long, uniqueId: String) {
        viewModelScope.launch {
            _viewRecord.emit(Resource.Loading())

            val validationResult =
                savedDataUseCase.viewSavedRecordUseCase(parcelNo, uniqueId)

            if (validationResult.isNotEmpty()) {
                _viewRecord.emit(Resource.Success(validationResult))
            } else {
                _viewRecord.emit(Resource.Error("No record found"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}
