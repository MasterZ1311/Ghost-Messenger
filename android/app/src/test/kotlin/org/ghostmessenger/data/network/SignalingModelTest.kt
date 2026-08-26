package org.ghostmessenger.data.network

import org.ghostmessenger.data.network.model.PresenceUpdate
import org.ghostmessenger.data.network.model.SignalingAnswer
import org.ghostmessenger.data.network.model.SignalingConnectionState
import org.ghostmessenger.data.network.model.SignalingEnvelope
import org.ghostmessenger.data.network.model.SignalingIceCandidate
import org.ghostmessenger.data.network.model.SignalingOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalingModelTest {

    @Test
    fun testSignalingOfferAndAnswer() {
        val offer = SignalingOffer(
            fromUserCode = "5JKL-2P4X",
            sdp = "v=0...offer_sdp...",
            type = "offer"
        )
        assertEquals("5JKL-2P4X", offer.fromUserCode)
        assertEquals("v=0...offer_sdp...", offer.sdp)
        assertEquals("offer", offer.type)

        val answer = SignalingAnswer(
            fromUserCode = "WXYZ-2345",
            sdp = "v=0...answer_sdp...",
            type = "answer"
        )
        assertEquals("WXYZ-2345", answer.fromUserCode)
        assertEquals("v=0...answer_sdp...", answer.sdp)
        assertEquals("answer", answer.type)
    }

    @Test
    fun testSignalingIceCandidateAndEnvelope() {
        val candidate = SignalingIceCandidate(
            fromUserCode = "5JKL-2P4X",
            candidate = "candidate:1 1 UDP 2122260223 192.168.1.100 50000 typ host",
            sdpMid = "0",
            sdpMLineIndex = 0
        )
        assertEquals("5JKL-2P4X", candidate.fromUserCode)
        assertEquals("0", candidate.sdpMid)
        assertEquals(0, candidate.sdpMLineIndex)

        val envelope = SignalingEnvelope(
            fromUserCode = "5JKL-2P4X",
            envelope = "BASE64_CIPHERTEXT"
        )
        assertEquals("5JKL-2P4X", envelope.fromUserCode)
        assertEquals("BASE64_CIPHERTEXT", envelope.envelope)

        val presence = PresenceUpdate(targetUserCode = "WXYZ-2345", online = true)
        assertEquals("WXYZ-2345", presence.targetUserCode)
        assertTrue(presence.online)

        val state = SignalingConnectionState.CONNECTED
        assertEquals(SignalingConnectionState.CONNECTED, state)
    }
}
