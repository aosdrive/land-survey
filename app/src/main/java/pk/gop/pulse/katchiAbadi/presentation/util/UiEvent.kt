package pk.gop.pulse.katchiAbadi.presentation.util

import pk.gop.pulse.katchiAbadi.common.Event
import android.content.Context
import android.content.Intent
import android.widget.Toast
object ToastUtil {
    fun showShort(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    fun showLong(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}

object IntentUtil {

    fun startActivity(context: Context, target: Class<*>) {
        val intent = Intent(context, target)
        context.startActivity(intent)
    }

}

sealed class UiEvent : Event() {
    data class ShowToast(val message: String) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    object NavigateUp : UiEvent()
    object OnLogin : UiEvent()
}