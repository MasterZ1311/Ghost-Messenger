package org.ghostmessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.ghostmessenger.ui.navigation.GhostNavHost
import org.ghostmessenger.ui.theme.GhostMessengerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val hasIdentity = securePreferences.hasIdentity()

        setContent {
            GhostMessengerTheme {
                GhostNavHost(hasIdentity = hasIdentity)
            }
        }
    }
}
