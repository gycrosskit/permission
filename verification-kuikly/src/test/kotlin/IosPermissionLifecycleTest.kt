package io.github.gycrosskit.permission

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import platform.AVFoundation.nativeCallbacks
import platform.AVFoundation.nativeRequests
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.locationManagers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Production iOS Kotlin with Apple API doubles; no real system permission prompt. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPermissionLifecycleTest {
    @Test fun cancelledCaptureWaitsForActualCompletionBeforeAnotherPermission() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        nativeRequests.clear()
        nativeCallbacks.clear()
        try {
            coroutineScope {
                val platform = IosPermissionPlatform()
                val first = async { platform.request(AppPermission.CAMERA) }
                runCurrent()
                val old = nativeCallbacks.single()
                first.cancel()
                runCurrent()
                val next = async { platform.request(AppPermission.MICROPHONE) }
                runCurrent()
                assertEquals(listOf<String?>("video"), nativeRequests)
                old(true)
                runCurrent()
                assertEquals(listOf<String?>("video", "audio"), nativeRequests)
                old(false)
                runCurrent()
                assertFalse(next.isCompleted, "late old callback cannot complete new owner")
                nativeCallbacks.last()(true)
                assertEquals(PermissionStatus.GRANTED, next.await())
                assertTrue(first.isCancelled)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun cancelledLocationAndQueuedCallerKeepNativeBarrier() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        nativeRequests.clear()
        nativeCallbacks.clear()
        locationManagers.clear()
        try {
            coroutineScope {
                val platform = IosPermissionPlatform()
                val first = async { platform.request(AppPermission.LOCATION_WHEN_IN_USE) }
                runCurrent()
                val manager = locationManagers.single()
                assertEquals(1, manager.requests)
                first.cancel()
                runCurrent()
                val abandoned = async { platform.request(AppPermission.CAMERA) }
                runCurrent()
                abandoned.cancel()
                runCurrent()
                val next = async { platform.request(AppPermission.MICROPHONE) }
                runCurrent()
                assertTrue(nativeRequests.isEmpty())
                manager.authorizationStatus = kCLAuthorizationStatusAuthorizedWhenInUse
                manager.delegate!!.locationManagerDidChangeAuthorization(manager)
                runCurrent()
                assertEquals(listOf<String?>("audio"), nativeRequests)
                manager.delegate!!.locationManagerDidChangeAuthorization(manager)
                runCurrent()
                assertFalse(next.isCompleted)
                nativeCallbacks.single()(false)
                assertEquals(PermissionStatus.DENIED, next.await())
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
