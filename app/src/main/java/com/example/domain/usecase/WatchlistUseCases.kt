package com.example.domain.usecase

import com.example.domain.model.WatchlistTicker
import com.example.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow

class GetWatchlistUseCase(private val repository: FinanceRepository) {
    operator fun invoke(): Flow<List<WatchlistTicker>> {
        return repository.getWatchlist()
    }
}

class AddTickerUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String, name: String) {
        repository.addTicker(symbol, name)
    }
}

class RemoveTickerUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String) {
        repository.removeTicker(symbol)
    }
}

class UpdateManualCostBasisUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String, costBasis: Double?) {
        repository.updateManualCostBasis(symbol, costBasis)
    }
}

class UpdateTargetPriceUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String, targetPrice: Double?) {
        repository.updateTargetPrice(symbol, targetPrice)
    }
}

class UpdateTargetYieldUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String, targetYield: Double?) {
        repository.updateTargetYield(symbol, targetYield)
    }
}

class UpdateTargetPriceAndYieldUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String, targetPrice: Double?, targetYield: Double?) {
        repository.updateTargetPriceAndYield(symbol, targetPrice, targetYield)
    }
}

class UpdateWatchlistOrderUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(orderedSymbols: List<String>) {
        repository.updateWatchlistOrder(orderedSymbols)
    }
}
