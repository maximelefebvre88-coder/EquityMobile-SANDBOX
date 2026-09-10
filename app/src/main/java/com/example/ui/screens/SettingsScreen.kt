package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FirebaseAuthState
import com.example.data.remote.FirebaseSyncState
import com.example.ui.theme.BorderGray
import com.example.ui.theme.GrayText
import com.example.ui.theme.SurfCard
import com.example.ui.theme.TealAccent
import com.example.viewmodel.ApiKeyTestState
import com.example.viewmodel.FinanceViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit
) {
    val apiKey by viewModel.apiKeyFlow.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKeyFlow.collectAsStateWithLifecycle()
    val finnhubTestState by viewModel.finnhubTestState.collectAsStateWithLifecycle()
    val geminiTestState by viewModel.geminiTestState.collectAsStateWithLifecycle()
    val currency by viewModel.currencyFlow.collectAsStateWithLifecycle()
    val riskFreeRate by viewModel.riskFreeRateFlow.collectAsStateWithLifecycle()
    val riskPremium by viewModel.riskPremiumFlow.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboardManager.current

    var apiInput by remember(apiKey) { mutableStateOf(apiKey) }
    var showFinnhubKey by remember { mutableStateOf(false) }

    var geminiApiInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey) }
    var showGeminiKey by remember { mutableStateOf(false) }

    var riskFreeInput by remember { mutableStateOf(riskFreeRate.toString()) }
    var riskPremiumInput by remember { mutableStateOf(riskPremium.toString()) }
    var searchQuery by remember { mutableStateOf("") }
    var tickerToRemove by remember { mutableStateOf<com.example.domain.model.WatchlistTicker?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Preferences & Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TealAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Finnhub Live API Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val isPresetActive = remember(apiKey) {
                            apiKey == com.example.BuildConfig.FINNHUB_API_KEY &&
                                    com.example.BuildConfig.FINNHUB_API_KEY.isNotEmpty() &&
                                    com.example.BuildConfig.FINNHUB_API_KEY != "YOUR_FINNHUB_API_KEY_HERE" &&
                                    !com.example.BuildConfig.FINNHUB_API_KEY.startsWith("MY_FINNHUB")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Finnhub API Integration",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isPresetActive) {
                                Surface(
                                    color = TealAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Preset Active", color = TealAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Provide your personal Finnhub API Key or paste directly from clipboard to fetch real-time quotes, financials, and company metrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiInput,
                            onValueChange = { newValue ->
                                apiInput = newValue
                                viewModel.updateApiKey(newValue)
                            },
                            label = { Text("Finnhub API Key") },
                            placeholder = { Text("Paste or enter API key...") },
                            singleLine = true,
                            visualTransformation = if (showFinnhubKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (apiInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            apiInput = ""
                                            viewModel.updateApiKey("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = GrayText, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = { showFinnhubKey = !showFinnhubKey }) {
                                        Icon(
                                            imageVector = if (showFinnhubKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showFinnhubKey) "Hide key" else "Show key",
                                            tint = TealAccent
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealAccent,
                                cursorColor = TealAccent
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                    if (clipText.isNotEmpty()) {
                                        apiInput = clipText
                                        viewModel.updateApiKey(clipText)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    if (apiInput.isNotEmpty()) {
                                        viewModel.testFinnhubApiKey(apiInput)
                                    }
                                },
                                enabled = apiInput.isNotEmpty() && finnhubTestState !is ApiKeyTestState.Testing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = TealAccent
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f))
                            ) {
                                if (finnhubTestState is ApiKeyTestState.Testing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TealAccent, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text("Test Connection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (com.example.BuildConfig.FINNHUB_API_KEY.isNotEmpty() &&
                                com.example.BuildConfig.FINNHUB_API_KEY != "YOUR_FINNHUB_API_KEY_HERE" &&
                                apiInput != com.example.BuildConfig.FINNHUB_API_KEY
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        apiInput = com.example.BuildConfig.FINNHUB_API_KEY
                                        viewModel.updateApiKey(com.example.BuildConfig.FINNHUB_API_KEY)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, BorderGray)
                                ) {
                                    Text("Reset Preset", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        // Test Status Banner for Finnhub
                        when (val state = finnhubTestState) {
                            is ApiKeyTestState.Success -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(state.message, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            is ApiKeyTestState.Error -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(state.message, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Section 1.5: Google Gemini AI API Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val isPresetActive = remember(geminiApiKey) {
                            geminiApiKey == com.example.BuildConfig.GEMINI_API_KEY &&
                                    com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() &&
                                    com.example.BuildConfig.GEMINI_API_KEY != "YOUR_GEMINI_API_KEY_HERE" &&
                                    com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" &&
                                    !com.example.BuildConfig.GEMINI_API_KEY.startsWith("MY_GEMINI")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Gemini AI API",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isPresetActive) {
                                Surface(
                                    color = TealAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealAccent, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Preset Active", color = TealAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Provide your Google Gemini API Key to enable AI-Powered intelligent Baseline & Valuation Growth Estimation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = geminiApiInput,
                            onValueChange = { newValue ->
                                geminiApiInput = newValue
                                viewModel.updateGeminiApiKey(newValue)
                            },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("Paste or enter Gemini API key...") },
                            singleLine = true,
                            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (geminiApiInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            geminiApiInput = ""
                                            viewModel.updateGeminiApiKey("")
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = GrayText, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                        Icon(
                                            imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showGeminiKey) "Hide key" else "Show key",
                                            tint = TealAccent
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealAccent,
                                cursorColor = TealAccent
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                    if (clipText.isNotEmpty()) {
                                        geminiApiInput = clipText
                                        viewModel.updateGeminiApiKey(clipText)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    if (geminiApiInput.isNotEmpty()) {
                                        viewModel.testGeminiApiKey(geminiApiInput)
                                    }
                                },
                                enabled = geminiApiInput.isNotEmpty() && geminiTestState !is ApiKeyTestState.Testing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1E293B),
                                    contentColor = TealAccent
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, TealAccent.copy(alpha = 0.5f))
                            ) {
                                if (geminiTestState is ApiKeyTestState.Testing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TealAccent, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Testing...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text("Test Connection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() &&
                                com.example.BuildConfig.GEMINI_API_KEY != "YOUR_GEMINI_API_KEY_HERE" &&
                                com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" &&
                                !com.example.BuildConfig.GEMINI_API_KEY.startsWith("MY_GEMINI") &&
                                geminiApiInput != com.example.BuildConfig.GEMINI_API_KEY
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        geminiApiInput = com.example.BuildConfig.GEMINI_API_KEY
                                        viewModel.updateGeminiApiKey(com.example.BuildConfig.GEMINI_API_KEY)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, BorderGray)
                                ) {
                                    Text("Reset Preset", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }

                        // Test Status Banner for Gemini
                        when (val state = geminiTestState) {
                            is ApiKeyTestState.Success -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(state.message, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            is ApiKeyTestState.Error -> {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(state.message, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }


            // Section 2: Preferences
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Default Currency & Macro Values",
                            style = MaterialTheme.typography.titleMedium,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // CAD / USD Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Reporting Currency", fontWeight = FontWeight.SemiBold)
                                Text("Toggle default display values", style = MaterialTheme.typography.bodySmall, color = GrayText)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BorderGray)
                            ) {
                                Button(
                                    onClick = { viewModel.updateCurrency("CAD") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currency == "CAD") TealAccent else Color.Transparent,
                                        contentColor = if (currency == "CAD") Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("CAD", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.updateCurrency("USD") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currency == "USD") TealAccent else Color.Transparent,
                                        contentColor = if (currency == "USD") Color.Black else Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("USD", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Risk Free Rate Input
                        OutlinedTextField(
                            value = riskFreeInput,
                            onValueChange = {
                                riskFreeInput = it
                                it.toFloatOrNull()?.let { f -> viewModel.updateRiskFreeRate(f) }
                            },
                            label = { Text("Default Risk-Free Rate %") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealAccent,
                                cursorColor = TealAccent
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Risk Premium Input
                        OutlinedTextField(
                            value = riskPremiumInput,
                            onValueChange = {
                                riskPremiumInput = it
                                it.toFloatOrNull()?.let { f -> viewModel.updateRiskPremium(f) }
                            },
                            label = { Text("Default Risk Premium %") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealAccent,
                                cursorColor = TealAccent
                            )
                        )
                    }
                }
            }

            // Section 3: Add / Remove Tickers from Watchlist (with autocomplete search using API)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ticker Watchlist Management",
                            style = MaterialTheme.typography.titleMedium,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchSymbols(it)
                            },
                            label = { Text("Search tickers (e.g., AAPL)") },
                            suffix = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TealAccent) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealAccent,
                                cursorColor = TealAccent
                            )
                        )

                        // Search Results Box
                        if (searchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    items(searchResults) { result ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.addTickerToWatchlist(result.symbol, result.name ?: result.symbol)
                                                    searchQuery = ""
                                                    viewModel.searchSymbols("")
                                                }
                                                .padding(vertical = 10.dp, horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(result.symbol, fontWeight = FontWeight.Bold, color = TealAccent)
                                                result.name?.let { Text(it, fontSize = 12.sp, color = GrayText) }
                                            }
                                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = TealAccent)
                                        }
                                        HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }

                        if (isSearching) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = TealAccent, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // Section 4: Cloud Storage & Sync Settings
            item {
                val context = LocalContext.current
                val firebaseManager = viewModel.firebaseManager
                val isInitialized by (firebaseManager?.isInitialized ?: MutableStateFlow(false)).collectAsStateWithLifecycle()
                val authState by (firebaseManager?.authState ?: MutableStateFlow(FirebaseAuthState.SignedOut)).collectAsStateWithLifecycle()
                val syncState by (firebaseManager?.syncState ?: MutableStateFlow(FirebaseSyncState.Idle)).collectAsStateWithLifecycle()

                val fbClientIdInput = remember { firebaseManager?.getFirebaseAuthClientId() ?: "" }
                val isConfigured = remember(isInitialized) { isInitialized }

                var syncMessage by remember { mutableStateOf("") }
                var isSyncing by remember { mutableStateOf(false) }

                val coroutineScope = rememberCoroutineScope()

                // Google Sign In Launcher
                val gso = remember(fbClientIdInput) {
                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(fbClientIdInput.ifEmpty { "123456789.apps.googleusercontent.com" }) // fallback client ID to avoid crash if empty
                        .requestEmail()
                        .build()
                }
                val googleSignInClient = remember(gso) {
                    GoogleSignIn.getClient(context, gso)
                }

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        val idToken = account?.idToken
                        if (idToken != null && firebaseManager != null) {
                            coroutineScope.launch {
                                isSyncing = true
                                syncMessage = "Authenticating with Google..."
                                val r = firebaseManager.signInWithGoogleIdToken(idToken)
                                isSyncing = false
                                if (r.isSuccess) {
                                    syncMessage = "Authenticated & Cloud Synced!"
                                } else {
                                    syncMessage = "Authentication failed: ${r.exceptionOrNull()?.message}"
                                }
                            }
                        } else {
                            syncMessage = "Google account token was null."
                        }
                    } catch (e: ApiException) {
                        syncMessage = "Google Sign-In failed: Code ${e.statusCode} (${e.localizedMessage})"
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Cloud Storage & Cross-Device Sync",
                            style = MaterialTheme.typography.titleMedium,
                            color = TealAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sign in with your Google account to automatically back up and synchronize your watchlist, trades, calculator baselines, and custom settings.",
                            fontSize = 12.sp,
                            color = GrayText
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Status Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B))
                            )
                            Text(
                                text = if (isConfigured) "Cloud Saving Active" else "Cloud Sync Pending Setup",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // User Auth State Panel
                        Spacer(modifier = Modifier.height(12.dp))
                        when (val currentAuth = authState) {
                            is FirebaseAuthState.SignedIn -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        GoogleCircularLetter(
                                            name = currentAuth.displayName.ifEmpty { currentAuth.email },
                                            size = 36.dp,
                                            fontSize = 16.sp
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(currentAuth.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(currentAuth.email, fontSize = 12.sp, color = GrayText)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isSyncing = true
                                                syncMessage = "Syncing with cloud..."
                                                val res = firebaseManager?.syncDataAcrossDevices()
                                                isSyncing = false
                                                syncMessage = if (res?.isSuccess == true) {
                                                    "Sync Completed Successfully!"
                                                } else {
                                                    "Sync Failed: ${res?.exceptionOrNull()?.message}"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                        modifier = Modifier.weight(1f),
                                        enabled = !isSyncing
                                    ) {
                                        Text("Sync Now", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            firebaseManager?.signOut()
                                            googleSignInClient.signOut()
                                            syncMessage = "Signed out successfully."
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Sign Out", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            FirebaseAuthState.SignedOut -> {
                                if (isConfigured) {
                                    GoogleSignInButton(
                                        onClick = {
                                            if (fbClientIdInput.isEmpty() || fbClientIdInput == "YOUR_FIREBASE_CLIENT_ID_HERE") {
                                                syncMessage = "Please add FIREBASE_CLIENT_ID to the Secrets panel in AI Studio."
                                            } else {
                                                googleSignInClient.signOut().addOnCompleteListener {
                                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Column {
                                        Text(
                                            text = "Automatic synchronization will be activated once Firebase credentials are set in the AI Studio Secrets panel.",
                                            fontSize = 12.sp,
                                            color = GrayText
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "Setup Instructions:",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealAccent
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Go to the 'Secrets' panel in AI Studio and add:\n• FIREBASE_CLIENT_ID\n• FIREBASE_API_KEY\n• FIREBASE_PROJECT_ID\n• FIREBASE_APP_ID",
                                                    fontSize = 11.sp,
                                                    color = GrayText,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Sync State messages
                        val currentSyncState = syncState
                        val displaySyncMsg = when {
                            isSyncing -> syncMessage
                            currentSyncState is FirebaseSyncState.Syncing -> "Cloud Sync in progress..."
                            currentSyncState is FirebaseSyncState.Success -> "All data synced and backed up successfully!"
                            currentSyncState is FirebaseSyncState.Error -> "Sync Alert: ${currentSyncState.message}"
                            syncMessage.isNotEmpty() -> syncMessage
                            else -> null
                        }

                        if (displaySyncMsg != null) {
                            val isPermissionError = displaySyncMsg.contains("permission-denied", ignoreCase = true) ||
                                                    displaySyncMsg.contains("permission denied", ignoreCase = true) ||
                                                    displaySyncMsg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                                                    displaySyncMsg.contains("insufficient permissions", ignoreCase = true)

                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPermissionError || displaySyncMsg.contains("Error") || displaySyncMsg.contains("failed") || displaySyncMsg.contains("Secrets")) {
                                        Color(0xFFEF4444).copy(alpha = 0.1f)
                                    } else {
                                        TealAccent.copy(alpha = 0.1f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = displaySyncMsg,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPermissionError || displaySyncMsg.contains("Error") || displaySyncMsg.contains("failed") || displaySyncMsg.contains("Secrets")) {
                                        Color(0xFFF87171)
                                    } else {
                                        TealAccent
                                    },
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            if (isPermissionError) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF1E1E1E),
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Firestore Rules Required",
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Firestore Security Rules Required",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFFF59E0B)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "A 'permission-denied' response means your Cloud Firestore Database rules block the application from saving/restoring backup data. By default, newly created Firestore databases deny all reads and writes. To resolve this:",
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "1. Open your Firebase Console and select your project.\n" +
                                                   "2. Navigate to Build > Firestore Database.\n" +
                                                   "3. Click the Rules tab.\n" +
                                                   "4. Copy and paste the secure user-isolated rules below:",
                                            fontSize = 12.sp,
                                            color = GrayText,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        // Code snippet block
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "rules_version = '2';\n" +
                                                           "service cloud.firestore {\n" +
                                                           "  match /databases/{database}/documents {\n" +
                                                           "    match /users/{userId}/{document=**} {\n" +
                                                           "      allow read, write: if request.auth != null && request.auth.uid == userId;\n" +
                                                           "    }\n" +
                                                           "  }\n" +
                                                           "}",
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = TealAccent,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "5. Click Publish to apply the rules. Once applied, click \"Sync Now\" above to backup and restore successfully!",
                                            fontSize = 12.sp,
                                            color = Color.White,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Watchlist item list
            item {
                Text(
                    text = "Current Tracked Watchlist (${watchlist.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayText,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(watchlist) { ticker ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfCard)
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(ticker.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TealAccent)
                        Text(ticker.companyName, fontSize = 13.sp, color = GrayText)
                    }
                    IconButton(onClick = { tickerToRemove = ticker }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        val targetTicker = tickerToRemove
        if (targetTicker != null) {
            AlertDialog(
                onDismissRequest = { tickerToRemove = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Remove ${targetTicker.symbol}?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove ${targetTicker.symbol} (${targetTicker.companyName}) from your tracked watchlist?",
                        color = GrayText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeTickerFromWatchlist(targetTicker.symbol)
                            tickerToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Remove", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { tickerToRemove = null }) {
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
fun GoogleCircularLetter(
    name: String,
    modifier: Modifier = Modifier,
    isSignedIn: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp
) {
    if (!isSignedIn || name.isEmpty() || name == "G") {
        // Official Google Chrome / Google Account Signed-Out Avatar:
        // A clean, simple silhouette profile icon of a person inside a circle.
        Box(
            modifier = modifier
                .size(size)
                .background(Color(0xFFE8EAED), shape = CircleShape), // Light grey background
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Signed Out",
                tint = Color(0xFF5F6368), // Standard Google grey
                modifier = Modifier.size(size * 0.6f)
            )
        }
    } else {
        // Official Google Chrome / Google Account Signed-In Avatar (with no photo):
        // A solid colored circle (dynamic Google brand color based on name)
        // with the user's capitalized first letter in white centered.
        val firstLetter = remember(name) { name.take(1).uppercase() }
        
        // Dynamically choose one of Google's official brand colors based on the hashCode of the name
        val googleColors = remember {
            listOf(
                Color(0xFF1A73E8), // Google Blue
                Color(0xFFD93025), // Google Red
                Color(0xFFF9AB00), // Google Yellow
                Color(0xFF1E8E3E)  // Google Green
            )
        }
        val backgroundColor = remember(name) {
            val index = kotlin.math.abs(name.hashCode()) % googleColors.size
            googleColors[index]
        }

        Box(
            modifier = modifier
                .size(size)
                .background(backgroundColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = firstLetter,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = fontSize
            )
        }
    }
}

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Sign in with Google",
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp), // Beautiful modern pill shape
        color = Color.White,
        border = BorderStroke(width = 1.dp, color = Color(0xFFDADCE0)),
        shadowElevation = 1.dp,
        modifier = modifier
            .height(44.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.ic_google_logo),
                contentDescription = "Google Logo",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.25.sp
            )
        }
    }
}
