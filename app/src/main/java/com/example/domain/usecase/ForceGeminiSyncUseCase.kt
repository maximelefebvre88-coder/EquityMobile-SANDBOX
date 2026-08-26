package com.example.domain.usecase

import com.example.domain.model.CalculatorSnapshot
import com.example.domain.repository.FinanceRepository

class ForceGeminiSyncUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String, force: Boolean = false): CalculatorSnapshot {
        return repository.forceGeminiSync(symbol, force)
    }
}
