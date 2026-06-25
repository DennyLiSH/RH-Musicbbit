package com.rabbithole.musicbbit.presentation.settings

import com.rabbithole.musicbbit.service.alarm.ports.PermissionPort
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PermissionDiagnosticsViewModel].
 *
 * Uses a mocked [PermissionPort] so no Android framework classes are needed.
 * Because the ViewModel reads Build.VERSION.SDK_INT at construction time,
 * tests run under whatever SDK the test JVM shadows (typically API 33 via
 * Robolectric or the raw JVM default). The permission list size therefore
 * varies by SDK level; tests assert relative to the actual list size.
 */
class PermissionDiagnosticsViewModelTest {

    private lateinit var permissionPort: PermissionPort

    @Before
    fun setUp() {
        permissionPort = mock()
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Tests: all granted
    // ------------------------------------------------------------------

    @Test
    fun `init loads permissions with allGranted true when all permissions granted`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val state = viewModel.uiState.value
        assertTrue(state.allGranted)
        assertFalse(state.permissions.isEmpty())
        // Every permission entry should be granted
        state.permissions.forEach { perm ->
            assertTrue("Permission '${perm.name}' should be granted", perm.isGranted)
        }
    }

    // ------------------------------------------------------------------
    // Tests: some denied
    // ------------------------------------------------------------------

    @Test
    fun `init loads permissions with allGranted false when some permissions denied`() {
        val viewModel = PermissionDiagnosticsViewModel(allDeniedPort())

        val state = viewModel.uiState.value
        assertFalse(state.allGranted)
        assertFalse(state.permissions.isEmpty())
        // At least one permission should be denied
        assertTrue(
            "At least one permission should be denied",
            state.permissions.any { !it.isGranted }
        )
    }

    // ------------------------------------------------------------------
    // Tests: permission names
    // ------------------------------------------------------------------

    @Test
    fun `permission list contains expected permission names`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val names = viewModel.uiState.value.permissions.map { it.name }
        assertTrue(
            "Should contain Read Media Audio",
            names.contains(PermissionDiagnosticsViewModel.PERMISSION_NAME_READ_MEDIA_AUDIO)
        )
        assertTrue(
            "Should contain Foreground Service",
            names.contains(PermissionDiagnosticsViewModel.PERMISSION_NAME_FOREGROUND_SERVICE)
        )
        assertTrue(
            "Should contain Full Screen Intent",
            names.contains(PermissionDiagnosticsViewModel.PERMISSION_NAME_FULL_SCREEN_INTENT)
        )
        assertTrue(
            "Should contain Boot Completed",
            names.contains(PermissionDiagnosticsViewModel.PERMISSION_NAME_BOOT_COMPLETED)
        )
    }

    // ------------------------------------------------------------------
    // Tests: runtime vs non-runtime
    // ------------------------------------------------------------------

    @Test
    fun `foreground service permission is not runtime and not requestable`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val fgService = viewModel.uiState.value.permissions.first {
            it.name == PermissionDiagnosticsViewModel.PERMISSION_NAME_FOREGROUND_SERVICE
        }
        assertFalse(fgService.isRuntime)
        assertFalse(fgService.canRequest)
    }

    @Test
    fun `boot completed permission is not runtime and not requestable`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val bootPerm = viewModel.uiState.value.permissions.first {
            it.name == PermissionDiagnosticsViewModel.PERMISSION_NAME_BOOT_COMPLETED
        }
        assertFalse(bootPerm.isRuntime)
        assertFalse(bootPerm.canRequest)
    }

    @Test
    fun `read media audio permission is runtime and requestable`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        val readMedia = viewModel.uiState.value.permissions.first {
            it.name == PermissionDiagnosticsViewModel.PERMISSION_NAME_READ_MEDIA_AUDIO
        }
        assertTrue(readMedia.isRuntime)
        assertTrue(readMedia.canRequest)
    }

    // ------------------------------------------------------------------
    // Tests: refreshPermissions
    // ------------------------------------------------------------------

    @Test
    fun `refreshPermissions updates state when permissions change`() {
        // Start with all denied
        val viewModel = PermissionDiagnosticsViewModel(allDeniedPort())
        assertFalse(viewModel.uiState.value.allGranted)

        // Re-configure the port to return granted (but the viewModel captured the port reference)
        // Since the port is captured at construction, we use a different approach:
        // Create a new viewModel with a port that changes behavior
        val mutablePort = mock<PermissionPort>().apply {
            whenever(canScheduleExactAlarms()).thenReturn(false)
            whenever(checkPermission(any())).thenReturn(false)
            whenever(isFullScreenIntentGranted()).thenReturn(false)
        }
        val vm = PermissionDiagnosticsViewModel(mutablePort)
        assertFalse(vm.uiState.value.allGranted)

        // Change the port behavior
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

        // Verify the port was queried
        verify(port, atLeast(1)).checkPermission(any())
        verify(port, atLeast(1)).isFullScreenIntentGranted()
    }

    // ------------------------------------------------------------------
    // Tests: specific permission reflects port value
    // ------------------------------------------------------------------

    @Test
    fun `full screen intent permission reflects port return value`() {
        val port = mock<PermissionPort>().apply {
            whenever(canScheduleExactAlarms()).thenReturn(true)
            whenever(checkPermission(any())).thenReturn(true)
            whenever(isFullScreenIntentGranted()).thenReturn(false)
        }
        val viewModel = PermissionDiagnosticsViewModel(port)

        val fsi = viewModel.uiState.value.permissions.first {
            it.name == PermissionDiagnosticsViewModel.PERMISSION_NAME_FULL_SCREEN_INTENT
        }
        assertFalse(fsi.isGranted)
    }

    @Test
    fun `uiState is initially populated on construction`() {
        val viewModel = PermissionDiagnosticsViewModel(allGrantedPort())

        // Should already have permissions populated from init block
        assertTrue(viewModel.uiState.value.permissions.isNotEmpty())
    }
}
