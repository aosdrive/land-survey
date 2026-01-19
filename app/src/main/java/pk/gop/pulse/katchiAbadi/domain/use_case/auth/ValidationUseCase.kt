package pk.gop.pulse.katchiAbadi.domain.use_case.auth

data class ValidationUseCase(
    val validateCredentials: ValidateCredentials,
    val validateCredentialsSur: ValidateCredentialsSur,
    val forgotPasswordUseCase: ForgotPasswordUseCase,
    val otpVerificationUseCase: OtpVerificationUseCase,
    val updatePasswordUseCase: UpdatePasswordUseCase,
    val checkAppVersionUseCase: CheckAppVersionUseCase // Add this

)
