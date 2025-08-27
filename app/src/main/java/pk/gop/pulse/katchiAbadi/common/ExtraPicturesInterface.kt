package pk.gop.pulse.katchiAbadi.common


interface ExtraPicturesInterface {
    fun makeAddMoreButtonVisible()
    fun checkCameraPermission(): Boolean
    fun requestCameraPermission(requestCode: Int)
    fun startImageCapture(requestCode: Int)
}