package pk.gop.pulse.katchiAbadi.common

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize

@Parcelize
data class SubParcel(
    var id: Int,
    var isFormFilled: Boolean = false,
    var isClicked: Boolean = false,
    var action: String = "SELECT"
) : Parcelable {
    companion object : Parceler<SubParcel> {

        override fun create(parcel: Parcel): SubParcel {
            return SubParcel(
                id = parcel.readInt(),
                isFormFilled = parcel.readByte() != 0.toByte(),
                isClicked = parcel.readByte() != 0.toByte(),
                action = parcel.readString() ?: "SELECT"
            )
        }

        override fun SubParcel.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(id)
            parcel.writeByte(if (isFormFilled) 1 else 0)
            parcel.writeByte(if (isClicked) 1 else 0)
            parcel.writeString(action)
        }
    }
}

@Parcelize
data class RejectedSubParcel(
    var id: Int,
    var position: Int,
    var isFormFilled: Boolean = false,
    val fieldRecordId: String,
    val subParcelNoAction: String,
    val pictureRevisitRequired: Boolean,
    val fullRevisitRequired: Boolean
) : Parcelable {
    companion object : Parceler<RejectedSubParcel> {

        override fun create(parcel: Parcel): RejectedSubParcel {
            return RejectedSubParcel(
                id = parcel.readInt(),
                position = parcel.readInt(),
                isFormFilled = parcel.readByte() != 0.toByte(),
                fieldRecordId = parcel.readString() ?: "",
                subParcelNoAction = parcel.readString() ?: "",
                pictureRevisitRequired = parcel.readByte() != 0.toByte(),
                fullRevisitRequired = parcel.readByte() != 0.toByte()
            )
        }

        override fun RejectedSubParcel.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(id)
            parcel.writeInt(position)
            parcel.writeByte(if (isFormFilled) 1 else 0)
            parcel.writeString(fieldRecordId)
            parcel.writeString(subParcelNoAction)
            parcel.writeByte(if (pictureRevisitRequired) 1 else 0)
            parcel.writeByte(if (fullRevisitRequired) 1 else 0)
        }
    }
}

