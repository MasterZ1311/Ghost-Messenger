package org.ghostmessenger.data.network.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.ghostmessenger.MainActivity
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.ghostmessenger.data.network.api.PreKeyApiClient
import org.ghostmessenger.data.network.socket.SignalingClient
import javax.inject.Inject

/**
 * Calypso Zero-Knowledge Firebase Messaging Service.
 *
 * CRITICAL PRIVACY & SECURITY GUARANTEES:
 * - FCM messages are strictly used as ephemeral wake-up pings.
 * - No plaintext, no sender user code, and no message payloads are EVER transmitted over FCM.
 * - On wake-up, the device establishes a direct encrypted session with the signaling server,
 *   fetches the queued encrypted envelopes, and decrypts locally using the Signal Double Ratchet.
 */
@AndroidEntryPoint
class CalypsoFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var securePreferences: SecurePreferences

    @Inject
    lateinit var signalingClient: SignalingClient

    @Inject
    lateinit var preKeyApiClient: PreKeyApiClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        securePreferences.setFcmToken(token)
        signalingClient.updateFcmToken(token)

        val identity = securePreferences.getIdentity() ?: return
        val serverUrl = securePreferences.getSignalingUrl()

        serviceScope.launch {
            preKeyApiClient.registerFcmToken(serverUrl, identity.userCode, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val messageType = remoteMessage.data["type"]
        if (messageType == "wake_up") {
            // Wake up client and pull queued encrypted messages from signaling server
            val identity = securePreferences.getIdentity() ?: return
            val serverUrl = securePreferences.getSignalingUrl()

            signalingClient.connect(serverUrl, identity.userCode)
            showLocalWakeUpNotification()
        }
    }

    private fun showLocalWakeUpNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "calypso_messages_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Encrypted Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming end-to-end encrypted messages"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Calypso")
            .setContentText("New encrypted message received")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
