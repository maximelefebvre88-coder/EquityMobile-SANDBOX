package com.example.di

import com.example.domain.repository.FinanceRepository
import com.example.domain.usecase.FinanceUseCases

interface AppContainer {
    val financeRepository: FinanceRepository
    val financeUseCases: FinanceUseCases
    val firebaseManager: com.example.data.remote.FirebaseManager
}
