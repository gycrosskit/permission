package io.github.gycrosskit.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/** Android 运行时权限的稳定语义。 */
internal enum class AndroidRuntimePermissionState {
    GRANTED,
    NOT_REQUESTED,
    DENIED,
    REQUESTED_WITHOUT_RATIONALE,
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
 * 统一保存系统权限申请历史。未指定 [preferencesName] 时历史只在当前实例内有效；指定后跨进程持久化。
 */
internal class AndroidPermissionRequestHistory(
    private val preferencesName: String? = null,
) {
    private val requestedKeys = mutableSetOf<String>()

    fun wasRequested(context: Context, key: String): Boolean =
        key in requestedKeys || preferences(context)?.getBoolean(key, false) == true

    fun markRequested(context: Context, key: String) {
        requestedKeys += key
        preferences(context)?.edit()?.putBoolean(key, true)?.apply()
    }

    private fun preferences(context: Context) = preferencesName?.let { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
