package io.github.gycrosskit.permission

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidRuntimePermissionTest {
    @Test
    fun `new denial stays denied and next request observes blocked history`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val history = AndroidPermissionRequestHistory()
            val result = async { activity.requestRuntimePermission(Manifest.permission.CAMERA, history) }
            runCurrent()
            val code = shadowOf(activity).lastRequestedPermission.requestCode
            assertTrue(activity.activityResultRegistry.dispatchResult(code, false))
            assertEquals(AndroidRuntimePermissionState.DENIED, result.await())
            assertEquals(
                AndroidRuntimePermissionState.REQUESTED_WITHOUT_RATIONALE,
                activity.requestRuntimePermission(Manifest.permission.CAMERA, history),
            )
        } finally {
            controller.destroy()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cancellation unregisters only its launcher and ignores late result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val old = async { activity.requestRuntimePermission(Manifest.permission.CAMERA, AndroidPermissionRequestHistory()) }
            runCurrent()
            val oldCode = shadowOf(activity).lastRequestedPermission.requestCode
            old.cancel()
            runCurrent()
            val newest = async {
                activity.requestRuntimePermission(Manifest.permission.CAMERA, AndroidPermissionRequestHistory())
            }
            runCurrent()
            // 同一 Activity 的旧系统权限框仍在时，Android 拒绝新的并行申请；取消不能关闭该框。
            assertEquals(AndroidRuntimePermissionState.DENIED, newest.await())
            // AndroidX 可保留已 launch 的 requestCode 接收迟到系统结果；取消的回调不能复活。
            activity.activityResultRegistry.dispatchResult(oldCode, true)
            assertTrue(old.isCancelled)
            assertEquals(AndroidRuntimePermissionState.DENIED, newest.await())
        } finally {
            controller.destroy()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `same history object preserves process scope and new instance stays unrequested`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val history = AndroidPermissionRequestHistory()
            history.markRequested(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            assertTrue(history.wasRequested(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            assertFalse(AndroidPermissionRequestHistory().wasRequested(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            assertFalse(history.wasRequested(activity, Manifest.permission.CAMERA))
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun `old explicit namespace and granted state bypass system request`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            val namespace = "runtime_permission_test"
            activity.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit()
                .putBoolean(Manifest.permission.CAMERA, true).commit()
            val history = AndroidPermissionRequestHistory(namespace)
            assertEquals(
                AndroidRuntimePermissionState.REQUESTED_WITHOUT_RATIONALE,
                activity.requestRuntimePermission(Manifest.permission.CAMERA, history),
            )
            shadowOf(activity).grantPermissions(Manifest.permission.CAMERA)
            assertEquals(
                AndroidRuntimePermissionState.GRANTED,
                activity.requestRuntimePermission(Manifest.permission.CAMERA, history),
            )
            assertTrue(AndroidPermissionRequestHistory(namespace).wasRequested(activity, Manifest.permission.CAMERA))
            activity.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().commit()
        } finally {
            controller.destroy()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `rationale takes priority over missing history`() {
        assertEquals(AndroidRuntimePermissionState.DENIED, resolveAndroidRuntimePermissionState(false, false, true))
        assertEquals(AndroidRuntimePermissionState.NOT_REQUESTED, resolveAndroidRuntimePermissionState(false, false, false))
        assertEquals(AndroidRuntimePermissionState.REQUESTED_WITHOUT_RATIONALE, resolveAndroidRuntimePermissionState(false, true, false))
    }
}
