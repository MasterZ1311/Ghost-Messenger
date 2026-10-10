package org.ghostmessenger.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.local.entities.MessageEntity
import org.ghostmessenger.data.repository.MessageRepository
import org.ghostmessenger.data.repository.SecurityEvent
import org.ghostmessenger.data.webrtc.WebRtcManager
import org.webrtc.DataChannel
import javax.inject.Inject

data class ChatUiState(
    val messageInput: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null,
    /**
     * Non-null when a contact's identity key changed unexpectedly (TOFU violation).
     * The UI must display this as a dismissable security warning banner.
     */
    val securityWarning: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val webRtcManager: WebRtcManager
) : ViewModel() {

    val recipientUserCode: String = checkNotNull(savedStateHandle["userCode"])

    val messages: StateFlow<List<MessageEntity>> = messageRepository.getMessages(recipientUserCode)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversation: StateFlow<ConversationEntity?> = messageRepository.getConversation(recipientUserCode)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dataChannelState: StateFlow<DataChannel.State> = webRtcManager.getDataChannelState(recipientUserCode)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Mark conversation as read on enter
        markAsRead()
        // Proactively attempt WebRTC P2P direct connection
        messageRepository.initiateP2PConnection(recipientUserCode)
        // Security (F4): observe identity-change events for this conversation partner
        viewModelScope.launch {
            messageRepository.securityEvents.collect { event ->
                if (event is SecurityEvent.IdentityChanged && event.peerUserCode == recipientUserCode) {
                    _uiState.value = _uiState.value.copy(
                        securityWarning = "\u26a0\ufe0f Security code changed for ${event.peerUserCode}. " +
                            "This message was rejected. Verify this contact before continuing."
                    )
                }
            }
        }
    }

    fun updateInput(input: String) {
        _uiState.value = _uiState.value.copy(messageInput = input, sendError = null)
    }

    fun dismissSecurityWarning() {
        _uiState.value = _uiState.value.copy(securityWarning = null)
    }

    fun sendMessage() {
        val content = _uiState.value.messageInput.trim()
        if (content.isBlank()) return

        _uiState.value = _uiState.value.copy(messageInput = "", isSending = true, sendError = null)

        viewModelScope.launch {
            val result = messageRepository.sendMessage(recipientUserCode, content)
            _uiState.value = _uiState.value.copy(
                isSending = false,
                sendError = if (result.isFailure) "Message failed to send. Tap to retry." else null
            )
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            val result = messageRepository.retrySendMessage(messageId)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    sendError = "Retry failed. Check network connection."
                )
            }
        }
    }

    fun markAsRead() {
        viewModelScope.launch {
            messageRepository.markConversationAsRead(recipientUserCode)
        }
    }
}
