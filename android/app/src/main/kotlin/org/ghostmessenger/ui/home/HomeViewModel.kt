package org.ghostmessenger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ghostmessenger.core.crypto.UserCodeUtils
import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.local.entities.ConversationEntity
import org.ghostmessenger.data.network.model.SignalingConnectionState
import org.ghostmessenger.data.repository.MessageRepository
import javax.inject.Inject

data class HomeUiState(
    val showAddContactDialog: Boolean = false,
    val addContactInput: String = "",
    val addContactError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val messageRepository: MessageRepository
) : ViewModel() {

    val currentIdentity: StateFlow<Identity?> = messageRepository.currentIdentity
    val signalingState: StateFlow<SignalingConnectionState> = messageRepository.signalingState

    val conversations: StateFlow<List<ConversationEntity>> = messageRepository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun openAddContactDialog() {
        _uiState.value = _uiState.value.copy(
            showAddContactDialog = true,
            addContactInput = "",
            addContactError = null
        )
    }

    fun closeAddContactDialog() {
        _uiState.value = _uiState.value.copy(showAddContactDialog = false)
    }

    fun updateAddContactInput(input: String) {
        _uiState.value = _uiState.value.copy(
            addContactInput = input.uppercase(),
            addContactError = null
        )
    }

    fun addContact(onSuccess: (String) -> Unit) {
        val input = _uiState.value.addContactInput.trim()

        if (!UserCodeUtils.isValidFormat(input)) {
            _uiState.value = _uiState.value.copy(
                addContactError = "Invalid UserCode format. Expected: XXXX-XXXX (Base32)"
            )
            return
        }

        val normalized = UserCodeUtils.normalize(input)
        if (normalized == currentIdentity.value?.userCode) {
            _uiState.value = _uiState.value.copy(
                addContactError = "You cannot start a conversation with yourself"
            )
            return
        }

        _uiState.value = _uiState.value.copy(showAddContactDialog = false)
        onSuccess(normalized)
    }

    fun deleteConversation(userCode: String) {
        viewModelScope.launch {
            messageRepository.deleteConversation(userCode)
        }
    }
}
