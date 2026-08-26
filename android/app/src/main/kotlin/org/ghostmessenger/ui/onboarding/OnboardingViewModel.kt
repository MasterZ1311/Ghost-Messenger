package org.ghostmessenger.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ghostmessenger.core.crypto.KeyManager
import org.ghostmessenger.core.model.Identity
import org.ghostmessenger.data.local.prefs.SecurePreferences
import org.ghostmessenger.data.repository.MessageRepository
import javax.inject.Inject

enum class OnboardingStep {
    LANDING,
    GENERATE_NEW,
    RESTORE_EXISTING
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.LANDING,
    val generatedMnemonicWords: List<String> = emptyList(),
    val generatedIdentity: Identity? = null,
    val restoreInput: String = "",
    val restoreError: String? = null,
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectGenerateNew() {
        val identity = KeyManager.createRandomIdentity()
        _uiState.value = _uiState.value.copy(
            step = OnboardingStep.GENERATE_NEW,
            generatedMnemonicWords = identity.mnemonicWords,
            generatedIdentity = identity,
            errorMessage = null
        )
    }

    fun selectRestore() {
        _uiState.value = _uiState.value.copy(
            step = OnboardingStep.RESTORE_EXISTING,
            restoreInput = "",
            restoreError = null,
            errorMessage = null
        )
    }

    fun setStep(step: OnboardingStep) {
        _uiState.value = _uiState.value.copy(step = step, errorMessage = null)
    }

    fun updateRestoreInput(text: String) {
        val clean = text.lowercase().replace("\n", " ").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val error = if (words.isNotEmpty() && words.size != 12) {
            "Requires exactly 12 words (${words.size}/12 entered)"
        } else if (words.size == 12 && !KeyManager.validateMnemonic(clean)) {
            "Invalid mnemonic checksum or wordlist"
        } else {
            null
        }

        _uiState.value = _uiState.value.copy(
            restoreInput = text,
            restoreError = error
        )
    }

    fun confirmGeneratedIdentity(onSuccess: () -> Unit) {
        val identity = _uiState.value.generatedIdentity ?: return
        initializeWithIdentity(identity, onSuccess)
    }

    fun confirmRestoreIdentity(onSuccess: () -> Unit) {
        val input = _uiState.value.restoreInput.trim()
        try {
            val identity = KeyManager.deriveIdentity(input)
            initializeWithIdentity(identity, onSuccess)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                restoreError = "Failed to derive keys: ${e.message}"
            )
        }
    }

    private fun initializeWithIdentity(identity: Identity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = messageRepository.initializeIdentity(identity)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isInitialized = true
                )
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to initialize identity"
                )
            }
        }
    }
}
