package io.github.gycrosskit.permission.consumer

import io.github.gycrosskit.permission.AndroidPermissionPlatform

fun androidPermissionPlatform() = AndroidPermissionPlatform()

fun migratedPermissionPlatform() = AndroidPermissionPlatform(historyPreferencesName = "app_permission_history")
