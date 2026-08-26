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
        SettingsUiState(signalingUrlInput = securePreferences.getSignalingUrl())
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
