package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.common.toEntityList
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.NAHRepository
import javax.inject.Inject

class NAHRepositoryImpl @Inject constructor(
    private val context: Context,
    private val api: ServerApi,
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : NAHRepository {

    override suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        val recordId = db.notAtHomeSurveyFormDao().insertSurvey(notAtHomeSurveyFormEntity)

        if (notAtHomeSurveyFormEntity.parcelOperation == "Same" || notAtHomeSurveyFormEntity.parcelOperation == "Merge") {

            if (Constants.ReVisitThreshold == notAtHomeSurveyFormEntity.visitCount || notAtHomeSurveyFormEntity.interviewStatus != "Respondent Not Present") {
                val surveyForms = db.notAtHomeSurveyFormDao()
                    .getAllSurveysForm(notAtHomeSurveyFormEntity.parcelNo, notAtHomeSurveyFormEntity.uniqueId)
                db.surveyFormDao().insertSurveys(surveyForms.toEntityList())
                db.notAtHomeSurveyFormDao().deleteAllSurveys(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                when (notAtHomeSurveyFormEntity.parcelOperation) {
                    "Same" -> {
                        db.parcelDao().updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.IN_PROCESS,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                    }

                    "Merge" -> {
                        db.parcelDao().updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.IN_PROCESS,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                        val parcelOperationValue =
                            notAtHomeSurveyFormEntity.parcelOperationValue

                        if (parcelOperationValue.contains(",")) {

                            val parcelNos = parcelOperationValue.split(",")

                            for (parcelNo in parcelNos) {
                                val newStatusId = db.parcelDao().getNewStatusId(parcelNo.toLong(), notAtHomeSurveyFormEntity.kachiAbadiId,)
                                db.parcelDao().updateParcelSurveyStatusWrtParcelId(
                                    newStatusId,
                                    ParcelStatus.MERGE,
                                    parcelNo.toLong()
                                )
                            }

                        } else {
                            val newStatusId = db.parcelDao().getNewStatusId(parcelOperationValue.toLong(), notAtHomeSurveyFormEntity.kachiAbadiId,)
                            db.parcelDao().updateParcelSurveyStatusWrtParcelId(
                                newStatusId,
                                ParcelStatus.MERGE,
                                parcelOperationValue.toLong()
                            )
                        }
                    }
                }
            }

        } else {

            if (notAtHomeSurveyFormEntity.interviewStatus == "Respondent Not Present") {

                val subParcelVisitCount = db.notAtHomeSurveyFormDao().getSubParcelVisitCount(notAtHomeSurveyFormEntity.parcelNo,
                    notAtHomeSurveyFormEntity.subParcelId, notAtHomeSurveyFormEntity.uniqueId)

                if(subParcelVisitCount == 3){

                    db.notAtHomeSurveyFormDao().updateSurveyStatusWrtParcel(1, notAtHomeSurveyFormEntity.parcelNo,
                        notAtHomeSurveyFormEntity.subParcelId, notAtHomeSurveyFormEntity.uniqueId)

                    val recordsCount = db.notAtHomeSurveyFormDao().getCountNAHForm(notAtHomeSurveyFormEntity.parcelNo, notAtHomeSurveyFormEntity.uniqueId)

                    if (recordsCount == null || recordsCount == 0) {

                        db.notAtHomeSurveyFormDao().updateAllSurveyStatusWrtParcel(0, notAtHomeSurveyFormEntity.parcelNo, notAtHomeSurveyFormEntity.uniqueId)

                        val surveyForms = db.notAtHomeSurveyFormDao()
                            .getAllSurveysForm(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)
                        db.surveyFormDao().insertSurveys(surveyForms.toEntityList())
                        db.notAtHomeSurveyFormDao()
                            .deleteAllSurveys(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                        db.parcelDao().updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.IN_PROCESS,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                    }
                }

            }else{

                db.notAtHomeSurveyFormDao().updateSurveyStatusWrtParcel(1, notAtHomeSurveyFormEntity.parcelNo,
                    notAtHomeSurveyFormEntity.subParcelId,notAtHomeSurveyFormEntity.uniqueId)

                val recordsCount = db.notAtHomeSurveyFormDao().getCountNAHForm(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                if (recordsCount == null || recordsCount == 0) {

                    db.notAtHomeSurveyFormDao().updateAllSurveyStatusWrtParcel(0, notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                    val surveyForms = db.notAtHomeSurveyFormDao()
                        .getAllSurveysForm(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)
                    db.surveyFormDao().insertSurveys(surveyForms.toEntityList())
                    db.notAtHomeSurveyFormDao()
                        .deleteAllSurveys(notAtHomeSurveyFormEntity.parcelNo,notAtHomeSurveyFormEntity.uniqueId)

                    db.parcelDao().updateParcelSurveyStatus(
                        notAtHomeSurveyFormEntity.newStatusId,
                        ParcelStatus.IN_PROCESS,
                        notAtHomeSurveyFormEntity.centroidGeom
                    )
                }
            }

        }

        return Resource.Success(Unit)

    }
}