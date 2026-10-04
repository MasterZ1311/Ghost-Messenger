package org.ghostmessenger.ui.verification

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ghostmessenger.core.crypto.SafetyNumberGenerator
import org.ghostmessenger.core.crypto.SignalCryptoManager
import org.ghostmessenger.data.local.dao.ConversationDao
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.signal.libsignal.protocol.SignalProtocolAddress
import javax.inject.Inject

data class VerificationUiState(
    val recipientUserCode: String = "",
    val safetyNumber: String? = null,
    val isVerified: Boolean = false,
    val hasRemoteSession: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val securePreferences: SecurePreferences,
    private val signalCryptoManager: SignalCryptoManager,
    private val conversationDao: ConversationDao
) : ViewModel() {

    val recipientUserCode: String = checkNotNull(savedStateHandle["userCode"])

    private val _uiState = MutableStateFlow(VerificationUiState(recipientUserCode = recipientUserCode))
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    init {
        loadVerificationData()
    }

    fun loadVerificationData() {
        viewModelScope.launch {
            val localIdentity = securePreferences.getIdentity()
            val remoteAddress = SignalProtocolAddress(recipientUserCode, 1)
            val remoteIdentityKey = signalCryptoManager.signalProtocolStore.getIdentity(remoteAddress)
            val conv = conversationDao.getConversation(recipientUserCode)

            if (localIdentity != null && remoteIdentityKey != null) {
                val localPublicKeyBytes = localIdentity.identityKeyPair.publicKey.publicKey.serialize()
                val remotePublicKeyBytes = remoteIdentityKey.publicKey.serialize()
                val safetyNumber = SafetyNumberGenerator.generateSafetyNumber(
                    localUserCode = localIdentity.userCode,
                    localIdentityKey = localPublicKeyBytes,
                    remoteUserCode = recipientUserCode,
                    remoteIdentityKey = remotePublicKeyBytes
                )
                _uiState.value = _uiState.value.copy(
                    safetyNumber = safetyNumber,
                    isVerified = conv?.isVerified == true,
                    hasRemoteSession = true,
                    statusMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    safetyNumber = null,
                    isVerified = conv?.isVerified == true,
                    hasRemoteSession = false,
                    statusMessage = "No active cryptographic session established with this peer yet. Exchange at least one message first to establish Double Ratchet keys."
                )
            }
        }
    }

    fun toggleVerification() {
        val newStatus = !_uiState.value.isVerified
        viewModelScope.launch {
            conversationDao.setVerified(recipientUserCode, newStatus)
            _uiState.value = _uiState.value.copy(isVerified = newStatus)
        }
    }
}
