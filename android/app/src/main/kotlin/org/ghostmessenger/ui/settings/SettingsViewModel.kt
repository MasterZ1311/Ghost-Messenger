package org.ghostmessenger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.ghostmessenger.data.repository.MessageRepository
import javax.inject.Inject

data class SettingsUiState(
    val signalingUrlInput: String = "",
    val isUrlSaved: Boolean = false,
    val turnUsernameInput: String = "",
    val turnPasswordInput: String = "",
    val turnServerUrlInput: String = "",
    val isTurnSaved: Boolean = false,
    val showPurgeDialog: Boolean = false,
    val isPurging: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val messageRepository: MessageRepository
) : ViewModel() {

    val currentIdentity: StateFlow<Identity?> = messageRepository.currentIdentity

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            signalingUrlInput = securePreferences.getSignalingUrl(),
            turnUsernameInput = securePreferences.getTurnUsername(),
            turnPasswordInput = securePreferences.getTurnPassword(),
            turnServerUrlInput = securePreferences.getTurnServerUrl()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateSignalingUrlInput(url: String) {
        _uiState.value = _uiState.value.copy(signalingUrlInput = url, isUrlSaved = false)
    }

    fun saveSignalingUrl() {
        val url = _uiState.value.signalingUrlInput.trim()
        if (url.isNotBlank()) {
            securePreferences.setSignalingUrl(url)
            _uiState.value = _uiState.value.copy(isUrlSaved = true)
        }
    }

    fun updateTurnUsernameInput(username: String) {
        _uiState.value = _uiState.value.copy(turnUsernameInput = username, isTurnSaved = false)
    }

    fun updateTurnPasswordInput(password: String) {
        _uiState.value = _uiState.value.copy(turnPasswordInput = password, isTurnSaved = false)
    }

    fun updateTurnServerUrlInput(url: String) {
        _uiState.value = _uiState.value.copy(turnServerUrlInput = url, isTurnSaved = false)
    }

    fun saveTurnConfiguration() {
        securePreferences.setTurnUsername(_uiState.value.turnUsernameInput.trim())
        securePreferences.setTurnPassword(_uiState.value.turnPasswordInput.trim())
        if (_uiState.value.turnServerUrlInput.isNotBlank()) {
            securePreferences.setTurnServerUrl(_uiState.value.turnServerUrlInput.trim())
        }
        _uiState.value = _uiState.value.copy(isTurnSaved = true)
    }

    fun openPurgeDialog() {
        _uiState.value = _uiState.value.copy(showPurgeDialog = true)
    }

    fun closePurgeDialog() {
        _uiState.value = _uiState.value.copy(showPurgeDialog = false)
    }

    fun purgeVault(onPurged: () -> Unit) {
        _uiState.value = _uiState.value.copy(isPurging = true, showPurgeDialog = false)
        viewModelScope.launch {
            messageRepository.resetAll()
            _uiState.value = _uiState.value.copy(isPurging = false)
            onPurged()
        }
    }
}
