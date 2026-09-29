package pk.gop.pulse.katchiAbadi.data.repository

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pk.gop.pulse.katchiAbadi.common.Constants
import pk.gop.pulse.katchiAbadi.data.local.ParcelActivityLogDao
import pk.gop.pulse.katchiAbadi.domain.model.ActiveParcelEntity
import pk.gop.pulse.katchiAbadi.domain.model.ParcelActivityLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParcelActivityLogger @Inject constructor(
    private val dao: ParcelActivityLogDao,
    private val prefs: SharedPreferences
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** FragmentMap / SurveyActivity apni aakhri location yahan set kar dein. */
    @Volatile var lastLat: Double? = null
    @Volatile var lastLng: Double? = null

    private val deviceInfo =
        "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}"

    fun log(
        parcelId: Long,
        parcelNo: String,
        action: String,
        subParcelNo: String = "",
        mauzaId: Long = prefs.getLong(Constants.SHARED_PREF_USER_SELECTED_MAUZA_ID, 0L),
        areaName: String = prefs.getString(Constants.SHARED_PREF_USER_SELECTED_AREA_NAME, "").orEmpty(),
        status: String = "OK",
        details: String = "",
        relatedParcelIds: String = "",
        surveyPkId: Long? = null
    ) {
        val entry = ParcelActivityLogEntity(
            parcelId = parcelId,
            parcelNo = parcelNo,
            subParcelNo = subParcelNo,
            mauzaId = mauzaId,
            areaName = areaName,
            action = action,
            status = status,
            details = details.take(2000),
            relatedParcelIds = relatedParcelIds,
            surveyPkId = surveyPkId,
            userId = prefs.getLong(Constants.SHARED_PREF_USER_ID, 0L),
            userName = prefs.getString(Constants.SHARED_PREF_USER_NAME, "").orEmpty(),
            lat = lastLat,
            lng = lastLng,
            appVersion = Constants.VERSION_NAME,
            deviceInfo = deviceInfo
        )
        scope.launch {
            try {
                dao.insert(entry)
                Log.d("PARCEL_LOG", "$action → $parcelNo/$subParcelNo ($status) $details")
            } catch (e: Exception) {
                Log.e("PARCEL_LOG", "insert failed: ${e.message}")
            }
        }
    }

    /** Shortcut jab poora parcel entity haath mein ho. */
    fun log(parcel: ActiveParcelEntity, action: String, status: String = "OK",
            details: String = "", relatedParcelIds: String = "", surveyPkId: Long? = null) =
        log(
            parcelId = parcel.id, parcelNo = parcel.parcelNo.toString(),
            subParcelNo = parcel.subParcelNo, mauzaId = parcel.mauzaId,
            areaName = parcel.areaAssigned, action = action, status = status,
            details = details, relatedParcelIds = relatedParcelIds, surveyPkId = surveyPkId
        )
}