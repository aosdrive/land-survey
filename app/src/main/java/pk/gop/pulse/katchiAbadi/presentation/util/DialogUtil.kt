package pk.gop.pulse.katchiAbadi.presentation.util

import android.content.Context

/**
 * Thin wrappers over [ZdAlert] for the two login-time messages. Kept as a separate object
 * so existing call sites stay unchanged; the dialogs themselves are plain ZdAlert now.
 */
object DialogUtil {

    fun showErrorDialog(
        context: Context,
        title: String = "Error",
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        ZdAlert.error(
            context = context,
            title = title,
            message = message,
            buttonText = "OK",
            onDismiss = onDismiss
        )
    }

    fun showAlreadyLoggedInDialog(
        context: Context,
        onDismiss: (() -> Unit)? = null
    ) {
        ZdAlert.warn(
            context = context,
            title = "Already Logged In",
            message = "You are already logged in on another device.\n\n" +
                    "Please logout from that device first or contact your administrator " +
                    "if you need to force logout from all devices.",
            buttonText = "OK",
            onDismiss = onDismiss
        )
    }
}
