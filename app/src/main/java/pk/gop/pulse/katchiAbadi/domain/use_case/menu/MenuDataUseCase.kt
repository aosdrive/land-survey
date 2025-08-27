package pk.gop.pulse.katchiAbadi.domain.use_case.menu

data class MenuDataUseCase(
    val getSyncDataUseCase: GetSyncDataUseCase,
    val getMouzaDataUseCase: GetMouzaDataUseCase,
    val fetchMauzaSyncUseCase: FetchMauzaSyncUseCase,
)
