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
    var selectedModel: String = "google/gemini-2.0-flash-thinking-exp:free"
    var availableModels: MutableList<String> = mutableListOf(
        "google/gemini-2.0-flash-thinking-exp:free",
        "anthropic/claude-3.7-sonnet",
        "deepseek/deepseek-r1",
        "deepseek/deepseek-r1:free",
        "deepseek/deepseek-chat:free",
        "deepseek/deepseek-r1-distill-llama-70b",
        "google/gemini-2.0-flash-001",
        "meta-llama/llama-3.3-70b-instruct",
        "openai/gpt-3.5-turbo",
        "openai/gpt-4"
    )
    var customModels: MutableList<String> = mutableListOf()

    override fun getState(): LlmPluginSettings = this

    override fun loadState(state: LlmPluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}