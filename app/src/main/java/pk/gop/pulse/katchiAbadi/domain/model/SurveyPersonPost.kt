package pk.gop.pulse.katchiAbadi.domain.model

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
    val pictures: List<Pictures>,
    val persons: List<SurveyPersonPost>,
)



