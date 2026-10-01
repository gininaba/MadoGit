package com.aipos.madogit

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MadoGitUiInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        // Automatically grant notification permission so system dialog doesn't block UI test
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun navigateToAuthScreenIfNeeded() {
        // Wait for splash screen to dismiss (up to 5 seconds)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText("Get Started")).fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodes(hasText("Personal Access Token")).fetchSemanticsNodes().isNotEmpty()
        }

        // If on onboarding screen, click "Get Started"
        if (composeTestRule.onAllNodes(hasText("Get Started")).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Get Started").performClick()
            // Wait for transition to step 1 (AuthScreen)
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodes(hasText("Personal Access Token")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun verifyOnboardingOrAuthScreenDisplayed() {
        navigateToAuthScreenIfNeeded()

        // Verify that Personal Access Token option is visible and displayed
        composeTestRule.onNodeWithText("Personal Access Token").assertIsDisplayed()
    }

    @Test
    fun verifyAuthTabSwitching() {
        navigateToAuthScreenIfNeeded()

        // Switch to OAuth App tab
        composeTestRule.onNodeWithText("OAuth App").performClick()

        // Wait for OAuth fields to render
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("GitHub OAuth App Settings")).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify OAuth App fields are displayed
        composeTestRule.onNodeWithText("GitHub OAuth App Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Client ID").assertIsDisplayed()
        composeTestRule.onNodeWithText("Client Secret").assertIsDisplayed()

        // Switch back to Personal Token tab
        composeTestRule.onNodeWithText("Personal Token").performClick()

        // Wait for Personal Access Token section to restore
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodes(hasText("Personal Access Token")).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Personal Access Token section is restored
        composeTestRule.onNodeWithText("Personal Access Token").assertIsDisplayed()
    }
}
