package com.vignan.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.vignan.tracker.update.UpdateOverlay

class MainActivity : ComponentActivity() {

    // Declared as a field so it is registered before onCreate runs
    // (registerForActivityResult must not be called after STARTED).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Grant or deny — either way the app works; a denial only means
            // the widget's tap-to-refresh toast won't be shown by Android 13+.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set before super.onCreate() so any code path that touches the credential
        // store during activity init already sees a valid context.
        AppContextProvider.context = applicationContext

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestToastPermissionIfNeeded()

        setContent {
            TrackerTheme {
                // In-app updater: checks for updates on every open, shows the
                // bottom status pill and the update popup when relevant.
                UpdateOverlay { App() }
            }
        }
    }

    /**
     * Android 13+ lets an app show toasts while it is in the foreground for free, but a
     * toast raised from a background state (the home-screen widget receiver) is dropped
     * unless POST_NOTIFICATIONS is granted. Ask once, up front.
     */
    private fun requestToastPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
