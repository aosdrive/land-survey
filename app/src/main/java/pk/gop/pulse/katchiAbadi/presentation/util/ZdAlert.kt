package pk.gop.pulse.katchiAbadi.presentation.util

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import pk.gop.pulse.katchiAbadi.R

object ZdAlert {

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

    fun confirm(
        context: Context,
        title: String,
        message: String,
        details: List<String>? = null,
        confirmText: String = "Continue",
        cancelText: String = "Cancel",
        iconRes: Int = R.drawable.ic_alert_28,
        onCancel: (() -> Unit)? = null,
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
        onConfirm = onConfirm,
        onCancel = onCancel
    )

    fun confirmDanger(
        context: Context,
        title: String,
        message: String,
        details: List<String>? = null,
        confirmText: String = "Delete",
        cancelText: String = "Cancel",
        onCancel: (() -> Unit)? = null,
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
        onCancel = onCancel,
        dangerButton = true
    )

    /**
     * Single-choice list in the ZD shell, replacing AlertDialog.setItems /
     * setSingleChoiceItems. Picking a row runs [onPick] and closes the dialog, so there
     * is no OK button - only Cancel.
     */
    fun list(
        context: Context,
        title: String,
        items: List<String>,
        message: String = "",
        selectedIndex: Int = -1,
        cancelText: String = "Cancel",
        onPick: (Int) -> Unit
    ): AlertDialog {
        var dialogRef: AlertDialog? = null

        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        items.forEachIndexed { index, label ->
            rows.addView(
                TextView(context).apply {
                    text = label
                    textSize = 14f
                    setTextColor(Color.parseColor(if (index == selectedIndex) "#1B5E20" else "#2C3A2C"))
                    if (index == selectedIndex) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(context, 4), dp(context, 13), dp(context, 4), dp(context, 13))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isClickable = true
                    setOnClickListener {
                        dialogRef?.dismiss()
                        onPick(index)
                    }
                }
            )
            if (index != items.lastIndex) {
                rows.addView(View(context).apply {
                    setBackgroundColor(Color.parseColor("#E6EFE6"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)
                    )
                })
            }
        }

        val scroller = buildScroller(context, rows)

        dialogRef = show(
            context = context,
            title = title,
            message = message,
            details = null,
            buttonText = "",
            discRes = R.drawable.zd_alert_disc_amber,
            iconRes = R.drawable.ic_alert_28,
            onDismiss = null,
            cancelText = cancelText,
            onConfirm = null,
            slotView = scroller,
            hideOk = true
        )
        return dialogRef
    }

    /**
     * Any custom view inside the ZD shell, replacing AlertDialog.setView. The caller owns
     * [view]; this only hosts it and wires the buttons.
     */
    fun custom(
        context: Context,
        title: String,
        view: View,
        message: String = "",
        confirmText: String = "Continue",
        cancelText: String? = "Cancel",
        iconRes: Int = R.drawable.ic_alert_28,
        dangerButton: Boolean = false,
        onCancel: (() -> Unit)? = null,
        onConfirm: (() -> Unit)? = null
    ): AlertDialog = show(
        context = context,
        title = title,
        message = message,
        details = null,
        buttonText = confirmText,
        discRes = if (dangerButton) R.drawable.zd_alert_disc_red else R.drawable.zd_alert_disc_amber,
        iconRes = iconRes,
        onDismiss = null,
        cancelText = cancelText,
        onConfirm = onConfirm,
        dangerButton = dangerButton,
        onCancel = onCancel,
        slotView = view
    )


    /**
     * Multi-select list, replacing AlertDialog.setMultiChoiceItems. Selections are only
     * reported when the confirm button is pressed, so Cancel leaves the caller untouched.
     */
    fun multiList(
        context: Context,
        title: String,
        items: List<String>,
        checked: BooleanArray,
        message: String = "",
        confirmText: String = "OK",
        cancelText: String = "Cancel",
        onConfirm: (Set<Int>) -> Unit
    ): AlertDialog {
        // Work on a copy so Cancel cannot leak a half-made selection back to the caller.
        val working = checked.copyOf()

        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { index, label ->
            rows.addView(
                CheckBox(context).apply {
                    text = label
                    textSize = 14f
                    isChecked = working.getOrElse(index) { false }
                    setTextColor(Color.parseColor("#2C3A2C"))
                    setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 10))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setOnCheckedChangeListener { _, isOn -> working[index] = isOn }
                }
            )
        }

        return show(
            context = context,
            title = title,
            message = message,
            details = null,
            buttonText = confirmText,
            discRes = R.drawable.zd_alert_disc_amber,
            iconRes = R.drawable.ic_alert_28,
            onDismiss = null,
            cancelText = cancelText,
            onConfirm = {
                onConfirm(working.indices.filter { working[it] }.toSet())
            },
            slotView = buildScroller(context, rows)
        )
    }

    /**
     * Rows wrap their own height so a short list is not a tall empty box, while a long one
     * (mauzas, tehsils) is capped so the buttons stay on screen.
     */
    private fun buildScroller(context: Context, rows: View): ScrollView {
        val maxListHeight = dp(context, 320)
        return ScrollView(context).apply {
            isFillViewport = false
            addView(rows)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            post {
                if (height > maxListHeight) {
                    layoutParams = layoutParams.apply { height = maxListHeight }
                    requestLayout()
                }
            }
        }
    }
    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

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
        dangerButton: Boolean = false,
        onCancel: (() -> Unit)? = null,
        slotView: View? = null,
        hideOk: Boolean = false
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_zd_alert, null)

        view.findViewById<View>(R.id.fl_alert_disc).setBackgroundResource(discRes)
        view.findViewById<ImageView>(R.id.iv_alert_icon).setImageResource(iconRes)
        view.findViewById<TextView>(R.id.tv_alert_title).text = title

        val messageView = view.findViewById<TextView>(R.id.tv_alert_message)
        if (message.isBlank()) {
            messageView.visibility = View.GONE
        } else {
            messageView.visibility = View.VISIBLE
            messageView.text = message
        }

        val detailsBox = view.findViewById<View>(R.id.ll_alert_details)
        if (details.isNullOrEmpty()) {
            detailsBox.visibility = View.GONE
        } else {
            detailsBox.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tv_alert_details).text =
                details.joinToString("\n")
        }

        // Slot is used by list() and custom(); plain alerts leave it gone.
        val slot = view.findViewById<FrameLayout>(R.id.fl_alert_slot)
        if (slotView == null) {
            slot.visibility = View.GONE
        } else {
            slot.visibility = View.VISIBLE
            (slotView.parent as? ViewGroup)?.removeView(slotView)
            slot.addView(slotView)
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
            cancelBtn.setOnClickListener {
                dialog.dismiss()
                onCancel?.invoke()
            }
        }

        val okBtn = view.findViewById<TextView>(R.id.btn_alert_ok)
        if (hideOk) {
            okBtn.visibility = View.GONE
            // With no OK button the lone Cancel should not hug one edge.
            (cancelBtn.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.weight = 1f
                cancelBtn.layoutParams = it
            }
            cancelBtn.gravity = Gravity.CENTER
        } else {
            okBtn.text = buttonText
            if (dangerButton) {
                okBtn.setBackgroundResource(R.drawable.zd_btn_danger_solid)
            }
            okBtn.setOnClickListener {
                dialog.dismiss()
                onConfirm?.invoke()
            }
        }

        dialog.setOnDismissListener { onDismiss?.invoke() }
        dialog.show()
        return dialog
    }
}
