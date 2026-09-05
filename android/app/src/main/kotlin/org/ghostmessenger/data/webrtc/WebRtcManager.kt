package org.ghostmessenger.data.webrtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages WebRTC PeerConnections and ordered, reliable SCTP DataChannels for P2P messaging.
 */
@Singleton
class WebRtcManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private val iceServers = listOf(
        // Google Public STUN
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),

        // Metered Public STUN
        PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),

        // Metered TURN Relays (UDP & TCP Fallbacks)
        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
            .setUsername("d8b6c3ca05209ab02a917f8b")
            .setPassword("1zz/bX4u3W5ILOXV")
            .createIceServer(),

        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
            .setUsername("d8b6c3ca05209ab02a917f8b")
            .setPassword("1zz/bX4u3W5ILOXV")
            .createIceServer(),

        PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
            .setUsername("d8b6c3ca05209ab02a917f8b")
            .setPassword("1zz/bX4u3W5ILOXV")
            .createIceServer(),

        PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
            .setUsername("d8b6c3ca05209ab02a917f8b")
            .setPassword("1zz/bX4u3W5ILOXV")
            .createIceServer()
    )

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val dataChannels = ConcurrentHashMap<String, DataChannel>()
    private val dataChannelStates = ConcurrentHashMap<String, MutableStateFlow<DataChannel.State>>()

    private val _incomingMessages = MutableSharedFlow<Pair<String, ByteArray>>(extraBufferCapacity = 128)
    val incomingMessages: SharedFlow<Pair<String, ByteArray>> = _incomingMessages.asSharedFlow()

    /**
     * Retrieves or creates a reactive StateFlow for a peer's DataChannel state.
     */
    fun getDataChannelState(remoteUserCode: String): StateFlow<DataChannel.State> {
        return dataChannelStates.getOrPut(remoteUserCode) {
            MutableStateFlow(DataChannel.State.CLOSED)
        }.asStateFlow()
    }

    /**
     * Checks whether a direct P2P DataChannel is currently open with [remoteUserCode].
     */
    fun isDataChannelOpen(remoteUserCode: String): Boolean {
        return dataChannels[remoteUserCode]?.state() == DataChannel.State.OPEN
    }

    /**
     * Sends binary data over the direct P2P DataChannel.
     */
    fun sendData(remoteUserCode: String, data: ByteArray): Boolean {
        val channel = dataChannels[remoteUserCode] ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false

        val buffer = DataChannel.Buffer(ByteBuffer.wrap(data), true)
        return channel.send(buffer)
    }

    /**
     * Creates an SDP Offer to initiate a P2P DataChannel connection.
     */
    suspend fun createOffer(
        remoteUserCode: String,
        onIceCandidate: (IceCandidate) -> Unit
    ): SessionDescription {
        val pc = getOrCreatePeerConnection(remoteUserCode, onIceCandidate)
        createDataChannel(remoteUserCode, pc)

        val constraints = MediaConstraints()
        val offer = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    cont.resume(desc)
                }

                override fun onCreateFailure(error: String) {
                    cont.resumeWithException(Exception("Failed to create offer: $error"))
                }
            }, constraints)
        }

        suspendCancellableCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    cont.resumeWithException(Exception("Failed to set local description: $error"))
                }
            }, offer)
        }

        return offer
    }

    /**
     * Creates an SDP Answer in response to an incoming offer.
     */
    suspend fun createAnswer(
        remoteUserCode: String,
        offerSdp: String,
        onIceCandidate: (IceCandidate) -> Unit
    ): SessionDescription {
        val pc = getOrCreatePeerConnection(remoteUserCode, onIceCandidate)

        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    cont.resumeWithException(Exception("Failed to set remote description: $error"))
                }
            }, offerDesc)
        }

        val constraints = MediaConstraints()
        val answer = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createAnswer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription) {
                    cont.resume(desc)
                }

                override fun onCreateFailure(error: String) {
                    cont.resumeWithException(Exception("Failed to create answer: $error"))
                }
            }, constraints)
        }

        suspendCancellableCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    cont.resumeWithException(Exception("Failed to set local description: $error"))
                }
            }, answer)
        }

        return answer
    }

    /**
     * Sets the remote answer received from the peer.
     */
    suspend fun setRemoteAnswer(remoteUserCode: String, answerSdp: String) {
        val pc = peerConnections[remoteUserCode]
            ?: throw IllegalStateException("PeerConnection not found for $remoteUserCode")

        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String) {
                    cont.resumeWithException(Exception("Failed to set remote answer: $error"))
                }
            }, answerDesc)
        }
    }

    /**
     * Adds an ICE candidate received via signaling from the peer.
     */
    fun addIceCandidate(remoteUserCode: String, iceCandidate: IceCandidate) {
        peerConnections[remoteUserCode]?.addIceCandidate(iceCandidate)
    }

    /**
     * Gets or initializes the PeerConnection for [remoteUserCode].
     */
    fun getOrCreatePeerConnection(
        remoteUserCode: String,
        onIceCandidate: (IceCandidate) -> Unit
    ): PeerConnection {
        return peerConnections.getOrPut(remoteUserCode) {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val observer = object : PeerConnectionObserverAdapter() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    onIceCandidate(candidate)
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    setupDataChannel(remoteUserCode, dataChannel)
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    if (newState == PeerConnection.PeerConnectionState.FAILED ||
                        newState == PeerConnection.PeerConnectionState.CLOSED
                    ) {
                        dataChannelStates[remoteUserCode]?.value = DataChannel.State.CLOSED
                    }
                }
            }

            factory.createPeerConnection(rtcConfig, observer)
                ?: throw IllegalStateException("Failed to create PeerConnection")
        }
    }

    private fun createDataChannel(remoteUserCode: String, pc: PeerConnection): DataChannel {
        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = -1 // Reliable SCTP
        }
        val channel = pc.createDataChannel("ghost_p2p", init)
        setupDataChannel(remoteUserCode, channel)
        return channel
    }

    private fun setupDataChannel(remoteUserCode: String, channel: DataChannel) {
        dataChannels[remoteUserCode]?.close()
        dataChannels[remoteUserCode] = channel

        val stateFlow = dataChannelStates.getOrPut(remoteUserCode) {
            MutableStateFlow(channel.state())
        }
        stateFlow.value = channel.state()

        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                stateFlow.value = channel.state()
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                scope.launch {
                    _incomingMessages.emit(remoteUserCode to data)
                }
            }
        })
    }

    /**
     * Closes the P2P connection to [remoteUserCode].
     */
    fun closePeerConnection(remoteUserCode: String) {
        dataChannels.remove(remoteUserCode)?.close()
        peerConnections.remove(remoteUserCode)?.close()
        dataChannelStates[remoteUserCode]?.value = DataChannel.State.CLOSED
    }

    /**
     * Closes all active connections.
     */
    fun closeAll() {
        for ((code, channel) in dataChannels) {
            channel.close()
        }
        dataChannels.clear()

        for ((code, pc) in peerConnections) {
            pc.close()
        }
        peerConnections.clear()

        for ((code, stateFlow) in dataChannelStates) {
            stateFlow.value = DataChannel.State.CLOSED
        }
    }
}

/**
 * Default empty adapter for SdpObserver callbacks.
 */
abstract class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}

/**
 * Default empty adapter for PeerConnection.Observer callbacks.
 */
abstract class PeerConnectionObserverAdapter : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: org.webrtc.MediaStream) {}
    override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
    override fun onDataChannel(dataChannel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
}
