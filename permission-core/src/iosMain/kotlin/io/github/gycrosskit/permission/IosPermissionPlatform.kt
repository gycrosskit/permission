package io.github.gycrosskit.permission

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS 原生权限实现。AVFoundation 权限直接桥接 completion handler；定位通过长期持有的
 * `CLLocationManager` delegate 恢复挂起调用，不依赖 UIViewController。
 *
 * iOS 宿主仍必须在 Info.plist 配置对应 Usage Description，否则系统会终止应用。
 */
@OptIn(ExperimentalForeignApi::class)
class IosPermissionPlatform : PermissionPlatform {
    // CoreLocation 在创建 manager 的线程 RunLoop 投递 delegate；后台构造组件时延迟到主线程创建。
    private val locationManager: CLLocationManager by lazy { CLLocationManager().also { it.delegate = locationDelegate } }
    private val requestMutex = Mutex()
    private val locationDelegate = LocationAuthorizationDelegate { status ->
        completeLocationRequest(locationStatus(status, locationManager.accuracyAuthorization))
    }
    private var locationContinuation: CancellableContinuation<PermissionStatus>? = null

    override suspend fun getStatus(permission: AppPermission): PermissionStatus = withContext(Dispatchers.Main.immediate) {
        val systemStatus = when (permission) {
            AppPermission.CAMERA -> captureStatus(AVMediaTypeVideo)
            AppPermission.MICROPHONE -> captureStatus(AVMediaTypeAudio)
            AppPermission.LOCATION_WHEN_IN_USE -> locationStatus(
                CLLocationManager.authorizationStatus(),
                locationManager.accuracyAuthorization,
            )
        }
        systemStatus
    }

    override suspend fun request(permission: AppPermission): PermissionStatus =
        withContext(Dispatchers.Main.immediate) {
            requestMutex.withLock {
                val current = getStatus(permission)
                if (current != PermissionStatus.NOT_DETERMINED) {
                    return@withLock current
                }
                when (permission) {
                    AppPermission.CAMERA -> requestCapture(AVMediaTypeVideo)
                    AppPermission.MICROPHONE -> requestCapture(AVMediaTypeAudio)
                    AppPermission.LOCATION_WHEN_IN_USE -> requestLocationWhenInUse()
                }
            }
        }

    private fun captureStatus(mediaType: String?): PermissionStatus =
        when (AVCaptureDevice.authorizationStatusForMediaType(mediaType)) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
            AVAuthorizationStatusDenied -> PermissionStatus.DENIED
            AVAuthorizationStatusRestricted -> PermissionStatus.RESTRICTED
            else -> PermissionStatus.NOT_DETERMINED
        }

    private suspend fun requestCapture(
        mediaType: String?,
    ): PermissionStatus = suspendCancellableCoroutine { continuation ->
        AVCaptureDevice.requestAccessForMediaType(mediaType) { granted: Boolean ->
            if (continuation.isActive) {
                continuation.resume(
                    if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                )
            }
        }
    }

    private suspend fun requestLocationWhenInUse(): PermissionStatus =
        suspendCancellableCoroutine { continuation ->
            locationContinuation?.let { previous ->
                if (previous.isActive) previous.resume(PermissionStatus.DENIED)
            }
            locationContinuation = continuation
            continuation.invokeOnCancellation {
                if (locationContinuation === continuation) locationContinuation = null
            }
            locationManager.requestWhenInUseAuthorization()
        }

    private fun completeLocationRequest(status: PermissionStatus) {
        if (status == PermissionStatus.NOT_DETERMINED) return
        val continuation = locationContinuation ?: return
        locationContinuation = null
        if (continuation.isActive) continuation.resume(status)
    }

    private fun locationStatus(
        status: CLAuthorizationStatus,
        accuracy: CLAccuracyAuthorization,
    ): PermissionStatus = when (status) {
        kCLAuthorizationStatusAuthorizedAlways,
        kCLAuthorizationStatusAuthorizedWhenInUse,
        -> if (accuracy == CLAccuracyAuthorization.CLAccuracyAuthorizationReducedAccuracy) {
            PermissionStatus.LIMITED
        } else {
            PermissionStatus.GRANTED
        }
        kCLAuthorizationStatusDenied -> PermissionStatus.DENIED
        kCLAuthorizationStatusRestricted -> PermissionStatus.RESTRICTED
        kCLAuthorizationStatusNotDetermined -> PermissionStatus.NOT_DETERMINED
        else -> PermissionStatus.NOT_DETERMINED
    }

    /** ObjC delegate 与 Kotlin 业务接口分离，避免一个类型混合 Kotlin 和 Objective-C supertypes。 */
    private class LocationAuthorizationDelegate(
        private val onChanged: (CLAuthorizationStatus) -> Unit,
    ) : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            onChanged(manager.authorizationStatus)
        }

        override fun locationManager(
            manager: CLLocationManager,
            didChangeAuthorizationStatus: CLAuthorizationStatus,
        ) {
            onChanged(didChangeAuthorizationStatus)
        }
    }
}
