package app.pwhs.blockads.ui.config

import app.pwhs.blockads.data.entities.ConfigProfile

data class ConfigUiState(
    val configs: List<ConfigProfile> = emptyList(),
    val activeConfig: ConfigProfile? = null,
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingConfig: ConfigProfile? = null,
)

sealed interface ConfigUiIntent {
    data class SelectActive(val configId: Long) : ConfigUiIntent
    data class AddRemoteConfig(val name: String, val url: String) : ConfigUiIntent
    data class AddLocalConfig(val name: String, val content: String) : ConfigUiIntent
    data class UpdateConfig(val configId: Long, val name: String, val content: String) : ConfigUiIntent
    data class DeleteConfig(val config: ConfigProfile) : ConfigUiIntent
    data object ShowAddDialog : ConfigUiIntent
    data object DismissAddDialog : ConfigUiIntent
    data class EditConfig(val config: ConfigProfile) : ConfigUiIntent
    data object DismissEditDialog : ConfigUiIntent
    data class RefreshRemote(val configId: Long) : ConfigUiIntent
}

sealed interface ConfigUiEffect {
    data class ShowToast(val messageRes: Int) : ConfigUiEffect
    data class ShowMessage(val message: String) : ConfigUiEffect
}
