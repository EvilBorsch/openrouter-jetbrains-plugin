package com.example.llmplugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Supports creating and managing a JPanel for the Settings Dialog.
 */
class LlmPluginSettingsComponent {
    private val openRouterBaseUrlField = JBTextField()
    private val openRouterApiKeyField = JBPasswordField()
    private val modelComboBox = JComboBox<String>()
    private val addModelButton = JButton("Add Model")
    private val removeModelButton = JButton("Remove Model")
    private val modelListModel = DefaultListModel<String>()
    private val modelList = JList(modelListModel)

    private val settings = service<LlmPluginSettings>()

    init {
        // Initialize the model combo box with available models
        updateModelComboBox()

        // Set up the add model button
        addModelButton.addActionListener {
            val dialog = AddModelDialog()
            if (dialog.showAndGet()) {
                val modelName = dialog.getModelName()
                if (modelName.isNotEmpty() && !settings.availableModels.contains(modelName) && !settings.customModels.contains(modelName)) {
                    settings.customModels.add(modelName)
                    updateModelComboBox()
                }
            }
        }

        // Set up the remove model button
        removeModelButton.addActionListener {
            val selectedValue = modelList.selectedValue
            if (selectedValue != null && settings.customModels.contains(selectedValue)) {
                settings.customModels.remove(selectedValue)
                updateModelComboBox()
            }
        }

        // Configure the model list
        modelList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        updateModelList()
    }

    /**
     * Updates the model combo box with all available models and custom models.
     */
    private fun updateModelComboBox() {
        modelComboBox.removeAllItems()

        // Add all available models
        for (model in settings.availableModels) {
            modelComboBox.addItem(model)
        }

        // Add custom models
        for (model in settings.customModels) {
            modelComboBox.addItem(model)
        }

        // Select the current model
        modelComboBox.selectedItem = settings.selectedModel

        // Update the model list
        updateModelList()
    }

    /**
     * Updates the model list display
     */
    private fun updateModelList() {
        modelListModel.clear()

        // Add custom models to the list
        for (model in settings.customModels) {
            modelListModel.addElement(model)
        }
    }

    val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("OpenRouter Base URL:"), openRouterBaseUrlField, 1, false)
        .addLabeledComponent(JBLabel("OpenRouter API Key:"), openRouterApiKeyField, 1, false)
        .addLabeledComponent(JBLabel("Model:"), modelComboBox, 1, false)
        .addSeparator()
        .addLabeledComponent(JBLabel("Custom Models:"), JScrollPane(modelList).apply {
            preferredSize = Dimension(300, 100)
        }, 1, false)
        .addComponentToRightColumn(
            JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(addModelButton)
                add(removeModelButton)
            }
        )
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

    /**
     * Dialog for adding a new model
     */
    private inner class AddModelDialog : DialogWrapper(true) {
        private val modelNameField = JBTextField()

        init {
            title = "Add Custom Model"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            panel.preferredSize = Dimension(400, 70)
            panel.border = EmptyBorder(10, 10, 10, 10)

            val label = JBLabel("Model Name:")
            panel.add(label, BorderLayout.NORTH)
            panel.add(modelNameField, BorderLayout.CENTER)

            return panel
        }

        fun getModelName(): String = modelNameField.text.trim()
    }
}