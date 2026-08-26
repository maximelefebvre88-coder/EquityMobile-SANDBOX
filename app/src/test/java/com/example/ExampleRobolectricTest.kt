package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.calculateBalanceSheetHealthScore
import com.example.ui.screens.calculateProfitQualityRankScore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Equity IQ", appName)
  }

  @Test
  fun `ViewModel flushes sync error on ticker navigation`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.viewmodel.FinanceViewModel(app)
    
    // Set a sync error
    viewModel.syncError.value = "Stale Sync Error"
    assertEquals("Stale Sync Error", viewModel.syncError.value)
    
    // Navigate/switch ticker
    viewModel.selectedCalculatorTicker.value = "MSFT"
    
    // It should immediately reset the stale sync error!
    assertEquals(null, viewModel.syncError.value)
  }

  @Test
  fun `ViewModel auto-dismisses sync error after inactivity delay`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.viewmodel.FinanceViewModel(app)
    
    // Set a sync error
    viewModel.syncError.value = "Transient Sync Error"
    assertEquals("Transient Sync Error", viewModel.syncError.value)
    
    // Fast-forward coroutines. Under Robolectric, the shadow looper can idle the thread.
    org.robolectric.shadows.ShadowLooper.idleMainLooper(11000L)
    
    // It should gently clear the sync error after the delay of 10 seconds
    assertEquals(null, viewModel.syncError.value)
  }

  @Test
  fun `calculateBalanceSheetHealthScore behaves correctly for all Tiers`() {
    // Tier 1: Zero or negative debt condition
    assertEquals(100, calculateBalanceSheetHealthScore(cashOnHand = 50.0, longTermDebt = 0.0, freeCashFlow = 10.0))
    assertEquals(100, calculateBalanceSheetHealthScore(cashOnHand = 50.0, longTermDebt = -10.0, freeCashFlow = 10.0))

    // Tier 2: Net Cash Condition (Cash >= Debt)
    // cash = 120, debt = 100. modifier = ((120 - 100)/100) * 10 = 2.0. score = 60 + 2 = 62
    assertEquals(62, calculateBalanceSheetHealthScore(cashOnHand = 120.0, longTermDebt = 100.0, freeCashFlow = 20.0))
    // cash = 200, debt = 100. modifier = ((200 - 100)/100) * 10 = 10.0. score = 60 + 10 = 70
    assertEquals(70, calculateBalanceSheetHealthScore(cashOnHand = 200.0, longTermDebt = 100.0, freeCashFlow = 20.0))
    // cash = 500, debt = 100. modifier capped at 30.0. score = 60 + 30 = 90
    assertEquals(90, calculateBalanceSheetHealthScore(cashOnHand = 500.0, longTermDebt = 100.0, freeCashFlow = 20.0))

    // Tier 3: Net Debt Condition (Cash < Debt)
    // cash = 40, debt = 100, netDebt = 60. FCF = 20. safetyThreshold = 70. ratio = 60/70. rawScore = 60 * (1 - 60/70) = 8.57 -> 9
    assertEquals(9, calculateBalanceSheetHealthScore(cashOnHand = 40.0, longTermDebt = 100.0, freeCashFlow = 20.0))
    // Zero or negative FCF with active Net Debt drops score to 0
    assertEquals(0, calculateBalanceSheetHealthScore(cashOnHand = 40.0, longTermDebt = 100.0, freeCashFlow = 0.0))
    assertEquals(0, calculateBalanceSheetHealthScore(cashOnHand = 40.0, longTermDebt = 100.0, freeCashFlow = -5.0))
  }

  @Test
  fun `calculateProfitQualityRankScore scales parameters independently without cross-contamination`() {
    // Case 1: All inputs already decimals (no division should occur)
    // roic = 0.15 (15%), fcfMargin = 0.12 (12%), fcfConversion = 0.85 (85%)
    // Component 1: (0.15 / 0.3) * 100 = 50 pts * 0.3 = 15 pts
    // Component 2: (0.12 / 0.3) * 100 = 40 pts * 0.4 = 16 pts
    // Component 3: (0.85 / 1.0) * 100 = 85 pts * 0.3 = 25.5 pts
    // Total = 15 + 16 + 25.5 = 56.5 -> 57 pts
    assertEquals(57, calculateProfitQualityRankScore(roic = 0.15, fcfMargin = 0.12, fcfConversion = 0.85))

    // Case 2: All inputs as percentages (divided by 100)
    // roic = 15.0 (15%), fcfMargin = 12.0 (12%), fcfConversion = 85.0 (85%) -> should produce exactly 57 pts
    assertEquals(57, calculateProfitQualityRankScore(roic = 15.0, fcfMargin = 12.0, fcfConversion = 85.0))

    // Case 3: Mixed inputs (crucial fix verification!)
    // roic = 15.0 (percentage), fcfMargin = 0.12 (decimal), fcfConversion = 85.0 (percentage)
    // Previously, the global isPercentage check would see 15.0 > 1.0, set isPercentage=true, 
    // and then divide fcfMargin (0.12) by 100, ruining the calculation.
    // Now, each should scale independently and produce exactly 57 pts!
    assertEquals(57, calculateProfitQualityRankScore(roic = 15.0, fcfMargin = 0.12, fcfConversion = 85.0))
  }
}
