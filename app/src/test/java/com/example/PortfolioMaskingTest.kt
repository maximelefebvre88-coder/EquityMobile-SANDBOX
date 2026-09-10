package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.PortfolioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FinanceViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PortfolioMaskingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPortfolioMaskingToggle() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = FinanceViewModel(app)

        // Add a dummy ticker to watchlist so Total P&L card is shown
        viewModel.addTickerToWatchlist("AAPL", "Apple Inc.")

        composeTestRule.setContent {
            MyApplicationTheme {
                PortfolioScreen(
                    viewModel = viewModel,
                    onNavigateToWheel = {},
                    onNavigateToCalculator = {},
                    onNavigateToIntelligence = {}
                )
            }
        }

        // Verify initial state: toggle button has "Hide balances" (open eye)
        val toggleButton = composeTestRule.onNodeWithTag("portfolio_toggle_mask_button")
        toggleButton.assertIsDisplayed()
        toggleButton.assertContentDescriptionEquals("Hide balances")

        // Click to mask/hide amounts (shut eye)
        toggleButton.performClick()

        // Verify toggle button now has "Show balances" (shut eye)
        toggleButton.assertContentDescriptionEquals("Show balances")

        // Verify that masked text "••••" is displayed in the UI
        val maskedNodes = composeTestRule.onAllNodesWithText("••••")
        assert(maskedNodes.fetchSemanticsNodes().isNotEmpty())

        // Click again to unmask amounts
        toggleButton.performClick()

        // Verify toggle button is back to "Hide balances" (open eye)
        toggleButton.assertContentDescriptionEquals("Hide balances")
    }
}
