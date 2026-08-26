package com.example.domain.usecase

import com.example.domain.model.CalculatorSnapshot
import com.example.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow

class GetCalculatorSnapshotUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(symbol: String): CalculatorSnapshot? {
        return repository.getCalculatorSnapshot(symbol)
    }
}

class GetCalculatorSnapshotFlowUseCase(private val repository: FinanceRepository) {
    operator fun invoke(symbol: String): Flow<CalculatorSnapshot?> {
        return repository.getCalculatorSnapshotFlow(symbol)
    }
}

class UpdateCalculatorSnapshotUseCase(private val repository: FinanceRepository) {
    suspend operator fun invoke(snapshot: CalculatorSnapshot) {
        repository.saveCalculatorSnapshot(snapshot)
    }
}
