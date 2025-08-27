package pk.gop.pulse.katchiAbadi.domain.repository

import androidx.lifecycle.LiveData
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.data.remote.response.NewSurveyNewDao
import pk.gop.pulse.katchiAbadi.domain.model.NewSurveyNewEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyMergeDetails

interface SavedRepository {
    fun getSurveyFormList(): LiveData<List<SurveyMergeDetails>>
    suspend fun deleteSavedRecord(survey: SurveyMergeDetails): SimpleResource
    fun getSavedRecordByStatusAndLimit(statusBit: Int): LiveData<SurveyMergeDetails>
    suspend fun postSavedData(parcelNo: Long, uniqueId: String): SimpleResource
//    suspend fun postAllSavedData(): SimpleResource
    suspend fun viewSavedData(parcelNo: Long, uniqueId: String): List<SurveyFormEntity>
    suspend fun viewSavedDataNew(parcelId: Long): List<NewSurveyNewEntity>
}