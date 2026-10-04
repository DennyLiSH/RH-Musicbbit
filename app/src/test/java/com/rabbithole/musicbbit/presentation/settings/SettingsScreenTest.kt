package com.rabbithole.musicbbit.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rabbithole.musicbbit.R
import com.rabbithole.musicbbit.TestActivity
import com.rabbithole.musicbbit.domain.model.ThemeMode
import com.rabbithole.musicbbit.navigation.About
import com.rabbithole.musicbbit.navigation.PermissionDiagnostics
import com.rabbithole.musicbbit.navigation.ScanDirectorySettings
import com.rabbithole.musicbbit.navigation.Settings
import dagger.hilt.android.testing.HiltTestApplication
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.times
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wiring-level tests — verify UI callback → ViewModel method only.
 * For behavior verification see ThemeViewModelTest, AlarmRingSettingsViewModelTest.
 *
 * Runs under Robolectric (JVM) to avoid Android 14+ JVMTI agent restrictions that
 * block mockk-android's inline mocking on instrumented environment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    private lateinit var themeViewModel: ThemeViewModel
    private lateinit var alarmRingViewModel: AlarmRingSettingsViewModel
    private lateinit var languageViewModel: LanguageViewModel

    @Before
    fun setUp() {
        themeViewModel = mock<ThemeViewModel>().also {
            whenever(it.uiState).thenReturn(
                MutableStateFlow(
                    ThemeViewModel.ThemeUiState(themeMode = ThemeMode.SYSTEM)
                )
            )
            whenever(it.messages).thenReturn(
                kotlinx.coroutines.flow.emptyFlow()
            )
        }
        alarmRingViewModel = mock<AlarmRingSettingsViewModel>().also {
            whenever(it.uiState).thenReturn(
                MutableStateFlow(
                    AlarmRingSettingsViewModel.AlarmRingSettingsUiState()
                )
            )
        }
        languageViewModel = mock<LanguageViewModel>().also {
            whenever(it.uiState).thenReturn(
                MutableStateFlow(
                    LanguageViewModel.LanguageUiState(language = AppLanguage.CHINESE)
                )
            )
        }
    }

    private fun str(@StringRes id: Int, vararg args: Any): String =
        composeTestRule.activity.getString(id, *args)

    private fun setContentWithNavHost(): NavController {
        lateinit var navController: NavController
        composeTestRule.setContent {
            navController = rememberNavController()
            NavHost(navController, startDestination = Settings) {
                composable<Settings> {
                    SettingsScreen(
                        navController = navController,
                        themeViewModel = themeViewModel,
                        alarmRingSettingsViewModel = alarmRingViewModel,
                        languageViewModel = languageViewModel
                    )
                }
                composable<ScanDirectorySettings> { }
                composable<PermissionDiagnostics> { }
                composable<About> { }
            }
        }
        return navController
    }

    @Test
    fun settingsTitleDisplayedInTopBar() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_title))
            .assertIsDisplayed()
    }

    @Test
    fun themeDropdownLabelDisplayed() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_theme))
            .assertIsDisplayed()
    }

    @Test
    fun volumeRampDropdownLabelDisplayed() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_volume_ramp_duration))
            .assertIsDisplayed()
    }

    @Test
    fun scanDirectoriesCardTitleDisplayed() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_scan_directories))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun permissionAndAboutCardTitlesDisplayed() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_permission_diagnostics))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.settings_about_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun themeDropdownSelectsDarkModeCallsViewModel() {
        setContentWithNavHost()

        composeTestRule.onAllNodesWithText(str(R.string.settings_theme_system))[0]
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText(str(R.string.settings_theme_dark))
            .filterToOne(hasClickAction())
            .performClick()

        verify(themeViewModel, times(1)).setThemeMode(ThemeMode.DARK)
    }

    @Test
    fun volumeRampDropdownSelectsDisabledCallsViewModel() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_volume_ramp_seconds, 5))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText(str(R.string.settings_volume_ramp_disabled))
            .filterToOne(hasClickAction())
            .performClick()

        verify(alarmRingViewModel, times(1)).setVolumeRampDuration(0)
    }

    @Test
    fun scanDirectoriesCardClickTriggersNavigation() {
        val nav = setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_scan_directories))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assert(nav.currentDestination?.hasRoute(ScanDirectorySettings::class) == true) {
            "Expected navigation to ScanDirectorySettings, but currentDestination=${nav.currentDestination}"
        }
    }

    @Test
    fun permissionDiagnosticsCardClickTriggersNavigation() {
        val nav = setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_permission_diagnostics))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assert(nav.currentDestination?.hasRoute(PermissionDiagnostics::class) == true) {
            "Expected navigation to PermissionDiagnostics, but currentDestination=${nav.currentDestination}"
        }
    }

    @Test
    fun aboutCardClickTriggersNavigation() {
        val nav = setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_about_title))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assert(nav.currentDestination?.hasRoute(About::class) == true) {
            "Expected navigation to About, but currentDestination=${nav.currentDestination}"
        }
    }

    @Test
    fun languageDropdownSelectsEnglishCallsViewModel() {
        setContentWithNavHost()

        composeTestRule.onNodeWithText(str(R.string.settings_language_zh))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText(str(R.string.settings_language_en))
            .filterToOne(hasClickAction())
            .performClick()

        verify(languageViewModel, times(1)).setLanguage(AppLanguage.ENGLISH)
    }
}
