package pk.gop.pulse.katchiAbadi.domain.use_case.survey_form

import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main.SaveAllSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.main.SaveSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home.GetAllNotAtHomeUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home.NotAtHomeSaveAllUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.not_at_home.NotAtHomeSaveUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.temp.GetAllTempSurveyFormUseCase
import pk.gop.pulse.katchiAbadi.domain.use_case.survey_form.temp.TempSaveSurveyFormUseCase

data class SurveyFormUseCase(
    val saveSurveyFormUseCase: SaveSurveyFormUseCase,
    val saveAllSurveyFormUseCase: SaveAllSurveyFormUseCase,
    val saveTempSurveyFormUseCase: TempSaveSurveyFormUseCase,
    val getAllTempSurveyFormUseCase: GetAllTempSurveyFormUseCase,
    val notAtHomeSaveAllUseCase: NotAtHomeSaveAllUseCase,
    val notAtHomeSaveUseCase: NotAtHomeSaveUseCase,
    val getAllNotAtHomeUseCase: GetAllNotAtHomeUseCase,
)
