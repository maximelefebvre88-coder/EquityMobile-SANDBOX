package com.example.domain.usecase

import com.example.domain.model.TradeEntity
import com.example.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow

class GetAllTradesUseCase(private val repository: FinanceRepository) {
    operator fun invoke(): Flow<List<TradeEntity>> {
        return repository.getAllTrades()
    }
}

class GetTradesForTickerUseCase(private val repository: FinanceRepository) {
    operator fun invoke(symbol: String): Flow<List<TradeEntity>> {
        return repository.getTradesForTicker(symbol)
    }
}

class LogTradeUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(trade: TradeEntity) {
        repository.insertTrade(trade)
    }
}

class UpdateTradeUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(trade: TradeEntity) {
        repository.insertTrade(trade)
    }
}

class DeleteLoggedTradeUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(id: Int) {
        repository.deleteTrade(id)
    }
}
