package pk.gop.pulse.katchiAbadi.data.remote.response

data class KachiAbadiList(
    val abadiId: Long,
    val abadiName: String
) {
    override fun toString(): String {
        return abadiName
    }

    override fun hashCode(): Int {
        var result = 17 // Choose a prime number as the initial value
        result = 31 * result + abadiId.hashCode()
        result = 31 * result + abadiName.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val otherAbadi = other as KachiAbadiList

        if (abadiId != otherAbadi.abadiId) return false
        if (abadiName != otherAbadi.abadiName) return false

        return true
    }
}
