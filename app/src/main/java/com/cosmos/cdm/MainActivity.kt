package com.cosmos.cdm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cosmos.cdm.ui.CdmApp
import com.cosmos.cdm.ui.CdmViewModel
import com.cosmos.cdm.ui.theme.CdmTheme
import com.cosmos.cdm.ui.theme.CosmosBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CdmTheme {
                Surface(Modifier.fillMaxSize(), color = CosmosBg) {
                    val vm: CdmViewModel = viewModel()
                    CdmApp(vm)
                }
            }
        }
    }
}
