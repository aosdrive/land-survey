package pk.gop.pulse.katchiAbadi.presentation.util

import pk.gop.pulse.katchiAbadi.common.Event

sealed class UiEvent : Event() {
    data class ShowToast(val message: String) : UiEvent()
    data class Navigate(val route: String) : UiEvent()
    object NavigateUp : UiEvent()
    object OnLogin : UiEvent()
}