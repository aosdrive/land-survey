package pk.gop.pulse.katchiAbadi.presentation.util

import android.content.Context
import androidx.appcompat.app.AlertDialog

object DialogUtil {

    fun showErrorDialog(
        context: Context,
        title: String = "Error",
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }

        val dialog = builder.create()
        dialog.show()

        // Style the OK button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    fun showAlreadyLoggedInDialog(
        context: Context,
        onDismiss: (() -> Unit)? = null
    ) {
        val message = "You are already logged in on another device.\n\n" +
                "Please logout from that device first or contact your administrator " +
                "if you need to force logout from all devices."

        val builder = AlertDialog.Builder(context)
            .setTitle("Already Logged In")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }

        val dialog = builder.create()
        dialog.show()

        // Style the OK button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }
}