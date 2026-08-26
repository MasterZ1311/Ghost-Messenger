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
import org.ghostmessenger.data.webrtc.WebRtcManager
import org.webrtc.DataChannel
import javax.inject.Inject

data class ChatUiState(
    val messageInput: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null
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
    }

    fun updateInput(input: String) {
        _uiState.value = _uiState.value.copy(messageInput = input, sendError = null)
    }

    fun sendMessage() {
        val content = _uiState.value.messageInput.trim()
        if (content.isBlank()) return

        _uiState.value = _uiState.value.copy(isSending = true, sendError = null)

        viewModelScope.launch {
            val result = messageRepository.sendMessage(recipientUserCode, content)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    messageInput = "",
                    isSending = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    sendError = result.exceptionOrNull()?.message ?: "Failed to send message"
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
