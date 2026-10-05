package io.github.gycrosskit.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android 运行时权限的稳定语义。 */
enum class AndroidRuntimePermissionState {
    /** 系统已授权。 */
    GRANTED,
    /** 尚无申请历史且系统不要求解释。 */
    NOT_REQUESTED,
    /** 本次拒绝，或系统仍允许展示申请解释。 */
    DENIED,
    /** 已申请且系统不再显示解释；仅是结合历史推断，不能证明永久拒绝。 */
    REQUESTED_WITHOUT_RATIONALE,
}

/**
 * 申请单个系统权限；自动切主线程。刚拒绝返回 DENIED，下次检查才推断系统阻止重申。
 * 取消注销本次 launcher，不关闭系统弹窗；宿主避免并行发起多个请求。
 * Launcher 注册/启动异常向上传播，只在系统受理后记录历史，不把启动失败当作用户拒绝。
 * @param permission Android Manifest 权限名，由宿主提前声明。
 * @param history 宿主共用的申请历史；不隐式创建另一个 namespace。
 */
suspend fun ComponentActivity.requestRuntimePermission(
    permission: String,
    history: AndroidPermissionRequestHistory,
): AndroidRuntimePermissionState = withContext(Dispatchers.Main.immediate) {
    when (val status = runtimePermissionState(permission, history.wasRequested(this@requestRuntimePermission, permission))) {
        AndroidRuntimePermissionState.GRANTED,
        AndroidRuntimePermissionState.REQUESTED_WITHOUT_RATIONALE,
        -> status
        AndroidRuntimePermissionState.NOT_REQUESTED,
        AndroidRuntimePermissionState.DENIED,
        -> {
            val granted = suspendCancellableCoroutine { continuation ->
                lateinit var launcher: ActivityResultLauncher<String>
                launcher = activityResultRegistry.register(
                    "runtime_permission_${System.nanoTime()}",
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    launcher.unregister()
                    if (continuation.isActive) continuation.resume(granted)
                }
                continuation.invokeOnCancellation { runOnUiThread { launcher.unregister() } }
                if (continuation.isActive) {
                    runCatching {
                        launcher.launch(permission)
                        history.markRequested(this@requestRuntimePermission, permission)
                    }.onFailure {
                        launcher.unregister()
                        if (continuation.isActive) continuation.resumeWithException(it)
                    }
                } else {
                    launcher.unregister()
                }
            }
            if (granted) AndroidRuntimePermissionState.GRANTED else AndroidRuntimePermissionState.DENIED
        }
    }
}

/**
 * Android 没有直接暴露“首次申请”和“永久拒绝”，需要结合系统 rationale 与 App 申请历史推断。
 * rationale 优先于历史，兼容升级前已经拒绝、但新版本尚未建立本地历史的用户。
 */
internal fun resolveAndroidRuntimePermissionState(
    granted: Boolean,
    requestedBefore: Boolean,
    shouldShowRationale: Boolean,
): AndroidRuntimePermissionState = when {
    granted -> AndroidRuntimePermissionState.GRANTED
    shouldShowRationale -> AndroidRuntimePermissionState.DENIED
    requestedBefore -> AndroidRuntimePermissionState.REQUESTED_WITHOUT_RATIONALE
    else -> AndroidRuntimePermissionState.NOT_REQUESTED
}

internal fun ComponentActivity.runtimePermissionState(
    permission: String,
    requestedBefore: Boolean,
): AndroidRuntimePermissionState = resolveAndroidRuntimePermissionState(
    granted = isRuntimePermissionGranted(permission),
    requestedBefore = requestedBefore,
    shouldShowRationale = shouldShowRequestPermissionRationale(permission),
)

internal fun Context.isRuntimePermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun ComponentActivity.shouldShowAnyPermissionRationale(
    permissions: Collection<String>,
): Boolean = permissions.any(::shouldShowRequestPermissionRationale)

/**
 * 统一保存系统权限申请历史；在主线程与权限入口一起访问。
 * @param preferencesName null 只在当前实例内保存；非 null 使用同名 SharedPreferences 跨进程持久化。
 */
class AndroidPermissionRequestHistory(
    private val preferencesName: String? = null,
) {
    private val requestedKeys = mutableSetOf<String>()

    /**
     * 查询实例缓存和可选持久化历史。
     * @param context 仅用于本次访问 SharedPreferences，不持有其引用。
     * @param key 与旧宿主保持一致的权限键，组件权限名与 Manifest 名不可混用。
     */
    fun wasRequested(context: Context, key: String): Boolean =
        key in requestedKeys || preferences(context)?.getBoolean(key, false) == true

    /**
     * 系统申请发起前记录历史；不表示用户已授权。
     * @param context 仅用于本次访问 SharedPreferences。
     * @param key 与 wasRequested 使用相同权限键。
     */
    fun markRequested(context: Context, key: String) {
        requestedKeys += key
        preferences(context)?.edit()?.putBoolean(key, true)?.apply()
    }

    private fun preferences(context: Context) = preferencesName?.let { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
