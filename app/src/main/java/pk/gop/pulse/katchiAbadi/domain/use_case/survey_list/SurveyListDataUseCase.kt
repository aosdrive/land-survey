package pk.gop.pulse.katchiAbadi.domain.use_case.survey_list

data class SurveyListDataUseCase(
    val getAllSurveysUseCase: GetAllSurveysUseCase,
    val getAllFilteredSurveysUseCase: GetAllFilteredSurveysUseCase,
)
