package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CalculatorSnapshot
import com.example.ui.theme.*
import com.example.viewmodel.FinanceViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-Fidelity interactive Intelligence screen.
 * Recreates the gauge HUD visualization from the mock-up image with circular gauges touching/overlapping,
 * live 3-Tier Balance Sheet Health calculation, and an interactive qualitative playbook.
 */
@Composable
fun IntelligenceScreen(
    viewModel: FinanceViewModel,
    activeTicker: String
) {
    val snapshot by viewModel.activeCalculatorSnapshot.collectAsStateWithLifecycle()
    val isSyncing by viewModel.tickerSyncing.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()

    val tickerItem = remember(watchlist, activeTicker) {
        watchlist.find { it.symbol.equals(activeTicker, ignoreCase = true) }
    }
    val dbLogoUrl = tickerItem?.logoUrl
    val companyName = tickerItem?.companyName ?: ""
    val fallbackSymbol = activeTicker.uppercase().trim()
    val fallbackLogoUrl = "https://financialmodelingprep.com/image-stock/$fallbackSymbol.png"
    val finalLogoUrl = if (!dbLogoUrl.isNullOrEmpty()) dbLogoUrl else fallbackLogoUrl

    var showBalanceSheetMethodology by remember { mutableStateOf(false) }
    var showProfitQualityMethodology by remember { mutableStateOf(false) }
    var showTotalScoreMethodology by remember { mutableStateOf(false) }

    // ----------------------------------------------------
    // Stateful Qualitative Scoring Playbook (Moat indicators)
    // ----------------------------------------------------
    val qualCriteria = remember(activeTicker) {
        mutableStateListOf(
            // Management & Capital Allocation
            ScoringCriteria(
                title = "Smart Capital Allocation",
                description = "Management reinvests cash flow effectively (buybacks, M&A, R&D, dividends) to compound the moat's value over time.",
                points = 4,
                category = MoatSubcategory.MANAGEMENT
            ),
            ScoringCriteria(
                title = "Visionary Leadership",
                description = "Strong, forward-thinking management team. Valuable but the least durable factor since leadership can change.",
                points = 3,
                category = MoatSubcategory.MANAGEMENT
            ),

            // Product & Pricing Dynamics
            ScoringCriteria(
                title = "Pricing Power",
                description = "Can raise prices without losing volume or customers. Direct, real-time proof that a moat exists and is being monetized.",
                points = 10,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),
            ScoringCriteria(
                title = "Toll Bridge Economics",
                description = "Captures a fee on a transaction or value flow regardless of who wins on either side of it (e.g., payment rails, exchanges).",
                points = 8,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),
            ScoringCriteria(
                title = "Recurring Revenue",
                description = "Subscription, contractual, or repeat-purchase revenue streams that create predictable, visible cash flows.",
                points = 6,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),
            ScoringCriteria(
                title = "Mission Critical, Small Ticket",
                description = "Product/service is essential to the customer's operations but represents a small % of their budget, making price increases easy to absorb.",
                points = 5,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),
            ScoringCriteria(
                title = "Unrealized Pricing Power",
                description = "Evidence the company could raise prices further but hasn't yet. Represents future optionality and margin upside.",
                points = 4,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),
            ScoringCriteria(
                title = "Economies of Scale",
                description = "Unit costs decline as the company grows, reinforcing pricing power and raising barriers to entry for smaller rivals.",
                points = 2,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),
            ScoringCriteria(
                title = "High ROIC / Capital-Light Model",
                description = "Sustained high return on invested capital with low reinvestment needs, evidence the moat shows up in the financials.",
                points = 2,
                category = MoatSubcategory.PRODUCT_DYNAMICS
            ),

            // Competitive Moat & Market Position
            ScoringCriteria(
                title = "Monopoly Characteristic",
                description = "Controls supply, pricing, or market access with little to no viable competitive response. The strongest possible moat signal.",
                points = 12,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "Network Effect",
                description = "Product/service becomes more valuable as more users join. Self-reinforcing and very difficult for competitors to replicate.",
                points = 10,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "High Switching Cost",
                description = "Customers face significant financial, operational, or technical friction to leave. Structurally locks in revenue, not just through loyalty.",
                points = 9,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "Entrenched Assets",
                description = "Owns physical, regulatory, or infrastructure assets that are costly, slow, or impossible for a competitor to duplicate.",
                points = 7,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "High Barrier to Entry",
                description = "Capital intensity, regulation, or scale requirements that deter new entrants from competing effectively.",
                points = 7,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "Intangible Assets",
                description = "Brand strength, patents, trademarks, licenses, or data assets that competitors cannot easily copy or acquire.",
                points = 6,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "Clear Industry Secular Tailwind",
                description = "Operates in a structurally growing industry. Supports growth but is an external factor, not the moat itself.",
                points = 4,
                category = MoatSubcategory.COMPETITIVE_MOAT
            ),
            ScoringCriteria(
                title = "Regulatory/Licensing Protection",
                description = "Government-granted exclusivity, permits, or compliance burdens that legally filter out potential competitors.",
                points = 1,
                category = MoatSubcategory.COMPETITIVE_MOAT
            )
        )
    }

    // Restore qualitative checked states from database
    LaunchedEffect(activeTicker, snapshot?.checkedQualitativeTitles) {
        val titlesStr = snapshot?.checkedQualitativeTitles
        if (titlesStr != null) {
            val delimiter = if (titlesStr.contains(";")) ";" else ","
            val checkedTitles = titlesStr.split(delimiter).map { it.trim() }
            qualCriteria.forEach { criteria ->
                criteria.checked = checkedTitles.contains(criteria.title)
            }
        }
    }

    // ----------------------------------------------------
    // Standalone Tiered Balance Sheet Score Calculation
    // ----------------------------------------------------
    val snap = snapshot
    val cashOnHand = snap?.cashOnHand ?: 0.0
    val longTermDebt = snap?.ltDebt ?: 0.0
    val freeCashFlow = snap?.ttmFcf ?: 0.0

    val balanceSheetScore = remember(cashOnHand, longTermDebt, freeCashFlow) {
        calculateBalanceSheetHealthScore(
            cashOnHand = cashOnHand,
            longTermDebt = longTermDebt,
            freeCashFlow = freeCashFlow
        )
    }

    // Profit Quality Score inputs from CalculatorSnapshot or fallback
    val dbRoic = snap?.roicPercent ?: 0.0
    val dbFcfMargin = snap?.fcfMarginPercent ?: 0.0

    val dbFcfConversion = remember(snap?.ttmFcf, snap?.ttmNetIncome) {
        val fcf = snap?.ttmFcf ?: 0.0
        val netInc = snap?.ttmNetIncome ?: 0.0
        if (netInc != 0.0) {
            (fcf / netInc) * 100.0
        } else {
            0.0
        }
    }

    var editRoicInput by remember(dbRoic) {
        mutableStateOf(if (dbRoic > 0.0) String.format(Locale.US, "%.1f", dbRoic) else "15.0")
    }
    var editFcfMarginInput by remember(dbFcfMargin) {
        mutableStateOf(if (dbFcfMargin > 0.0) String.format(Locale.US, "%.1f", dbFcfMargin) else "12.0")
    }
    var editFcfConversionInput by remember(dbFcfConversion) {
        mutableStateOf(if (dbFcfConversion != 0.0) String.format(Locale.US, "%.1f", dbFcfConversion) else "85.0")
    }

    val roicVal = editRoicInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val fcfMarginVal = editFcfMarginInput.replace(',', '.').toDoubleOrNull() ?: 0.0
    val fcfConversionVal = editFcfConversionInput.replace(',', '.').toDoubleOrNull() ?: 0.0

    val profitQualityScore = remember(roicVal, fcfMarginVal, fcfConversionVal) {
        calculateProfitQualityRankScore(
            roic = roicVal,
            fcfMargin = fcfMarginVal,
            fcfConversion = fcfConversionVal
        )
    }

    val totalScore = ((balanceSheetScore + profitQualityScore) / 2)

    val profitabilityScore = qualCriteria.filter { it.checked }.sumOf { it.points }

    val totalQualPoints = qualCriteria.filter { it.checked }.sumOf { it.points }
    val checkedCount = qualCriteria.count { it.checked }

    val moatRating = when {
        totalQualPoints >= 70 -> "Wide Moat (Pristine Monopolistic Defense)"
        totalQualPoints >= 40 -> "Narrow Moat (Strong Competitive Advantage)"
        totalQualPoints >= 15 -> "Minimal Moat (Moderate Defensibility)"
        else -> "No Moat / Commodity Business"
    }

    val ratingColor = when {
        totalQualPoints >= 70 -> OptionCcIndigo
        totalQualPoints >= 40 -> EmeraldGreen
        totalQualPoints >= 15 -> AmberWarning
        else -> GrayText
    }

    // Local inputs for editing baseline inline
    var editCashInput by remember(cashOnHand) { mutableStateOf(String.format(Locale.US, "%.1f", cashOnHand / 1_000_000.0)) }
    var editDebtInput by remember(longTermDebt) { mutableStateOf(String.format(Locale.US, "%.1f", longTermDebt / 1_000_000.0)) }
    var editFcfInput by remember(freeCashFlow) { mutableStateOf(String.format(Locale.US, "%.1f", freeCashFlow / 1_000_000.0)) }

    // Keep inputs synchronized with active calculator snapshot updates from other tabs
    LaunchedEffect(snap?.cashOnHand, snap?.ltDebt, snap?.ttmFcf, snap?.roicPercent, snap?.fcfMarginPercent, snap?.ttmNetIncome) {
        val curCash = snap?.cashOnHand ?: 0.0
        val curDebt = snap?.ltDebt ?: 0.0
        val curFcf = snap?.ttmFcf ?: 0.0
        val curRoic = snap?.roicPercent ?: 0.0
        val curMargin = snap?.fcfMarginPercent ?: 0.0
        val curNetInc = snap?.ttmNetIncome ?: 0.0
        val curConv = if (curNetInc != 0.0) (curFcf / curNetInc) * 100.0 else 0.0

        editCashInput = String.format(Locale.US, "%.1f", curCash / 1_000_000.0)
        editDebtInput = String.format(Locale.US, "%.1f", curDebt / 1_000_000.0)
        editFcfInput = String.format(Locale.US, "%.1f", curFcf / 1_000_000.0)
        if (curRoic > 0.0) editRoicInput = String.format(Locale.US, "%.1f", curRoic)
        if (curMargin > 0.0) editFcfMarginInput = String.format(Locale.US, "%.1f", curMargin)
        if (curConv != 0.0) editFcfConversionInput = String.format(Locale.US, "%.1f", curConv)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ----------------------------------------------------
        // Centered Ticker with Logo (No header, no tab title)
        // ----------------------------------------------------
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Main Centered Content
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        var imageLoadFailed by remember(finalLogoUrl) { mutableStateOf(false) }
                        if (!imageLoadFailed) {
                            AsyncImage(
                                model = finalLogoUrl,
                                contentDescription = "$activeTicker Logo",
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                onError = { imageLoadFailed = true }
                            )
                        } else {
                            Text(
                                text = activeTicker,
                                fontWeight = FontWeight.ExtraBold,
                                color = LightText,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = activeTicker.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 28.sp,
                        color = LightText,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = Shadow(
                                color = OptionCcIndigo.copy(alpha = 0.75f),
                                offset = Offset(0f, 0f),
                                blurRadius = 16f
                            )
                        )
                    )

                    if (companyName.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = companyName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = GrayText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Reset Button
                IconButton(
                    onClick = {
                        qualCriteria.forEach { it.checked = false }
                        // Reset numbers back to baseline (database values)
                        editCashInput = String.format(Locale.US, "%.1f", cashOnHand / 1_000_000.0)
                        editDebtInput = String.format(Locale.US, "%.1f", longTermDebt / 1_000_000.0)
                        editFcfInput = String.format(Locale.US, "%.1f", freeCashFlow / 1_000_000.0)
                        editRoicInput = if (dbRoic > 0.0) String.format(Locale.US, "%.1f", dbRoic) else "15.0"
                        editFcfMarginInput = if (dbFcfMargin > 0.0) String.format(Locale.US, "%.1f", dbFcfMargin) else "12.0"
                        editFcfConversionInput = if (dbFcfConversion != 0.0) String.format(Locale.US, "%.1f", dbFcfConversion) else "85.0"
                        
                        snapshot?.let { snapObj ->
                            viewModel.updateCalculatorSnapshot(snapObj.copy(checkedQualitativeTitles = ""))
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Checklist",
                        tint = LightText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ----------------------------------------------------
        // Overlapping Glowing Gauges Component (Responsive Width)
        // ----------------------------------------------------
        item {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerWidth = maxWidth
                
                // Scale diameters depending on available screen width to make them touch/overlap perfectly
                val centerDiameter = (containerWidth * 0.44f).coerceIn(135.dp, 185.dp)
                val outerDiameter = (containerWidth * 0.35f).coerceIn(105.dp, 140.dp)
                
                // Compute precise overlapping offset to tuck sides under the center
                val horizontalOffset = (centerDiameter / 2.2f) + (outerDiameter / 7f)
                
                // Vertical offset difference: 50% of outer diameter to move Total score gage upward
                val verticalDiff = outerDiameter * 0.5f
                val leftRightYOffset = verticalDiff * 0.35f
                val centerYOffset = -verticalDiff * 0.65f

                // 1. Balance Sheet Score Gauge (Left - Green, tucked under)
                Box(
                    modifier = Modifier
                        .size(outerDiameter)
                        .align(Alignment.Center)
                        .offset(x = -horizontalOffset, y = leftRightYOffset)
                ) {
                    GlowingGauge(
                        score = balanceSheetScore,
                        label = "BALANCE SHEET",
                        color = EmeraldGreen,
                        size = outerDiameter,
                        onClick = { showBalanceSheetMethodology = true }
                    )
                }

                // 2. Profit Quality Score Gauge (Right - Orange/Amber, tucked under)
                Box(
                    modifier = Modifier
                        .size(outerDiameter)
                        .align(Alignment.Center)
                        .offset(x = horizontalOffset, y = leftRightYOffset)
                ) {
                    GlowingGauge(
                        score = profitQualityScore,
                        label = "PROFIT QUALITY",
                        color = AmberWarning,
                        size = outerDiameter,
                        onClick = { showProfitQualityMethodology = true }
                    )
                }

                // 3. Total/Top Score Gauge (Center - Purple, Layered ON TOP of Left & Right)
                Box(
                    modifier = Modifier
                        .size(centerDiameter)
                        .align(Alignment.Center)
                        .offset(y = centerYOffset)
                        .drawBehind {
                            // Faint elegant radial glow behind center gauge
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        OptionCcIndigo.copy(alpha = 0.18f),
                                        OptionCcIndigo.copy(alpha = 0.04f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.width * 0.95f
                            )
                        }
                ) {
                    GlowingGauge(
                        score = totalScore,
                        label = "TOTAL SCORE",
                        color = OptionCcIndigo,
                        size = centerDiameter,
                        isCenterLarge = true,
                        onClick = { showTotalScoreMethodology = true }
                    )
                }
            }
        }

        // ----------------------------------------------------
        // QUALITATIVE MOAT STATUS HUD (Moved directly below 3 circles)
        // ----------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfCard.copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, ratingColor.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "QUALITATIVE MOAT STATUS",
                                color = GrayText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = moatRating,
                                color = ratingColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "MOAT SCORE",
                                color = GrayText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "$totalQualPoints / 100 PTS",
                                color = LightText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Custom polished linear progress indicator for points
                    LinearProgressIndicator(
                        progress = { totalQualPoints.toFloat() / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ratingColor,
                        trackColor = BorderGray
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$checkedCount of ${qualCriteria.size} checks active",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Target: 70+ PTS for Wide Moat",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Handle Sync/Network errors inline
        // ----------------------------------------------------
        syncError?.let { err ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1515)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "Error", tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync Alert: $err. You can manually adjust all parameters below.", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Quantitative Balance Sheet Inputs & Scenario Controller
        // ----------------------------------------------------
        item {
            Column {
                Text(
                    text = "QUANTITATIVE ENGINE (BALANCE SHEET HEALTH)",
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Adjust values (in Millions) to test Balance Sheet stability. Click 'APPLY DATA' to persist as active calculator metrics.",
                            fontSize = 11.sp,
                            color = GrayText,
                            lineHeight = 15.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = editCashInput,
                                onValueChange = { editCashInput = it },
                                label = { Text("Cash (\$M)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = EmeraldGreen,
                                    cursorColor = EmeraldGreen
                                )
                            )

                            OutlinedTextField(
                                value = editDebtInput,
                                onValueChange = { editDebtInput = it },
                                label = { Text("LT Debt (\$M)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = EmeraldGreen,
                                    cursorColor = EmeraldGreen
                                )
                            )

                            OutlinedTextField(
                                value = editFcfInput,
                                onValueChange = { editFcfInput = it },
                                label = { Text("FCF (\$M)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = EmeraldGreen,
                                    cursorColor = EmeraldGreen
                                )
                            )
                        }

                        // Save Balance Sheet Metrics Button
                        Button(
                            onClick = {
                                val snapObj = snap
                                if (snapObj != null) {
                                    val cVal = editCashInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val dVal = editDebtInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val fVal = editFcfInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    
                                    val shares = if (snapObj.sharesOutstanding > 0.0) snapObj.sharesOutstanding else 1.0
                                    val netCashPerShareVal = ((cVal - dVal) * 1_000_000.0) / shares
                                    val fcfPerShareVal = (fVal * 1_000_000.0) / shares
                                    val fcfMarginVal = if (snapObj.ttmRevenue > 0.0) ((fVal * 1_000_000.0) / snapObj.ttmRevenue) * 100.0 else snapObj.fcfMarginPercent
                                    
                                    viewModel.updateCalculatorSnapshot(
                                        snapObj.copy(
                                            cashOnHand = cVal * 1_000_000.0,
                                            ltDebt = dVal * 1_000_000.0,
                                            ttmFcf = fVal * 1_000_000.0,
                                            netCashPerShare = netCashPerShareVal,
                                            fcfPerShare = fcfPerShareVal,
                                            fcfMarginPercent = fcfMarginVal
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = NavyDark),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("APPLY BALANCE SHEET DATA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Quantitative Profit Quality Inputs & Scenario Controller
        // ----------------------------------------------------
        item {
            Column {
                Text(
                    text = "QUANTITATIVE ENGINE (PROFIT QUALITY RANK)",
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BorderGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Adjust values to test Profit Quality Rank. Click 'APPLY PROFIT DATA' to persist as active calculator metrics.",
                            fontSize = 11.sp,
                            color = GrayText,
                            lineHeight = 15.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = editRoicInput,
                                onValueChange = { editRoicInput = it },
                                label = { Text("ROIC (%)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = AmberWarning,
                                    cursorColor = AmberWarning
                                )
                            )

                            OutlinedTextField(
                                value = editFcfMarginInput,
                                onValueChange = { editFcfMarginInput = it },
                                label = { Text("FCF Margin (%)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = AmberWarning,
                                    cursorColor = AmberWarning
                                )
                            )

                            OutlinedTextField(
                                value = editFcfConversionInput,
                                onValueChange = { editFcfConversionInput = it },
                                label = { Text("FCF Conv (%)", fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedBorderColor = AmberWarning,
                                    cursorColor = AmberWarning
                                )
                            )
                        }

                        Button(
                            onClick = {
                                val snapObj = snap
                                if (snapObj != null) {
                                    val rVal = editRoicInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val mVal = editFcfMarginInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    val convVal = editFcfConversionInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                                    
                                    val newNetIncome = if (convVal != 0.0 && snapObj.ttmFcf != 0.0) {
                                        snapObj.ttmFcf / (convVal / 100.0)
                                    } else {
                                        snapObj.ttmNetIncome
                                    }

                                    viewModel.updateCalculatorSnapshot(
                                        snapObj.copy(
                                            roicPercent = rVal,
                                            fcfMarginPercent = mVal,
                                            ttmNetIncome = newNetIncome
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = NavyDark),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("APPLY PROFIT DATA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // QUALITATIVE Scorecard Checklist Section
        // ----------------------------------------------------
        item {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text(
                    text = "QUALITATIVE CRITERIA (PROFITABILITY / MOAT Checklist)",
                    color = AmberWarning,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        // Group qualCriteria by MoatSubcategory and loop over each group
        MoatSubcategory.values().forEach { subcat ->
            val subcatItems = qualCriteria.filter { it.category == subcat }
            if (subcatItems.isNotEmpty()) {
                item(key = "header_${subcat.name}") {
                    val subcatPoints = subcatItems.filter { it.checked }.sumOf { it.points }
                    val subcatTotal = subcatItems.sumOf { it.points }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = subcat.title.uppercase(Locale.US),
                                color = subcat.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$subcatPoints / $subcatTotal PTS",
                                color = subcat.color.copy(alpha = 0.8f),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = subcat.color.copy(alpha = 0.25f), thickness = 1.dp)
                    }
                }
                
                items(
                    items = subcatItems,
                    key = { "${subcat.name}_${it.title}" }
                ) { criteria ->
                    InteractiveCriteriaRow(
                        criteria = criteria,
                        color = subcat.color,
                        onCheckedChange = { isChecked ->
                            criteria.checked = isChecked
                            val updatedTitles = qualCriteria.filter { it.checked }.joinToString(";") { it.title }
                            snapshot?.let { snapObj ->
                                viewModel.updateCalculatorSnapshot(
                                    snapObj.copy(checkedQualitativeTitles = updatedTitles)
                                )
                            }
                        }
                    )
                }
            }
        }
        
        // ----------------------------------------------------
        // Educational Footer Section
        // ----------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfCard.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f)),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = GrayText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Note: Qualitative indicators are subjective. When doing research, utilize official Yahoo Finance financial statements and reports as your definitive source of truth.",
                        color = GrayText,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }

    if (showBalanceSheetMethodology) {
        AlertDialog(
            onDismissRequest = { showBalanceSheetMethodology = false },
            title = {
                Text(
                    text = "BALANCE SHEET HEALTH METHODOLOGY",
                    color = EmeraldGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.8.sp
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "The score evaluates balance sheet safety using a strict three-tier priority logic:",
                        fontSize = 12.sp,
                        color = LightText
                    )

                    Divider(color = BorderGray, thickness = 0.5.dp)

                    // Tier 1 Explainer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EmeraldGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tier 1: Zero Long Term Debt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "If the company operates with exactly zero long-term debt liabilities, it represents pristine financial safety and triggers an immediate maximum score of 100/100 points.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }

                    // Tier 2 Explainer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EmeraldGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tier 2: Net Cash Position (Cash >= Debt)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Establishes a solid baseline score of 60. Then adds a bonus modifier of +10 points for every multiplier of net cash cushion: ((Cash - Debt) / Debt) * 10, capped at a maximum bonus of +30 points (max tier score of 90/100).",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }

                    // Tier 3 Explainer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AmberWarning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tier 3: Net Debt Position (Cash < Debt)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Measures Net Debt (Debt - Cash) against a threshold of 3.5 times trailing Free Cash Flow. The score is scaled down from 60 points based on the ratio: 60 * (1 - (Net Debt / (3.5 * FCF))), with a hard floor of 0.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showBalanceSheetMethodology = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("CLOSE", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showProfitQualityMethodology) {
        AlertDialog(
            onDismissRequest = { showProfitQualityMethodology = false },
            title = {
                Text(
                    text = "PROFIT QUALITY METHODOLOGY",
                    color = AmberWarning,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.8.sp
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "The score measures profit durability out of 100 based on three weighted performance metrics:",
                        fontSize = 12.sp,
                        color = LightText
                    )

                    Divider(color = BorderGray, thickness = 0.5.dp)

                    // ROIC Explainer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AmberWarning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Return on Invested Capital (ROIC) - 30% Weight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Measures how efficiently a company allocates capital to profitable investments. Normalized against a perfect target of 30% ROIC: (ROIC / 30%) * 100, capped at 100 points, with a floor of 0.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }

                    // FCF Margin Explainer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AmberWarning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Free Cash Flow (FCF) Margin - 40% Weight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Measures the percentage of revenue turned into free cash flow. Normalized against a perfect target of 30% FCF Margin: (FCF Margin / 30%) * 100, capped at 100 points, with a floor of 0.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }

                    // FCF Conversion Explainer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AmberWarning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Free Cash Flow Conversion - 30% Weight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Measures the percentage of earnings converted into operating cash flows minus capital expenditures. Normalized against a perfect target of 100% conversion: (FCF Conversion / 100%) * 100, capped at 100 points, with a floor of 0.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showProfitQualityMethodology = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("CLOSE", color = AmberWarning, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showTotalScoreMethodology) {
        AlertDialog(
            onDismissRequest = { showTotalScoreMethodology = false },
            title = {
                Text(
                    text = "FINANCIAL INTELLIGENCE METHODOLOGY",
                    color = OptionCcIndigo,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.8.sp
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "The Financial Intelligence Score represents a comprehensive weighted blend of financial risk, capital efficiency, and earnings durability:",
                        fontSize = 12.sp,
                        color = LightText
                    )

                    Divider(color = BorderGray, thickness = 0.5.dp)

                    // Balance Sheet component
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EmeraldGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Balance Sheet Safety (50% Weight)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Assesses insolvency risk and debt buffer using our three-tier net cash/debt-to-FCF safety priority model.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }

                    // Profit Quality component
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AmberWarning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Profit Quality & Durability (50% Weight)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                        }
                        Text(
                            text = "Synthesizes capital efficiency (ROIC), cash profitability margins (FCF Margin), and cash conversion rates (FCF Conversion) to ensure high-quality, durable earnings.",
                            fontSize = 11.sp,
                            color = GrayText,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showTotalScoreMethodology = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("CLOSE", color = OptionCcIndigo, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Robust Three-Tier Balance Sheet Health Score Calculation Function.
 * Implements strict hierarchical evaluation with error shielding and safety caps.
 */
fun calculateBalanceSheetHealthScore(
    cashOnHand: Double,
    longTermDebt: Double,
    freeCashFlow: Double
): Int {
    return try {
        val safeCash = Math.max(0.0, cashOnHand)
        val safeDebt = Math.max(0.0, longTermDebt)

        // Tier 1: Zero Debt Condition
        if (safeDebt <= 0.0) {
            return 100
        }

        // Tier 2: Net Cash Condition
        if (safeCash >= safeDebt) {
            val baseline = 60.0
            val bonusModifier = ((safeCash - safeDebt) / safeDebt) * 10.0
            val cappedBonus = bonusModifier.coerceIn(0.0, 30.0)
            val total = baseline + cappedBonus
            return total.roundToInt().coerceIn(0, 100)
        } else {
            // Tier 3: Net Debt Condition (Cash < Debt)
            val netDebt = safeDebt - safeCash
            
            // Measure Net Debt against a safety threshold of 3.5 times FreeCashFlow.
            val safetyThreshold = 3.5 * freeCashFlow
            if (safetyThreshold <= 0.0) {
                // If FCF is zero or negative with active Net Debt, the score drops to 0
                0
            } else {
                val ratio = netDebt / safetyThreshold
                val rawScore = 60.0 * (1.0 - ratio)
                return rawScore.roundToInt().coerceIn(0, 60)
            }
        }
    } catch (e: Exception) {
        0
    }
}

/**
 * Calculates a company's Profit Quality Rank Score out of 100 based on three performance metrics.
 * 
 * Weights:
 * - Return on Invested Capital (ROIC): 30%
 * - Free Cash Flow (FCF) Margin: 40%
 * - Free Cash Flow (FCF) Conversion: 30%
 * 
 * Normalization Targets:
 * - Target ROIC: 30% (0.30)
 * - Target FCF Margin: 30% (0.30)
 * - Target FCF Conversion: 100% (1.00)
 * 
 * Safety:
 * - Negative inputs are floored at 0.0
 * - Component scores are capped at 100.0 before weighting
 * - Graceful try-catch shields mathematical operations and defaults to 0 on failure
 */
fun calculateProfitQualityRankScore(
    roic: Double?,
    fcfMargin: Double?,
    fcfConversion: Double?
): Int {
    return try {
        val rInput = roic ?: 0.0
        val mInput = fcfMargin ?: 0.0
        val cInput = fcfConversion ?: 0.0

        // Detect if inputs are expressed as percentages (e.g., 15.0 for 15.0%) or decimals (e.g., 0.15 for 15.0%) independently.
        // If the absolute value of the parameter is strictly greater than 1.0 (or 2.0 for FCF conversion), we treat it as percentage mode.
        val r = if (Math.abs(rInput) > 1.0) rInput / 100.0 else rInput
        val m = if (Math.abs(mInput) > 1.0) mInput / 100.0 else mInput
        val c = if (Math.abs(cInput) > 2.0) cInput / 100.0 else cInput

        // Enforce floors at 0.0 to handle negative inputs
        val safeRoic = if (r < 0.0) 0.0 else r
        val safeFcfMargin = if (m < 0.0) 0.0 else m
        val safeFcfConversion = if (c < 0.0) 0.0 else c

        // Target benchmarks
        val targetRoic = 0.30
        val targetFcfMargin = 0.30
        val targetFcfConversion = 1.00

        // 1. Return on Invested Capital (Weight: 30%)
        val normalizedRoic = (safeRoic / targetRoic) * 100.0
        val cappedRoic = Math.min(normalizedRoic, 100.0)
        val roicContribution = cappedRoic * 0.30

        // 2. Free Cash Flow Margin (Weight: 40%)
        val normalizedFcfMargin = (safeFcfMargin / targetFcfMargin) * 100.0
        val cappedFcfMargin = Math.min(normalizedFcfMargin, 100.0)
        val fcfMarginContribution = cappedFcfMargin * 0.40

        // 3. Free Cash Flow Conversion (Weight: 30%)
        val normalizedFcfConversion = (safeFcfConversion / targetFcfConversion) * 100.0
        val cappedFcfConversion = Math.min(normalizedFcfConversion, 100.0)
        val fcfConversionContribution = cappedFcfConversion * 0.30

        // Total score out of 100, rounded to nearest whole number
        val totalScore = roicContribution + fcfMarginContribution + fcfConversionContribution
        totalScore.roundToInt().coerceIn(0, 100)
    } catch (e: Exception) {
        0
    }
}

/**
 * Custom Speedometer/Arc Gauge drawn in high fidelity Canvas.
 * Incorporates:
 * 1. Double concentric framing outlines.
 * 2. High-fidelity carbon dotted background texture and cognitive radar coordinates.
 * 3. Thicker solid active progress arc with score-proportionate multi-layered neon glow.
 * 4. Active sweep leading cursor orbit particle.
 * 5. Faint outer golden dash-orbit crown for pristine Wall Street tier scores (>= 85).
 * 6. Soft glow shadow effects overlaid on center text.
 */
@Composable
fun GlowingGauge(
    score: Int,
    label: String,
    color: Color,
    size: Dp,
    isCenterLarge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = score.toFloat() / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gauge_progress"
    )

    // Continuous rotation angle for outer golden dash and leading cursor flares (slowed down by 50% from 6s to 12s)
    val infiniteTransition = rememberInfiniteTransition(label = "gauge_glow")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    // Gentle, very subtle and slow rotation of the solar rays to create shimmering (slowed down by 50% from 45s to 90s)
    val starRayRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(90000, easing = LinearEasing), // 90 seconds per full turn (very subtle and premium)
            repeatMode = RepeatMode.Restart
        ),
        label = "star_ray_rotation"
    )

    // Gentle breathing bloom pulse specifically for the divine light emission of the purple gauge
    val bloomBreathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bloom_breathing"
    )

    val labelSize = if (isCenterLarge) 8.sp else 6.5.sp
    val scoreTextSize = if (isCenterLarge) 32.sp else 22.sp
    val strokeWidthPx = with(LocalDensity.current) { (if (isCenterLarge) 10.dp else 7.dp).toPx() }
    val safetyPaddingPx = with(LocalDensity.current) { (if (isCenterLarge) 18.dp else 14.dp).toPx() }
    val tickColor = Color.White.copy(alpha = 0.18f)
    val scoreFactor = (score.toFloat() / 100f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(size)
            .aspectRatio(1f)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClick)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCenterLarge) 12.dp else 10.dp)
        ) {
            val width = this.size.width
            val height = this.size.height
            val center = Offset(width / 2f, height / 2f)
            val safetyPadding = safetyPaddingPx
            val radius = (width - strokeWidthPx - safetyPadding) / 2f

            // Gauge bounds
            val startAngle = 135f
            val totalSweep = 270f
            val activeSweep = animatedProgress * totalSweep

            // 1. Draw Textured Background, Outlines & Divine Bloom Filter for Purple Gauge
            if (color == OptionCcIndigo) {
                // Wide primary divine glow background layer (outer bloom)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.38f * bloomBreathingAlpha),
                            color.copy(alpha = 0.20f * bloomBreathingAlpha),
                            color.copy(alpha = 0.06f * bloomBreathingAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius + (strokeWidthPx / 2f) + 26.dp.toPx()
                    ),
                    radius = radius + (strokeWidthPx / 2f) + 26.dp.toPx(),
                    center = center
                )

                // Tight ambient halo around the frame rim
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.48f * (0.85f + bloomBreathingAlpha * 0.15f)),
                            color.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius + (strokeWidthPx / 2f) + 12.dp.toPx()
                    ),
                    radius = radius + (strokeWidthPx / 2f) + 12.dp.toPx(),
                    center = center
                )

                // Lens/optical flare concentric bloom rings
                val bloomRings = 4
                for (layer in 1..bloomRings) {
                    val bloomRadius = radius + (strokeWidthPx / 2f) + (layer * 3.dp.toPx())
                    drawCircle(
                        color = color.copy(alpha = (0.15f / layer) * bloomBreathingAlpha),
                        radius = bloomRadius,
                        style = Stroke(width = (1.5.dp.toPx() * layer))
                    )
                }
            }

            // Outer circular boundary outline
            drawCircle(
                color = color.copy(alpha = if (color == OptionCcIndigo) 0.35f else 0.15f),
                radius = radius + (strokeWidthPx / 2f),
                style = Stroke(width = 1.dp.toPx())
            )

            // Inner gauge face dark background solid fill
            drawCircle(
                color = Color(0xFF0F1626).copy(alpha = 0.85f),
                radius = radius - (strokeWidthPx / 2f)
            )

            // Inner boundary outline
            drawCircle(
                color = color.copy(alpha = 0.08f),
                radius = radius - (strokeWidthPx / 2f),
                style = Stroke(width = 1.dp.toPx())
            )

            // Background texture: Carbon-Dotted tech mesh
            val dotSpacing = 8.dp.toPx()
            val dotRadius = 1.dp.toPx()
            val cols = (width / dotSpacing).toInt()
            val rows = (height / dotSpacing).toInt()
            for (col in 0..cols) {
                for (row in 0..rows) {
                    val dotX = col * dotSpacing
                    val dotY = row * dotSpacing
                    val dx = dotX - center.x
                    val dy = dotY - center.y
                    val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (dist < radius - strokeWidthPx) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.04f),
                            radius = dotRadius,
                            center = Offset(dotX, dotY)
                        )
                    }
                }
            }

            // Background texture: Cognitive Radar circular & crosshair guides
            val innerRadius = radius - strokeWidthPx
            drawCircle(
                color = Color.White.copy(alpha = 0.015f),
                radius = innerRadius * 0.35f,
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.015f),
                radius = innerRadius * 0.7f,
                style = Stroke(width = 0.8.dp.toPx())
            )
            // Crosshairs
            drawLine(
                color = Color.White.copy(alpha = 0.01f),
                start = Offset(center.x - innerRadius, center.y),
                end = Offset(center.x + innerRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.01f),
                start = Offset(center.x, center.y - innerRadius),
                end = Offset(center.x, center.y + innerRadius),
                strokeWidth = 1f
            )

            // 2. Draw Background Track Arc
            drawArc(
                color = color.copy(alpha = 0.12f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                size = Size(width - strokeWidthPx, height - strokeWidthPx),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // 3. Draw Active Solid Progress Arc with Glowing layers
            val topLeftOffset = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
            val arcSize = Size(width - strokeWidthPx, height - strokeWidthPx)

            if (activeSweep > 0.1f) {
                val isPurple = color == OptionCcIndigo
                val baseGlowLayers = if (score >= 85) 3 else if (score >= 60) 2 else 1
                val glowLayers = if (isPurple) baseGlowLayers + 2 else baseGlowLayers
                
                for (layer in 1..glowLayers) {
                    val extraWidth = layer * (if (isPurple) 5.dp.toPx() else 4.dp.toPx()) * (0.3f + scoreFactor * 0.7f)
                    val baseAlpha = if (isPurple) 0.08f else 0.05f
                    val alphaMultiplier = if (isPurple) (0.45f + bloomBreathingAlpha * 0.55f) else 1.0f
                    drawArc(
                        color = color.copy(alpha = ((baseAlpha / layer) * (0.5f + scoreFactor * 0.5f) * alphaMultiplier).coerceIn(0f, 1f)),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = topLeftOffset,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx + extraWidth, cap = StrokeCap.Round)
                    )
                }

                // Core Active Arc
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            // 4. Draw Analog Tick Markers around the scale
            val tickCount = 11
            for (i in 0 until tickCount) {
                val tickAngleDeg = startAngle + (i.toFloat() / (tickCount - 1)) * totalSweep
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val isMajor = (i == 0 || i == 5 || i == 10)
                val tickLength = if (isMajor) strokeWidthPx * 1.5f else strokeWidthPx * 0.8f
                
                val tickInnerRad = radius - strokeWidthPx
                val startX = center.x + tickInnerRad * cos(tickAngleRad).toFloat()
                val startY = center.y + tickInnerRad * sin(tickAngleRad).toFloat()
                
                val endRadius = tickInnerRad - tickLength
                val endX = center.x + endRadius * cos(tickAngleRad).toFloat()
                val endY = center.y + endRadius * sin(tickAngleRad).toFloat()

                val passProgress = (i.toFloat() / (tickCount - 1)) <= animatedProgress
                val currentTickColor = if (passProgress && score > 0) color.copy(alpha = 0.8f) else tickColor

                drawLine(
                    color = currentTickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 2f else 1f
                )
            }

            // 5. Special High Score Visual Rewards (Neon Blooming Double Ring on Center Circle ONLY)
            // Replace the spiky rotating ring with a beautiful double-layered neon glowing ring.
            // The higher the score, the more blooming effect.
            if (isCenterLarge && score >= 70) {
                val scoreFloat = score.toFloat()
                // Scale from 70 to 100 for the blooming factor
                val highScoreFactor = ((scoreFloat - 70f) / 30f).coerceIn(0f, 1f)
                
                // Let the bloom range from 1.0x to 2.2x the baseline glow width, scaling with the high score
                val bloomGlowScale = 1.0f + highScoreFactor * 1.2f
                val bloomAlphaScale = 0.6f + highScoreFactor * 0.4f
                
                val neonRingRadius = radius + (strokeWidthPx / 2f)
                
                val cyanColor = Color(0xFF00E5FF)
                val magentaColor = Color(0xFFE040FB)
                
                // 5a. Cyan/Blue Outer Neon Glow Layers (creates the wide electric outer halo)
                val cyanLayers = 5
                for (layer in 1..cyanLayers) {
                    val strokeWidthVal = (6.dp.toPx() + layer * 5.dp.toPx()) * bloomGlowScale * (0.85f + bloomBreathingAlpha * 0.15f)
                    val alphaVal = (0.15f / layer) * bloomAlphaScale * (0.8f + bloomBreathingAlpha * 0.2f)
                    drawCircle(
                        color = cyanColor.copy(alpha = alphaVal.coerceIn(0f, 1f)),
                        radius = neonRingRadius,
                        style = Stroke(width = strokeWidthVal)
                    )
                }
                
                // 5b. Purple/Magenta Inner Neon Glow Layers (creates the inner blending purple halo)
                val magentaLayers = 3
                for (layer in 1..magentaLayers) {
                    val strokeWidthVal = (4.dp.toPx() + layer * 4.dp.toPx()) * bloomGlowScale * (0.85f + bloomBreathingAlpha * 0.15f)
                    val alphaVal = (0.18f / layer) * bloomAlphaScale * (0.8f + bloomBreathingAlpha * 0.2f)
                    drawCircle(
                        color = magentaColor.copy(alpha = alphaVal.coerceIn(0f, 1f)),
                        radius = neonRingRadius - 1.dp.toPx(),
                        style = Stroke(width = strokeWidthVal)
                    )
                }
                
                // 5c. Bright Pearlescent White Core Ring (matching the reference image's luminous sharp center)
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f),
                    radius = neonRingRadius,
                    style = Stroke(width = 2.2.dp.toPx())
                )
            }
        }

        // 7. Overlaid Center Texts with a glowing colored background behind the numbers
        // The higher the score, the larger and more vibrant the background glow!
        val bgGlowSize = if (isCenterLarge) {
            (70.dp + 45.dp * scoreFactor)
        } else {
            (50.dp + 35.dp * scoreFactor)
        }
        val bgGlowAlpha = (0.08f + 0.42f * scoreFactor)

        Box(
            modifier = Modifier
                .size(bgGlowSize)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = bgGlowAlpha),
                            color.copy(alpha = bgGlowAlpha * 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val displayScore = (animatedProgress * 100).toInt()
                val displayTextColor = when {
                    score >= 85 -> Color(0xFFF8FAFC) // bright pearlescent white
                    else -> LightText
                }

                Text(
                    text = "$displayScore",
                    fontSize = scoreTextSize,
                    color = displayTextColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = scoreTextSize,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(
                            color = color.copy(alpha = (0.3f + scoreFactor * 0.6f).coerceIn(0.1f, 0.9f)),
                            offset = Offset(0f, 0f),
                            blurRadius = with(LocalDensity.current) { (if (score >= 85) 12.dp else 6.dp).toPx() }
                        )
                    )
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = label,
                    fontSize = labelSize,
                    color = color.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Interactive scorecard item list row.
 * Custom checkbox styled with a clean glowing border that expands dynamically on touch.
 */
@Composable
fun InteractiveCriteriaRow(
    criteria: ScoringCriteria,
    color: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(criteria.checked) }
    
    // Sync external state updates (like Reset action)
    LaunchedEffect(criteria.checked) {
        isChecked = criteria.checked
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) color.copy(alpha = 0.04f) else SurfCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isChecked) 1.2.dp else 1.dp,
            color = if (isChecked) color.copy(alpha = 0.4f) else BorderGray
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                isChecked = !isChecked
                onCheckedChange(isChecked)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Custom checkbox representation
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isChecked) color else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (isChecked) color else GrayText.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = NavyDark,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = criteria.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = LightText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = criteria.description,
                    fontSize = 11.sp,
                    color = GrayText,
                    lineHeight = 14.sp
                )
            }

            // Points Badge Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isChecked) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "+${criteria.points} PTS",
                    color = if (isChecked) color else GrayText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Moat subcategories for organizing qualitative scoring playbook.
 */
enum class MoatSubcategory(val title: String, val color: Color) {
    MANAGEMENT("Management & Capital Allocation", OptionCcIndigo),
    PRODUCT_DYNAMICS("Product & Pricing Dynamics", EmeraldGreen),
    COMPETITIVE_MOAT("Competitive Moat & Market Position", AmberWarning)
}

/**
 * Scorecard criteria data representation class.
 */
class ScoringCriteria(
    val title: String,
    val description: String,
    val points: Int,
    val category: MoatSubcategory,
    initialChecked: Boolean = false
) {
    var checked by mutableStateOf(initialChecked)
}
