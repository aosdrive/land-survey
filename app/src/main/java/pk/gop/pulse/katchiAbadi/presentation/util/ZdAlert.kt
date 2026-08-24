package pk.gop.pulse.katchiAbadi.presentation.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import pk.gop.pulse.katchiAbadi.R

/**
 * ZD ke validation dialogs. Purane AlertDialog.Builder blocks ki jagah.
 *
 *   ZdAlert.warn(this, "Owner Required", "Please add at least one owner...")
 *   ZdAlert.error(this, "Invalid CNIC", "Format 12345-1234567-1 chahiye.", invalidCnics)
 *
 * warn  = amber  (kuch reh gaya hai - surveyor ko bharna hai)
 * error = red    (jo bhara hai wo galat hai)
 */
object ZdAlert {

    /** Amber: koi field khaali hai ya limit lag gayi. */
    fun warn(
        context: Context,
        title: String,
        message: String,
        details: List<String>? = null,
        buttonText: String = "Got it",
        onDismiss: (() -> Unit)? = null
    ) = show(
        context = context,
        title = title,
        message = message,
        details = details,
        buttonText = buttonText,
        discRes = R.drawable.zd_alert_disc_amber,
        iconRes = R.drawable.ic_alert_28,
        onDismiss = onDismiss
    )

    /** Red: value maujood hai magar format/qeemat galat hai. */
    fun error(
        context: Context,
        title: String,
        message: String,
        details: List<String>? = null,
        buttonText: String = "Fix it",
        onDismiss: (() -> Unit)? = null
    ) = show(
        context = context,
        title = title,
        message = message,
        details = details,
        buttonText = buttonText,
        discRes = R.drawable.zd_alert_disc_red,
        iconRes = R.drawable.ic_alert_red_28,
        onDismiss = onDismiss
    )

    /**
     * Do button wala confirm. Amber disc, aur "aage barhein?" wala sawal.
     *
     *   ZdAlert.confirm(this, "Clear cache?", "...", confirmText = "Clear") { doIt() }
     */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        details: List<String>? = null,
        confirmText: String = "Continue",
        cancelText: String = "Cancel",
        iconRes: Int = R.drawable.ic_alert_28,
        onConfirm: () -> Unit
    ) = show(
        context = context,
        title = title,
        message = message,
        details = details,
        buttonText = confirmText,
        discRes = R.drawable.zd_alert_disc_amber,
        iconRes = iconRes,
        onDismiss = null,
        cancelText = cancelText,
        onConfirm = onConfirm
    )

    /**
     * Khatarnak amal ka confirm - red disc. Data delete karne wale kaamon ke liye.
     */
    fun confirmDanger(
        context: Context,
        title: String,
        message: String,
        details: List<String>? = null,
        confirmText: String = "Delete",
        cancelText: String = "Cancel",
        onConfirm: () -> Unit
    ) = show(
        context = context,
        title = title,
        message = message,
        details = details,
        buttonText = confirmText,
        discRes = R.drawable.zd_alert_disc_red,
        iconRes = R.drawable.ic_alert_red_28,
        onDismiss = null,
        cancelText = cancelText,
        onConfirm = onConfirm,
        dangerButton = true
    )

    private fun show(
        context: Context,
        title: String,
        message: String,
        details: List<String>?,
        buttonText: String,
        discRes: Int,
        iconRes: Int,
        onDismiss: (() -> Unit)?,
        cancelText: String? = null,
        onConfirm: (() -> Unit)? = null,
        dangerButton: Boolean = false
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zd_alert, null)

        view.findViewById<View>(R.id.fl_alert_disc).setBackgroundResource(discRes)
        view.findViewById<ImageView>(R.id.iv_alert_icon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.tv_alert_title).text = title
        view.findViewById<TextView>(R.id.tv_alert_message).text = message

        val detailsBox = view.findViewById<View>(R.id.ll_alert_details)
        if (details.isNullOrEmpty()) {
            detailsBox.visibility = View.GONE
        } else {
            detailsBox.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tv_alert_details).text =
                details.joinToString("\n")
        }

        val dialog = AlertDialog.Builder(context, R.style.ZdDialog)
            .setView(view)
            // Sirf button se band ho - bahar tap ya back se nahi, warna
            // surveyor galti se band kar deta hai aur paighaam padha hi nahi jata
            .setCancelable(false)
            .create()

        dialog.setCanceledOnTouchOutside(false)

        // Cancel sirf confirm() me nazar aata hai
        val cancelBtn = view.findViewById<TextView>(R.id.btn_alert_cancel)
        if (cancelText == null) {
            cancelBtn.visibility = View.GONE
        } else {
            cancelBtn.visibility = View.VISIBLE
            cancelBtn.text = cancelText
            cancelBtn.setOnClickListener { dialog.dismiss() }
        }

        val okBtn = view.findViewById<TextView>(R.id.btn_alert_ok)
        okBtn.text = buttonText
        if (dangerButton) {
            okBtn.setBackgroundResource(R.drawable.zd_btn_danger_solid)
        }
        okBtn.setOnClickListener {
            dialog.dismiss()
            onConfirm?.invoke()
        }

        dialog.setOnDismissListener { onDismiss?.invoke() }
        dialog.show()
        return dialog
    }
}