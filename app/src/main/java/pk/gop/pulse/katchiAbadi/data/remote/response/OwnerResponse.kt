// OwnerResponse.kt
//data class OwnerResponse(
//    val khewatID: Long,
//    val khewatNo: String?,
//    val ownerCount: Int?,
//    val ownershipDetails: List<OwnershipDetail>
//)
data class OwnerResponse(
    val first_Name: String?,
    val last_Name: String?,
    val person_ID: Long,
    val relation: String?,
    val caste: String?,
    val nic: String?,
    val mobile: String?,
    val grower_Code: String?,
    val area_KMF: String?,
    val extra1: String?,
    val extra2: String?,
    val mauza_Id: Long,
    val mauza_Name: String?
)
