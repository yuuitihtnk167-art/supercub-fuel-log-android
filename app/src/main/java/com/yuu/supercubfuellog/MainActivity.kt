package com.yuu.supercubfuellog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuu.supercubfuellog.ui.AppRoot
import com.yuu.supercubfuellog.ui.SupercubFuelLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SupercubFuelLogTheme {
                val viewModel: MainViewModel = viewModel()
                AppRoot(viewModel = viewModel)
            }
        }
    }
}
