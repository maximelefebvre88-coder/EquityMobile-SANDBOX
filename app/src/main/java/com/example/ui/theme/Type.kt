package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val RecoletaFontFamily = FontFamily(
  Font(R.font.recoleta_bold, FontWeight.Bold),
  Font(R.font.recoleta_bold, FontWeight.ExtraBold),
  Font(R.font.recoleta_bold, FontWeight.SemiBold),
  Font(R.font.recoleta_bold, FontWeight.Medium),
  Font(R.font.recoleta_bold, FontWeight.Normal),
)

// Set of Material typography styles to start with
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
  )
