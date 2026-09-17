package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.FirebaseAuthState
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.IntelligenceScreen
import com.example.ui.screens.PortfolioScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WheelTrackerScreen
import com.example.ui.screens.GoogleCircularLetter
import com.example.ui.theme.*
import com.example.viewmodel.FinanceViewModel

import com.example.viewmodel.FinanceViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: FinanceViewModel by viewModels {
        FinanceViewModelFactory(
            application,
            (application as EquityIQApplication).container.financeUseCases
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val tabs = remember { listOf("PORTFOLIO", "INTELLIGENCE", "CALCULATOR", "WHEEL") }
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { tabs.size }
                )
                val currentTab = tabs[pagerState.currentPage]
                var portfolioLandTrigger by remember { mutableIntStateOf(1) }
                var previousTab by remember { mutableStateOf("PORTFOLIO") }
                LaunchedEffect(currentTab) {
                    if (currentTab == "PORTFOLIO" && previousTab != "PORTFOLIO") {
                        portfolioLandTrigger++
                    }
                    previousTab = currentTab
                }
                val coroutineScope = rememberCoroutineScope()
                var showExitConfirmationDialog by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showAddTickerDialog by remember { mutableStateOf(false) }

                BackHandler {
                    if (showSettings) {
                        showSettings = false
                    } else if (currentTab != "PORTFOLIO") {
                        coroutineScope.launch {
                            pagerState.scrollToPage(tabs.indexOf("PORTFOLIO"))
                        }
                    } else {
                        showExitConfirmationDialog = true
                    }
                }

                val haptic = LocalHapticFeedback.current
                var previousSettledPage by remember { mutableIntStateOf(0) }
                LaunchedEffect(pagerState.settledPage) {
                    if (pagerState.settledPage != previousSettledPage) {
                        previousSettledPage = pagerState.settledPage
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
                val activeTicker by viewModel.selectedCalculatorTicker.collectAsStateWithLifecycle()

                LaunchedEffect(watchlist) {
                    if (watchlist.isNotEmpty()) {
                        if (activeTicker.isEmpty() || watchlist.none { it.symbol.equals(activeTicker, ignoreCase = true) }) {
                            viewModel.selectedCalculatorTicker.value = watchlist.first().symbol
                        }
                    } else {
                        if (activeTicker.isNotEmpty()) {
                            viewModel.selectedCalculatorTicker.value = ""
                        }
                    }
                }

                if (showSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { showSettings = false }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = NavyDark,
                        topBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NavyDark)
                                    .statusBarsPadding()
                                    .padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                // Logo + Actions Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // EquityIQ Brand on Left Side
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { showSettings = true }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, TealAccent, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_launcher_background),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                                contentDescription = "Equity IQ Logo",
                                                modifier = Modifier.fillMaxSize().padding(2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Equity IQ",
                                                fontFamily = RecoletaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = Color.White,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "FINANCE INTELLIGENCE",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 8.sp,
                                                color = GrayText,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }

                                    // Action Items on Right Side: ADD TICKER Button & Profile Icon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // + ADD TICKER Action Button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF360D4D).copy(alpha = 0.3f))
                                                .border(
                                                    width = 1.1.dp,
                                                    color = Color(0xFFA21CAF),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { showAddTickerDialog = true }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "ADD TICKER",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp,
                                                color = Color(0xFFF472B6),
                                                letterSpacing = 0.6.sp
                                            )
                                        }

                                        // Google Circular Avatar Profile (Extreme Top Right Corner)
                                        val firebaseManager = viewModel.firebaseManager
                                        val authStateForAvatar = firebaseManager?.authState?.collectAsStateWithLifecycle()?.value
                                        val (avatarName, photoUrl) = when (authStateForAvatar) {
                                            is FirebaseAuthState.SignedIn -> {
                                                Pair(
                                                    authStateForAvatar.displayName.ifEmpty { authStateForAvatar.email },
                                                    authStateForAvatar.photoUrl
                                                )
                                            }
                                            else -> Pair("", null)
                                        }

                                        GoogleCircularLetter(
                                            name = avatarName,
                                            photoUrl = photoUrl,
                                            size = 34.dp,
                                            fontSize = 14.sp,
                                            isSignedIn = authStateForAvatar is com.example.data.remote.FirebaseAuthState.SignedIn,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable { showSettings = true }
                                        )
                                    }
                                }
 
                                Spacer(modifier = Modifier.height(6.dp))
 
                                // Horizontal Navigation Pills Row (exact image top bar buttons reconstruction)
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        // PORTFOLIO Tab Button
                                        val isActive = currentTab == "PORTFOLIO"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActive) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isActive) Color(0xFF3B82F6).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    coroutineScope.launch {
                                                        pagerState.scrollToPage(tabs.indexOf("PORTFOLIO"))
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "PORTFOLIO",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = if (isActive) Color(0xFF93C5FD) else LightText,
                                                letterSpacing = 0.6.sp
                                            )
                                        }
                                    }
 
                                    item {
                                        // INTELLIGENCE Tab Button
                                        val isActive = currentTab == "INTELLIGENCE"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActive) Color(0xFF581C87).copy(alpha = 0.35f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isActive) Color(0xFF8B5CF6).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    coroutineScope.launch {
                                                        pagerState.scrollToPage(tabs.indexOf("INTELLIGENCE"))
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "INTELLIGENCE",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = if (isActive) Color(0xFFC084FC) else LightText,
                                                letterSpacing = 0.6.sp
                                            )
                                        }
                                    }
 
                                    item {
                                        // CALCULATOR Tab Button
                                        val isActive = currentTab == "CALCULATOR"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActive) Color(0xFF114D45).copy(alpha = 0.35f) else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isActive) TealAccent else Color.White.copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    coroutineScope.launch {
                                                        pagerState.scrollToPage(tabs.indexOf("CALCULATOR"))
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "CALCULATOR",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = if (isActive) TealAccent else LightText,
                                                letterSpacing = 0.6.sp
                                            )
                                        }
                                    }
 
                                    item {
                                        // WHEEL TRACKER Tab Button
                                        val isActive = currentTab == "WHEEL"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isActive) Color(0xFF78350F).copy(alpha = 0.35f) else Color.Transparent)
                                                .border(
                                                    width = 1.2.dp,
                                                    color = if (isActive) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    coroutineScope.launch {
                                                        pagerState.scrollToPage(tabs.indexOf("WHEEL"))
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "WHEEL TRACKER",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                color = if (isActive) Color(0xFFFDE68A) else LightText,
                                                letterSpacing = 0.6.sp
                                            )
                                        }
                                    }
                                }
 
                                Spacer(modifier = Modifier.height(2.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(NavyDark)
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                when (tabs[page]) {
                                    "PORTFOLIO" -> PortfolioScreen(
                                        viewModel = viewModel,
                                        onNavigateToWheel = { sym ->
                                            viewModel.selectedCalculatorTicker.value = sym
                                            coroutineScope.launch {
                                                pagerState.scrollToPage(tabs.indexOf("WHEEL"))
                                            }
                                        },
                                        onNavigateToCalculator = { sym ->
                                            viewModel.selectedCalculatorTicker.value = sym
                                            coroutineScope.launch {
                                                pagerState.scrollToPage(tabs.indexOf("CALCULATOR"))
                                            }
                                        },
                                        onNavigateToIntelligence = { sym ->
                                            viewModel.selectedCalculatorTicker.value = sym
                                            coroutineScope.launch {
                                                pagerState.scrollToPage(tabs.indexOf("INTELLIGENCE"))
                                            }
                                        },
                                        onAddTicker = { showAddTickerDialog = true },
                                        landTrigger = portfolioLandTrigger
                                    )
                                    "INTELLIGENCE" -> {
                                        if (activeTicker.isEmpty() || watchlist.isEmpty()) {
                                            EmptyTickersFallback(onAddTicker = { showAddTickerDialog = true })
                                        } else {
                                            IntelligenceScreen(
                                                viewModel = viewModel,
                                                activeTicker = activeTicker
                                            )
                                        }
                                    }
                                    "CALCULATOR" -> {
                                        if (activeTicker.isEmpty() || watchlist.isEmpty()) {
                                            EmptyTickersFallback(onAddTicker = { showAddTickerDialog = true })
                                        } else {
                                            CalculatorScreen(
                                                viewModel = viewModel,
                                                onNavigateToSettings = { showSettings = true }
                                            )
                                        }
                                    }
                                    "WHEEL" -> {
                                        if (activeTicker.isEmpty() || watchlist.isEmpty()) {
                                            EmptyTickersFallback(onAddTicker = { showAddTickerDialog = true })
                                        } else {
                                            WheelTrackerScreen(viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
 
                // Styled Add Ticker Dialog
                if (showAddTickerDialog) {
                    var tickerSymbol by remember { mutableStateOf("") }
                    var companyName by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showAddTickerDialog = false },
                        title = { Text("Log New Watchlist Ticker", color = TealAccent, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enter stock ticker symbol to track using EquityIQ's Wheel and DCF valuation tools.", color = GrayText, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = tickerSymbol,
                                    onValueChange = { tickerSymbol = it.uppercase() },
                                    label = { Text("Ticker Symbol (e.g. V)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = LightText,
                                        unfocusedTextColor = LightText,
                                        focusedBorderColor = TealAccent
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = companyName,
                                    onValueChange = { companyName = it },
                                    label = { Text("Company Name (e.g. Visa Inc)") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = LightText,
                                        unfocusedTextColor = LightText,
                                        focusedBorderColor = TealAccent
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent, contentColor = Color.Black),
                                onClick = {
                                    if (tickerSymbol.isNotEmpty()) {
                                        val finalName = companyName.ifEmpty { tickerSymbol }
                                        viewModel.addTickerToWatchlist(tickerSymbol, finalName)
                                        viewModel.selectedCalculatorTicker.value = tickerSymbol
                                        showAddTickerDialog = false
                                    }
                                }
                            ) {
                                Text("ADD TICKER", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddTickerDialog = false }) {
                                Text("CANCEL", color = GrayText)
                            }
                        },
                        containerColor = SurfCard,
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                // Styled Exit Confirmation Dialog
                if (showExitConfirmationDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirmationDialog = false },
                        title = { Text("Exit Equity IQ", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text("Are you sure you want to close the app?", color = LightText) },
                        confirmButton = {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                                onClick = {
                                    showExitConfirmationDialog = false
                                    this@MainActivity.finish()
                                }
                            ) {
                                Text("EXIT", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirmationDialog = false }) {
                                Text("CANCEL", color = GrayText)
                            }
                        },
                        containerColor = SurfCard,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTickersFallback(
    onAddTicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF360D4D).copy(alpha = 0.35f))
                    .border(1.5.dp, Color(0xFFA21CAF).copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Addchart,
                    contentDescription = "No Tickers Tracked",
                    tint = Color(0xFFF472B6),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No Tickers Tracked",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No stock tickers are currently tracked. Add your first ticker to analyze fundamental metrics, run DCF valuation models, and execute options strategies.",
                fontSize = 13.sp,
                color = GrayText,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                    .testTag("empty_fallback_add_ticker_button")
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
                    letterSpacing = 0.6.sp
                )
            }
        }
    }
}
