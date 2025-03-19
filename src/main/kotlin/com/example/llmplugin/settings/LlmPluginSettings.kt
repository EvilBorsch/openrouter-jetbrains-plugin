package com.example.llmplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for the LLM Plugin.
 * Stores OpenRouter API configuration.
 */
@State(
    name = "com.example.llmplugin.settings.LlmPluginSettings",
    storages = [Storage("LlmPluginSettings.xml")]
)
class LlmPluginSettings : PersistentStateComponent<LlmPluginSettings> {
    var openRouterBaseUrl: String = "https://openrouter.ai/api/v1"
    var openRouterApiKey: String = ""
    var selectedModel: String = "openai/gpt-3.5-turbo"

    override fun getState(): LlmPluginSettings = this

    override fun loadState(state: LlmPluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
