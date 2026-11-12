package com.msop.player.feature.local.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionStateTest {

    @Test
    fun `已授权时为 GRANTED`() {
        assertEquals(
            PermissionUiState.GRANTED,
            resolvePermissionState(granted = true, shouldShowRationale = false, askedBefore = true),
        )
    }

    @Test
    fun `拒绝过一次且系统允许再问时为 DENIED_CAN_ASK`() {
        assertEquals(
            PermissionUiState.DENIED_CAN_ASK,
            resolvePermissionState(granted = false, shouldShowRationale = true, askedBefore = true),
        )
    }

    @Test
    fun `拒绝且不再询问时为 DENIED_PERMANENTLY`() {
        assertEquals(
            PermissionUiState.DENIED_PERMANENTLY,
            resolvePermissionState(granted = false, shouldShowRationale = false, askedBefore = true),
        )
    }

    @Test
    fun `从未申请过时为 UNKNOWN`() {
        assertEquals(
            PermissionUiState.UNKNOWN,
            resolvePermissionState(granted = false, shouldShowRationale = false, askedBefore = false),
        )
    }

    @Test
    fun `首次申请前即被系统判定可问时仍为 DENIED_CAN_ASK`() {
        // 少数 ROM 在首次申请前就返回 rationale=true，此时应允许直接申请
        assertEquals(
            PermissionUiState.DENIED_CAN_ASK,
            resolvePermissionState(granted = false, shouldShowRationale = true, askedBefore = false),
        )
    }

    @Test
    fun `已授权优先于其他条件`() {
        assertEquals(
            PermissionUiState.GRANTED,
            resolvePermissionState(granted = true, shouldShowRationale = true, askedBefore = false),
        )
    }
}
