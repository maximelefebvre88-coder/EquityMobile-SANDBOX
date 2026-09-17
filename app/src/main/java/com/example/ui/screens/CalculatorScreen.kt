package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CalculatorSnapshot
import com.example.domain.util.getTickerCurrency
import com.example.ui.theme.*
import com.example.viewmodel.FinanceViewModel
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: FinanceViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val activeTicker by viewModel.selectedCalculatorTicker.collectAsStateWithLifecycle()
    val snapshot by viewModel.activeCalculatorSnapshot.collectAsStateWithLifecycle()
    val isSyncing by viewModel.tickerSyncing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val tickerCurrency = getTickerCurrency(activeTicker)

    val snackbarHostState = remember { SnackbarHostState() }

    // Surface any sync errors directly via the Snackbar if user is scrolled anywhere
    LaunchedEffect(syncError) {
        syncError?.let { err ->
            snackbarHostState.showSnackbar("Sync Notice: $err")
        }
    }

    var isEditingBaseline by remember { mutableStateOf(false) }

    // State fields for editable metrics in Corporate Finance Baseline
    var sharesInput by remember { mutableStateOf("") }
    var ttmRevenueInput by remember { mutableStateOf("") }
    var ttmFcfInput by remember { mutableStateOf("") }
    var histFcfYieldInput by remember { mutableStateOf("") }
    var cashOnHandInput by remember { mutableStateOf("") }
    var ltDebtInput by remember { mutableStateOf("") }
    var interestCoverInput by remember { mutableStateOf("") }
    var roicInput by remember { mutableStateOf("") }
    var ttmNetIncomeInput by remember { mutableStateOf("") }

    // Sync input fields when snapshot updates or when entering edit mode
    LaunchedEffect(isEditingBaseline, snapshot) {
        if (!isEditingBaseline) {
            val snap = snapshot
            if (snap != null) {
                val shares = if (snap.sharesOutstanding > 0.0) snap.sharesOutstanding else 1.0
                val sharesM = snap.sharesOutstanding / 1_000_000.0
                sharesInput = if (sharesM > 0.0) String.format(Locale.US, "%.2f", sharesM) else ""

                val snapRevenueM = if (snap.ttmRevenue > 0.0) snap.ttmRevenue / 1_000_000.0 else (snap.revenuePerShare * shares) / 1_000_000.0
                ttmRevenueInput = if (snapRevenueM > 0.0) String.format(Locale.US, "%.2f", snapRevenueM) else ""

                val snapFcfM = if (snap.ttmFcf > 0.0) snap.ttmFcf / 1_000_000.0 else (snap.fcfPerShare * shares) / 1_000_000.0
                ttmFcfInput = if (snapFcfM > 0.0) String.format(Locale.US, "%.2f", snapFcfM) else ""

                histFcfYieldInput = if (snap.historicalFcfYield > 0.0) String.format(Locale.US, "%.2f", snap.historicalFcfYield) else ""

                val cashM = snap.cashOnHand / 1_000_000.0
                cashOnHandInput = if (cashM > 0.0) String.format(Locale.US, "%.2f", cashM) else ""

                val debtM = snap.ltDebt / 1_000_000.0
                ltDebtInput = if (debtM > 0.0) String.format(Locale.US, "%.2f", debtM) else ""

                interestCoverInput = if (snap.interestCoverage != 0.0) String.format(Locale.US, "%.2f", snap.interestCoverage) else ""

                roicInput = if (snap.roicPercent > 0.0) String.format(Locale.US, "%.2f", snap.roicPercent) else ""

                val snapNetIncomeM = snap.ttmNetIncome / 1_000_000.0
                ttmNetIncomeInput = if (kotlin.math.abs(snapNetIncomeM) > 0.0) String.format(Locale.US, "%.2f", snapNetIncomeM) else ""
            }
        }
    }

    // Save callback for baseline modifications
    val saveBaselineMetrics: () -> Unit = {
        val snap = snapshot
        if (snap != null) {
            val parsedSharesM = sharesInput.replace(',', '.').toDoubleOrNull()
            val newShares = if (parsedSharesM != null && parsedSharesM > 0.0) parsedSharesM * 1_000_000.0 else snap.sharesOutstanding
            val safeShares = if (newShares > 0.0) newShares else 1.0

            val parsedRevM = ttmRevenueInput.replace(',', '.').toDoubleOrNull()
            val newTtmRev = if (parsedRevM != null) parsedRevM * 1_000_000.0 else snap.ttmRevenue

            val parsedFcfM = ttmFcfInput.replace(',', '.').toDoubleOrNull()
            val newTtmFcf = if (parsedFcfM != null) parsedFcfM * 1_000_000.0 else snap.ttmFcf

            val parsedCashM = cashOnHandInput.replace(',', '.').toDoubleOrNull()
            val newCashOnHand = if (parsedCashM != null) parsedCashM * 1_000_000.0 else snap.cashOnHand

            val parsedDebtM = ltDebtInput.replace(',', '.').toDoubleOrNull()
            val newLtDebt = if (parsedDebtM != null) parsedDebtM * 1_000_000.0 else snap.ltDebt

            val parsedInterestCover = interestCoverInput.replace(',', '.').toDoubleOrNull()
            val newInterestCover = parsedInterestCover ?: snap.interestCoverage

            val parsedNetIncomeM = ttmNetIncomeInput.replace(',', '.').toDoubleOrNull()
            val newNetIncome = if (parsedNetIncomeM != null) parsedNetIncomeM * 1_000_000.0 else snap.ttmNetIncome

            // FCF Margin is logically calculated from TTM FCF and TTM Revenue
            val newFcfMargin = if (newTtmRev > 0.0) (newTtmFcf / newTtmRev) * 100.0 else snap.fcfMarginPercent

            val parsedHistFcfYield = histFcfYieldInput.replace(',', '.').toDoubleOrNull()
            val newHistFcfYield = parsedHistFcfYield ?: snap.historicalFcfYield

            val parsedRoic = roicInput.replace(',', '.').toDoubleOrNull()
            val newRoic = parsedRoic ?: snap.roicPercent

            val newRevPerShare = newTtmRev / safeShares
            val newFcfPerShare = newTtmFcf / safeShares
            val newNetCashPerShare = (newCashOnHand - newLtDebt) / safeShares
            val newMarketCap = if (snap.currentPrice > 0.0) snap.currentPrice * safeShares else snap.marketCap

            val updatedSnap = snap.copy(
                sharesOutstanding = newShares,
                ttmRevenue = newTtmRev,
                revenuePerShare = newRevPerShare,
                ttmFcf = newTtmFcf,
                fcfPerShare = newFcfPerShare,
                fcfMarginPercent = newFcfMargin,
                cashOnHand = newCashOnHand,
                ltDebt = newLtDebt,
                interestCoverage = newInterestCover,
                netCashPerShare = newNetCashPerShare,
                roicPercent = newRoic,
                historicalFcfYield = newHistFcfYield,
                ttmNetIncome = newNetIncome,
                marketCap = newMarketCap
            )

            viewModel.updateCalculatorSnapshot(updatedSnap)
            isEditingBaseline = false
        }
    }

    // Ensure we have a default selected active ticker if none is selected yet but we have watchlist items
    LaunchedEffect(watchlist) {
        if (activeTicker.isEmpty() && watchlist.isNotEmpty()) {
            viewModel.selectedCalculatorTicker.value = watchlist.first().symbol
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
    ) {
        if (activeTicker.isEmpty()) {
            // Direct ticker entry fall-back
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Search & Type ticker below to evaluate:", color = GrayText)
                    Spacer(modifier = Modifier.height(12.dp))
                    var customTickerInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = customTickerInput,
                        onValueChange = { customTickerInput = it },
                        placeholder = { Text("e.g. AAPL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealAccent)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (customTickerInput.isNotEmpty()) {
                                viewModel.selectedCalculatorTicker.value = customTickerInput.uppercase()
                                viewModel.syncTicker(customTickerInput.uppercase(), force = true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black)
                    ) {
                        Text("Evaluate", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Main Valuation Flow with loaded ticker
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                    // Header Row
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfCard),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // First Row: Large Ticker Icon & Live Price Block & Info Block
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Large Ticker Symbol box / logo
                                    val matchingTicker = watchlist.find { it.symbol.equals(activeTicker, ignoreCase = true) }
                                    val dbLogoUrl = matchingTicker?.logoUrl
                                    val fallbackSymbol = activeTicker.uppercase().trim()
                                    val fallbackLogoUrl = "https://financialmodelingprep.com/image-stock/$fallbackSymbol.png"
                                    val finalLogoUrl = if (!dbLogoUrl.isNullOrEmpty()) dbLogoUrl else fallbackLogoUrl

                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfCard)
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        var imageLoadFailed by remember(finalLogoUrl) { mutableStateOf(false) }
                                        if (!imageLoadFailed) {
                                            AsyncImage(
                                                model = finalLogoUrl,
                                                contentDescription = "$activeTicker Logo",
                                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                                onError = { imageLoadFailed = true }
                                            )
                                        } else {
                                            Text(
                                                text = activeTicker.take(1).uppercase(),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 24.sp,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // STOCK PRICE block
                                    val currentLivePrice = snapshot?.currentPrice ?: 0.0
                                    Box(
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NavyDark)
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("STOCK PRICE", fontSize = 8.sp, color = GrayText, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                                Text(
                                                    text = String.format(Locale.getDefault(), "$%.2f %s", currentLivePrice, tickerCurrency),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.syncTicker(activeTicker, force = true) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                if (isSyncing == activeTicker) {
                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = TealAccent, strokeWidth = 1.5.dp)
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Sync",
                                                        tint = OptionBlue,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Ticker full name/info block
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        val companyNameStr = matchingTicker?.companyName ?: activeTicker
                                        Text(
                                            text = companyNameStr,
                                            fontSize = 12.sp,
                                            color = GrayText,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "VALUE CALCULATOR",
                                            fontSize = 11.sp,
                                            color = TealAccent,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Handles Sync errors inline gracefully
                    syncError?.let { err ->
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = "Error", tint = Color.Red)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Alert: $err. You can manually adjust all data inputs below.", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // CALCULATIONS BLOCK
                    snapshot?.let { snap ->
                        val riskFree = snap.riskFreeRate
                        val riskPremium = snap.riskPremium
                        val requiredReturn = riskFree + riskPremium
                        val fcfPerShare = snap.fcfPerShare
                        val currentPrice = snap.currentPrice

                        // Core output parameters calculated in real-time
                        val fcfYield = if (currentPrice > 0.0) (fcfPerShare / currentPrice) * 100.0 else 0.0
                        val impliedGrowth = requiredReturn - fcfYield

                        // 1. Market Free Cashflow Sentiment (Historical FCF Yield Normalization)
                        val histFcfYield = snap.historicalFcfYield
                        val fcfSentimentValue = if (histFcfYield > 0.0) {
                            snap.fcfPerShare / (histFcfYield / 100.0)
                        } else 0.0
                        val livePrice = currentPrice

                        val fcfSentimentDiffPct = if (fcfSentimentValue > 0.0 && livePrice > 0.0) {
                            ((fcfSentimentValue - livePrice) / livePrice) * 100.0
                        } else 0.0

                        // 2. Two-Stage DCF (Dynamic Moat-Quality Adjusted)
                        val (fairValue, dcfAdjustment) = viewModel.calculateMoatQualityAdjustedDcf(snap)

                        val dcfDiffPct = if (fairValue > 0.0 && livePrice > 0.0) {
                            ((fairValue - livePrice) / livePrice) * 100.0
                        } else 0.0

                        // 3. Buffett Shortcut (Dynamic Moat-Quality Adjusted)
                        val buffettValue = viewModel.calculateMoatQualityAdjustedBuffett(snap, dcfAdjustment)
                        val buffettDiffPct = if (buffettValue > 0.0 && livePrice > 0.0) {
                            ((buffettValue - livePrice) / livePrice) * 100.0
                        } else 0.0

                        // Cards Row (FCF Yield, Implied Growth, Required Return)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricsCard("FCF Yield", String.format(Locale.getDefault(), "%.2f%%", fcfYield), modifier = Modifier.weight(1f))
                                MetricsCard("Implied Growth", String.format(Locale.getDefault(), "%.2f%%", impliedGrowth), modifier = Modifier.weight(1f))
                                MetricsCard("Required Return", String.format(Locale.getDefault(), "%.2f%%", requiredReturn), modifier = Modifier.weight(1f))
                            }
                        }

                        // NEW VALUATION METHOD: Market Free Cashflow Sentiment Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(26.dp),
                                border = BorderStroke(1.2.dp, OptionBlue.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Market Free Cashflow Sentiment",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = OptionBlue
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (fcfSentimentDiffPct >= 0) TealAccent.copy(alpha = 0.2f) else RedLoss.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (fcfSentimentDiffPct >= 0) {
                                                    String.format(Locale.getDefault(), "+%.1f%% Undervalued", fcfSentimentDiffPct)
                                                } else {
                                                    String.format(Locale.getDefault(), "%.1f%% Overvalued", fcfSentimentDiffPct)
                                                },
                                                color = if (fcfSentimentDiffPct >= 0) TealAccent else RedLoss,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = String.format(Locale.getDefault(), "$%.2f %s", fcfSentimentValue, tickerCurrency),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = LightText,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (histFcfYield > 0.0) {
                                            String.format(
                                                Locale.getDefault(),
                                                "Target price derived by normalizing current FCF yield (%.2f%%) to historical average FCF yield (%.2f%%). When current yield is below historical, the stock is trading at a premium over its historical cashflow valuation.",
                                                fcfYield,
                                                histFcfYield
                                            )
                                        } else {
                                            "Target price derived from realtime price vs historical FCF yield benchmark. Set Historical FCF Yield in Corporate Finance Baseline to calibrate target price."
                                        },
                                        fontSize = 11.sp,
                                        color = GrayText
                                    )
                                }
                            }
                        }

                        // PRIMARY CARD: 2-stage DCF value card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(26.dp),
                                border = BorderStroke(1.2.dp, TealAccent.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "2-Stage DCF Fair Value",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TealAccent
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (dcfDiffPct >= 0) TealAccent.copy(alpha = 0.2f) else RedLoss.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (dcfDiffPct >= 0) {
                                                    String.format(Locale.getDefault(), "+%.1f%% Undervalued", dcfDiffPct)
                                                } else {
                                                    String.format(Locale.getDefault(), "%.1f%% Overvalued", dcfDiffPct)
                                                },
                                                color = if (dcfDiffPct >= 0) TealAccent else RedLoss,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = String.format(Locale.getDefault(), "$%.2f %s", fairValue, tickerCurrency),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = LightText,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Intrinsically valued based on discounted cash flow projections over ${dcfAdjustment.highGrowthYears} high-growth years + terminal value discounted back.",
                                        fontSize = 11.sp,
                                        color = GrayText
                                    )

                                    // Moat-Quality Adjustment Details
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Moat-Quality Composite", color = GrayText, fontSize = 10.sp)
                                            Text(String.format(Locale.US, "%.1f pts", dcfAdjustment.compositeScore), color = LightText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Required Rate of Return", color = GrayText, fontSize = 10.sp)
                                            val baseRet = snap.riskFreeRate + snap.riskPremium
                                            val adjBps = dcfAdjustment.requiredRateOfReturnAdjustmentBps
                                            val adjSign = if (adjBps >= 0) "+" else ""
                                            Text(
                                                text = String.format(Locale.US, "%.2f%% (%s%.0fbps)", baseRet + dcfAdjustment.requiredRateOfReturnAdjustmentPercent, adjSign, adjBps),
                                                color = if (adjBps <= 0) TealAccent else RedLoss,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Growth Stage 1", color = GrayText, fontSize = 10.sp)
                                            Text("${dcfAdjustment.highGrowthYears} Years (Override)", color = LightText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (dcfAdjustment.isLowReliability) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(RedLoss.copy(alpha = 0.1f))
                                                .border(1.dp, RedLoss.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.Top) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "Speculative Warning",
                                                    tint = RedLoss,
                                                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "SPECULATIVE DCF ALERT: Low Composite Score (${String.format(Locale.US, "%.1f", dcfAdjustment.compositeScore)}). Valuation is highly sensitive and less reliable. Cross-checking with an earnings-multiple check is strongly recommended.",
                                                    color = RedLoss,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECONDARY CARD: Buffett Shortcut Value
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Buffett Shortcut Value",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = AmberWarning
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (buffettDiffPct >= 0) TealAccent.copy(alpha = 0.15f) else RedLoss.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (buffettDiffPct >= 0) {
                                                    String.format(Locale.getDefault(), "+%.1f%% Undervalued", buffettDiffPct)
                                                } else {
                                                    String.format(Locale.getDefault(), "%.1f%% Overvalued", buffettDiffPct)
                                                },
                                                color = if (buffettDiffPct >= 0) TealAccent else RedLoss,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = String.format(Locale.getDefault(), "$%.2f %s", buffettValue, tickerCurrency),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightText,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    val adjBps = dcfAdjustment.requiredRateOfReturnAdjustmentBps
                                    val adjSign = if (adjBps >= 0) "+" else ""
                                    Text(
                                        text = "Buffett Shortcut Formula: (FCF per share * FCF growth rate) / (Adjusted Required Return - Fond. growth rate) + net cash. Required Return is adjusted by $adjSign${String.format(Locale.US, "%.0f", adjBps)}bps based on Moat-Quality Composite.",
                                        fontSize = 11.sp,
                                        color = GrayText
                                    )
                                }
                            }
                        }

                        // Contribution Split Section
                        item {
                            val hurdleRate = if (requiredReturn > 0.0) requiredReturn else 8.5
                            val baseFcfYield = fcfYield
                            
                            // 1. Expected Hurdle Return Split
                            // hurdleYieldContrib % = (baseFcfYield / hurdleRate) * 100
                            // hurdleGrowthContrib % = 100 - hurdleYieldContrib
                            val hurdleYieldContrib = (baseFcfYield / hurdleRate) * 100.0
                            val hurdleGrowthContrib = 100.0 - hurdleYieldContrib

                            val visualHurdleYieldBarPct = hurdleYieldContrib.coerceIn(0.0, 100.0)
                            val visualHurdleGrowthBarPct = 100.0 - visualHurdleYieldBarPct

                            // 2. Stock Purchase Price Allocation Split
                            // No-Growth Value = (FCF per Share / (Hurdle Rate / 100.0)) + Net Cash per Share
                            val discountFraction = hurdleRate / 100.0
                            val noGrowthValue = if (discountFraction > 0.0) {
                                (fcfPerShare / discountFraction) + snap.netCashPerShare
                            } else 0.0

                            val pricePaidForFcfYieldPct = if (currentPrice > 0.0) {
                                (noGrowthValue / currentPrice) * 100.0
                            } else 0.0
                            
                            val pricePaidForGrowthPct = 100.0 - pricePaidForFcfYieldPct

                            val visualPriceFcfYieldBarPct = pricePaidForFcfYieldPct.coerceIn(0.0, 100.0)
                            val visualPriceGrowthBarPct = 100.0 - visualPriceFcfYieldBarPct

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.2.dp, OptionBlue.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Allocation Contribution",
                                            tint = TealAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Allocation Contribution Split",
                                            fontSize = 15.sp,
                                            color = LightText,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // --- SPLIT 1: expected hurdle return split ---
                                    Text(
                                        text = "EXPECTED HURDLE RETURN SPLIT",
                                        fontSize = 10.sp,
                                        color = GrayText,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = String.format(Locale.getDefault(), "Shows support of your target %.1f%% hurdle rate from business cash yield (FCF Yield) versus priced-in future valuation growth.", hurdleRate),
                                        fontSize = 11.sp,
                                        color = LightText.copy(alpha = 0.8f),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Bar visual 1
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                    ) {
                                        if (visualHurdleYieldBarPct > 0.0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(visualHurdleYieldBarPct.toFloat())
                                                    .background(TealAccent)
                                            )
                                        }
                                        if (visualHurdleGrowthBarPct > 0.0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(visualHurdleGrowthBarPct.toFloat())
                                                    .background(OptionCcIndigo)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(TealAccent))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = String.format(Locale.getDefault(), "Cash Yield: %.1f%% of hurdle", hurdleYieldContrib),
                                                fontSize = 10.sp,
                                                color = LightText
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(OptionCcIndigo))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = String.format(Locale.getDefault(), "Growth: %.1f%% of hurdle", hurdleGrowthContrib),
                                                fontSize = 10.sp,
                                                color = LightText
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // --- SPLIT 2: stock purchase price split ---
                                    Text(
                                        text = "PURCHASE PRICE INVESTED CAPITAL SPLIT",
                                        fontSize = 10.sp,
                                        color = GrayText,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = String.format(Locale.getDefault(), "Of the purchase price of $%.2f %s, $%.2f is backed by current business cash yield (FCF) zero-growth asset value, and the rest is paid for growth projections.", livePrice, tickerCurrency, noGrowthValue),
                                        fontSize = 11.sp,
                                        color = LightText.copy(alpha = 0.8f),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Bar visual 2
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                    ) {
                                        if (visualPriceFcfYieldBarPct > 0.0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(visualPriceFcfYieldBarPct.toFloat())
                                                    .background(OptionBlue)
                                            )
                                        }
                                        if (visualPriceGrowthBarPct > 0.0) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .weight(visualPriceGrowthBarPct.toFloat())
                                                    .background(AmberWarning)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(OptionBlue))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = String.format(Locale.getDefault(), "Paid for FCF Yield: %.1f%%", pricePaidForFcfYieldPct),
                                                fontSize = 10.sp,
                                                color = LightText
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(AmberWarning))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = String.format(Locale.getDefault(), "Paid for Growth: %.1f%%", pricePaidForGrowthPct),
                                                fontSize = 10.sp,
                                                color = LightText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // User Editable parameters block (Fully editable inputs)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Valuation Inputs & Assumptions",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LightText
                                )
                                TextButton(
                                    onClick = { isEditingBaseline = !isEditingBaseline },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (isEditingBaseline) "Done Editing" else "✏️ Edit Baseline",
                                        color = TealAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Growth & Horizon", fontSize = 12.sp, color = TealAccent, fontWeight = FontWeight.Bold)
                                    // Row 1: Growth Rates
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        EditableField(
                                            label = "FCF Growth %",
                                            value = snap.fcfGrowthRate,
                                            onValueChange = { newVal ->
                                                viewModel.updateCalculatorSnapshot(snap.copy(fcfGrowthRate = newVal))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        EditableField(
                                            label = "Fund. Growth %",
                                            value = snap.fundamentalGrowthRate,
                                            onValueChange = { newVal ->
                                                viewModel.updateCalculatorSnapshot(snap.copy(fundamentalGrowthRate = newVal))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Row 2: Terminal & Horizon
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        EditableField(
                                            label = "Terminal Growth %",
                                            value = snap.terminalGrowthRate,
                                            onValueChange = { newVal ->
                                                viewModel.updateCalculatorSnapshot(snap.copy(terminalGrowthRate = newVal))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        EditableField(
                                            label = "Growth Years N",
                                            value = snap.highGrowthYears.toDouble(),
                                            onValueChange = { newVal ->
                                                viewModel.updateCalculatorSnapshot(snap.copy(highGrowthYears = newVal.toInt()))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                                    Text("Hurdle & Discount Rates", fontSize = 12.sp, color = TealAccent, fontWeight = FontWeight.Bold)

                                    // Row 3: Rates
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        EditableField(
                                            label = "Risk-Free Rate %",
                                            value = snap.riskFreeRate,
                                            onValueChange = { newVal ->
                                                viewModel.updateCalculatorSnapshot(snap.copy(riskFreeRate = newVal))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        EditableField(
                                            label = "Risk Premium %",
                                            value = snap.riskPremium,
                                            onValueChange = { newVal ->
                                                viewModel.updateCalculatorSnapshot(snap.copy(riskPremium = newVal))
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Corporate Finance Baseline Card (Supports view mode and inline editable mode)
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isEditingBaseline) TealAccent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "Corporate Finance Baseline",
                                                fontSize = 12.sp,
                                                color = TealAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isEditingBaseline) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(TealAccent.copy(alpha = 0.2f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        "Editing Mode",
                                                        color = TealAccent,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        if (!isEditingBaseline) {
                                            TextButton(
                                                onClick = { isEditingBaseline = true },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text("✏️ Edit All Metrics", color = TealAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = { isEditingBaseline = false },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                                ) {
                                                    Text("Cancel", color = GrayText, fontSize = 11.sp)
                                                }
                                                Button(
                                                    onClick = { saveBaselineMetrics() },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = TealAccent,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp)

                                    if (!isEditingBaseline) {
                                        // --- READ-ONLY DISPLAY MODE ---
                                        val safeShares = if (snap.sharesOutstanding > 0.0) snap.sharesOutstanding else 1.0
                                        val mcapVal = if (snap.marketCap > 0.0) snap.marketCap else (snap.currentPrice * safeShares)
                                        val revTotal = if (snap.ttmRevenue > 0.0) snap.ttmRevenue else (snap.revenuePerShare * safeShares)
                                        val fcfTotal = if (snap.ttmFcf > 0.0) snap.ttmFcf else (snap.fcfPerShare * safeShares)
                                        val fcfYieldVal = if (snap.currentPrice > 0.0) {
                                            (snap.fcfPerShare / snap.currentPrice) * 100.0
                                        } else 0.0
                                        val cashTotal = snap.cashOnHand
                                        val debtTotal = snap.ltDebt
                                        val fcfMarginVal = if (revTotal > 0.0) (fcfTotal / revTotal) * 100.0 else snap.fcfMarginPercent
                                        val fcfConv = if (snap.ttmNetIncome != 0.0) (fcfTotal / snap.ttmNetIncome) * 100.0 else 0.0

                                        // 1. Market cap
                                        StatsRow("Market cap", String.format(Locale.getDefault(), "$%.2f B", mcapVal / 1_000_000_000.0))
                                        // 2. Shares outstanding
                                        StatsRow("Shares outstanding", String.format(Locale.getDefault(), "%.2f M", snap.sharesOutstanding / 1_000_000.0))
                                        // 3. TTM revenue
                                        StatsRow("TTM revenue", String.format(Locale.getDefault(), "$%.2f M", revTotal / 1_000_000.0))
                                        // 4. Net income
                                        StatsRow("Net income", String.format(Locale.getDefault(), "$%.2f M", snap.ttmNetIncome / 1_000_000.0))
                                        // 5. TTM free cash flow
                                        StatsRow("TTM free cash flow", String.format(Locale.getDefault(), "$%.2f M", fcfTotal / 1_000_000.0))
                                        // 6. FCF per share
                                        StatsRow("FCF per share", String.format(Locale.getDefault(), "$%.2f %s", snap.fcfPerShare, tickerCurrency))
                                        // 7. FCF yield
                                        StatsRow("FCF yield", String.format(Locale.getDefault(), "%.2f%%", fcfYieldVal))
                                        // 8. Historical FCF yield
                                        StatsRow("Historical FCF yield", String.format(Locale.getDefault(), "%.2f%%", snap.historicalFcfYield))
                                        // 9. Cash on hands
                                        StatsRow("Cash on hands", String.format(Locale.getDefault(), "$%.2f M", cashTotal / 1_000_000.0))
                                        // 10. Long term debt
                                        StatsRow("Long term debt", String.format(Locale.getDefault(), "$%.2f M", debtTotal / 1_000_000.0))
                                        // 11. Interest cover
                                        StatsRow("Interest cover", if (snap.interestCoverage != 0.0) String.format(Locale.getDefault(), "%.2fx", snap.interestCoverage) else "0.00x")
                                        // 12. Net cash / share
                                        StatsRow("Net cash / share", String.format(Locale.getDefault(), "$%.2f %s", snap.netCashPerShare, tickerCurrency))
                                        // 12. ROIC
                                        StatsRow("ROIC", String.format(Locale.getDefault(), "%.2f%%", snap.roicPercent))
                                        // 13. FCF margin
                                        StatsRow("FCF margin", String.format(Locale.getDefault(), "%.2f%%", fcfMarginVal))
                                        // 14. FCF conversion
                                        StatsRow("FCF conversion", String.format(Locale.getDefault(), "%.1f%%", fcfConv))
                                    } else {
                                        // --- INLINE EDITABLE MODE WITH REAL-TIME LIVE CALCULATIONS ---
                                        val parsedSharesM = sharesInput.replace(',', '.').toDoubleOrNull() ?: (snap.sharesOutstanding / 1_000_000.0)
                                        val safeSharesTotal = if (parsedSharesM > 0.0) parsedSharesM * 1_000_000.0 else snap.sharesOutstanding.coerceAtLeast(1.0)

                                        val livePrice = snap.currentPrice
                                        val liveMarketCapB = (livePrice * safeSharesTotal) / 1_000_000_000.0

                                        val parsedRevM = ttmRevenueInput.replace(',', '.').toDoubleOrNull() ?: (if (snap.ttmRevenue > 0.0) snap.ttmRevenue / 1_000_000.0 else (snap.revenuePerShare * safeSharesTotal) / 1_000_000.0)
                                        val parsedNetIncomeM = ttmNetIncomeInput.replace(',', '.').toDoubleOrNull() ?: (snap.ttmNetIncome / 1_000_000.0)

                                        val parsedFcfM = ttmFcfInput.replace(',', '.').toDoubleOrNull() ?: (snap.ttmFcf / 1_000_000.0)
                                        val liveFcfPerShare = (parsedFcfM * 1_000_000.0) / safeSharesTotal

                                        val rawLiveFcfPerShare = (parsedFcfM * 1_000_000.0) / safeSharesTotal
                                        val liveFcfYieldVal = if (livePrice > 0.0) (rawLiveFcfPerShare / livePrice) * 100.0 else 0.0

                                        val parsedCashM = cashOnHandInput.replace(',', '.').toDoubleOrNull() ?: (snap.cashOnHand / 1_000_000.0)
                                        val parsedDebtM = ltDebtInput.replace(',', '.').toDoubleOrNull() ?: (snap.ltDebt / 1_000_000.0)
                                        val liveNetCashPerShare = ((parsedCashM - parsedDebtM) * 1_000_000.0) / safeSharesTotal

                                        val liveFcfMargin = if (parsedRevM > 0.0) (parsedFcfM / parsedRevM) * 100.0 else 0.0
                                        val liveFcfConversion = if (parsedNetIncomeM != 0.0) (parsedFcfM / parsedNetIncomeM) * 100.0 else 0.0

                                        // 1. Market cap (Calculated)
                                        CalculatedStatsRow("Market cap", String.format(Locale.getDefault(), "$%.2f B", liveMarketCapB), "Auto: Price × Shares")

                                        // 2. Shares outstanding (Editable)
                                        BaselineEditRow("Shares outstanding", sharesInput, { sharesInput = it }, "M")

                                        // 3. TTM revenue (Editable)
                                        BaselineEditRow("TTM revenue", ttmRevenueInput, { ttmRevenueInput = it }, "${'$'}M")

                                        // 4. Net income (Editable)
                                        BaselineEditRow("Net income", ttmNetIncomeInput, { ttmNetIncomeInput = it }, "${'$'}M")

                                        // 5. TTM free cash flow (Editable)
                                        BaselineEditRow("TTM free cash flow", ttmFcfInput, { ttmFcfInput = it }, "${'$'}M")

                                        // 6. FCF per share (Calculated)
                                        CalculatedStatsRow("FCF per share", String.format(Locale.getDefault(), "$%.2f %s", liveFcfPerShare, tickerCurrency), "Auto: TTM FCF / Shares")

                                        // 7. FCF yield (Calculated)
                                        CalculatedStatsRow("FCF yield", String.format(Locale.getDefault(), "%.2f%%", liveFcfYieldVal), "Auto: FCF per Share / Price")

                                        // 8. Historical FCF yield (Editable)
                                        BaselineEditRow("Historical FCF yield", histFcfYieldInput, { histFcfYieldInput = it }, "%")

                                        // 9. Cash on hands (Editable)
                                        BaselineEditRow("Cash on hands", cashOnHandInput, { cashOnHandInput = it }, "\$M")

                                        // 10. Long term debt (Editable)
                                        BaselineEditRow("Long term debt", ltDebtInput, { ltDebtInput = it }, "\$M")

                                        // 11. Interest cover (Editable)
                                        BaselineEditRow("Interest cover", interestCoverInput, { interestCoverInput = it }, "x")

                                        // 12. Net cash / share (Calculated)
                                        CalculatedStatsRow("Net cash / share", String.format(Locale.getDefault(), "$%.2f %s", liveNetCashPerShare, tickerCurrency), "Auto: (Cash - Debt) / Shares")

                                        // 12. ROIC (Editable)
                                        BaselineEditRow("ROIC", roicInput, { roicInput = it }, "%")

                                        // 13. FCF margin (Calculated)
                                        CalculatedStatsRow("FCF margin", String.format(Locale.getDefault(), "%.2f%%", liveFcfMargin), "Auto: TTM FCF / TTM Revenue")

                                        // 14. FCF conversion (Calculated)
                                        CalculatedStatsRow("FCF conversion", String.format(Locale.getDefault(), "%.1f%%", liveFcfConversion), "Auto: TTM FCF / Net Income")

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { isEditingBaseline = false },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GrayText),
                                                border = BorderStroke(1.dp, BorderGray),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Cancel")
                                            }
                                            Button(
                                                onClick = { saveBaselineMetrics() },
                                                modifier = Modifier.weight(1.5f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = TealAccent,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("💾 Save Metrics", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom action button
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (isEditingBaseline) {
                                        saveBaselineMetrics()
                                    } else {
                                        isEditingBaseline = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEditingBaseline) TealAccent else OptionCcIndigo,
                                    contentColor = if (isEditingBaseline) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (isEditingBaseline) "💾 Save Corporate Finance Baseline" else "✏️ Edit Corporate Finance Baseline",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } ?: run {
                        // If selected but no data fetched yet
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No metrics loaded yet for $activeTicker", color = GrayText)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.syncTicker(activeTicker, force = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black)
                                    ) {
                                        Text("Sync Live Data Now", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = SurfCard,
                        contentColor = LightText,
                        actionColor = TealAccent,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            )
        }
}

@Composable
fun MetricsCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                color = GrayText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                color = LightText,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EditableField(label: String, value: Double, onValueChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    var rawText by remember {
        mutableStateOf(
            if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.2f", value)
            }
        )
    }

    LaunchedEffect(value) {
        val currentLocalDouble = rawText.toDoubleOrNull()
        if (currentLocalDouble == null || currentLocalDouble != value) {
            rawText = if (value % 1.0 == 0.0) {
                value.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.2f", value)
            }
        }
    }

    OutlinedTextField(
        value = rawText,
        onValueChange = { input ->
            rawText = input
            val cleansed = input.replace(',', '.')
            cleansed.toDoubleOrNull()?.let { d -> onValueChange(d) }
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealAccent,
            cursorColor = TealAccent
        )
    )
}

@Composable
fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = GrayText, fontSize = 12.sp)
        Text(value, color = LightText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun CalculatedStatsRow(label: String, value: String, explanation: String = "Calculated") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = LightText.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(explanation, color = TealAccent.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        Text(
            text = value,
            color = TealAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun BaselineEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = LightText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            trailingIcon = {
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                color = LightText
            ),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfCard.copy(alpha = 0.8f),
                unfocusedContainerColor = SurfCard.copy(alpha = 0.4f),
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = BorderGray,
                cursorColor = TealAccent,
                focusedTextColor = LightText,
                unfocusedTextColor = LightText
            ),
            modifier = Modifier
                .width(150.dp)
                .height(50.dp)
        )
    }
}
