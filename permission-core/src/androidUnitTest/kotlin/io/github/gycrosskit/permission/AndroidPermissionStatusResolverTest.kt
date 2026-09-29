package io.github.gycrosskit.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidPermissionStatusResolverTest {
    @Test
    fun `主权限授权时返回完整授权`() {
        assertStatus(PermissionStatus.GRANTED, primaryGranted = true)
    }

    @Test
    fun `替代权限授权时返回受限授权`() {
        assertStatus(PermissionStatus.LIMITED, alternativeGranted = true)
    }

    @Test
    fun `系统建议解释用途时返回拒绝`() {
        assertStatus(
            PermissionStatus.DENIED,
            shouldShowRationale = true,
            hasRequested = true,
        )
    }

    @Test
    fun `App 重启后仍根据系统解释状态识别已经拒绝`() {
        assertStatus(PermissionStatus.DENIED, shouldShowRationale = true)
    }

    @Test
    fun `从未申请且系统不建议解释时返回未决定`() {
        assertStatus(PermissionStatus.NOT_DETERMINED)
    }

    @Test
    fun `已经申请且系统不再建议解释时仍统一返回拒绝`() {
        assertStatus(PermissionStatus.DENIED, hasRequested = true)
    }

    private fun assertStatus(
        expected: PermissionStatus,
        primaryGranted: Boolean = false,
        alternativeGranted: Boolean = false,
        shouldShowRationale: Boolean = false,
        hasRequested: Boolean = false,
    ) {
        assertEquals(
            expected,
            resolveAndroidPermissionStatus(
                primaryGranted = primaryGranted,
                alternativeGranted = alternativeGranted,
                shouldShowRationale = shouldShowRationale,
                hasRequested = hasRequested,
            ),
        )
    }
}
