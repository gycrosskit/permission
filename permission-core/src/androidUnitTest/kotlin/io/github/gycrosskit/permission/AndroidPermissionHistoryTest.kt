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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidPermissionHistoryTest {
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
