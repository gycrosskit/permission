package io.github.gycrosskit.permission

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.async

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidPermissionHistoryTest {
    @Test fun temporaryHostRestoresPreviousHostAndDuplicateBindingFails() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val firstController = Robolectric.buildActivity(PermissionHistoryHostActivity::class.java).setup()
        val secondController = Robolectric.buildActivity(PermissionHistoryHostActivity::class.java).setup()
        val first = firstController.get()
        val second = secondController.get()
        val platform = AndroidPermissionPlatform(AndroidPermissionRequestHistory())
        try {
            first.showRationale = true
            platform.bind(first)
            platform.bind(second)
            assertFailsWith<IllegalStateException> { platform.bind(second) }
            assertEquals(PermissionStatus.NOT_DETERMINED, platform.getStatus(AppPermission.CAMERA))
            platform.unbind(second)
            platform.unbind(second)
            assertEquals(PermissionStatus.DENIED, platform.getStatus(AppPermission.CAMERA))
            platform.unbind(first)
            val waiting = async { platform.getStatus(AppPermission.CAMERA) }
            runCurrent()
            kotlin.test.assertFalse(waiting.isCompleted, "missing host waits instead of using a stale Activity")
            platform.bind(first)
            assertEquals(PermissionStatus.DENIED, waiting.await())
        } finally {
            platform.unbind(first)
            platform.unbind(second)
            firstController.destroy()
            secondController.destroy()
            Dispatchers.resetMain()
        }
    }

    @Test fun oldNamespaceSurvivesInstanceReplacement() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            activity.getSharedPreferences("app_permission_history", Context.MODE_PRIVATE)
                .edit().putBoolean("CAMERA", true).commit()
            repeat(2) {
                val platform = AndroidPermissionPlatform(historyPreferencesName = "app_permission_history")
                platform.bind(activity)
                assertEquals(PermissionStatus.DENIED, platform.getStatus(AppPermission.CAMERA))
                assertEquals(PermissionStatus.NOT_DETERMINED, platform.getStatus(AppPermission.MICROPHONE))
                platform.unbind(activity)
            }
            val defaultPlatform = AndroidPermissionPlatform()
            defaultPlatform.bind(activity)
            assertEquals(PermissionStatus.NOT_DETERMINED, defaultPlatform.getStatus(AppPermission.CAMERA))
            val migrated = AndroidPermissionPlatform("app_permission_history")
            migrated.bind(activity)
            shadowOf(activity).grantPermissions(Manifest.permission.CAMERA)
            assertEquals(PermissionStatus.GRANTED, migrated.getStatus(AppPermission.CAMERA))
            migrated.unbind(activity)
            defaultPlatform.unbind(activity)
        } finally {
            controller.destroy()
            Dispatchers.resetMain()
        }
    }
}

/** 仅替换系统 rationale 事实；宿主栈和等待逻辑仍来自生产实现。 */
class PermissionHistoryHostActivity : ComponentActivity() {
    var showRationale = false
    override fun shouldShowRequestPermissionRationale(permission: String): Boolean = showRationale
}
