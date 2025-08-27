package pk.gop.pulse.katchiAbadi.data.remote.post

data class Partition(
    val Landuse: String = "",
    val Occupancy: String = "",
    val CommercialActivity: String = "",
    val PartitionNumber: Int = 0,
    val TenantName: String? = null,
    val TenantFatherName: String? = null,
    val TenantCnic: String? = null,
    val TenantMobile: String? = null,
)