package org.ghostmessenger.data.network.socket

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ghostmessenger.data.network.model.PresenceUpdate
import org.ghostmessenger.data.network.model.SignalingAnswer
import org.ghostmessenger.data.network.model.SignalingConnectionState
import org.ghostmessenger.data.network.model.SignalingEnvelope
import org.ghostmessenger.data.network.model.SignalingIceCandidate
import org.ghostmessenger.data.network.model.SignalingOffer
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Socket.IO client for real-time WebRTC SDP/ICE signaling and ephemeral message delivery.
 */
@Singleton
class SignalingClient @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var currentUserCode: String? = null

    private val _connectionState = MutableStateFlow(SignalingConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    private val _incomingOffers = MutableSharedFlow<SignalingOffer>(extraBufferCapacity = 64)
    val incomingOffers: SharedFlow<SignalingOffer> = _incomingOffers.asSharedFlow()

    private val _incomingAnswers = MutableSharedFlow<SignalingAnswer>(extraBufferCapacity = 64)
    val incomingAnswers: SharedFlow<SignalingAnswer> = _incomingAnswers.asSharedFlow()

    private val _incomingIceCandidates = MutableSharedFlow<SignalingIceCandidate>(extraBufferCapacity = 64)
    val incomingIceCandidates: SharedFlow<SignalingIceCandidate> = _incomingIceCandidates.asSharedFlow()

    private val _incomingEnvelopes = MutableSharedFlow<SignalingEnvelope>(extraBufferCapacity = 64)
    val incomingEnvelopes: SharedFlow<SignalingEnvelope> = _incomingEnvelopes.asSharedFlow()

    private val _presenceUpdates = MutableSharedFlow<PresenceUpdate>(extraBufferCapacity = 64)
    val presenceUpdates: SharedFlow<PresenceUpdate> = _presenceUpdates.asSharedFlow()

    /**
     * Connects to the signaling server and registers the local user's UserCode.
     */
    fun connect(serverUrl: String, userCode: String) {
        if (socket != null && socket?.connected() == true && currentUserCode == userCode) {
            return
        }

        disconnect()
        currentUserCode = userCode
        _connectionState.value = SignalingConnectionState.CONNECTING

        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                timeout = 10000
            }

            val sock = IO.socket(serverUrl, opts)
            socket = sock

            sock.on(Socket.EVENT_CONNECT) {
                _connectionState.value = SignalingConnectionState.CONNECTED
                // Register local UserCode
                val regData = JSONObject().apply { put("userCode", userCode) }
                sock.emit("register", regData)
            }

            sock.on(Socket.EVENT_DISCONNECT) {
                _connectionState.value = SignalingConnectionState.DISCONNECTED
            }

            sock.on(Socket.EVENT_CONNECT_ERROR) {
                _connectionState.value = SignalingConnectionState.DISCONNECTED
            }

            // WebRTC Offer
            sock.on("webrtc-offer") { args ->
                val data = args?.firstOrNull() as? JSONObject ?: return@on
                val fromUserCode = data.optString("fromUserCode")
                val offerObj = data.optJSONObject("offer")
                val sdp = offerObj?.optString("sdp") ?: ""
                val type = offerObj?.optString("type", "offer") ?: "offer"

                if (fromUserCode.isNotBlank() && sdp.isNotBlank()) {
                    scope.launch {
                        _incomingOffers.emit(SignalingOffer(fromUserCode, sdp, type))
                    }
                }
            }

            // WebRTC Answer
            sock.on("webrtc-answer") { args ->
                val data = args?.firstOrNull() as? JSONObject ?: return@on
                val fromUserCode = data.optString("fromUserCode")
                val answerObj = data.optJSONObject("answer")
                val sdp = answerObj?.optString("sdp") ?: ""
                val type = answerObj?.optString("type", "answer") ?: "answer"

                if (fromUserCode.isNotBlank() && sdp.isNotBlank()) {
                    scope.launch {
                        _incomingAnswers.emit(SignalingAnswer(fromUserCode, sdp, type))
                    }
                }
            }

            // WebRTC ICE Candidate
            sock.on("ice-candidate") { args ->
                val data = args?.firstOrNull() as? JSONObject ?: return@on
                val fromUserCode = data.optString("fromUserCode")
                val candObj = data.optJSONObject("candidate")
                val candidate = candObj?.optString("candidate") ?: ""
                val sdpMid = candObj?.optString("sdpMid") ?: "0"
                val sdpMLineIndex = candObj?.optInt("sdpMLineIndex", 0) ?: 0

                if (fromUserCode.isNotBlank() && candidate.isNotBlank()) {
                    scope.launch {
                        _incomingIceCandidates.emit(
                            SignalingIceCandidate(fromUserCode, candidate, sdpMid, sdpMLineIndex)
                        )
                    }
                }
            }

            // Ephemeral Relay Envelope Fallback
            sock.on("encrypted-envelope") { args ->
                val data = args?.firstOrNull() as? JSONObject ?: return@on
                val fromUserCode = data.optString("fromUserCode")
                val envelope = data.optString("envelope")

                if (fromUserCode.isNotBlank() && envelope.isNotBlank()) {
                    scope.launch {
                        _incomingEnvelopes.emit(SignalingEnvelope(fromUserCode, envelope))
                    }
                }
            }

            // Presence Result
            sock.on("presence-result") { args ->
                val data = args?.firstOrNull() as? JSONObject ?: return@on
                val targetUserCode = data.optString("targetUserCode")
                val online = data.optBoolean("online", false)

                if (targetUserCode.isNotBlank()) {
                    scope.launch {
                        _presenceUpdates.emit(PresenceUpdate(targetUserCode, online))
                    }
                }
            }

            sock.connect()
        } catch (e: Exception) {
            _connectionState.value = SignalingConnectionState.DISCONNECTED
        }
    }

    /**
     * Checks presence for a target user.
     */
    fun checkPresence(targetUserCode: String) {
        val data = JSONObject().apply { put("targetUserCode", targetUserCode) }
        socket?.emit("check-presence", data)
    }

    /**
     * Sends a WebRTC SDP Offer to a peer.
     */
    fun sendOffer(targetUserCode: String, sdp: String, type: String = "offer") {
        val offerObj = JSONObject().apply {
            put("type", type)
            put("sdp", sdp)
        }
        val data = JSONObject().apply {
            put("targetUserCode", targetUserCode)
            put("offer", offerObj)
        }
        socket?.emit("webrtc-offer", data)
    }

    /**
     * Sends a WebRTC SDP Answer to a peer.
     */
    fun sendAnswer(targetUserCode: String, sdp: String, type: String = "answer") {
        val answerObj = JSONObject().apply {
            put("type", type)
            put("sdp", sdp)
        }
        val data = JSONObject().apply {
            put("targetUserCode", targetUserCode)
            put("answer", answerObj)
        }
        socket?.emit("webrtc-answer", data)
    }

    /**
     * Sends an ICE Candidate to a peer.
     */
    fun sendIceCandidate(targetUserCode: String, candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val candObj = JSONObject().apply {
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }
        val data = JSONObject().apply {
            put("targetUserCode", targetUserCode)
            put("candidate", candObj)
        }
        socket?.emit("ice-candidate", data)
    }

    /**
     * Sends an ephemeral encrypted envelope via signaling relay fallback.
     */
    fun sendEncryptedEnvelope(
        targetUserCode: String,
        envelopeBase64: String,
        onAck: ((Boolean) -> Unit)? = null
    ) {
        val data = JSONObject().apply {
            put("targetUserCode", targetUserCode)
            put("envelope", envelopeBase64)
        }
        if (onAck != null) {
            socket?.emit("encrypted-envelope", arrayOf<Any>(data), Ack { args ->
                val resp = args?.firstOrNull() as? JSONObject
                val delivered = resp?.optBoolean("delivered", false) ?: false
                onAck(delivered)
            })
        } else {
            socket?.emit("encrypted-envelope", data)
        }
    }

    /**
     * Disconnects from the signaling server.
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = SignalingConnectionState.DISCONNECTED
    }
}
