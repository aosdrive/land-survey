package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline copy of GetAnalyticsParcels rows for one mauza, so the Map Analysis screen works
 * without internet. Replaced as a whole each time the mauza is fetched online.
 */
@Entity(
    tableName = "analytics_parcels",
    indices = [Index(value = ["mauzaId"])]
)
data class AnalyticsParcelEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val serverRowId: Int,
    val parcelId: Int,
    val mauzaId: Int,
    val parcelNo: String?,
    val khewat: String?,
    val status: String?,
    val parcelAcres: Double?,
    val computedAcres: Double?,
    val geometryWKT: String?,
    val cachedAt: Long,
    /** Server-side "Accepted" / "Declined" from Map Analysis; added in schema version 31. */
    val action: String? = null,
    // Sub-parcel / area details from the server; added in schema version 32.
    val parentParcelId: Int? = null,
    val subParcelNo: String? = null,
    val areaAssigned: String? = null,
    val surveyStatusCode: Int? = null,
    /** Other active sub-parcel numbers of the same parcel number on the server; added in schema version 33. */
    val existingSubParcelNos: String? = null
) {
    fun toDto() = AnalyticsParcelDto(
        id = serverRowId,
        parcelId = parcelId,
        mauzaId = mauzaId,
        parcelNo = parcelNo,
        khewat = khewat,
        status = status,
        growerName = null,
        growerCode = null,
        growerMobile = null,
        cropType = null,
        parcelAcres = parcelAcres,
        caneAcres = null,
        canePct = null,
        missingAcres = null,
        lat = null,
        lng = null,
        computedAcres = computedAcres,
        geometryWKT = geometryWKT,
        action = action,
        parentParcelId = parentParcelId,
        subParcelNo = subParcelNo,
        areaAssigned = areaAssigned,
        surveyStatusCode = surveyStatusCode,
        existingSubParcelNos = existingSubParcelNos
    )

    companion object {
        fun from(dto: AnalyticsParcelDto, cachedAt: Long) = AnalyticsParcelEntity(
            serverRowId = dto.id,
            parcelId = dto.parcelId,
            mauzaId = dto.mauzaId,
            parcelNo = dto.parcelNo,
            khewat = dto.khewat,
            status = dto.status,
            parcelAcres = dto.parcelAcres,
            computedAcres = dto.computedAcres,
            geometryWKT = dto.geometryWKT,
            cachedAt = cachedAt,
            action = dto.action,
            parentParcelId = dto.parentParcelId,
            subParcelNo = dto.subParcelNo,
            areaAssigned = dto.areaAssigned,
            surveyStatusCode = dto.surveyStatusCode,
            existingSubParcelNos = dto.existingSubParcelNos
        )
    }
}
