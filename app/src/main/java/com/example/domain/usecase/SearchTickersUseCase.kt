package com.example.domain.usecase

import com.example.domain.model.FmpSearchResponse
import com.example.domain.repository.FinanceRepository

class SearchTickersUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(query: String): List<FmpSearchResponse> {
        return repository.searchTickers(query)
    }
}
