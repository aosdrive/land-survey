package pk.gop.pulse.katchiAbadi.domain.use_case.saved

data class SavedDataUseCase(
    val getAllSavedFormsUseCase: GetAllSavedFormsUseCase,
    val deleteSavedRecordUseCase: DeleteSavedRecordUseCase,
    val getSavedRecordByStatusAndLimitUseCase: GetSavedRecordByStatusAndLimitUseCase,
    val postSavedRecordUseCase: PostSavedRecordUseCase,
//    val postAllSavedRecordUseCase: PostAllSavedRecordUseCase,
    val viewSavedRecordUseCase: ViewSavedRecordUseCase,
)
