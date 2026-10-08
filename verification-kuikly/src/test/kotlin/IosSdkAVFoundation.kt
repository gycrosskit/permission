package platform.AVFoundation

const val AVAuthorizationStatusAuthorized = 3
const val AVAuthorizationStatusDenied = 2
const val AVAuthorizationStatusRestricted = 1
const val AVMediaTypeAudio = "audio"
const val AVMediaTypeVideo = "video"
val nativeRequests = mutableListOf<String?>()
val nativeCallbacks = mutableListOf<(Boolean) -> Unit>()
class AVCaptureDevice { companion object }
fun AVCaptureDevice.Companion.authorizationStatusForMediaType(type: String?): Int = 0
fun AVCaptureDevice.Companion.requestAccessForMediaType(type: String?, callback: (Boolean) -> Unit) {
    nativeRequests.add(type)
    nativeCallbacks.add(callback)
}
