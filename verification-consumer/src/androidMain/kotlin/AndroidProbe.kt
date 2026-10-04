package io.github.gycrosskit.permission.consumer

import io.github.gycrosskit.permission.AndroidPermissionPlatform
import io.github.gycrosskit.permission.AndroidPermissionRequestHistory
import io.github.gycrosskit.permission.AndroidRuntimePermissionState
import io.github.gycrosskit.permission.requestRuntimePermission
import androidx.activity.ComponentActivity

fun androidPermissionPlatform() = AndroidPermissionPlatform()

fun migratedPermissionPlatform() = AndroidPermissionPlatform(historyPreferencesName = "app_permission_history")

fun permissionPlatformWithHistory(history: AndroidPermissionRequestHistory) =
    AndroidPermissionPlatform(requestHistory = history)

suspend fun mediaPermission(activity: ComponentActivity, permission: String, history: AndroidPermissionRequestHistory):
    AndroidRuntimePermissionState = activity.requestRuntimePermission(permission, history)
