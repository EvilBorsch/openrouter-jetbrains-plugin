package com.example.llmplugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import javax.swing.*

/**
 * Provides controller functionality for application settings.
 */
class LlmPluginSettingsConfigurable : Configurable {
    private var settingsComponent: LlmPluginSettingsComponent? = null

    override fun getDisplayName(): String = "LLM Plugin Settings"

    override fun getPreferredFocusedComponent(): JComponent? {
        return settingsComponent?.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        settingsComponent = LlmPluginSettingsComponent()
        return settingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings = service<LlmPluginSettings>()
        val panel = settingsComponent!!

        return panel.getOpenRouterBaseUrl() != settings.openRouterBaseUrl ||
                panel.getOpenRouterApiKey() != settings.openRouterApiKey ||
                panel.getSelectedModel() != settings.selectedModel ||
                panel.isIncludeMessageHistory() != settings.includeMessageHistory
    }

    override fun apply() {
        val settings = service<LlmPluginSettings>()
        val panel = settingsComponent!!

        settings.openRouterBaseUrl = panel.getOpenRouterBaseUrl()
        settings.openRouterApiKey = panel.getOpenRouterApiKey()
        settings.selectedModel = panel.getSelectedModel()
        settings.includeMessageHistory = panel.isIncludeMessageHistory()
    }

    override fun reset() {
        val settings = service<LlmPluginSettings>()
        val panel = settingsComponent!!

        panel.setOpenRouterBaseUrl(settings.openRouterBaseUrl)
        panel.setOpenRouterApiKey(settings.openRouterApiKey)
        panel.setSelectedModel(settings.selectedModel)
        panel.setIncludeMessageHistory(settings.includeMessageHistory)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
