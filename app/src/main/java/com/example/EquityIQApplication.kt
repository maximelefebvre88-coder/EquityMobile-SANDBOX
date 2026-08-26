package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.AppContainerImpl

class EquityIQApplication : Application() {
    
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }
}
