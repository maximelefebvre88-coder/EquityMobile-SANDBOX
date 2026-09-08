package com.example.domain.util

fun isCanadianTicker(symbol: String): Boolean {
    val upper = symbol.uppercase().trim()
    return upper.endsWith(".TO") ||
           upper.endsWith(".V") ||
           upper.endsWith(".VN") ||
           upper.endsWith(".CN") ||
           upper.endsWith(".NE") ||
           upper.endsWith(".NEO") ||
           upper.endsWith(".TSX")
}

fun getTickerCurrency(symbol: String): String {
    val upper = symbol.uppercase().trim()
    if (upper == "CASH" || upper.isEmpty()) return ""
    return if (isCanadianTicker(upper)) "CAD" else "USD"
}

fun getFxMultiplier(fromCurrency: String, toCurrency: String, usdToCadRate: Double = 1.36): Double {
    val from = fromCurrency.uppercase().trim()
    val to = toCurrency.uppercase().trim()
    if (from.isEmpty() || to.isEmpty() || from == to) return 1.0
    if (from == "USD" && to == "CAD") {
        return usdToCadRate
    }
    if (from == "CAD" && to == "USD") {
        return 1.0 / usdToCadRate
    }
    return 1.0
}
