package com.example.di

import android.content.Context
import com.example.data.repository.FinanceRepositoryImpl
import com.example.domain.repository.FinanceRepository
import com.example.domain.usecase.FinanceUseCases

class AppContainerImpl(private val context: Context) : AppContainer {

    override val firebaseManager: com.example.data.remote.FirebaseManager by lazy {
        com.example.data.remote.FirebaseManager(context)
    }

    override val financeRepository: FinanceRepository by lazy {
        FinanceRepositoryImpl(context)
    }

    override val financeUseCases: FinanceUseCases by lazy {
        FinanceUseCases.createDefault(context, financeRepository)
    }
}
