package org.ghostmessenger.data.network.model

/**
 * Socket.IO Connection State.
 */
enum class SignalingConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/**
 * Incoming WebRTC SDP Offer from a peer.
 */
data class SignalingOffer(
    val fromUserCode: String,
    val sdp: String,
    val type: String = "offer"
)

/**
 * Incoming WebRTC SDP Answer from a peer.
 */
data class SignalingAnswer(
    val fromUserCode: String,
    val sdp: String,
    val type: String = "answer"
)

/**
 * Incoming WebRTC ICE Candidate from a peer.
 */
data class SignalingIceCandidate(
    val fromUserCode: String,
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int
)

/**
 * Incoming Ephemeral Encrypted Message Envelope (Relay fallback).
 */
data class SignalingEnvelope(
    val fromUserCode: String,
    val envelope: String
)

/**
 * Presence update for a given userCode.
 */
data class PresenceUpdate(
    val targetUserCode: String,
    val online: Boolean
)
