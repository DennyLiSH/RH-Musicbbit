package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PermissionDiagnosticsViewModel].
 *
 * Uses a mocked [PermissionPort] so no Android framework classes are needed.
 * The ViewModel reads Build.VERSION.SDK_INT at construction time; tests run on
 * whatever SDK the test JVM shadows. The permission list size therefore varies
 * by SDK level; tests assert relative to the actual list size.
 *
 * Permission rows use [PermissionKey] enum + [nameResId]/[descriptionResId]
 * instead of stringly dispatch (Plan B Task 7).
 */
class PermissionDiagnosticsViewModelTest {

    private lateinit var permissionPort: PermissionPort

    @Before
    fun setUp() {
        permissionPort = mock()
    }

    private fun allGrantedPort(): PermissionPort = mock<PermissionPort>().apply {
        whenever(canScheduleExactAlarms()).thenReturn(true)
        whenever(checkPermission(any())).thenReturn(true)
        whenever(isFullScreenIntentGranted()).thenReturn(true)
    }

    private fun allDeniedPort(): PermissionPort = mock<PermissionPort>().apply {
        whenever(canScheduleExactAlarms()).thenReturn(false)
        whenever(checkPermission(any())).thenReturn(false)
        whenever(isFullScreenIntentGranted()).thenReturn(false)
    }

    @Test
    fun `init loads permissions with allGranted true when all permissions granted`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val state = viewModel.uiState.value
        assertTrue(state.allGranted)
        assertFalse(state.permissions.isEmpty())
        state.permissions.forEach { perm ->
            assertTrue("Permission ${perm.key} should be granted", perm.isGranted)
        }
    }

    @Test
    fun `init loads permissions with allGranted false when some permissions denied`() {
        val viewModel = PermissionDiagnosticsViewModel(allDeniedPort())

        val state = viewModel.uiState.value
        assertFalse(state.allGranted)
        assertTrue(
            "At least one permission should be denied",
            state.permissions.any { !it.isGranted }
        )
    }

    @Test
    fun `permission list contains expected permission keys`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val keys = viewModel.uiState.value.permissions.map { it.key }
        assertTrue("Should contain READ_MEDIA_AUDIO", keys.contains(PermissionKey.READ_MEDIA_AUDIO))
        assertTrue("Should contain FOREGROUND_SERVICE", keys.contains(PermissionKey.FOREGROUND_SERVICE))
        assertTrue("Should contain FULL_SCREEN_INTENT", keys.contains(PermissionKey.FULL_SCREEN_INTENT))
        assertTrue("Should contain BOOT_COMPLETED", keys.contains(PermissionKey.BOOT_COMPLETED))
    }

    @Test
    fun `every permission row has a non-zero nameResId and descriptionResId`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        viewModel.uiState.value.permissions.forEach { perm ->
            assertNotEquals("nameResId for ${perm.key} must be a real string", 0, perm.nameResId)
            assertNotEquals("descriptionResId for ${perm.key} must be a real string", 0, perm.descriptionResId)
        }
    }

    @Test
    fun `foreground service permission is not runtime and not requestable`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val fgService = viewModel.uiState.value.permissions.first {
            it.key == PermissionKey.FOREGROUND_SERVICE
        }
        assertFalse(fgService.isRuntime)
        assertFalse(fgService.canRequest)
    }

    @Test
    fun `boot completed permission is not runtime and not requestable`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val bootPerm = viewModel.uiState.value.permissions.first {
            it.key == PermissionKey.BOOT_COMPLETED
        }
        assertFalse(bootPerm.isRuntime)
        assertFalse(bootPerm.canRequest)
    }

    @Test
    fun `read media audio permission is runtime and requestable`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val readMedia = viewModel.uiState.value.permissions.first {
            it.key == PermissionKey.READ_MEDIA_AUDIO
        }
        assertTrue(readMedia.isRuntime)
        assertTrue(readMedia.canRequest)
    }

    @Test
    fun `refreshPermissions updates state when permissions change`() {
        val mutablePort = mock<PermissionPort>().apply {
            whenever(canScheduleExactAlarms()).thenReturn(false)
            whenever(checkPermission(any())).thenReturn(false)
            whenever(isFullScreenIntentGranted()).thenReturn(false)
        }
        val vm = PermissionDiagnosticsViewModel(mutablePort)
        assertFalse(vm.uiState.value.allGranted)

        whenever(mutablePort.canScheduleExactAlarms()).thenReturn(true)
        whenever(mutablePort.checkPermission(any())).thenReturn(true)
        whenever(mutablePort.isFullScreenIntentGranted()).thenReturn(true)

        vm.refreshPermissions()

        assertTrue(vm.uiState.value.allGranted)
    }

    @Test
    fun `refreshPermissions calls port methods`() {
        val port = allGrantedPort()
        val viewModel = PermissionDiagnosticsViewModel(port)

        viewModel.refreshPermissions()

        verify(port, atLeast(1)).checkPermission(any())
        verify(port, atLeast(1)).isFullScreenIntentGranted()
    }

    @Test
    fun `full screen intent permission reflects port return value`() {
        val port = mock<PermissionPort>().apply {
            whenever(canScheduleExactAlarms()).thenReturn(true)
            whenever(checkPermission(any())).thenReturn(true)
            whenever(isFullScreenIntentGranted()).thenReturn(false)
        }
        val viewModel = PermissionDiagnosticsViewModel(port)

        val fsi = viewModel.uiState.value.permissions.first {
            it.key == PermissionKey.FULL_SCREEN_INTENT
        }
        assertFalse(fsi.isGranted)
    }

    @Test
    fun `uiState is initially populated on construction`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        assertTrue(viewModel.uiState.value.permissions.isNotEmpty())
    }
}