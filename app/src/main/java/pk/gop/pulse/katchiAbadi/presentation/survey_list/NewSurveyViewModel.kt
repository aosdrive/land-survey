package pk.gop.pulse.katchiAbadi.presentation.survey_list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.data.local.SurveyWithKhewat
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyPersonPost
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main.NewSurveyUseCase
import javax.inject.Inject

@HiltViewModel
class NewSurveyViewModel @Inject constructor(
    private val useCase: NewSurveyUseCase
) : ViewModel() {

    val surveys: StateFlow<List<NewSurveyNewEntity>> = useCase.getAllPendingSurveys()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalPendingCount: StateFlow<Int> = useCase.getTotalPendingCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _deleted = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val deleted = _deleted.asStateFlow()

    private val _uploaded = MutableStateFlow<Resource<Unit>>(Resource.Unspecified())
    val uploaded = _uploaded.asStateFlow()

//    private val _surveysWithKhewat = MutableStateFlow<List<SurveyWithKhewat>>(emptyList())
//    val surveysWithKhewat = _surveysWithKhewat.asStateFlow()

    val surveysWithKhewat: StateFlow<List<SurveyWithKhewat>> = useCase.getAllPendingSurveysWithKhewat()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    fun deleteData(survey: NewSurveyNewEntity) {
        viewModelScope.launch {
            _deleted.emit(Resource.Loading())
            val result = useCase.deleteSurvey(survey)
            _deleted.emit(result)
        }
    }

    fun uploadData(context: Context, survey: NewSurveyNewEntity) {
        viewModelScope.launch {
            _uploaded.emit(Resource.Loading())
            val result = useCase.uploadSurvey(context, survey)
            _uploaded.emit(result)
        }
    }

    suspend fun getOnePendingSurvey(): NewSurveyNewEntity? {
        return useCase.getOnePendingSurvey()
    }

    suspend fun getSurveyById(id: Long): NewSurveyNewEntity? {
        return useCase.getSurveyById(id)
    }

    suspend fun getPersonsForSurvey(surveyId: Long): List<SurveyPersonPost> {
        val entities = useCase.getPersonsForSurvey(surveyId)
        return entities.map { p ->
            SurveyPersonPost(
                personId = p.personId,
                firstName = p.firstName,
                lastName = p.lastName,
                gender = p.gender,
                relation = p.relation,
                religion = p.religion,
                mobile = p.mobile,
                nic = p.nic,
                growerCode = p.growerCode,
                personArea = p.personArea,
                ownershipType = p.ownershipType,
                extra1 = p.extra1,
                extra2 = p.extra2,
                mauzaId = p.mauzaId,
                mauzaName = p.mauzaName
            )
        }
    }

    // Method to load surveys with khewat info
//    fun loadSurveysWithKhewatInfo() {
//        viewModelScope.launch {
//            try {
//                val surveysWithKhewatData = getSurveysWithKhewatInfo()
//                _surveysWithKhewat.emit(surveysWithKhewatData)
//            } catch (e: Exception) {
//                // Handle error - could emit an error state or log
//                _surveysWithKhewat.emit(emptyList())
//            }
//        }
//    }

    // In your ViewModel, replace the _surveysWithKhewat and related methods with:


// Remove the loadSurveysWithKhewatInfo method as it's no longer needed
}