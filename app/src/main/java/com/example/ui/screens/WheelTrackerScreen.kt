package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.domain.model.TradeEntity
import com.example.ui.theme.*
import com.example.viewmodel.FinanceViewModel
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WheelTrackerScreen(viewModel: FinanceViewModel) {
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val allTrades by viewModel.allTrades.collectAsStateWithLifecycle()
    val activeTicker by viewModel.selectedCalculatorTicker.collectAsStateWithLifecycle()
    val snapshot by viewModel.activeCalculatorSnapshot.collectAsStateWithLifecycle()
    val isSyncing by viewModel.tickerSyncing.collectAsStateWithLifecycle()
    val currencyMultiplier by viewModel.currencyMultiplier.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.currencyFlow.collectAsStateWithLifecycle()

    // Ensure we select a default ticker if none is active
    LaunchedEffect(watchlist) {
        if (activeTicker.isEmpty() && watchlist.isNotEmpty()) {
            viewModel.selectedCalculatorTicker.value = watchlist.first().symbol
        }
    }

    if (activeTicker.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(NavyDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "No Ticker Selected",
                    color = TealAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Go to Portfolio or Settings to add and select stock tickers to track your Wheel Strategy.",
                    color = GrayText,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Core states for form inputs
        var showLogDialog by remember { mutableStateOf(false) }
        var tradeToDelete by remember { mutableStateOf<TradeEntity?>(null) }
        var editingTrade by remember { mutableStateOf<TradeEntity?>(null) }
        var tradeType by remember { mutableStateOf("Sell CSP") }
        var contractsInput by remember { mutableStateOf("1") }
        var strikeInput by remember { mutableStateOf("") }
        var premiumInput by remember { mutableStateOf("") }
        var feesInput by remember { mutableStateOf("0.99") }
        var fcfYieldInput by remember { mutableStateOf("") }

        var isClosedInput by remember { mutableStateOf(false) }
        var closePremiumInput by remember { mutableStateOf("") }
        var closeFeesInput by remember { mutableStateOf("0.99") }
        var closeDateStr by remember { mutableStateOf("") }
        var manualOutcomeInput by remember { mutableStateOf("") }

        val activeTickerObj = watchlist.find { it.symbol.equals(activeTicker, ignoreCase = true) }
        val currentLivePrice = if ((snapshot?.currentPrice ?: 0.0) > 0.0) snapshot!!.currentPrice else (activeTickerObj?.livePrice ?: 0.0)

        LaunchedEffect(tradeType, strikeInput, snapshot) {
            val isShareTarget = tradeType == "Buying shares" || tradeType == "Selling shares" || tradeType == "Assignment" || tradeType == "Called Away"
            val snapshotVal = snapshot
            if (isShareTarget) {
                val strike = strikeInput.toDoubleOrNull() ?: 0.0
                if (strike > 0.0 && snapshotVal != null && snapshotVal.fcfPerShare > 0.0) {
                    val calcYield = (snapshotVal.fcfPerShare / strike) * 100.0
                    fcfYieldInput = String.format(Locale.US, "%.2f", calcYield)
                } else if (snapshotVal != null) {
                    val stockPrice = if (snapshotVal.currentPrice > 0.0) snapshotVal.currentPrice else 0.0
                    if (stockPrice > 0.0 && snapshotVal.fcfPerShare > 0.0) {
                        fcfYieldInput = String.format(Locale.US, "%.2f", (snapshotVal.fcfPerShare / stockPrice) * 100.0)
                    } else if (snapshotVal.historicalFcfYield > 0.0) {
                        fcfYieldInput = String.format(Locale.US, "%.2f", snapshotVal.historicalFcfYield)
                    } else {
                        fcfYieldInput = ""
                    }
                } else {
                    fcfYieldInput = ""
                }
            } else {
                fcfYieldInput = ""
            }
        }

        // Date selection simplified with string entries + quick adders for maximum reliability
        val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
        val todayStr = remember { sdf.format(Date()) }
        val nextMonthStr = remember { sdf.format(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) }
        
        var dateStr by remember { mutableStateOf(todayStr) }
        var expiryStr by remember { mutableStateOf(nextMonthStr) }

        // Filter trade files for currently active ticker
        val tickerTrades = remember(allTrades, activeTicker) {
            allTrades.filter { it.ticker.equals(activeTicker, ignoreCase = true) }
        }

        // Portfolio calculations rules
        // 1. Total Shares: Assignment (+100 * contracts), Called Away (-100 * contracts), Buying shares (+contracts), Selling shares (-contracts)
        val totalSharesHeld = remember(tickerTrades, currentLivePrice) {
            var shares = 0
            val now = System.currentTimeMillis()
            tickerTrades.forEach {
                when (it.tradeType) {
                    "Assignment" -> shares += it.contracts * 100
                    "Called Away" -> shares -= it.contracts * 100
                    "Buying shares" -> shares += it.contracts
                    "Selling shares" -> shares -= it.contracts
                    "Sell CSP" -> {
                        when (it.manualOutcome) {
                            "ASSIGNED" -> {
                                shares += it.contracts * 100
                            }
                            "EXPIRED_WORTHLESS" -> {
                                // do nothing
                            }
                            else -> {
                                val isExpired = it.expiryDate != null && it.expiryDate <= now
                                if (!it.isClosed && isExpired && currentLivePrice > 0.0 && currentLivePrice < it.strikePrice) {
                                    shares += it.contracts * 100
                                }
                            }
                        }
                    }
                    "Sell CC" -> {
                        when (it.manualOutcome) {
                            "CALLED_AWAY" -> {
                                shares -= it.contracts * 100
                            }
                            "EXPIRED_WORTHLESS" -> {
                                // do nothing
                            }
                            else -> {
                                val isExpired = it.expiryDate != null && it.expiryDate <= now
                                if (!it.isClosed && isExpired && currentLivePrice > 0.0 && currentLivePrice > it.strikePrice) {
                                    shares -= it.contracts * 100
                                }
                            }
                        }
                    }
                }
            }
            shares.coerceAtLeast(0)
        }

        // 2. Net premium collected from options (Sell CSP, BTC, Sell CC)
        val totalPremiumCollected = remember(tickerTrades) {
            tickerTrades.filter { 
                it.tradeType == "Sell CSP" || it.tradeType == "Buy to Close" || it.tradeType == "Sell CC"
            }.sumOf { it.netCreditDebit }
        }

        // 3. Weighted Average Annualized Return
        val avgAnnReturn = remember(tickerTrades) {
            val optionTrades = tickerTrades.filter { 
                (it.tradeType == "Sell CSP" || it.tradeType == "Sell CC") && it.annualizedReturn > 0.0 
            }
            if (optionTrades.isNotEmpty()) {
                optionTrades.sumOf { it.annualizedReturn } / optionTrades.size
            } else {
                0.0
            }
        }

        // 4. Assigned Cost Basics: sum of strike paid on assignment + buying shares
        val assignmentCost = remember(totalSharesHeld, tickerTrades, currentLivePrice) {
            var totalBuyShares = 0
            var totalBuyCost = 0.0
            val now = System.currentTimeMillis()
            tickerTrades.forEach {
                when (it.tradeType) {
                    "Assignment" -> {
                        totalBuyShares += it.contracts * 100
                        totalBuyCost += it.strikePrice * 100.0 * it.contracts
                    }
                    "Buying shares" -> {
                        totalBuyShares += it.contracts
                        totalBuyCost += it.strikePrice * it.contracts
                    }
                    "Sell CSP" -> {
                        when (it.manualOutcome) {
                            "ASSIGNED" -> {
                                totalBuyShares += it.contracts * 100
                                totalBuyCost += it.strikePrice * 100.0 * it.contracts
                            }
                            "EXPIRED_WORTHLESS" -> {
                                // do nothing
                            }
                            else -> {
                                val isExpired = it.expiryDate != null && it.expiryDate <= now
                                if (!it.isClosed && isExpired && currentLivePrice > 0.0 && currentLivePrice < it.strikePrice) {
                                    totalBuyShares += it.contracts * 100
                                    totalBuyCost += it.strikePrice * 100.0 * it.contracts
                                }
                            }
                        }
                    }
                }
            }
            val avgBuyPrice = if (totalBuyShares > 0) totalBuyCost / totalBuyShares else 0.0
            avgBuyPrice * totalSharesHeld
        }

        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current

        val centerStrike = remember(currentLivePrice) {
            if (currentLivePrice > 0.0) {
                (Math.round(currentLivePrice / 0.25) * 0.25).toFloat()
            } else {
                100f
            }
        }
        val minStrike = remember(centerStrike) {
            (centerStrike - 64 * 0.25f).coerceAtLeast(0.25f)
        }
        val maxStrike = remember(centerStrike) {
            centerStrike + 64 * 0.25f
        }

        var strikeSliderVal by remember { mutableStateOf(centerStrike) }
        var premiumSliderVal by remember { mutableStateOf(0f) }

        var lastHapticStrike by remember { mutableStateOf(centerStrike) }
        var lastHapticPremium by remember { mutableStateOf(0f) }

        val tradeDatePickerDialog = remember(dateStr) {
            val cal = Calendar.getInstance()
            try {
                sdf.parse(dateStr)?.let { cal.time = it }
            } catch (e: Exception) {}
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val resultCal = Calendar.getInstance()
                    resultCal.set(year, month, dayOfMonth)
                    dateStr = sdf.format(resultCal.time)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        val expiryDatePickerDialog = remember(expiryStr) {
            val cal = Calendar.getInstance()
            try {
                sdf.parse(expiryStr)?.let { cal.time = it }
            } catch (e: Exception) {}
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val resultCal = Calendar.getInstance()
                    resultCal.set(year, month, dayOfMonth)
                    expiryStr = sdf.format(resultCal.time)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        val closeDatePickerDialog = remember(closeDateStr) {
            val cal = Calendar.getInstance()
            try {
                if (closeDateStr.isNotEmpty()) {
                    sdf.parse(closeDateStr)?.let { cal.time = it }
                }
            } catch (e: Exception) {}
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val resultCal = Calendar.getInstance()
                    resultCal.set(year, month, dayOfMonth)
                    closeDateStr = sdf.format(resultCal.time)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        // LaunchedEffect to reset sliders on dialog open
        LaunchedEffect(showLogDialog) {
            if (showLogDialog) {
                if (editingTrade == null) {
                    val base = if (currentLivePrice > 0.0) currentLivePrice.toFloat() else 100f
                    strikeSliderVal = base
                    lastHapticStrike = base
                    strikeInput = String.format(Locale.US, "%.2f", base)

                    val basePrem = premiumInput.toFloatOrNull() ?: 1.0f
                    premiumSliderVal = basePrem.coerceIn(0f, 10f)
                    lastHapticPremium = basePrem.coerceIn(0f, 10f)

                    isClosedInput = false
                    closePremiumInput = ""
                    closeFeesInput = "0.99"
                    closeDateStr = todayStr
                    manualOutcomeInput = ""
                } else {
                    val base = editingTrade!!.strikePrice.toFloat()
                    strikeSliderVal = base
                    lastHapticStrike = base
                    
                    val basePrem = editingTrade!!.premiumPerShare.toFloat()
                    premiumSliderVal = basePrem.coerceIn(0f, 10f)
                    lastHapticPremium = basePrem.coerceIn(0f, 10f)

                    isClosedInput = editingTrade!!.isClosed
                    closePremiumInput = if (editingTrade!!.isClosed) String.format(Locale.US, "%.2f", editingTrade!!.closePremium) else ""
                    closeFeesInput = String.format(Locale.US, "%.2f", editingTrade!!.closeFees)
                    closeDateStr = editingTrade!!.closeDate?.let { sdf.format(Date(it)) } ?: todayStr
                    manualOutcomeInput = editingTrade!!.manualOutcome
                }
            }
        }

        // 5. Effective Cost Basis (Assignment price - total net premiums collected) / shares
        val manuallyEnteredCostBasis = activeTickerObj?.manuallyEnteredCostBasis
        val effectiveCostBasis = remember(totalSharesHeld, assignmentCost, totalPremiumCollected, currentLivePrice, tickerTrades, manuallyEnteredCostBasis) {
            viewModel.calculateEffectiveCostBasis(
                symbol = activeTicker,
                livePrice = currentLivePrice,
                manuallyEnteredCostBasis = manuallyEnteredCostBasis,
                trades = tickerTrades
            )
        }

        // 6. Total P&L
        val totalPnL = remember(totalSharesHeld, effectiveCostBasis, currentLivePrice, totalPremiumCollected) {
            if (totalSharesHeld > 0) {
                (currentLivePrice - effectiveCostBasis) * totalSharesHeld
            } else {
                totalPremiumCollected
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDark),
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
                            // First Row: Large Ticker Icon & Live Price Block
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Large Ticker Symbol box like the 'V' in the image
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
                                            val convertedPrice = currentLivePrice * currencyMultiplier
                                            Text(
                                                text = String.format(Locale.getDefault(), "$%.2f", convertedPrice),
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
                                    val matchingTicker = watchlist.find { it.symbol.equals(activeTicker, ignoreCase = true) }
                                    val companyNameStr = matchingTicker?.companyName ?: activeTicker
                                    Text(
                                        text = companyNameStr,
                                        fontSize = 12.sp,
                                        color = GrayText,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "WHEEL STRATEGY TRACKER",
                                        fontSize = 11.sp,
                                        color = OptionBlue,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Summary Row Cards (Total Shares, Premiums, Weighted Return, Cost basis, P&L)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryCard(
                                title = "Total Shares Held",
                                value = "$totalSharesHeld",
                                desc = "Assigned vs Called",
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCard(
                                title = "Total Premium Collected",
                                value = String.format(Locale.getDefault(), "$%.2f", totalPremiumCollected * currencyMultiplier),
                                desc = "Net options income",
                                modifier = Modifier.weight(1f),
                                contentColor = EmeraldGreen
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryCard(
                                title = "Weighted Avg Ann. Return",
                                value = String.format(Locale.getDefault(), "%.1f%%", avgAnnReturn),
                                desc = "Option yield avg",
                                modifier = Modifier.weight(1f),
                                contentColor = TealAccent
                            )
                            SummaryCard(
                                title = "Effective Cost Basis",
                                value = String.format(Locale.getDefault(), "$%.2f", effectiveCostBasis * currencyMultiplier),
                                desc = "Strike - Net premiums",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SummaryCard(
                            title = "Total P&L (Unrealized Math Included)",
                            value = String.format(Locale.getDefault(), "$%.2f %s", totalPnL * currencyMultiplier, baseCurrency),
                            desc = "(Price - Cost basis) * shares + options premiums",
                            modifier = Modifier.fillMaxWidth(),
                            contentColor = if (totalPnL >= 0) EmeraldGreen else RedLoss
                        )
                    }
                }

                // Log a Trade Form block
                item {
                    if (showLogDialog) {
                        Dialog(
                            onDismissRequest = { showLogDialog = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfCard),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .fillMaxHeight(0.85f)
                                    .testTag("log_trade_dialog")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (editingTrade != null) "EDIT LOGGED STRATEGY TRADE" else "LOG NEW STRATEGY TRADE", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        IconButton(
                                            onClick = { showLogDialog = false },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("✕", color = GrayText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Trade Type Dropdown selection (represented as dynamic pill row for gorgeous interactive styling)
                                    Text("Trade Action Type", fontSize = 11.sp, color = GrayText)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val actions = remember(editingTrade) {
                                        val base = listOf("Sell CSP", "Sell CC", "Buying shares", "Selling shares")
                                        if (editingTrade != null && !base.contains(editingTrade!!.tradeType)) {
                                            base + editingTrade!!.tradeType
                                        } else {
                                            base
                                        }
                                    }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(actions) { action ->
                                            val isChosen = action == tradeType
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isChosen) TealAccent else MaterialTheme.colorScheme.background)
                                                    .clickable { tradeType = action }
                                                    .border(1.dp, if (isChosen) TealAccent else BorderGray, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(action, fontSize = 11.sp, color = if (isChosen) Color.Black else LightText, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = contractsInput,
                                            onValueChange = { contractsInput = it },
                                            label = {
                                                if (tradeType == "Buying shares" || tradeType == "Selling shares") {
                                                    Text("Quantity (Shares)")
                                                } else {
                                                    Text("Contracts")
                                                }
                                            },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealAccent)
                                        )

                                        OutlinedTextField(
                                            value = feesInput,
                                            onValueChange = { feesInput = it },
                                            label = { Text("Commissions ($)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f),
                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealAccent)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    val isShareTarget = tradeType == "Buying shares" || tradeType == "Selling shares" || tradeType == "Assignment" || tradeType == "Called Away"
                                    val snapshotVal = snapshot
                                    if (isShareTarget) {
                                        OutlinedTextField(
                                            value = fcfYieldInput,
                                            onValueChange = { fcfYieldInput = it },
                                            label = { Text("FCF Yield of Buy/Sell (%) — Custom Entry") },
                                            placeholder = { Text("e.g. 5.4") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = LightText,
                                                unfocusedTextColor = LightText,
                                                focusedBorderColor = TealAccent,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                            ),
                                            supportingText = {
                                                if (snapshotVal != null) {
                                                    Text("Calculated automatically based on: (FCF per share: $${String.format(Locale.getDefault(), "%.2f", snapshotVal.fcfPerShare)} / share price) * 100", color = GrayText)
                                                } else {
                                                    Text("Manually enter the FCF yield of this transaction to track in the portfolio.", color = GrayText)
                                                }
                                            }
                                        )
                                    }

                                    // Strike Price Slider with haptic ticks
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(NavyDark.copy(alpha = 0.6f))
                                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val strikeLabel = when (tradeType) {
                                                "Buying shares" -> "Avg Buy Price"
                                                "Selling shares" -> "Avg Sell Price"
                                                else -> "Strike Price"
                                            }
                                            Text(strikeLabel, fontSize = 12.sp, color = GrayText, fontWeight = FontWeight.Bold)
                                            val currentOrZeroVal = strikeInput.toDoubleOrNull() ?: 0.0
                                            Text(
                                                text = String.format(Locale.getDefault(), "$%.2f", currentOrZeroVal),
                                                fontSize = 18.sp,
                                                color = TealAccent,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = String.format(Locale.getDefault(), "$%.2f", minStrike),
                                                fontSize = 11.sp,
                                                color = GrayText
                                            )

                                            Slider(
                                                value = strikeSliderVal.coerceIn(minStrike, maxStrike),
                                                onValueChange = { newValue ->
                                                    val tickOffset = Math.round((newValue - centerStrike) / 0.25f).coerceIn(-64, 64)
                                                    val snapped = (centerStrike + tickOffset * 0.25f).coerceAtLeast(0.25f)
                                                    strikeSliderVal = snapped
                                                    if (snapped != lastHapticStrike) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        lastHapticStrike = snapped
                                                    }
                                                    strikeInput = String.format(Locale.US, "%.2f", snapped)
                                                },
                                                valueRange = minStrike..maxStrike,
                                                modifier = Modifier.weight(1f),
                                                colors = SliderDefaults.colors(
                                                    activeTrackColor = TealAccent,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                                                    thumbColor = TealAccent
                                                )
                                            )

                                            Text(
                                                text = String.format(Locale.getDefault(), "$%.2f", maxStrike),
                                                fontSize = 11.sp,
                                                color = GrayText
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Adjust manually ($): ", fontSize = 11.sp, color = GrayText)
                                            BasicTextField(
                                                value = strikeInput,
                                                onValueChange = {
                                                    strikeInput = it
                                                    it.toFloatOrNull()?.let { f ->
                                                        strikeSliderVal = f.coerceIn(minStrike, maxStrike)
                                                    }
                                                },
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                            )
                                        }
                                    }

                                    val premiumEnabled = tradeType != "Assignment" && tradeType != "Called Away" && tradeType != "Buying shares" && tradeType != "Selling shares"

                                    if (premiumEnabled) {
                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Premium Slider with haptic ticks
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(NavyDark.copy(alpha = 0.6f))
                                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                                                .padding(14.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Premium / Share", fontSize = 12.sp, color = GrayText, fontWeight = FontWeight.Bold)
                                                val currentPremOrZero = premiumInput.toDoubleOrNull() ?: 0.0
                                                Text(
                                                    text = String.format(Locale.getDefault(), "$%.2f", currentPremOrZero),
                                                    fontSize = 18.sp,
                                                    color = TealAccent,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("$0.00", fontSize = 11.sp, color = GrayText)

                                                Slider(
                                                    value = premiumSliderVal.coerceIn(0f, 10f),
                                                    onValueChange = { newValue ->
                                                        premiumSliderVal = newValue
                                                        val snapped = (Math.round(newValue / 0.05f) * 0.05f).toFloat()
                                                        if (snapped != (Math.round(lastHapticPremium / 0.05f) * 0.05f).toFloat()) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            lastHapticPremium = snapped
                                                        }
                                                        premiumInput = String.format(Locale.US, "%.2f", snapped)
                                                    },
                                                    valueRange = 0f..10f,
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        activeTrackColor = TealAccent,
                                                        inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                                                        thumbColor = TealAccent
                                                    )
                                                )

                                                Text("$10.00", fontSize = 11.sp, color = GrayText)
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Adjust manually ($): ", fontSize = 11.sp, color = GrayText)
                                                BasicTextField(
                                                    value = premiumInput,
                                                    onValueChange = {
                                                        premiumInput = it
                                                        it.toFloatOrNull()?.let { f ->
                                                            premiumSliderVal = f.coerceIn(0f, 10f)
                                                        }
                                                     },
                                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold),
                                                    modifier = Modifier
                                                        .width(100.dp)
                                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    if (tradeType == "Sell CSP" || tradeType == "Sell CC") {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = NavyDark.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Close early? (Buy to Close)", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                        Text("Ticking this calculates net options returns on early closing.", fontSize = 10.sp, color = GrayText)
                                                    }
                                                    Switch(
                                                        checked = isClosedInput,
                                                        onCheckedChange = { isClosedInput = it },
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = TealAccent,
                                                            checkedTrackColor = TealAccent.copy(alpha = 0.4f)
                                                        )
                                                    )
                                                }
                                                
                                                if (isClosedInput) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        OutlinedTextField(
                                                            value = closePremiumInput,
                                                            onValueChange = { closePremiumInput = it },
                                                            label = { Text("Closing Premium ($)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            modifier = Modifier.weight(1f),
                                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealAccent)
                                                        )
                                                        
                                                        OutlinedTextField(
                                                            value = closeFeesInput,
                                                            onValueChange = { closeFeesInput = it },
                                                            label = { Text("Closing Fees ($)") },
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                            modifier = Modifier.weight(1f),
                                                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealAccent)
                                                        )
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { closeDatePickerDialog.show() }
                                                    ) {
                                                        OutlinedTextField(
                                                            value = closeDateStr,
                                                            onValueChange = {},
                                                            label = { Text("Closing Date") },
                                                            readOnly = true,
                                                            enabled = false,
                                                            trailingIcon = { Text("📅", fontSize = 14.sp) },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                disabledTextColor = Color.White,
                                                                disabledBorderColor = Color.White.copy(alpha = 0.25f),
                                                                disabledLabelColor = GrayText
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = NavyDark.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text("Override Expiration Outcome", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text("Manually override if you got assigned or called away, ignoring automatic price calculation.", fontSize = 10.sp, color = GrayText)
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    val options = if (tradeType == "Sell CSP") {
                                                        listOf(
                                                            "" to "Auto (Live Price)",
                                                            "EXPIRED_WORTHLESS" to "Expired Worthless",
                                                            "ASSIGNED" to "Assigned (Got Shares)"
                                                        )
                                                    } else {
                                                        listOf(
                                                            "" to "Auto (Live Price)",
                                                            "EXPIRED_WORTHLESS" to "Expired Worthless",
                                                            "CALLED_AWAY" to "Called Away"
                                                        )
                                                    }
                                                    
                                                    options.forEach { (value, label) ->
                                                        val isSelected = manualOutcomeInput == value
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(if (isSelected) AmberWarning.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                                                .border(1.dp, if (isSelected) AmberWarning else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                                .clickable { manualOutcomeInput = value }
                                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = label,
                                                                fontSize = 9.sp,
                                                                color = if (isSelected) AmberWarning else Color.White,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Custom visual click targets for date layouts
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { tradeDatePickerDialog.show() }
                                        ) {
                                            OutlinedTextField(
                                                value = dateStr,
                                                onValueChange = {},
                                                label = { Text("Transaction Date") },
                                                readOnly = true,
                                                enabled = false,
                                                trailingIcon = { Text("📅", fontSize = 14.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    disabledTextColor = Color.White,
                                                    disabledBorderColor = Color.White.copy(alpha = 0.25f),
                                                    disabledLabelColor = GrayText
                                                )
                                            )
                                        }

                                        val expiryEnabled = tradeType == "Sell CSP" || tradeType == "Buy to Close" || tradeType == "Sell CC"
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = expiryEnabled) { expiryDatePickerDialog.show() }
                                        ) {
                                            OutlinedTextField(
                                                value = if (expiryEnabled) expiryStr else "N/A",
                                                onValueChange = {},
                                                label = { Text("Expiry Date") },
                                                readOnly = true,
                                                enabled = false,
                                                trailingIcon = { Text("📅", fontSize = 14.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    disabledTextColor = if (expiryEnabled) Color.White else GrayText,
                                                    disabledBorderColor = if (expiryEnabled) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                                    disabledLabelColor = GrayText
                                                )
                                            )
                                        }
                                    }

                                    // Live yield calculation block
                                    val instantAnnualizedYield = remember(tradeType, contractsInput, strikeInput, premiumInput, feesInput, dateStr, expiryStr) {
                                        val isSell = tradeType == "Sell CSP" || tradeType == "Sell CC"
                                        val isBuyToClose = tradeType == "Buy to Close"
                                        if (!(isSell || isBuyToClose)) return@remember null

                                        val contracts = contractsInput.toIntOrNull() ?: 1
                                        val strike = strikeInput.toDoubleOrNull() ?: 0.0
                                        val premium = premiumInput.toDoubleOrNull() ?: 0.0
                                        val fees = feesInput.toDoubleOrNull() ?: 0.0

                                        if (strike <= 0.0) return@remember null

                                        val tradeDate = try { sdf.parse(dateStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                                        val expiryDate = try { sdf.parse(expiryStr)?.time } catch (e: Exception) { null } ?: return@remember null

                                        val netCreditDebit = when {
                                            isSell -> (premium * 100.0 * contracts) - fees
                                            isBuyToClose -> -(premium * 100.0 * contracts) - fees
                                            else -> 0.0
                                        }

                                        val dteMillis = expiryDate - tradeDate
                                        val dteDays = (dteMillis / (24.0 * 60.0 * 60.0 * 1000.0)).coerceAtLeast(1.0)
                                        (netCreditDebit / (strike * 100.0 * contracts)) * (365.0 / dteDays) * 100.0
                                    }

                                    if (instantAnnualizedYield != null) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(TealAccent.copy(alpha = 0.08f))
                                                .border(BorderStroke(1.dp, TealAccent.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = "LIVE ANNUALIZED YIELD",
                                                    fontSize = 9.sp,
                                                    color = TealAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = String.format(Locale.getDefault(), "%.1f%%", instantAnnualizedYield),
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = TealAccent
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Based on current inputs & expiration DTE.",
                                                    fontSize = 10.sp,
                                                    color = GrayText
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showLogDialog = false },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                                            border = BorderStroke(1.dp, AmberWarning),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("CANCEL", fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                val contracts = contractsInput.toIntOrNull() ?: 1
                                                val strike = strikeInput.toDoubleOrNull() ?: 0.0
                                                val premium = premiumInput.toDoubleOrNull() ?: 0.0
                                                val fees = feesInput.toDoubleOrNull() ?: 0.0
                                                val isShareTarget = tradeType == "Buying shares" || tradeType == "Selling shares" || tradeType == "Assignment" || tradeType == "Called Away"
                                                val fcfYieldVal = if (isShareTarget) fcfYieldInput.toDoubleOrNull() ?: 0.0 else 0.0
                                                val tradeDate = try { sdf.parse(dateStr)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                                                val expiryDate = if (tradeType == "Sell CSP" || tradeType == "Buy to Close" || tradeType == "Sell CC") {
                                                    try { sdf.parse(expiryStr)?.time } catch (e: Exception) { null }
                                                } else null

                                                val closePremium = if (isClosedInput) closePremiumInput.toDoubleOrNull() ?: 0.0 else 0.0
                                                val closeFees = if (isClosedInput) closeFeesInput.toDoubleOrNull() ?: 0.0 else 0.0
                                                val closeDate = if (isClosedInput) {
                                                    try { if (closeDateStr.isNotEmpty()) sdf.parse(closeDateStr)?.time else null } catch (e: Exception) { null }
                                                } else null

                                                val editTradeId = editingTrade?.id
                                                if (editTradeId != null) {
                                                    viewModel.updateTrade(
                                                        manualOutcome = manualOutcomeInput,
                                                        id = editTradeId,
                                                        ticker = activeTicker,
                                                        tradeType = tradeType,
                                                        date = tradeDate,
                                                        contracts = contracts,
                                                        strikePrice = strike,
                                                        premiumPerShare = premium,
                                                        expiryDate = expiryDate,
                                                        fees = fees,
                                                        fcfYield = fcfYieldVal,
                                                        isClosed = isClosedInput,
                                                        closePremium = closePremium,
                                                        closeFees = closeFees,
                                                        closeDate = closeDate
                                                    )
                                                } else {
                                                    viewModel.logTrade(
                                                        manualOutcome = manualOutcomeInput,
                                                        ticker = activeTicker,
                                                        tradeType = tradeType,
                                                        date = tradeDate,
                                                        contracts = contracts,
                                                        strikePrice = strike,
                                                        premiumPerShare = premium,
                                                        expiryDate = expiryDate,
                                                        fcfYield = fcfYieldVal,
                                                        fees = fees,
                                                        isClosed = isClosedInput,
                                                        closePremium = closePremium,
                                                        closeFees = closeFees,
                                                        closeDate = closeDate
                                                    )
                                                }

                                                // clear/reset inputs
                                                strikeInput = ""
                                                premiumInput = ""
                                                editingTrade = null
                                                showLogDialog = false
                                            },
                                            modifier = Modifier.weight(1.1f).testTag("log_trade_confirm_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = Color.Black)
                                        ) {
                                            Text(if (editingTrade != null) "SAVE CHANGES" else "LOG TRADE", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            editingTrade = null
                            tradeType = "Sell CSP"
                            contractsInput = "1"
                            strikeSliderVal = centerStrike
                            lastHapticStrike = centerStrike
                            strikeInput = String.format(Locale.US, "%.2f", centerStrike)
                            premiumInput = ""
                            premiumSliderVal = 0f
                            feesInput = "0.99"
                            fcfYieldInput = ""
                            dateStr = todayStr
                            expiryStr = nextMonthStr
                            isClosedInput = false
                            closePremiumInput = ""
                            closeFeesInput = "0.99"
                            closeDateStr = todayStr
                            showLogDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("log_transaction_trade_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+ LOG TRANSACTION TRADE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // Table Header log title
                item {
                    Text(
                        "Transaction Trade Logging (${tickerTrades.size} entries)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = GrayText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Table logs
                if (tickerTrades.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfCard)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No trades logged yet. Fill out the form above to record your options and assignments.", color = GrayText, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    items(tickerTrades) { trade ->
                        TradeRow(
                            trade = trade,
                            currencyMultiplier = currencyMultiplier,
                            baseCurrency = baseCurrency,
                            onDelete = {
                                tradeToDelete = trade
                            },
                            onEdit = {
                                editingTrade = trade
                                tradeType = trade.tradeType
                                contractsInput = trade.contracts.toString()
                                strikeInput = String.format(Locale.US, "%.2f", trade.strikePrice)
                                strikeSliderVal = trade.strikePrice.toFloat().coerceIn(minStrike, maxStrike)
                                lastHapticStrike = strikeSliderVal
                                premiumInput = String.format(Locale.US, "%.2f", trade.premiumPerShare)
                                premiumSliderVal = trade.premiumPerShare.toFloat().coerceIn(0f, 20f)
                                feesInput = String.format(Locale.US, "%.2f", trade.fees)
                                fcfYieldInput = if (trade.fcfYield > 0) String.format(Locale.US, "%.2f", trade.fcfYield) else ""
                                dateStr = sdf.format(Date(trade.date))
                                expiryStr = trade.expiryDate?.let { sdf.format(Date(it)) } ?: nextMonthStr
                                isClosedInput = trade.isClosed
                                closePremiumInput = if (trade.isClosed) String.format(Locale.US, "%.2f", trade.closePremium) else ""
                                closeFeesInput = String.format(Locale.US, "%.2f", trade.closeFees)
                                closeDateStr = trade.closeDate?.let { sdf.format(Date(it)) } ?: todayStr
                                showLogDialog = true
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

        val targetTradeToDelete = tradeToDelete
        if (targetTradeToDelete != null) {
            val formattedDate = sdf.format(Date(targetTradeToDelete.date))
            val isShareTrade = targetTradeToDelete.tradeType == "Buying shares" || targetTradeToDelete.tradeType == "Selling shares"
            val qtyDesc = if (isShareTrade) "${targetTradeToDelete.contracts} shares" else "${targetTradeToDelete.contracts} contracts"
            val priceFormatted = String.format(Locale.getDefault(), "$%.2f", targetTradeToDelete.strikePrice * currencyMultiplier)

            AlertDialog(
                onDismissRequest = { tradeToDelete = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = RedLoss,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete ${targetTradeToDelete.tradeType}?",
                        color = LightText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete this ${targetTradeToDelete.tradeType} trade ($qtyDesc @ $priceFormatted on $formattedDate) for ${targetTradeToDelete.ticker}?",
                        color = GrayText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteLoggedTrade(targetTradeToDelete.id)
                            tradeToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RedLoss,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_delete_trade_button")
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { tradeToDelete = null },
                        modifier = Modifier.testTag("cancel_delete_trade_button")
                    ) {
                        Text("Cancel", color = GrayText, fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = SurfCard,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    desc: String,
    modifier: Modifier = Modifier,
    contentColor: Color = LightText
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfCard),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title.uppercase(),
                fontSize = 9.sp,
                color = GrayText,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = contentColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, fontSize = 10.sp, color = GrayText)
        }
    }
}

@Composable
fun TradeRow(
    trade: TradeEntity,
    currencyMultiplier: Double,
    baseCurrency: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val formattedDate = sdf.format(Date(trade.date))
    val expiryStr = trade.expiryDate?.let { sdf.format(Date(it)) } ?: "—"

    val badgeBg = if (trade.isClosed) {
        RedLoss.copy(alpha = 0.15f)
    } else {
        when (trade.tradeType) {
             "Sell CSP" -> AccentPill.copy(alpha = 0.15f)
             "Buy to Close" -> RedLoss.copy(alpha = 0.15f)
             "Assignment" -> AmberWarning.copy(alpha = 0.15f)
             "Sell CC" -> OptionCcIndigo.copy(alpha = 0.15f)
             "Called Away" -> TealAccent.copy(alpha = 0.15f)
             "Buying shares" -> EmeraldGreen.copy(alpha = 0.15f)
             "Selling shares" -> OptionCcIndigo.copy(alpha = 0.15f)
             else -> GrayText.copy(alpha = 0.15f)
        }
    }
 
    val badgeText = if (trade.isClosed) {
        RedLoss
    } else {
        when (trade.tradeType) {
             "Sell CSP" -> OptionBlue
             "Buy to Close" -> RedLoss
             "Assignment" -> AmberWarning
             "Sell CC" -> OptionCcIndigo
             "Called Away" -> TealAccent
             "Buying shares" -> EmeraldGreen
             "Selling shares" -> OptionCcIndigo
             else -> LightText
        }
    }

    val now = System.currentTimeMillis()
    val isCspActive = trade.tradeType == "Sell CSP" && !trade.isClosed && trade.expiryDate != null && trade.expiryDate > now
    val isCcActive = trade.tradeType == "Sell CC" && !trade.isClosed && trade.expiryDate != null && trade.expiryDate > now

    val cardBorder = when {
        isCspActive -> BorderStroke(1.5.dp, EmeraldGreen)
        isCcActive -> BorderStroke(1.5.dp, AmberWarning)
        trade.isClosed -> BorderStroke(1.dp, RedLoss.copy(alpha = 0.3f))
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfCard),
        shape = RoundedCornerShape(18.dp),
        border = cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val labelText = if (trade.isClosed) "${trade.tradeType.uppercase()} (CLOSED)" else trade.tradeType.uppercase()
                    Text(labelText, color = badgeText, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formattedDate, fontSize = 11.sp, color = GrayText)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = TealAccent.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onEdit() }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete trade",
                        tint = RedLoss.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(16.dp)
                            .testTag("delete_trade_button_${trade.id}")
                            .clickable { onDelete() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Details", fontSize = 10.sp, color = GrayText)
                    val isShareTrade = trade.tradeType == "Buying shares" || trade.tradeType == "Selling shares"
                    val detailText = if (isShareTrade) {
                        "${trade.contracts} shares @ ${String.format(Locale.getDefault(), "$%.2f", trade.strikePrice * currencyMultiplier)}"
                    } else {
                        "${trade.contracts} contracts @ ${String.format(Locale.getDefault(), "$%.2f", trade.strikePrice * currencyMultiplier)} strike"
                    }
                    Text(
                        text = detailText,
                        fontSize = 12.sp,
                        color = LightText,
                        fontWeight = FontWeight.Bold
                    )
                    if (trade.fcfYield > 0.0) {
                        Text("FCF Yield: ${String.format(Locale.getDefault(), "%.2f%%", trade.fcfYield)}", fontSize = 10.sp, color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                    if (trade.isClosed && trade.closeDate != null) {
                        val closePremiumFormatted = String.format(Locale.getDefault(), "$%.2f", trade.closePremium * currencyMultiplier)
                        Text("Closed early on ${sdf.format(Date(trade.closeDate))} @ $closePremiumFormatted", fontSize = 10.sp, color = RedLoss, fontWeight = FontWeight.Bold)
                    } else if (trade.expiryDate != null) {
                        Text("Expires: $expiryStr", fontSize = 10.sp, color = GrayText)
                    }
                    if (trade.manualOutcome.isNotEmpty()) {
                        val outcomeLabel = when (trade.manualOutcome) {
                            "ASSIGNED" -> "ASSIGNED (Got Shares)"
                            "CALLED_AWAY" -> "CALLED AWAY (Sold Shares)"
                            "EXPIRED_WORTHLESS" -> "EXPIRED WORTHLESS"
                            else -> trade.manualOutcome
                        }
                        Text("Override: $outcomeLabel", fontSize = 10.sp, color = AmberWarning, fontWeight = FontWeight.Bold)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Net Cashflow", fontSize = 10.sp, color = GrayText)
                    val creditBase = trade.netCreditDebit * currencyMultiplier
                    Text(
                        text = String.format(Locale.getDefault(), "%s$%.2f", if (creditBase >= 0) "+" else "", creditBase),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = if (creditBase >= 0) TealAccent else RedLoss,
                        fontFamily = FontFamily.Monospace
                    )

                    if (trade.annualizedReturn > 0.0) {
                        Text(
                            text = String.format(Locale.getDefault(), "Ann. Return: %.1f%%", trade.annualizedReturn),
                            fontSize = 10.sp,
                            color = TealAccent
                        )
                    }
                }
            }
        }
    }
}
