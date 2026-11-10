package com.metroevidencias.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.metroevidencias.app.data.LocalRecordRepository
import com.metroevidencias.app.ui.MetroApp
import com.metroevidencias.app.ui.MetroViewModel
import com.metroevidencias.app.ui.theme.MetroTheme

/** Android dependencies are created here, outside the screens. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = LocalRecordRepository(applicationContext)
        setContent {
            val model: MetroViewModel = viewModel(factory = viewModelFactory {
                initializer { MetroViewModel(repository, createSavedStateHandle()) }
            })
            MetroTheme { MetroApp(model) }
        }
    }
}
