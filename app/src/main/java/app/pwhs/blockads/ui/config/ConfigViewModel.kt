package app.pwhs.blockads.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.blockads.R
import app.pwhs.blockads.data.dao.ConfigDao
import app.pwhs.blockads.data.entities.ConfigProfile
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ConfigViewModel(
    private val configDao: ConfigDao,
    private val client: HttpClient,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ConfigUiState(isLoading = true))
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ConfigUiEffect>()
    val effects: SharedFlow<ConfigUiEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            seedDefaultConfigIfNeeded()
            combine(
                configDao.getAllFlow(),
                configDao.getActiveFlow()
            ) { configs, active ->
                _uiState.update { current ->
                    current.copy(
                        configs = configs,
                        activeConfig = active ?: configs.firstOrNull(),
                        isLoading = false
                    )
                }
            }.collect {}
        }
    }

    fun onIntent(intent: ConfigUiIntent) {
        when (intent) {
            is ConfigUiIntent.SelectActive -> selectActive(intent.configId)
            is ConfigUiIntent.AddRemoteConfig -> addRemoteConfig(intent.name, intent.url)
            is ConfigUiIntent.AddLocalConfig -> addLocalConfig(intent.name, intent.content)
            is ConfigUiIntent.UpdateConfig -> updateConfig(intent.configId, intent.name, intent.content)
            is ConfigUiIntent.DeleteConfig -> deleteConfig(intent.config)
            is ConfigUiIntent.ShowAddDialog -> _uiState.update { it.copy(showAddDialog = true) }
            is ConfigUiIntent.DismissAddDialog -> _uiState.update { it.copy(showAddDialog = false) }
            is ConfigUiIntent.EditConfig -> _uiState.update { it.copy(editingConfig = intent.config) }
            is ConfigUiIntent.DismissEditDialog -> _uiState.update { it.copy(editingConfig = null) }
            is ConfigUiIntent.RefreshRemote -> refreshRemoteConfig(intent.configId)
        }
    }

    private suspend fun seedDefaultConfigIfNeeded() = withContext(Dispatchers.IO) {
        if (configDao.getCount() == 0) {
            val defaultConfig = ConfigProfile(
                name = ConfigProfile.DEFAULT_NAME,
                content = "# Quantumult X default configuration\n[general]\n\n[dns]\nserver = 1.1.1.1\nserver = 8.8.8.8\n\n[filter_local]\nfinal, direct\n",
                isActive = true,
                isBuiltIn = true
            )
            configDao.insert(defaultConfig)
        }
    }

    private fun selectActive(configId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            configDao.setActive(configId)
            _effects.emit(ConfigUiEffect.ShowToast(R.string.config_activated))
        }
    }

    private fun addRemoteConfig(name: String, url: String) {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        if (trimmedName.isEmpty() || trimmedUrl.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, showAddDialog = false) }
            try {
                val content = withContext(Dispatchers.IO) {
                    client.get(trimmedUrl).bodyAsText()
                }
                val newConfig = ConfigProfile(
                    name = trimmedName,
                    content = content,
                    remoteUrl = trimmedUrl,
                    lastUpdated = System.currentTimeMillis(),
                    isActive = false
                )
                withContext(Dispatchers.IO) {
                    configDao.insert(newConfig)
                }
                _effects.emit(ConfigUiEffect.ShowToast(R.string.config_added))
            } catch (e: Exception) {
                Timber.e(e, "Failed to download remote config")
                _effects.emit(ConfigUiEffect.ShowToast(R.string.config_fetch_failed))
            } finally {
                _uiState.update { it.copy(isUpdating = false) }
            }
        }
    }

    private fun addLocalConfig(name: String, content: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val newConfig = ConfigProfile(
                name = trimmedName,
                content = content,
                isActive = false
            )
            configDao.insert(newConfig)
            _uiState.update { it.copy(showAddDialog = false) }
            _effects.emit(ConfigUiEffect.ShowToast(R.string.config_added))
        }
    }

    private fun updateConfig(configId: Long, name: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = configDao.getById(configId) ?: return@launch
            configDao.update(existing.copy(name = name.trim(), content = content))
            _uiState.update { it.copy(editingConfig = null) }
            _effects.emit(ConfigUiEffect.ShowToast(R.string.config_updated))
        }
    }

    private fun deleteConfig(config: ConfigProfile) {
        if (config.isBuiltIn) return
        viewModelScope.launch(Dispatchers.IO) {
            configDao.delete(config)
            if (config.isActive) {
                configDao.getAll().firstOrNull()?.let { configDao.setActive(it.id) }
            }
            _effects.emit(ConfigUiEffect.ShowToast(R.string.config_deleted))
        }
    }

    private fun refreshRemoteConfig(configId: Long) {
        viewModelScope.launch {
            val config = withContext(Dispatchers.IO) { configDao.getById(configId) } ?: return@launch
            val url = config.remoteUrl ?: return@launch

            _uiState.update { it.copy(isUpdating = true) }
            try {
                val content = withContext(Dispatchers.IO) {
                    client.get(url).bodyAsText()
                }
                withContext(Dispatchers.IO) {
                    configDao.update(
                        config.copy(
                            content = content,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
                _effects.emit(ConfigUiEffect.ShowToast(R.string.config_updated))
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh remote config")
                _effects.emit(ConfigUiEffect.ShowToast(R.string.config_fetch_failed))
            } finally {
                _uiState.update { it.copy(isUpdating = false) }
            }
        }
    }
}
