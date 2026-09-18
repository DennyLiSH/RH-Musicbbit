package com.rabbithole.musicbbit.presentation.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rabbithole.musicbbit.TestActivity
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = HiltTestApplication::class)
class SingleChoiceDropdownTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun `renders selected option label`() {
        composeTestRule.setContent {
            SingleChoiceDropdown(
                label = "Sort",
                options = listOf("A", "B"),
                selectedOption = "A",
                optionLabel = { it },
                onOptionSelect = {}
            )
        }
        composeTestRule.onNodeWithText("A").assertExists()
    }

    @Test
    fun `renders placeholder when selected option is null`() {
        composeTestRule.setContent {
            SingleChoiceDropdown(
                label = "Sort",
                options = listOf("A", "B"),
                selectedOption = null,
                optionLabel = { it },
                onOptionSelect = {},
                placeholder = "none"
            )
        }
        composeTestRule.onNodeWithText("none").assertExists()
    }

    @Test
    fun `selecting option invokes callback and closes menu`() {
        var selected: String? = null
        composeTestRule.setContent {
            SingleChoiceDropdown(
                label = "Sort",
                options = listOf("A", "B", "C"),
                selectedOption = "A",
                optionLabel = { it },
                onOptionSelect = { selected = it }
            )
        }
        composeTestRule.onNodeWithText("A").performClick() // 打开菜单
        composeTestRule.onAllNodesWithText("B").assertCountEquals(1) // 菜单含全部选项
        composeTestRule.onNodeWithText("C").assertExists()
        composeTestRule.onNodeWithText("B").performClick() // 选中 B
        assertEquals("B", selected)
        composeTestRule.onNodeWithText("C").assertDoesNotExist() // 菜单已关闭，其余选项不再渲染
    }

    @Test
    fun `renders error supporting text when set`() {
        composeTestRule.setContent {
            SingleChoiceDropdown(
                label = "Sort",
                options = listOf("A"),
                selectedOption = null,
                optionLabel = { it },
                onOptionSelect = {},
                placeholder = "none",
                isError = true,
                supportingText = "pick one"
            )
        }
        composeTestRule.onNodeWithText("pick one").assertExists()
    }
}
