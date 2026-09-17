package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.example.domain.model.WatchlistTicker
import com.example.domain.util.getTickerCurrency
import com.example.domain.util.getFxMultiplier
import com.example.ui.theme.*
import com.example.viewmodel.FinanceViewModel
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

private val CURRENCY_DECIMAL_FORMAT by lazy {
    val symbols = java.text.DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ' '
    }
    java.text.DecimalFormat("#,##0.00", symbols)
}

private const val MASKED_AMOUNT = "••••"

private fun formatCurrency(value: Double, currency: String = "", isHidden: Boolean = false): String {
    if (isHidden) {
        return MASKED_AMOUNT
    }
    val isNegative = value < 0
    val absVal = kotlin.math.abs(value)
    val formatted = synchronized(CURRENCY_DECIMAL_FORMAT) {
        CURRENCY_DECIMAL_FORMAT.format(absVal)
    }
    val currencyStr = if (currency.isNotEmpty()) " $currency" else ""
    return if (isNegative) {
        "-$$formatted$currencyStr"
    } else {
        "$$formatted$currencyStr"
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: FinanceViewModel,
    onNavigateToWheel: (String) -> Unit,
    onNavigateToCalculator: (String) -> Unit,
    onNavigateToIntelligence: (String) -> Unit,
    onAddTicker: () -> Unit = {},
    landTrigger: Int = 0
) {
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val tickerScores by viewModel.tickerScores.collectAsStateWithLifecycle()
    val allSnapshots by viewModel.allCalculatorSnapshots.collectAsStateWithLifecycle()
    val allTrades by viewModel.allTrades.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.currencyFlow.collectAsStateWithLifecycle()
    val isSyncingAll by viewModel.isSyncingAll.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var localWatchlist by remember(watchlist) { mutableStateOf(watchlist) }
    var draggedSymbol by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(watchlist, draggedSymbol) {
        if (draggedSymbol == null) {
            localWatchlist = watchlist
        }
    }

    // Ensure all watchlist tickers have fresh prices on load
    LaunchedEffect(Unit) {
        viewModel.syncAllWatchlistTickers(force = false)
    }

    var isAmountsHidden by rememberSaveable { mutableStateOf(false) }
    var showAddCashDialog by remember { mutableStateOf(false) }
    var depositAmountInput by remember { mutableStateOf("") }
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var depositDateInput by remember { mutableStateOf(sdf.format(java.util.Date())) }
    var isDepositSelected by remember { mutableStateOf(true) }

    var showLogStockDialog by remember { mutableStateOf(false) }
    var isBuyStockSelected by remember { mutableStateOf(true) }
    var stockTickerInput by remember { mutableStateOf("") }
    var stockQtyInput by remember { mutableStateOf("") }
    var stockPriceInput by remember { mutableStateOf("") }
    var stockFeesInput by remember { mutableStateOf("0.0") }
    var stockDateInput by remember { mutableStateOf(sdf.format(java.util.Date())) }
    var stockFcfYieldInput by remember { mutableStateOf("") }
    var isTickerDropdownExpanded by remember { mutableStateOf(false) }
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    val matchingWatchlist = remember(stockTickerInput, watchlist) {
        val query = stockTickerInput.trim().uppercase()
        if (query.isEmpty()) emptyList()
        else watchlist.filter {
            it.symbol.uppercase().contains(query) || it.companyName.uppercase().contains(query)
        }
    }
    val remoteMatches = remember(stockTickerInput, searchResults, matchingWatchlist) {
        val query = stockTickerInput.trim().uppercase()
        if (query.length < 2) emptyList()
        else searchResults.filter { res ->
            matchingWatchlist.none { it.symbol.equals(res.symbol, ignoreCase = true) }
        }
    }

    LaunchedEffect(stockTickerInput, stockPriceInput, isBuyStockSelected) {
        if (stockTickerInput.isNotEmpty() && isBuyStockSelected) {
            val sym = stockTickerInput.uppercase().trim()
            val snapshot = allSnapshots[sym] ?: viewModel.getCalculatorSnapshot(sym)
            val price = stockPriceInput.toDoubleOrNull() ?: 0.0
            if (snapshot != null && price > 0.0 && snapshot.fcfPerShare > 0.0) {
                stockFcfYieldInput = String.format(Locale.US, "%.2f", (snapshot.fcfPerShare / price) * 100.0)
            } else if (snapshot != null) {
                val stockPrice = if (snapshot.currentPrice > 0.0) snapshot.currentPrice else 0.0
                if (stockPrice > 0.0 && snapshot.fcfPerShare > 0.0) {
                    stockFcfYieldInput = String.format(Locale.US, "%.2f", (snapshot.fcfPerShare / stockPrice) * 100.0)
                } else if (snapshot.historicalFcfYield > 0.0) {
                    stockFcfYieldInput = String.format(Locale.US, "%.2f", snapshot.historicalFcfYield)
                } else {
                    stockFcfYieldInput = ""
                }
            } else {
                stockFcfYieldInput = ""
            }
        } else {
            stockFcfYieldInput = ""
        }
    }

    val portfolioSummary = remember(watchlist, allTrades, allSnapshots, baseCurrency) {
        viewModel.calculatePortfolioSummary(
            watchlist = watchlist,
            trades = allTrades,
            snapshots = allSnapshots,
            baseCurrency = baseCurrency
        )
    }

    val totalCashBalance = portfolioSummary.totalCashBalance
    val totalEquity = portfolioSummary.totalEquity
    val buyingPower = portfolioSummary.buyingPower
    val totalStockMarketValue = portfolioSummary.totalStockMarketValue
    val aggregatePremiums = portfolioSummary.aggregatePremiums
    val aggregateUnrealizedPnL = portfolioSummary.aggregateUnrealizedPnL
    val aggregateTotalPnL = portfolioSummary.aggregateTotalPnL
    val totalCspLocked = portfolioSummary.totalCspLocked
    val isRefreshing = isSyncingAll

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            viewModel.syncAllWatchlistTickers(force = true)
        },
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
    ) {
        if (watchlist.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Welcome Card in empty state
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = TealAccent.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Welcome to Equity IQ!",
                            color = TealAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No tracked stocks in your portfolio yet. Add assets to retrieve live financial metrics, valuation models, and log options trades.",
                            color = GrayText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onAddTicker,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFA21CAF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("portfolio_add_ticker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ADD TICKER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Available Cash/Buying Power/Total Equity Card in empty state
            if (totalCashBalance != 0.0 || totalEquity != 0.0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfCard),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "TOTAL EQUITY",
                                        fontSize = 10.sp,
                                        color = GrayText,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(totalEquity, baseCurrency, isHidden = isAmountsHidden),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "BUYING POWER",
                                        fontSize = 10.sp,
                                        color = GrayText,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(buyingPower, baseCurrency, isHidden = isAmountsHidden),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealAccent
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isAmountsHidden) "Cash: ••••" else "Cash: ${formatCurrency(totalCashBalance, baseCurrency)}",
                                    fontSize = 11.sp,
                                    color = LightText
                                )
                                if (totalStockMarketValue > 0.0) {
                                    Text(
                                        text = if (isAmountsHidden) "Market Value: ••••" else "Market Value: ${formatCurrency(totalStockMarketValue, baseCurrency)}",
                                        fontSize = 11.sp,
                                        color = LightText
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cash Management Card in empty state
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Cash Management",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Log cash deposits or withdrawals to manage your buying power and total equity calculations.",
                            color = GrayText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isDepositSelected = true
                                    showAddCashDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("+ DEPOSIT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    isDepositSelected = false
                                    showAddCashDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfCard, contentColor = RedLoss),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RedLoss.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("- WITHDRAW", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Stock Transactions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Log buying or selling of stock shares to track your custom investment yield & portfolio holdings directly.",
                            color = GrayText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isBuyStockSelected = true
                                    showLogStockDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("+ BUY STOCK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    isBuyStockSelected = false
                                    showLogStockDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfCard, contentColor = RedLoss),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RedLoss.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("- SELL STOCK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    } else {

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDark)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            syncError?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Sync Alert", tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(err, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Beautiful Top Overall Premium-P&L Card (Sleek Theme style)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = TealAccent.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.15f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TOTAL P&L (CONSOLIDATED)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatCurrency(aggregateTotalPnL, isHidden = isAmountsHidden),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    color = LightText,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val signPrems = if (aggregatePremiums >= 0) "+" else ""
                                    Text(
                                        text = if (isAmountsHidden) "Premiums: ••••" else "Premiums: $signPrems${formatCurrency(aggregatePremiums)}",
                                        fontSize = 12.sp,
                                        color = TealAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = " • ",
                                        color = GrayText,
                                        fontSize = 12.sp
                                    )
                                    val signUnrealized = if (aggregateUnrealizedPnL >= 0) "+" else ""
                                    Text(
                                        text = if (isAmountsHidden) "Unrealized: ••••" else "Unrealized: $signUnrealized${formatCurrency(aggregateUnrealizedPnL)}",
                                        fontSize = 12.sp,
                                        color = if(aggregateUnrealizedPnL >= 0) TealAccent else RedLoss,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { isAmountsHidden = !isAmountsHidden },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("portfolio_toggle_mask_button")
                                ) {
                                    Icon(
                                        imageVector = if (isAmountsHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isAmountsHidden) "Show balances" else "Hide balances",
                                        tint = GrayText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Currency indicator pill matching HTML theme
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = baseCurrency,
                                        color = LightText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = TealAccent.copy(alpha = 0.12f),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        // 2x2 Grid of Account Values
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Total Equity
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("TOTAL EQUITY", fontSize = 10.sp, color = GrayText, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(totalEquity, isHidden = isAmountsHidden),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                // Cash
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("CASH", fontSize = 10.sp, color = GrayText, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(totalCashBalance, isHidden = isAmountsHidden),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Buying Power
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("BUYING POWER", fontSize = 10.sp, color = GrayText, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatCurrency(buyingPower, isHidden = isAmountsHidden),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (totalCspLocked > 0.0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isAmountsHidden) "CSP Lock: ••••" else "CSP Lock: " + formatCurrency(totalCspLocked),
                                            fontSize = 9.sp,
                                            color = AmberWarning,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Stock Market Value
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("MARKET VALUE", fontSize = 10.sp, color = GrayText, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (totalStockMarketValue > 0.0) {
                                            formatCurrency(totalStockMarketValue, isHidden = isAmountsHidden)
                                        } else "—",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(localWatchlist, key = { it.symbol }) { ticker ->
                val isCurrentDragged = draggedSymbol == ticker.symbol
                val cardScale by animateFloatAsState(
                    targetValue = if (isCurrentDragged) 1.03f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "cardScale_${ticker.symbol}"
                )
                val cardElevation by animateDpAsState(
                    targetValue = if (isCurrentDragged) 18.dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "cardElevation_${ticker.symbol}"
                )

                // Filter trades logged for this specific ticker
                val tickerTrades = allTrades.filter { it.ticker.equals(ticker.symbol, ignoreCase = true) }
                val now = System.currentTimeMillis()

                // Fetch total assigned shares
                val assignedShares = tickerTrades.fold(0) { sum, trade ->
                    when (trade.tradeType) {
                        "Assignment" -> sum + (trade.contracts * 100)
                        "Called Away" -> sum - (trade.contracts * 100)
                        "Buying shares" -> sum + trade.contracts
                        "Selling shares" -> sum - trade.contracts
                        "Sell CSP" -> {
                            when (trade.manualOutcome) {
                                "ASSIGNED" -> sum + (trade.contracts * 100)
                                "EXPIRED_WORTHLESS" -> sum
                                else -> {
                                    val isExpired = trade.expiryDate != null && trade.expiryDate <= now
                                    if (!trade.isClosed && isExpired && ticker.livePrice > 0.0 && ticker.livePrice < trade.strikePrice) {
                                        sum + (trade.contracts * 100)
                                    } else sum
                                }
                            }
                        }
                        "Sell CC" -> {
                            when (trade.manualOutcome) {
                                "CALLED_AWAY" -> sum - (trade.contracts * 100)
                                "EXPIRED_WORTHLESS" -> sum
                                else -> {
                                    val isExpired = trade.expiryDate != null && trade.expiryDate <= now
                                    if (!trade.isClosed && isExpired && ticker.livePrice > 0.0 && ticker.livePrice > trade.strikePrice) {
                                        sum - (trade.contracts * 100)
                                    } else sum
                                }
                            }
                        }
                        else -> sum
                    }
                }.coerceAtLeast(0)

                // Sum total option premiums collected
                val premiumsCollected = tickerTrades.filter {
                    it.tradeType == "Sell CSP" || it.tradeType == "Buy to Close" || it.tradeType == "Sell CC"
                }.sumOf { it.netCreditDebit }

                // Assigned Stock Paid cost
                val totalBuyShares = tickerTrades.fold(0) { sum, it ->
                    when (it.tradeType) {
                        "Assignment" -> sum + (it.contracts * 100)
                        "Buying shares" -> sum + it.contracts
                        "Sell CSP" -> {
                            when (it.manualOutcome) {
                                "ASSIGNED" -> sum + (it.contracts * 100)
                                "EXPIRED_WORTHLESS" -> sum
                                else -> {
                                    val isExpired = it.expiryDate != null && it.expiryDate <= now
                                    if (!it.isClosed && isExpired && ticker.livePrice > 0.0 && ticker.livePrice < it.strikePrice) {
                                        sum + (it.contracts * 100)
                                    } else sum
                                }
                            }
                        }
                        else -> sum
                    }
                }
                val totalBuyCost = tickerTrades.sumOf {
                    when (it.tradeType) {
                        "Assignment" -> it.strikePrice * 100.0 * it.contracts
                        "Buying shares" -> it.strikePrice * it.contracts
                        "Sell CSP" -> {
                            when (it.manualOutcome) {
                                "ASSIGNED" -> it.strikePrice * 100.0 * it.contracts
                                "EXPIRED_WORTHLESS" -> 0.0
                                else -> {
                                    val isExpired = it.expiryDate != null && it.expiryDate <= now
                                    if (!it.isClosed && isExpired && ticker.livePrice > 0.0 && ticker.livePrice < it.strikePrice) {
                                        it.strikePrice * 100.0 * it.contracts
                                    } else 0.0
                                }
                            }
                        }
                        else -> 0.0
                    }
                }
                val avgBuyPrice = if (totalBuyShares > 0) totalBuyCost / totalBuyShares else 0.0

                // Determine active Cost Basis:
                val activeCostBasis = viewModel.calculateEffectiveCostBasis(
                    symbol = ticker.symbol,
                    livePrice = ticker.livePrice,
                    manuallyEnteredCostBasis = ticker.manuallyEnteredCostBasis,
                    trades = tickerTrades
                )

                // Query calculator snapshots to find DCF values, Market FCF sentiment & owner FCF parameters
                val symClean = ticker.symbol.uppercase().trim()
                val liveSnapshot = allSnapshots[symClean] ?: allSnapshots[ticker.symbol]

                var cachedDcfVal by remember { mutableStateOf<Double?>(null) }
                var cachedMarketFcfSentimentVal by remember { mutableStateOf<Double?>(null) }
                var cachedFcfPerShare by remember { mutableStateOf<Double?>(null) }
                var cachedHistoricalFcfYield by remember { mutableStateOf<Double?>(null) }
                var cachedSnapshotPrice by remember { mutableStateOf<Double?>(null) }
                var cachedSnapshotScore by remember(ticker.symbol) { mutableIntStateOf(0) }
                LaunchedEffect(ticker.symbol, liveSnapshot) {
                    val snap = liveSnapshot ?: viewModel.getCalculatorSnapshot(ticker.symbol)
                    if (snap != null) {
                        if (snap.currentPrice > 0.0) {
                            cachedSnapshotPrice = snap.currentPrice
                        }
                        cachedFcfPerShare = snap.fcfPerShare
                        cachedHistoricalFcfYield = snap.historicalFcfYield
                        val (adjustedDcf, _) = viewModel.calculateMoatQualityAdjustedDcf(snap)
                        cachedDcfVal = adjustedDcf
                        val fcfSent = if (snap.historicalFcfYield > 0.0 && snap.fcfPerShare > 0.0) {
                            snap.fcfPerShare / (snap.historicalFcfYield / 100.0)
                        } else null
                        cachedMarketFcfSentimentVal = fcfSent

                        val bsScore = calculateBalanceSheetHealthScore(snap.cashOnHand, snap.ltDebt, snap.ttmFcf, snap.interestCoverage)
                        val conv = if (snap.ttmNetIncome != 0.0) (snap.ttmFcf / snap.ttmNetIncome) * 100.0 else 0.0
                        val pqScore = calculateProfitQualityRankScore(snap.roicPercent, snap.fcfMarginPercent, conv)
                        cachedSnapshotScore = (bsScore + pqScore) / 2
                    }
                    if (snap == null || ticker.livePrice <= 0.0) {
                        viewModel.syncTicker(ticker.symbol, force = false)
                    }
                }

                val effectiveSnapshot = liveSnapshot
                val effectiveFcfPerShare = effectiveSnapshot?.fcfPerShare?.takeIf { it > 0.0 } ?: cachedFcfPerShare ?: 0.0
                val effectiveHistoricalFcfYield = effectiveSnapshot?.historicalFcfYield?.takeIf { it > 0.0 } ?: cachedHistoricalFcfYield ?: 0.0

                val evaluatedTotalScore = tickerScores[symClean]
                    ?: tickerScores[ticker.symbol]
                    ?: cachedSnapshotScore
                val isHighScore = evaluatedTotalScore > 80

                val dcfVal = if (liveSnapshot != null) {
                    viewModel.calculateMoatQualityAdjustedDcf(liveSnapshot).first
                } else {
                    cachedDcfVal ?: 0.0
                }

                val marketFcfSentimentVal = if (liveSnapshot != null && liveSnapshot.historicalFcfYield > 0.0 && liveSnapshot.fcfPerShare > 0.0) {
                    liveSnapshot.fcfPerShare / (liveSnapshot.historicalFcfYield / 100.0)
                } else {
                    cachedMarketFcfSentimentVal ?: 0.0
                }

                // Unrealized P&L
                val stockLivePrice = if (ticker.livePrice > 0.0) ticker.livePrice else (effectiveSnapshot?.currentPrice ?: (cachedSnapshotPrice ?: 0.0))
                val baseCostBasis = ticker.manuallyEnteredCostBasis ?: avgBuyPrice
                val unrealizedPnL = if (assignedShares > 0) {
                    (stockLivePrice - baseCostBasis) * assignedShares
                } else 0.0

                val unrealizedPnLPct = if (baseCostBasis > 0.0) {
                    ((stockLivePrice - baseCostBasis) / baseCostBasis) * 100.0
                } else 0.0

                // Weighted FCF Yield of buys & assignments:
                val buyTrades = tickerTrades.filter { trade ->
                    when (trade.tradeType) {
                        "Buying shares", "Assignment" -> true
                        "Sell CSP" -> {
                            trade.manualOutcome == "ASSIGNED" || (!trade.isClosed && trade.expiryDate != null && trade.expiryDate <= now && ticker.livePrice > 0.0 && ticker.livePrice < trade.strikePrice)
                        }
                        else -> false
                    }
                }
                val totalBuySharesForYield = buyTrades.sumOf { trade ->
                    when (trade.tradeType) {
                        "Assignment", "Sell CSP" -> trade.contracts * 100
                        "Buying shares" -> trade.contracts
                        else -> 0
                    }
                }

                val averageOwnersFcfYieldPct = if (assignedShares > 0 && totalBuySharesForYield > 0) {
                    val weightedFcfYieldSum = buyTrades.sumOf { trade ->
                        val qty = when (trade.tradeType) {
                            "Assignment", "Sell CSP" -> trade.contracts * 100
                            "Buying shares" -> trade.contracts
                            else -> 0
                        }
                        val yieldOfTrade = when {
                            trade.fcfYield > 0.0 -> trade.fcfYield
                            effectiveFcfPerShare > 0.0 && trade.strikePrice > 0.0 -> (effectiveFcfPerShare / trade.strikePrice) * 100.0
                            effectiveFcfPerShare > 0.0 && stockLivePrice > 0.0 -> (effectiveFcfPerShare / stockLivePrice) * 100.0
                            effectiveHistoricalFcfYield > 0.0 -> effectiveHistoricalFcfYield
                            ticker.targetYield != null && ticker.targetYield > 0.0 -> ticker.targetYield
                            else -> 0.0
                        }
                        qty * yieldOfTrade
                    }
                    if (totalBuySharesForYield > 0) weightedFcfYieldSum / totalBuySharesForYield else 0.0
                } else {
                    0.0
                }

                val estimatedAnnualFcfValue = if (assignedShares > 0) {
                    val fcfPerShareVal = when {
                        effectiveFcfPerShare > 0.0 -> effectiveFcfPerShare
                        effectiveHistoricalFcfYield > 0.0 && stockLivePrice > 0.0 -> (effectiveHistoricalFcfYield / 100.0) * stockLivePrice
                        averageOwnersFcfYieldPct > 0.0 && activeCostBasis > 0.0 -> (averageOwnersFcfYieldPct / 100.0) * activeCostBasis
                        averageOwnersFcfYieldPct > 0.0 && stockLivePrice > 0.0 -> (averageOwnersFcfYieldPct / 100.0) * stockLivePrice
                        else -> 0.0
                    }
                    fcfPerShareVal * assignedShares
                } else 0.0

                val liveFcfYield = if (effectiveFcfPerShare > 0.0 && stockLivePrice > 0.0) {
                    (effectiveFcfPerShare / stockLivePrice) * 100.0
                } else null

                var isEditingTargetPrice by remember(ticker.symbol) { mutableStateOf(false) }
                var manualTargetInput by remember(ticker.symbol, ticker.targetPrice) {
                    mutableStateOf(ticker.targetPrice?.let { String.format(Locale.US, "%.2f", it) } ?: "")
                }
                var manualTargetYieldInput by remember(ticker.symbol, ticker.targetYield, ticker.targetPrice, effectiveFcfPerShare) {
                    val explicitYield = ticker.targetYield
                    if (explicitYield != null && explicitYield > 0.0) {
                        mutableStateOf(String.format(Locale.US, "%.2f", explicitYield))
                    } else {
                        val fcf = effectiveFcfPerShare
                        val tp = ticker.targetPrice
                        val y = if (tp != null && tp > 0.0 && fcf > 0.0) {
                            String.format(Locale.US, "%.2f", (fcf / tp) * 100.0)
                        } else ""
                        mutableStateOf(y)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .zIndex(if (isCurrentDragged) 10f else 1f)
                        .padding(horizontal = 2.dp, vertical = 6.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentDragged) Color(0xFF1E293B) else SurfCard
                        ),
                        shape = RoundedCornerShape(28.dp),
                        border = if (isCurrentDragged) {
                            BorderStroke(2.dp, TealAccent.copy(alpha = 0.9f))
                        } else if (isHighScore) {
                            BorderStroke(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFFD700), // Warm Gold
                                        Color(0xFFFFA500), // Amber
                                        Color(0xFFFFBF00), // Amber Gold
                                        Color(0xFFFFD700)  // Warm Gold
                                    )
                                )
                            )
                        } else {
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isCurrentDragged) {
                                    translationY = dragOffsetY
                                    scaleX = cardScale
                                    scaleY = cardScale
                                    shadowElevation = cardElevation.toPx()
                                }
                            }
                            .pointerInput(ticker.symbol) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedSymbol = ticker.symbol
                                    dragOffsetY = 0f
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y

                                    val fromIndex = localWatchlist.indexOfFirst { it.symbol == ticker.symbol }
                                    if (fromIndex != -1) {
                                        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                        val draggedItemInfo = visibleItems.find { it.key == ticker.symbol }
                                        if (draggedItemInfo != null) {
                                            val draggedCenter = draggedItemInfo.offset + (draggedItemInfo.size / 2) + dragOffsetY

                                            // Check moving up
                                            if (fromIndex > 0) {
                                                val prevSymbol = localWatchlist[fromIndex - 1].symbol
                                                val prevItemInfo = visibleItems.find { it.key == prevSymbol }
                                                if (prevItemInfo != null) {
                                                    val prevCenter = prevItemInfo.offset + (prevItemInfo.size / 2)
                                                    if (draggedCenter < prevCenter) {
                                                        val updated = localWatchlist.toMutableList()
                                                        val item = updated.removeAt(fromIndex)
                                                        updated.add(fromIndex - 1, item)
                                                        localWatchlist = updated
                                                        dragOffsetY += (draggedItemInfo.offset - prevItemInfo.offset)
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }

                                            // Check moving down
                                            if (fromIndex < localWatchlist.size - 1) {
                                                val nextSymbol = localWatchlist[fromIndex + 1].symbol
                                                val nextItemInfo = visibleItems.find { it.key == nextSymbol }
                                                if (nextItemInfo != null) {
                                                    val nextCenter = nextItemInfo.offset + (nextItemInfo.size / 2)
                                                    if (draggedCenter > nextCenter) {
                                                        val updated = localWatchlist.toMutableList()
                                                        val item = updated.removeAt(fromIndex)
                                                        updated.add(fromIndex + 1, item)
                                                        localWatchlist = updated
                                                        dragOffsetY -= (nextItemInfo.offset - draggedItemInfo.offset)
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Edge auto-scrolling
                                    val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                    val draggedItemInfo = visibleItems.find { it.key == ticker.symbol }
                                    if (draggedItemInfo != null) {
                                        val viewportTop = lazyListState.layoutInfo.viewportStartOffset
                                        val viewportBottom = lazyListState.layoutInfo.viewportEndOffset
                                        val currentTop = draggedItemInfo.offset + dragOffsetY
                                        val currentBottom = currentTop + draggedItemInfo.size
                                        if (currentTop < viewportTop + 140) {
                                            coroutineScope.launch {
                                                lazyListState.scrollBy(-20f)
                                            }
                                        } else if (currentBottom > viewportBottom - 140) {
                                            coroutineScope.launch {
                                                lazyListState.scrollBy(20f)
                                            }
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val orderedSymbols = localWatchlist.map { it.symbol }
                                    viewModel.updateWatchlistOrder(orderedSymbols)
                                    draggedSymbol = null
                                    dragOffsetY = 0f
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragCancel = {
                                    draggedSymbol = null
                                    dragOffsetY = 0f
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Ticker Info row (Sleek Theme style)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onNavigateToIntelligence(ticker.symbol) }
                            ) {
                                val dbLogoUrl = ticker.logoUrl
                                val fallbackSymbol = ticker.symbol.uppercase().trim()
                                val fallbackLogoUrl = "https://financialmodelingprep.com/image-stock/$fallbackSymbol.png"
                                val finalLogoUrl = if (!dbLogoUrl.isNullOrEmpty()) dbLogoUrl else fallbackLogoUrl

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    var imageLoadFailed by remember(finalLogoUrl) { mutableStateOf(false) }
                                    if (!imageLoadFailed) {
                                        AsyncImage(
                                            model = finalLogoUrl,
                                            contentDescription = "${ticker.symbol} Logo",
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            onError = { imageLoadFailed = true }
                                        )
                                    } else {
                                        Text(
                                            text = ticker.symbol,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = LightText,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(ticker.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LightText)
                                        if (isHighScore) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFB45309).copy(alpha = 0.25f))
                                                    .border(0.8.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "★ $evaluatedTotalScore",
                                                    color = Color(0xFFFFD700),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                    Text(ticker.companyName, fontSize = 11.sp, color = GrayText)
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                val tickerCurrency = getTickerCurrency(ticker.symbol)
                                Text(
                                    text = formatCurrency(stockLivePrice, tickerCurrency),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    val changePct = ticker.changePercent
                                    if (changePct != null) {
                                        val isPositive = changePct > 0.0001
                                        val isNegative = changePct < -0.0001
                                        val pctColor = when {
                                            isPositive -> EmeraldGreen
                                            isNegative -> RedLoss
                                            else -> GrayText
                                        }
                                        val prefix = if (isPositive) "+" else ""
                                        Text(
                                            text = String.format(Locale.US, "%s%.2f%%", prefix, changePct),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = pctColor
                                        )
                                        Text(
                                            text = " • ",
                                            fontSize = 10.sp,
                                            color = GrayText.copy(alpha = 0.5f)
                                        )
                                    }
                                    Text(
                                        text = if (liveFcfYield != null && liveFcfYield > 0.0) {
                                            String.format(Locale.US, "%.2f%% FCF", liveFcfYield)
                                        } else {
                                            "FCF N/A"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (liveFcfYield != null && liveFcfYield > 0.0) AmberWarning else GrayText.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(vertical = 12.dp))

                        // Stats Grid Row (3 Equal Sized Squares: Cost Basis, DCF Valuation, Price Target)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Cost Basis Square (Navigates to Wheel Tracker on Click)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.035f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                    .clickable { onNavigateToWheel(ticker.symbol) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "COST BASIS",
                                        fontSize = 8.sp,
                                        color = GrayText,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.4.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val costDisplay = activeCostBasis
                                    Text(
                                        text = formatCurrency(costDisplay),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LightText,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (ticker.manuallyEnteredCostBasis != null) "Manual" else "Calculated",
                                            fontSize = 7.5.sp,
                                            color = if (ticker.manuallyEnteredCostBasis != null) AmberWarning else GrayText,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                        if (ticker.manuallyEnteredCostBasis != null) {
                                            Text(
                                                text = " (M)",
                                                fontSize = 7.5.sp,
                                                color = AmberWarning,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Split DCF & Market FCF Sentiment Valuation Square (Left & Right Split)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.035f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                    .clickable { onNavigateToCalculator(ticker.symbol) }
                                    .padding(horizontal = 6.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: DCF Valuation
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "DCF",
                                            fontSize = 8.sp,
                                            color = GrayText,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (dcfVal > 0.0) {
                                                formatCurrency(dcfVal)
                                            } else "N/A",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TealAccent,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Intrinsic",
                                            fontSize = 7.5.sp,
                                            color = GrayText,
                                            maxLines = 1
                                        )
                                    }

                                    // Subtle Vertical Divider
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(Color.White.copy(alpha = 0.08f))
                                    )

                                    // Right: Market FCF Sentiment
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 5.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "FCF",
                                            fontSize = 8.sp,
                                            color = OptionBlue.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (marketFcfSentimentVal > 0.0) {
                                                formatCurrency(marketFcfSentimentVal)
                                            } else "N/A",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = OptionBlue,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Sentiment",
                                            fontSize = 7.5.sp,
                                            color = GrayText,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // 3. Split Price Target & Yield Target Square (Left & Right Split)
                            val targetVal = ticker.targetPrice
                            val explicitYield = ticker.targetYield
                            val fcfPerShareForTarget = cachedFcfPerShare ?: 0.0
                            val targetYieldVal = if (explicitYield != null && explicitYield > 0.0) {
                                explicitYield
                            } else if (targetVal != null && targetVal > 0.0 && fcfPerShareForTarget > 0.0) {
                                (fcfPerShareForTarget / targetVal) * 100.0
                            } else null
                            val hasTarget = targetVal != null && targetVal > 0.0
                            val targetBorderColor = if (isEditingTargetPrice) TealAccent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.035f))
                                    .border(1.dp, targetBorderColor, RoundedCornerShape(14.dp))
                                    .clickable {
                                        isEditingTargetPrice = !isEditingTargetPrice
                                    }
                                    .padding(horizontal = 6.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: Price Target
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "PRICE",
                                            fontSize = 8.sp,
                                            color = GrayText,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (targetVal != null && targetVal > 0.0) {
                                                formatCurrency(targetVal)
                                            } else "--",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (hasTarget) LightText else GrayText.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Target",
                                            fontSize = 7.5.sp,
                                            color = GrayText,
                                            maxLines = 1
                                        )
                                    }

                                    // Subtle Vertical Divider
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(30.dp)
                                            .background(Color.White.copy(alpha = 0.08f))
                                    )

                                    // Right: Yield Target
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 5.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = "YIELD",
                                            fontSize = 8.sp,
                                            color = AmberWarning.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.4.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (targetYieldVal != null && targetYieldVal > 0.0) {
                                                String.format(Locale.US, "%.1f%%", targetYieldVal)
                                            } else "--",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (targetYieldVal != null) AmberWarning else GrayText.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "Target",
                                            fontSize = 7.5.sp,
                                            color = GrayText,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Price & Yield Target editor field inline
                        if (isEditingTargetPrice) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, TealAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = manualTargetInput,
                                        onValueChange = {
                                            manualTargetInput = it
                                            val p = it.replace(',', '.').toDoubleOrNull()
                                            val fcf = cachedFcfPerShare ?: 0.0
                                            if (p != null && p > 0.0 && fcf > 0.0) {
                                                manualTargetYieldInput = String.format(Locale.US, "%.2f", (fcf / p) * 100.0)
                                            }
                                        },
                                        label = { Text("Price Target ($)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = LightText,
                                            unfocusedTextColor = LightText,
                                            focusedBorderColor = TealAccent,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    OutlinedTextField(
                                        value = manualTargetYieldInput,
                                        onValueChange = {
                                            manualTargetYieldInput = it
                                            val y = it.replace(',', '.').toDoubleOrNull()
                                            val fcf = cachedFcfPerShare ?: 0.0
                                            if (y != null && y > 0.0 && fcf > 0.0) {
                                                val calcPrice = fcf / (y / 100.0)
                                                manualTargetInput = String.format(Locale.US, "%.2f", calcPrice)
                                            }
                                        },
                                        label = { Text("Yield Target (%)", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = LightText,
                                            unfocusedTextColor = LightText,
                                            focusedBorderColor = AmberWarning,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.updateTickerTargets(ticker.symbol, null, null)
                                            manualTargetInput = ""
                                            manualTargetYieldInput = ""
                                            isEditingTargetPrice = false
                                        }
                                    ) {
                                        Text("Clear", color = RedLoss, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            val targetPriceVal = manualTargetInput.replace(',', '.').toDoubleOrNull()
                                            val targetYieldVal = manualTargetYieldInput.replace(',', '.').toDoubleOrNull()
                                            viewModel.updateTickerTargets(ticker.symbol, targetPriceVal, targetYieldVal)
                                            isEditingTargetPrice = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shares, Investment Yield, Premiums & Unrealized P&L
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Shares owned:", fontSize = 12.sp, color = GrayText)
                                Text("$assignedShares shares", fontSize = 12.sp, color = LightText, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Investment Yield:", fontSize = 12.sp, color = GrayText)
                                if (assignedShares > 0 && averageOwnersFcfYieldPct > 0.0) {
                                    val fcfAnnStr = if (estimatedAnnualFcfValue > 0.0) {
                                        if (isAmountsHidden) " (••••/yr)" else " (${formatCurrency(estimatedAnnualFcfValue)}/yr)"
                                    } else ""
                                    Text(
                                        text = "${String.format(Locale.US, "%.2f%%", averageOwnersFcfYieldPct)}$fcfAnnStr",
                                        fontSize = 12.sp,
                                        color = TealAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (assignedShares > 0 && estimatedAnnualFcfValue > 0.0) {
                                    Text(
                                        text = if (isAmountsHidden) "••••" else "${formatCurrency(estimatedAnnualFcfValue)}/yr",
                                        fontSize = 12.sp,
                                        color = TealAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (averageOwnersFcfYieldPct > 0.0) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f%%", averageOwnersFcfYieldPct),
                                        fontSize = 12.sp,
                                        color = TealAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text("—", fontSize = 12.sp, color = GrayText)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Option premiums collected:", fontSize = 12.sp, color = GrayText)
                                val premDisplay = premiumsCollected
                                Text(
                                    text = formatCurrency(premDisplay, isHidden = isAmountsHidden),
                                    fontSize = 12.sp,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Unrealized stock P&L:", fontSize = 12.sp, color = GrayText)
                                val pnlDisplay = unrealizedPnL
                                Text(
                                    text = if (assignedShares > 0) {
                                        if (isAmountsHidden) {
                                            "••••"
                                        } else {
                                            "${formatCurrency(pnlDisplay)} (${String.format(Locale.getDefault(), "%.1f%%", unrealizedPnLPct)})"
                                        }
                                    } else "—",
                                    fontSize = 12.sp,
                                    color = if (pnlDisplay >= 0) TealAccent else RedLoss,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Cash Management",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Log cash deposits or withdrawals to manage your buying power and total equity calculations.",
                            color = GrayText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isDepositSelected = true
                                    showAddCashDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("+ DEPOSIT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    isDepositSelected = false
                                    showAddCashDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfCard, contentColor = RedLoss),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RedLoss.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("- WITHDRAW", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Stock Transactions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LightText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Log buying or selling of stock shares to track your custom investment yield & portfolio holdings directly.",
                            color = GrayText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isBuyStockSelected = true
                                    showLogStockDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("+ BUY STOCK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    isBuyStockSelected = false
                                    showLogStockDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfCard, contentColor = RedLoss),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RedLoss.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("- SELL STOCK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
    }

    if (showAddCashDialog) {
        AlertDialog(
            onDismissRequest = { showAddCashDialog = false },
            title = { 
                Text(
                    text = if (isDepositSelected) "Register Cash Deposit" else "Register Cash Withdrawal", 
                    color = if (isDepositSelected) TealAccent else RedLoss, 
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = if (isDepositSelected) {
                            "Enter the amount of cash to deposit into your trading account. This increases your Buying Power & Total Equity."
                        } else {
                            "Enter the amount of cash to withdraw from your trading account. This decreases your Buying Power & Total Equity."
                        },
                        color = GrayText,
                        fontSize = 12.sp
                    )

                    // Segmented Control inside Dialog for seamless toggling
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDepositSelected) TealAccent else Color.Transparent)
                                .clickable { isDepositSelected = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Deposit",
                                color = if (isDepositSelected) Color.Black else GrayText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isDepositSelected) RedLoss else Color.Transparent)
                                .clickable { isDepositSelected = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Withdrawal",
                                color = if (!isDepositSelected) Color.White else GrayText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = depositAmountInput,
                        onValueChange = { depositAmountInput = it },
                        label = { Text("Amount ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = if (isDepositSelected) TealAccent else RedLoss
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = depositDateInput,
                        onValueChange = { depositDateInput = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedBorderColor = if (isDepositSelected) TealAccent else RedLoss
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDepositSelected) TealAccent else RedLoss, 
                        contentColor = if (isDepositSelected) Color.Black else Color.White
                    ),
                    onClick = {
                        val amount = depositAmountInput.toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) {
                            val tradeTime = try { sdf.parse(depositDateInput)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                            viewModel.logTrade(
                                ticker = "CASH",
                                tradeType = if (isDepositSelected) "Deposit" else "Withdrawal",
                                date = tradeTime,
                                contracts = 1,
                                strikePrice = 0.0,
                                premiumPerShare = amount,
                                expiryDate = null,
                                fees = 0.0
                            )
                            depositAmountInput = ""
                            showAddCashDialog = false
                        }
                    }
                ) {
                    Text("REGISTER", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCashDialog = false }) {
                    Text("CANCEL", color = GrayText)
                }
            },
            containerColor = SurfCard,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showLogStockDialog) {
        AlertDialog(
            onDismissRequest = { showLogStockDialog = false },
            title = {
                Text(
                    text = if (isBuyStockSelected) "Log Stock Purchase" else "Log Stock Sale",
                    color = if (isBuyStockSelected) TealAccent else RedLoss,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (isBuyStockSelected) {
                            "Log manual buying of shares to hold in your portfolio. Tracks cost basis and owners yield."
                        } else {
                            "Log manual selling of shares from your portfolio to reduce position sizes."
                        },
                        color = GrayText,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isBuyStockSelected) TealAccent else Color.Transparent)
                                .clickable { isBuyStockSelected = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Buy Stock",
                                color = if (isBuyStockSelected) Color.Black else GrayText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isBuyStockSelected) RedLoss else Color.Transparent)
                                .clickable { isBuyStockSelected = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sell Stock",
                                color = if (!isBuyStockSelected) Color.White else GrayText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    val onSelectTicker: (String, Double?, String?) -> Unit = { symbol, price, _ ->
                        val sym = symbol.uppercase().trim()
                        stockTickerInput = sym
                        isTickerDropdownExpanded = false
                        viewModel.searchSymbols("")
                        if (price != null && price > 0.0 && stockPriceInput.isEmpty()) {
                            stockPriceInput = String.format(Locale.US, "%.2f", price)
                        }
                        val snap = allSnapshots[sym]
                        if (snap != null) {
                            if (stockPriceInput.isEmpty() && snap.currentPrice > 0.0) {
                                stockPriceInput = String.format(Locale.US, "%.2f", snap.currentPrice)
                            }
                            val effPrice = stockPriceInput.toDoubleOrNull() ?: snap.currentPrice
                            if (effPrice > 0.0 && snap.fcfPerShare > 0.0) {
                                stockFcfYieldInput = String.format(Locale.US, "%.2f", (snap.fcfPerShare / effPrice) * 100.0)
                            } else if (snap.historicalFcfYield > 0.0) {
                                stockFcfYieldInput = String.format(Locale.US, "%.2f", snap.historicalFcfYield)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = stockTickerInput,
                            onValueChange = {
                                val upper = it.uppercase()
                                stockTickerInput = upper
                                if (upper.length >= 2) {
                                    viewModel.searchSymbols(upper)
                                } else {
                                    viewModel.searchSymbols("")
                                }
                                isTickerDropdownExpanded = upper.isNotBlank()
                            },
                            label = { Text("Stock Ticker Symbol") },
                            placeholder = { Text("e.g. AAPL") },
                            trailingIcon = {
                                if (stockTickerInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            stockTickerInput = ""
                                            viewModel.searchSymbols("")
                                            isTickerDropdownExpanded = false
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = GrayText)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = if (isBuyStockSelected) TealAccent else RedLoss
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("stock_ticker_input")
                        )

                        DropdownMenu(
                            expanded = isTickerDropdownExpanded && (matchingWatchlist.isNotEmpty() || remoteMatches.isNotEmpty()),
                            onDismissRequest = { isTickerDropdownExpanded = false },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier
                                .background(SurfCard)
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 240.dp)
                        ) {
                            if (matchingWatchlist.isNotEmpty()) {
                                Text(
                                    text = "WATCHLIST",
                                    color = TealAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                matchingWatchlist.forEach { item ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.symbol, fontWeight = FontWeight.Bold, color = TealAccent)
                                                    if (item.companyName.isNotEmpty() && item.companyName != item.symbol) {
                                                        Text(item.companyName, fontSize = 11.sp, color = GrayText, maxLines = 1)
                                                    }
                                                }
                                                if (item.livePrice > 0.0) {
                                                    Text(
                                                        String.format(Locale.US, "$%.2f", item.livePrice),
                                                        fontSize = 12.sp,
                                                        color = LightText,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            onSelectTicker(item.symbol, if (item.livePrice > 0.0) item.livePrice else null, item.companyName)
                                        }
                                    )
                                }
                            }

                            if (remoteMatches.isNotEmpty()) {
                                if (matchingWatchlist.isNotEmpty()) {
                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                                }
                                Text(
                                    text = "MARKET SEARCH",
                                    color = GrayText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                remoteMatches.forEach { res ->
                                    DropdownMenuItem(
                                        text = {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(res.symbol, fontWeight = FontWeight.Bold, color = LightText)
                                                if (!res.name.isNullOrEmpty()) {
                                                    Text(res.name, fontSize = 11.sp, color = GrayText, maxLines = 1)
                                                }
                                            }
                                        },
                                        onClick = {
                                            onSelectTicker(res.symbol, null, res.name)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = stockQtyInput,
                            onValueChange = { stockQtyInput = it },
                            label = { Text("Quantity (Shares)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = if (isBuyStockSelected) TealAccent else RedLoss
                            ),
                            modifier = Modifier.weight(1f).testTag("stock_qty_input")
                        )

                        OutlinedTextField(
                            value = stockPriceInput,
                            onValueChange = { stockPriceInput = it },
                            label = { Text("Price per Share ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = if (isBuyStockSelected) TealAccent else RedLoss
                            ),
                            modifier = Modifier.weight(1f).testTag("stock_price_input")
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = stockFeesInput,
                            onValueChange = { stockFeesInput = it },
                            label = { Text("Commissions ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = if (isBuyStockSelected) TealAccent else RedLoss
                            ),
                            modifier = Modifier.weight(1f).testTag("stock_fees_input")
                        )

                        OutlinedTextField(
                            value = stockDateInput,
                            onValueChange = { stockDateInput = it },
                            label = { Text("Date") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = if (isBuyStockSelected) TealAccent else RedLoss
                            ),
                            modifier = Modifier.weight(1.2f).testTag("stock_date_input")
                        )
                    }

                    if (isBuyStockSelected) {
                        OutlinedTextField(
                            value = stockFcfYieldInput,
                            onValueChange = { stockFcfYieldInput = it },
                            label = { Text("FCF Yield Override (%)") },
                            placeholder = { Text("e.g. 6.2") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedBorderColor = TealAccent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("stock_fcf_yield_input"),
                            supportingText = {
                                Text("Calculated automatically if ticker corresponds to a loaded snapshot, or enter manually.", color = GrayText)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuyStockSelected) TealAccent else RedLoss,
                        contentColor = if (isBuyStockSelected) Color.Black else Color.White
                    ),
                    modifier = Modifier.testTag("stock_transaction_confirm_button"),
                    onClick = {
                        val symbol = stockTickerInput.uppercase().trim()
                        val qty = stockQtyInput.toIntOrNull() ?: 0
                        val price = stockPriceInput.toDoubleOrNull() ?: 0.0
                        val fees = stockFeesInput.toDoubleOrNull() ?: 0.0
                        val fcfYieldVal = stockFcfYieldInput.toDoubleOrNull() ?: 0.0

                        if (symbol.isNotEmpty() && qty > 0 && price > 0.0) {
                            val tradeTime = try { sdf.parse(stockDateInput)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                            viewModel.logTrade(
                                ticker = symbol,
                                tradeType = if (isBuyStockSelected) "Buying shares" else "Selling shares",
                                date = tradeTime,
                                contracts = qty,
                                strikePrice = price,
                                premiumPerShare = 0.0,
                                expiryDate = null,
                                fees = fees,
                                fcfYield = fcfYieldVal
                            )
                            // Reset inputs
                            stockTickerInput = ""
                            stockQtyInput = ""
                            stockPriceInput = ""
                            stockFeesInput = "0.0"
                            stockFcfYieldInput = ""
                            isTickerDropdownExpanded = false
                            viewModel.searchSymbols("")
                            showLogStockDialog = false
                        }
                    }
                ) {
                    Text("REGISTER", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLogStockDialog = false
                        isTickerDropdownExpanded = false
                        viewModel.searchSymbols("")
                    },
                    modifier = Modifier.testTag("stock_transaction_cancel_button")
                ) {
                    Text("CANCEL", color = GrayText)
                }
            },
            containerColor = SurfCard,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
