package com.example.llmplugin.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Supports creating and managing a JPanel for the Settings Dialog.
 */
class LlmPluginSettingsComponent {
    private val openRouterBaseUrlField = JBTextField()
    private val openRouterApiKeyField = JBPasswordField()
    private val modelComboBox = JComboBox(arrayOf(
        "openai/gpt-3.5-turbo",
        "openai/gpt-4",
        "anthropic/claude-3-opus",
        "anthropic/claude-3-sonnet",
        "anthropic/claude-3-haiku",
        "google/gemini-pro"
    ))

    val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("OpenRouter Base URL:"), openRouterBaseUrlField, 1, false)
        .addLabeledComponent(JBLabel("OpenRouter API Key:"), openRouterApiKeyField, 1, false)
        .addLabeledComponent(JBLabel("Model:"), modelComboBox, 1, false)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    val preferredFocusedComponent: JComponent
        get() = openRouterBaseUrlField

    fun getOpenRouterBaseUrl(): String = openRouterBaseUrlField.text

    fun setOpenRouterBaseUrl(url: String) {
        openRouterBaseUrlField.text = url
    }

    fun getOpenRouterApiKey(): String = String(openRouterApiKeyField.password)

    fun setOpenRouterApiKey(apiKey: String) {
        openRouterApiKeyField.text = apiKey
    }

    fun getSelectedModel(): String = modelComboBox.selectedItem as String

    fun setSelectedModel(model: String) {
        modelComboBox.selectedItem = model
    }
}
