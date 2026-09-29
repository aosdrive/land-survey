package pk.gop.pulse.katchiAbadi.domain.model

import com.google.gson.annotations.SerializedName
import pk.gop.pulse.katchiAbadi.data.remote.post.Pictures


// For persons
data class SurveyPersonPost(
    val personId: Long,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val relation: String,
    val religion: String,
    val mobile: String,
    val nic: String,
    val growerCode: String,
    val personArea: String,
    val ownershipType: String,
    val address: String,
    val extra1: String,
    val extra2: String,
    val mauzaId: Long,
    val mauzaName: String
)

// Main survey payload
// Updated SurveyPostNew model to include geometry
data class SurveyPostNew(
    val propertyType: String,
    val ownershipStatus: String,
    val variety: String,
    val cropType: String,
    val crop: String,
    val year: String,
    val area: String,
    val calculatedArea: String? = null,
    val isGeometryCorrect: Boolean,
    val remarks: String,
    val mauzaId: Long,
    val areaName: String,
    val parcelId: Long, // Will be 0 for server creation
    val parcelNo: String,
    val subParcelNo: String,
    val parcelOperation: String, // Will be "CreateNew" for split parcels
    val parcelOperationValue: String,
    val geomWKT: String? = null, // Add geometry data
    val centriod: String? = null, //  Add centroid data
    val khewatInfo: String? = null, // Add this field
    val parcelAreaKMF: String?= null,
    val distance: Int = 100,
    val sowingStatus: String = "No",
    val sowingDate: String? = null,
    val pictures: List<Pictures>,
    val persons: List<SurveyPersonPost>,
    val sowingPersons: List<SowingPersonPostDto>? = null,
    val farmerProfile: String? = null,
    )




// Corporate Farms upload payload.
// SurveyPostNew ka corporate bhai — zone/division/section/farm/block/plot,
// lessors and irrigation source are extra, and most fields are nullable because
// not everything is mandatory on the corporate form.
data class CorporateParcelPost(
    val parcelId: Long,
    val parcelNo: String,
    val subParcelNo: String,
    val mauzaId: Long,
    val parcelOperation: String,
    val parcelOperationValue: String,

    // Geometry
    val geomWKT: String? = null,
    val centriod: String? = null,
    val calculatedArea: String? = null,
    val isGeometryCorrect: Boolean = false,
    val distance: Int = 100,

    // Parcel metadata
    val khewatInfo: String? = null,
    val areaAssigned: String? = null,
    val parcelAreaKMF: String? = null,
    val areaName: String? = null,

    // Survey fields - same names as SurveyPostNew
    val propertyType: String? = null,
    val ownershipStatus: String? = null,
    val variety: String? = null,
    val cropType: String? = null,
    val crop: String? = null,
    val year: String? = null,
    val area: String? = null,
    val remarks: String? = null,
    val sowingStatus: String = "No",
    val sowingDate: String? = null,

    // Corporate-specific
    val zone: String? = null,
    val division: String? = null,
    val section: String? = null,
    val farm: String? = null,
    val block: String? = null,
    val plot: String? = null,
    val lessorName: String? = null,
    val plotBifurcation: String? = null,
    val plotSizeAcres: String? = null,

    val persons: List<SurveyPersonPost> = emptyList(),
    val pictures: List<Pictures> = emptyList(),
    val sowingPersons: List<SowingPersonPostDto>? = null,
    val irrigationSource: String? = null,
    val irrigationSourceQuantity: String? = null,
    @SerializedName("lessors")
    val lessors: List<LessorPostDto> = emptyList(),
)
