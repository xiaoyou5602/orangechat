package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun addCustomTheme(theme: me.rerere.rikkahub.ui.theme.CustomTheme) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(customThemes = settings.customThemes + theme)
            }
        }
    }

    fun updateCustomTheme(theme: me.rerere.rikkahub.ui.theme.CustomTheme) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    customThemes = settings.customThemes.map {
                        if (it.id == theme.id) theme else it
                    }
                )
            }
        }
    }

    fun deleteCustomTheme(themeId: String) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                val ns = settings.copy(customThemes = settings.customThemes.filter { it.id != themeId })
                if (settings.themeId == themeId) ns.copy(themeId = me.rerere.rikkahub.ui.theme.PresetThemes[0].id) else ns
            }
        }
    }
}
