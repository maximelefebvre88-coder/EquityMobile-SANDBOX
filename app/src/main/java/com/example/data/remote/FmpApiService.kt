package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query

import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class FmpSearchResponse(
    val symbol: String,
    val name: String? = null,
    val currency: String? = null,
    val stockExchange: String? = null
)

@JsonClass(generateAdapter = true)
data class FinnhubSearchResponse(
    val count: Int? = null,
    val result: List<FinnhubSearchResult>? = null
)

@JsonClass(generateAdapter = true)
data class FinnhubSearchResult(
    val description: String? = null,
    val displaySymbol: String? = null,
    val symbol: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGoogleSearch(
    val dummy: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiTool(
    @Json(name = "google_search") val googleSearch: GeminiGoogleSearch? = null,
    val googleSearchRetrieval: GeminiGoogleSearch? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>? = null,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiStockBaseline(
    val companyName: String? = null,
    val currentPrice: Double? = null,
    val marketCap: Double? = null,
    val sharesOutstanding: Double? = null,
    val revenuePerShare: Double? = null,
    val fcfPerShare: Double? = null,
    val fcfMarginPercent: Double? = null,
    val netCashPerShare: Double? = null,
    val roicPercent: Double? = null,
    val fcfGrowthRate: Double? = null,
    val ttmRevenue: Double? = null,
    val ttmFcf: Double? = null,
    val historicalFcfYield: Double? = null,
    val cashOnHand: Double? = null,
    val ltDebt: Double? = null,
    val ttmNetIncome: Double? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

@JsonClass(generateAdapter = true)
data class YahooChartMeta(
    val currency: String? = null,
    val symbol: String? = null,
    val regularMarketPrice: Double? = null,
    val previousClose: Double? = null,
    val chartPreviousClose: Double? = null,
    val shortName: String? = null,
    val longName: String? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartResult(
    val meta: YahooChartMeta? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartContainer(
    val result: List<YahooChartResult>? = null
)

@JsonClass(generateAdapter = true)
data class YahooChartResponse(
    val chart: YahooChartContainer? = null
)

interface YahooFinanceService {
    @GET("v8/finance/chart/{symbol}?interval=1d&range=1d")
    suspend fun getChart(
        @Path("symbol") symbol: String
    ): YahooChartResponse
}

interface FmpApiService {
    @GET("quote")
    suspend fun getQuote(
        @Query("symbol") symbol: String,
        @Query("token") apiKey: String
    ): FmpQuoteResponse

    @GET("stock/profile2")
    suspend fun getProfile(
        @Query("symbol") symbol: String,
        @Query("token") apiKey: String
    ): FmpProfileResponse

    @GET("stock/metric")
    suspend fun getKeyMetrics(
        @Query("symbol") symbol: String,
        @Query("metric") metricType: String = "all",
        @Query("token") apiKey: String
    ): FmpKeyMetricsResponse

    @GET("search")
    suspend fun searchSymbols(
        @Query("q") query: String,
        @Query("token") apiKey: String
    ): FinnhubSearchResponse
}
