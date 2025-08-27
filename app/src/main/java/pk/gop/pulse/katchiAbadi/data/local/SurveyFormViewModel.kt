package pk.gop.pulse.katchiAbadi.data.local

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pk.gop.pulse.katchiAbadi.domain.model.SurveyImage

class SurveyFormViewModel : ViewModel() {

    private val _surveyImages = MutableStateFlow<List<SurveyImage>>(emptyList())
    val surveyImages = _surveyImages.asStateFlow()

    fun addImage(image: SurveyImage) {
        _surveyImages.value = _surveyImages.value + image
    }

    fun removeImage(image: SurveyImage) {
        _surveyImages.value = _surveyImages.value.toMutableList().apply {
            remove(image)
        }
    }
}
