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
    val extra1: String,
    val extra2: String,
    val mauzaId: Long,
    val mauzaName: String
)

// Main survey payload
data class SurveyPostNew(
    val propertyType: String,
    val ownershipStatus: String,
    val variety: String,
    val cropType: String,
    val crop: String,
    val year: String,
    val area: String,
    val isGeometryCorrect: Boolean,
    val remarks: String,
    val mauzaId: Long,
    val areaName: String,
    val parcelId: Long,
    val parcelNo: String,
    val subParcelNo: String,
    val parcelOperation: String,
    val parcelOperationValue: String,
    val pictures: List<Pictures>,
    val persons: List<SurveyPersonPost>
)