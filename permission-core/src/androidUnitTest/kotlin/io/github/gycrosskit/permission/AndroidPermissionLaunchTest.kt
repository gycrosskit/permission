package io.github.gycrosskit.permission

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AndroidPermissionLaunchTest {
    @Test fun launchFailureDoesNotRecordHistoryOrBlockNextRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val registry = FailingRegistry()
        ReflectionHelpers.setField(activity, "activityResultRegistry", registry)
        val history = AndroidPermissionRequestHistory()
        val platform = AndroidPermissionPlatform(history)
        platform.bind(activity)
        try {
            supervisorScope {
                val failed = async { platform.request(AppPermission.CAMERA) }
                runCurrent()
                assertFailsWith<ActivityNotFoundException> { failed.await() }
                assertFalse(history.wasRequested(activity, AppPermission.CAMERA.name))
                registry.fail = false
                val next = async { platform.request(AppPermission.CAMERA) }
                runCurrent()
                assertFalse(next.isCompleted)
                registry.dispatchResult(registry.code, emptyMap<String, Boolean>())
                runCurrent()
                assertEquals(PermissionStatus.DENIED, next.await())
            }
            registry.fail = true
            supervisorScope {
                val failed = async { activity.requestRuntimePermission(Manifest.permission.CAMERA, history) }
                runCurrent()
                assertFailsWith<ActivityNotFoundException> { failed.await() }
                assertFalse(history.wasRequested(activity, Manifest.permission.CAMERA))
            }
        } finally {
            platform.unbind(activity)
            controller.destroy()
            Dispatchers.resetMain()
        }
    }

    private class FailingRegistry : ActivityResultRegistry() {
        var fail = true
        var code = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            code = requestCode
            if (fail) throw ActivityNotFoundException("Permission UI unavailable")
        }
    }
}
