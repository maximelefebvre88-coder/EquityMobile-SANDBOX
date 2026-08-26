package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FmpQuoteResponse(
    val symbol: String? = "",
    @Json(name = "c") val price: Double? = null,
    @Json(name = "d") val change: Double? = null,
    @Json(name = "dp") val percentChange: Double? = null,
    @Json(name = "pc") val previousClose: Double? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class FmpProfileResponse(
    @Json(name = "ticker") val symbol: String? = null,
    @Json(name = "name") val companyName: String? = null,
    @Json(name = "marketCapitalization") val mktCap: Double? = null,
    val price: Double? = null,
    @Json(name = "weburl") val website: String? = null,
    @Json(name = "finnhubIndustry") val description: String? = null,
    @Json(name = "logo") val image: String? = null
)

@JsonClass(generateAdapter = true)
data class FinnhubMetrics(
    @Json(name = "peTTM") val peTTM: Double? = null,
    @Json(name = "pfcfTTM") val pfcfTTM: Double? = null,
    @Json(name = "salesPerShareTTM") val salesPerShareTTM: Double? = null,
    @Json(name = "fcfMarginTTM") val fcfMarginTTM: Double? = null,
    @Json(name = "fcfMarginAnnual") val fcfMarginAnnual: Double? = null,
    @Json(name = "roeTTM") val roeTTM: Double? = null,
    @Json(name = "roeAnnual") val roeAnnual: Double? = null,
    @Json(name = "roaTTM") val roaTTM: Double? = null,
    @Json(name = "epsTTM") val epsTTM: Double? = null,
    @Json(name = "ebitMarginTTM") val ebitMarginTTM: Double? = null,
    @Json(name = "netProfitMarginTTM") val netProfitMarginTTM: Double? = null,
    @Json(name = "marketCapitalization") val marketCapitalization: Double? = null
)

@JsonClass(generateAdapter = true)
data class FmpKeyMetricsResponse(
    val symbol: String? = null,
    val metric: FinnhubMetrics? = null
)
