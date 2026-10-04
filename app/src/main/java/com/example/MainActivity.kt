package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.ui.InsViewModel
import com.example.ui.MainScreen
import com.example.ui.theme.CyberVoid
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: InsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        requestNotificationPermissionIfNeeded()
        observeDisplayRefreshRate()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberVoid
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkStoragePermission()
        applyDisplayRefreshRate(viewModel.displayState.value.refreshRateHz)
    }

    private fun observeDisplayRefreshRate() {
        lifecycleScope.launch {
            viewModel.displayState.collectLatest { state ->
                applyDisplayRefreshRate(state.refreshRateHz)
            }
        }
    }

    private fun applyDisplayRefreshRate(targetHz: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
                val modes = display?.supportedModes ?: emptyArray()
                val targetFloat = targetHz.toFloat()

                val matchedMode = modes.minByOrNull { Math.abs(it.refreshRate - targetFloat) }
                    ?: modes.maxByOrNull { it.refreshRate }

                if (matchedMode != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = matchedMode.modeId
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        lp.preferredRefreshRate = matchedMode.refreshRate
                    }
                    window.attributes = lp
                }
            }
        } catch (_: Exception) {}
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }
}
