package com.example.domain.model

data class FmpSearchResponse(
    val symbol: String,
    val name: String? = null,
    val currency: String? = null,
    val stockExchange: String? = null
)
