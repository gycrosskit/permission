package io.github.gycrosskit.permission.consumer

import io.github.gycrosskit.permission.AppPermission
import io.github.gycrosskit.permission.PermissionPlatform
import io.github.gycrosskit.permission.PermissionStatus

suspend fun cameraIsUsable(platform: PermissionPlatform): Boolean =
    platform.getStatus(AppPermission.CAMERA).let(PermissionStatus::allowsUse)
