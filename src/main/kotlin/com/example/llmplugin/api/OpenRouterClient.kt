package com.example.llmplugin.api

import com.example.llmplugin.settings.ChatData
import com.example.llmplugin.settings.LlmPluginSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID

/**
 * Client for communicating with the OpenRouter API.
 */
class OpenRouterClient {
    private val client = OkHttpClient.Builder().build()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    /**
     * Callback interface for handling responses.
     */
    interface ResponseCallback {
        fun onStart()
        fun onToken(token: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }
    
    /**
     * Data class for parsing the chat completion response.
     */
    data class ChatCompletion(
        val id: String,
        val choices: List<ChatCompletionChoice>
    )
    
    /**
     * Data class for parsing the choice in the chat completion.
     */
    data class ChatCompletionChoice(
        val index: Int,
        val message: ChatCompletionMessage
    )
    
    /**
     * Data class for parsing the message in the choice.
     */
    data class ChatCompletionMessage(
        val role: String,
        val content: String
    )
    
    // We'll use the settings service to store chat histories
    private val settings = service<LlmPluginSettings>()
    
    /**
     * Sends a prompt to the OpenRouter API and handles the response.
     */
    fun sendPrompt(
        project: Project,
        prompt: String,
        fileContents: Map<String, String>,
        callback: ResponseCallback
    ) {
        val settings = service<LlmPluginSettings>()
        
        if (settings.openRouterApiKey.isBlank()) {
            callback.onError("OpenRouter API key is not configured. Please set it in the plugin settings.")
            return
        }
        
        // Build the system message with file contents
        val systemMessage = buildSystemMessage(fileContents)
        
        // Get current chat
        val chatId = settings.currentChatId
        var chat = settings.getChatById(chatId)
        
        // If chat doesn't exist, create it
        if (chat == null) {
            chat = settings.addChat(chatId, "Chat $chatId")
        }
        
        // Add user message to chat history
        chat.messages.add(com.example.llmplugin.settings.ChatMessage(role = "user", content = prompt))
        
        // Convert chat messages to the format expected by buildRequestJson
        val messageHistory = chat.messages.map { Pair(it.role, it.content) }
        
        // Build the request JSON
        val requestJson = buildRequestJson(prompt, systemMessage, settings.selectedModel, settings.includeMessageHistory, messageHistory)
        
        // Create the request
        val request = Request.Builder()
            .url("${settings.openRouterBaseUrl}/chat/completions")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${settings.openRouterApiKey}")
            .build()
        
        // Notify that we're starting
        callback.onStart()
        
        // Execute the request asynchronously
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("Connection error: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback.onError("API error: ${response.code} ${response.message}")
                    return
                }
                
                try {
                    // For non-streaming response, just return the full text
                    val responseBody = response.body?.string() ?: ""
                    
                    // Parse the JSON response
                    val adapter: JsonAdapter<ChatCompletion> = moshi.adapter(ChatCompletion::class.java)
                    val chatCompletion = adapter.fromJson(responseBody)
                    
                    if (chatCompletion != null) {
                        val content = chatCompletion.choices.firstOrNull()?.message?.content ?: ""
                        
                        // Add assistant response to chat history
                        chat.messages.add(com.example.llmplugin.settings.ChatMessage(role = "assistant", content = content))
                        
                        callback.onComplete(content)
                    } else {
                        callback.onError("Failed to parse response")
                    }
                } catch (e: Exception) {
                    callback.onError("Error processing response: ${e.message}")
                }
            }
        })
    }
    
    /**
     * Creates a new chat context
     */
    fun createNewChat(): String {
        // Create new chat with empty message history
        val newChat = com.example.llmplugin.settings.ChatData(
            id = UUID.randomUUID().toString(),
            name = "Chat ${settings.chats.size + 1}",
            messages = mutableListOf()
        )
        
        // Add to beginning of chats list and make current
        settings.chats.add(0, newChat)
        settings.currentChatId = newChat.id
        
        // Persistence is handled automatically by IntelliJ's PersistentStateComponent
        
        return newChat.id
    }

    fun addAssistantMessage(content: String) {
        settings.getChatById(settings.currentChatId)?.messages?.add(
            com.example.llmplugin.settings.ChatMessage("assistant", content)
        )
    }
    
    /**
     * Switches to an existing chat context
     */
    fun switchChat(chatId: String) {
        if (settings.getChatById(chatId) != null) {
            settings.currentChatId = chatId
        }
    }
    
    /**
     * Clears the history for the current chat
     */
    fun clearCurrentChat() {
        val chatId = settings.currentChatId
        val chat = settings.getChatById(chatId)
        chat?.messages?.clear()
    }
    
    /**
     * Deletes a chat
     */
    fun deleteChat(chatId: String): Boolean {
        return settings.deleteChat(chatId)
    }
    
    /**
     * Gets all available chat IDs
     */
    fun getAvailableChatIds(): List<String> {
        return settings.getChatIds()
    }
    
    /**
     * Gets all available chats
     */
    fun getAvailableChats(): List<com.example.llmplugin.settings.ChatData> {
        return settings.chats
    }
    
    /**
     * Gets the current chat ID
     */
    fun getCurrentChatId(): String {
        return settings.currentChatId
    }
    
    /**
     * Gets the current chat
     */
    fun getCurrentChat(): com.example.llmplugin.settings.ChatData? {
        return settings.getChatById(settings.currentChatId)
    }
    
    /**
     * Builds the system message with file contents.
     */
    private fun buildSystemMessage(fileContents: Map<String, String>): String {
        if (fileContents.isEmpty()) {
            return "You are a helpful assistant."
        }
        
        val sb = StringBuilder()
        sb.append("You are a helpful assistant. The user has shared the following files with you:\n\n")
        
        for ((path, content) in fileContents) {
            sb.append("FILE: $path\n")
            sb.append("```\n")
            sb.append(content)
            sb.append("\n```\n\n")
        }
        
        sb.append("Please refer to these files when answering the user's questions.")
        return sb.toString()
    }
    
    /**
     * Builds the request JSON for the OpenRouter API.
     */
    private fun buildRequestJson(
        prompt: String, 
        systemMessage: String, 
        model: String, 
        includeHistory: Boolean = false,
        messageHistory: List<Pair<String, String>> = emptyList()
    ): String {
        val messages = mutableListOf<Map<String, String>>()
        
        // Always include system message first
        messages.add(mapOf("role" to "system", "content" to systemMessage))
        
        if (includeHistory) {
            // Include previous messages from history (excluding the current prompt which we'll add last)
            val previousMessages = messageHistory.dropLast(1)
            for ((role, content) in previousMessages) {
                messages.add(mapOf("role" to role, "content" to content))
            }
        }
        
        // Add current user prompt
        messages.add(mapOf("role" to "user", "content" to prompt))
        
        val requestMap = mapOf(
            "model" to model,
            "messages" to messages,
            "stream" to false
        )
        
        val adapter = moshi.adapter(Map::class.java) as JsonAdapter<Map<String, Any>>
        return adapter.toJson(requestMap)
    }
}
