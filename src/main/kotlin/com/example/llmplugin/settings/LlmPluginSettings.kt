package com.example.llmplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.Serializable

/**
 * Data class to represent a chat message for persistence
 */
data class ChatMessage(
    var role: String = "",
    var content: String = "",
    var cost: Double? = null,
    var generationId: String? = null
) : Serializable

/**
 * Data class to represent a chat with its messages for persistence
 */
data class ChatData(
    var id: String = "",
    var name: String = "",
    var messages: MutableList<ChatMessage> = mutableListOf()
) : Serializable

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
    
    // Chat context settings
    var includeMessageHistory: Boolean = false // Whether to include previous messages in context
    var currentChatId: String = "default" // Current active chat ID
    var chats: MutableList<ChatData> = mutableListOf(ChatData(id = "default", name = "Default Chat"))
    
    // Helper function to get all chat IDs
    fun getChatIds(): List<String> {
        return chats.map { it.id }
    }
    
    // Helper function to get a chat by ID
    fun getChatById(id: String): ChatData? {
        return chats.find { it.id == id }
    }
    
    // Helper function to add a new chat
    fun addChat(id: String, name: String): ChatData {
        val newChat = ChatData(id = id, name = name)
        chats.add(newChat)
        return newChat
    }
    
    // Helper function to delete a chat
    fun deleteChat(id: String): Boolean {
        if (id == "default") return false // Don't allow deleting the default chat
        val removed = chats.removeIf { it.id == id }
        if (removed && currentChatId == id) {
            currentChatId = "default" // Switch to default chat if the current one is deleted
        }
        return removed
    }
    
    var availableModels: MutableList<String> = mutableListOf(
        "google/gemini-2.0-flash-thinking-exp:free",
        "anthropic/claude-3.7-sonnet",
        "anthropic/claude-3.7-sonnet:thinking",
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
