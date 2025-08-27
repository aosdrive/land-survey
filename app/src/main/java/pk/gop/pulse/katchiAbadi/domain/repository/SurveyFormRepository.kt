package pk.gop.pulse.katchiAbadi.domain.repository

import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity

interface SurveyFormRepository {
    suspend fun saveData(surveyFormEntity: SurveyFormEntity): SimpleResource
    suspend fun saveAllData(surveyFormEntityList: List<SurveyFormEntity>): SimpleResource
    suspend fun saveTempData(tempSurveyFormEntity: TempSurveyFormEntity): SimpleResource
    suspend fun getAllTempData(parcelNo: Long): List<TempSurveyFormEntity>

    suspend fun saveAllNotAtHomeData(notAtHomeSurveyFormEntityList: List<NotAtHomeSurveyFormEntity>): SimpleResource
    suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource
    suspend fun getAllNotAtHomeData(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity>
}
