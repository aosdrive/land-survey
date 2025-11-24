package pk.gop.pulse.katchiAbadi.data.local

data class AddVarietyResponse(
    val message: String,
    val variety: DropdownItem? = null,
    val alreadyExists: Boolean? = null
)
