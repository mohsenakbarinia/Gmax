package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GameSettings
import com.example.data.HarvestEvent
import com.example.data.SettingsRepository
import com.example.data.TrophyRecord
import com.example.data.TrophyRepository
import com.example.data.UiState
import com.example.data.UserProfileStats
import com.example.engine.GameEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WildwoodViewModel(
    private val engine: GameEngine,
    private val settingsRepo: SettingsRepository,
    private val trophyRepo: TrophyRepository
) : ViewModel() {

    val settings: StateFlow<GameSettings> = settingsRepo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = GameSettings()
        )

    val statsState: StateFlow<UiState<UserProfileStats>> = trophyRepo.observeStats()
        .map<UserProfileStats, UiState<UserProfileStats>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Failed to sync hunter stats"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val trophiesState: StateFlow<UiState<List<TrophyRecord>>> = trophyRepo.observeTrophies()
        .map<List<TrophyRecord>, UiState<List<TrophyRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(UiState.Error(rootError.message ?: "Failed to sync trophy log"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        viewModelScope.launch {
            settings.collect { next ->
                engine.applySettings(next)
            }
        }

        viewModelScope.launch {
            statsState.collect { st ->
                if (st is UiState.Success) {
                    engine.activeContractIndex = st.data.activeContractIndex.toInt()
                }
            }
        }

        engine.onHarvest = { event, contractCompleted ->
            persistHarvest(event, contractCompleted)
        }
    }

    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        viewModelScope.launch {
            settingsRepo.update(transform)
        }
    }

    private fun persistHarvest(event: HarvestEvent, contractCompleted: Boolean) {
        val currentStats = (statsState.value as? UiState.Success)?.data ?: UserProfileStats()
        viewModelScope.launch {
            val result = trophyRepo.recordHarvest(event, currentStats, contractCompleted)
            if (result.isFailure) {
                _syncMessage.value = "Harvest saved locally; cloud sync retry pending."
            }
        }
    }

    fun deleteTrophy(trophyId: String) {
        viewModelScope.launch {
            val res = trophyRepo.deleteTrophy(trophyId)
            if (res.isFailure) {
                _syncMessage.value = "Could not delete trophy right now."
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        engine.onHarvest = null
    }

    class Factory(
        private val context: Context,
        private val engine: GameEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            return WildwoodViewModel(
                engine = engine,
                settingsRepo = SettingsRepository(appCtx),
                trophyRepo = TrophyRepository(appCtx)
            ) as T
        }
    }
}
