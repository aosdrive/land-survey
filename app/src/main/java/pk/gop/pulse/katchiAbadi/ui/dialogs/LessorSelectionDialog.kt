package pk.gop.pulse.katchiAbadi.ui.dialogs

import android.content.Context
import pk.gop.pulse.katchiAbadi.domain.model.LessorEntity
import pk.gop.pulse.katchiAbadi.presentation.util.ZdAlert

/**
 * Corporate Farms lessor picker. Built entirely on ZdAlert so it matches every other
 * dialog in the app - ZdAlert.list provides the scrolling single-choice list.
 */
class LessorSelectionDialog(
    private val context: Context,
    private val lessors: List<LessorEntity>,
    private val currentSelectedId: Int?,
    private val onConfirm: (LessorEntity) -> Unit
) {
    fun show() {
        if (lessors.isEmpty()) {
            ZdAlert.warn(
                context = context,
                title = "No lessors",
                message = "No lessors are available right now. The list syncs automatically once you are online.",
                buttonText = "Got it"
            )
            return
        }

        val names = lessors.map {
            if (it.code.isNullOrBlank()) it.name else "${it.name}  (${it.code})"
        }

        ZdAlert.list(
            context = context,
            title = "Select lessor",
            items = names,
            // indexOfFirst returns -1 when nothing is picked yet, which leaves every row
            // unhighlighted - that is what we want on a first open.
            selectedIndex = lessors.indexOfFirst { it.id == currentSelectedId },
            cancelText = "Cancel"
        ) { index ->
            onConfirm(lessors[index])
        }
    }
}
