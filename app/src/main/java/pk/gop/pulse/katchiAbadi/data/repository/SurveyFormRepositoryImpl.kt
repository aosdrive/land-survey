package pk.gop.pulse.katchiAbadi.data.repository

import android.content.Context
import android.content.SharedPreferences
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.common.Resource
import pk.gop.pulse.katchiAbadi.common.SimpleResource
import pk.gop.pulse.katchiAbadi.common.SubParcel
import pk.gop.pulse.katchiAbadi.data.local.AppDatabase
import pk.gop.pulse.katchiAbadi.data.remote.ServerApi
import pk.gop.pulse.katchiAbadi.domain.model.NotAtHomeSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelStatus
import pk.gop.pulse.katchiAbadi.domain.model.SurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.model.TempSurveyFormEntity
import pk.gop.pulse.katchiAbadi.domain.repository.SurveyFormRepository
import javax.inject.Inject

class SurveyFormRepositoryImpl @Inject constructor(
    private val context: Context,
    private val api: ServerApi,
    private val db: AppDatabase,
    private val sharedPreferences: SharedPreferences
) : SurveyFormRepository {

    override suspend fun saveData(surveyFormEntity: SurveyFormEntity): SimpleResource {
        return try {

            db.surveyFormDao().insertSurvey(surveyFormEntity)

            when (surveyFormEntity.parcelOperation) {
                "Same" -> {
                    db.parcelDao().updateParcelSurveyStatus(
                        surveyFormEntity.newStatusId,
                        ParcelStatus.IN_PROCESS,
                        surveyFormEntity.centroidGeom
                    )
                }

                "Merge" -> {
                    db.parcelDao().updateParcelSurveyStatus(
                        surveyFormEntity.newStatusId,
                        ParcelStatus.IN_PROCESS,
                        surveyFormEntity.centroidGeom
                    )
                    val parcelOperationValue = surveyFormEntity.parcelOperationValue

                    if (parcelOperationValue.contains(",")) {

                        val parcelNos = parcelOperationValue.split(",")

                        for (parcelNo in parcelNos) {
                            val newStatusId = db.parcelDao().getNewStatusId(parcelNo.toLong(), surveyFormEntity.kachiAbadiId,)
                            db.parcelDao().updateParcelSurveyStatusWrtParcelId(
                                newStatusId,
                                ParcelStatus.MERGE,
                                parcelNo.toLong()
                            )
                        }

                    } else {
                        val newStatusId = db.parcelDao().getNewStatusId(parcelOperationValue.toLong(), surveyFormEntity.kachiAbadiId,)
                        db.parcelDao().updateParcelSurveyStatusWrtParcelId(
                            newStatusId,
                            ParcelStatus.MERGE,
                            parcelOperationValue.toLong()
                        )
                    }
                }
            }

            db.surveyDao().updateSurveyStatus(true, surveyFormEntity.surveyId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(
                message = e.message.toString()
            )
        }

    }

    override suspend fun saveTempData(tempSurveyFormEntity: TempSurveyFormEntity): SimpleResource {
        return try {
            db.tempSurveyFormDao().insertSurvey(tempSurveyFormEntity)

            // This check is mandatory if the property attachment is restricted to only one owner
            db.surveyDao().updateSurveyStatus(true, tempSurveyFormEntity.surveyId)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(
                message = e.message.toString()
            )
        }

    }

    override suspend fun saveAllData(surveyFormEntityList: List<SurveyFormEntity>): SimpleResource {
        return try {
            db.surveyFormDao().insertSurveys(surveyFormEntityList)

            for (item in surveyFormEntityList) {
                db.surveyDao().updateSurveyStatus(true, item.surveyId)

                db.parcelDao().updateParcelSurveyStatus(
                    item.newStatusId,
                    ParcelStatus.IN_PROCESS,
                    item.centroidGeom
                )
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(
                message = e.message.toString()
            )
        }
    }

    override suspend fun getAllTempData(parcelNo: Long): List<TempSurveyFormEntity> {
        return db.tempSurveyFormDao().getAllSurveysForm(parcelNo)
    }

    override suspend fun saveAllNotAtHomeData(notAtHomeSurveyFormEntityList: List<NotAtHomeSurveyFormEntity>): SimpleResource {
        return try {
            db.notAtHomeSurveyFormDao().insertSurveys(notAtHomeSurveyFormEntityList)

            for (item in notAtHomeSurveyFormEntityList) {
                db.surveyDao().updateSurveyStatus(true, item.surveyId)

                db.parcelDao().updateParcelSurveyStatus(
                    item.newStatusId,
                    ParcelStatus.NOT_AT_HOME,
                    item.centroidGeom
                )

            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(
                message = e.message.toString()
            )
        }
    }

    override suspend fun saveNotAtHomeData(notAtHomeSurveyFormEntity: NotAtHomeSurveyFormEntity): SimpleResource {
        return try {
            db.notAtHomeSurveyFormDao().insertSurvey(notAtHomeSurveyFormEntity)

            when (notAtHomeSurveyFormEntity.parcelOperation) {
                "Same" -> {
                    db.parcelDao()
                        .updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.NOT_AT_HOME,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                    db.surveyDao().updateSurveyStatus(true, notAtHomeSurveyFormEntity.surveyId)
                }

                "Merge" -> {
                    val parcelOperationValue = notAtHomeSurveyFormEntity.parcelOperationValue

                    if (parcelOperationValue.contains(",")) {

                        val parcelNos = parcelOperationValue.split(",")

                        for (parcelNo in parcelNos) {
                            val newStatusId = db.parcelDao().getNewStatusId(parcelNo.toLong(), notAtHomeSurveyFormEntity.kachiAbadiId,)
                            db.parcelDao()
                                .updateParcelSurveyStatusWrtParcelId(
                                    newStatusId,
                                    ParcelStatus.MERGE,
                                    parcelNo.toLong()
                                )
                        }

                    } else {
                        val newStatusId = db.parcelDao().getNewStatusId(parcelOperationValue.toLong(), notAtHomeSurveyFormEntity.kachiAbadiId,)
                        db.parcelDao()
                            .updateParcelSurveyStatusWrtParcelId(
                                newStatusId,
                                ParcelStatus.MERGE,
                                parcelOperationValue.toLong()
                            )

                    }

                    db.parcelDao()
                        .updateParcelSurveyStatus(
                            notAtHomeSurveyFormEntity.newStatusId,
                            ParcelStatus.NOT_AT_HOME,
                            notAtHomeSurveyFormEntity.centroidGeom
                        )
                    db.surveyDao().updateSurveyStatus(true, notAtHomeSurveyFormEntity.surveyId)
                }
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(
                message = e.message.toString()
            )
        }

    }

    override suspend fun getAllNotAtHomeData(parcelNo: Long, uniqueId: String): List<NotAtHomeSurveyFormEntity> {
        return db.notAtHomeSurveyFormDao().getAllSurveysForm(parcelNo, uniqueId)
    }
}