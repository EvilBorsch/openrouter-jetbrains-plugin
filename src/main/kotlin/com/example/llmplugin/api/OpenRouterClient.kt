package com.example.llmplugin.api

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
        fun onComplete(fullResponse: String, generationId: String = "")
        fun onError(error: String)
        fun onCostUpdate(generationId: String, cost: Double?)
    }
    
    /**
     * Data class for parsing the chat completion response.
     */
    data class ChatCompletion(
        val id: String,
        val choices: List<ChatCompletionChoice>,
        val usage: UsageInfo? = null
    )

    data class UsageInfo(
        val prompt_tokens: Int? = null,
        val completion_tokens: Int? = null,
        val total_tokens: Int? = null,
        val cost: Double? = null
    )

    data class GenerationCostResponse(
        val data: GenerationCost
    )

    data class GenerationCost(
        val id: String,
        val total_cost: Double
        // You can add other fields if needed
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
    
    /**
     * Data classes for parsing streaming responses
     */
    data class StreamResponse(
        val id: String,
        val choices: List<StreamChoice>
    )
    
    data class StreamChoice(
        val index: Int,
        val delta: StreamDelta,
        val finish_reason: String?
    )
    
    data class StreamDelta(
        val role: String? = null,
        val content: String? = null
    )
    
    // We'll use the settings service to store chat histories
    private val settings = service<LlmPluginSettings>()

    fun fetchGenerationCost(generationId: String, maxRetries: Int = 5, callback: (Double?) -> Unit) {
        if (settings.openRouterApiKey.isBlank()) {
            println("DEBUG: API key is blank, skipping cost fetch")
            callback(null)
            return
        }

        println("DEBUG: Fetching cost for generation ID: $generationId")

        // Track retry count
        var retryCount = 0
        val baseDelayMs = 500 // Start with 500ms delay

        fun makeRequest() {
            // Create the request
            val request = Request.Builder()
                .url("${settings.openRouterBaseUrl}/generation?id=$generationId")
                .header("Authorization", "Bearer ${settings.openRouterApiKey}")
                .build()

            // Execute the request asynchronously
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("DEBUG: Cost fetch failed: ${e.message}")
                    retryIfNeeded()
                }

                override fun onResponse(call: Call, response: Response) {
                    println("DEBUG: Cost fetch response code: ${response.code}")

                    if (!response.isSuccessful) {
                        println("DEBUG: Cost fetch failed with code: ${response.code}")
                        retryIfNeeded()
                        return
                    }

                    try {
                        // Parse the JSON response
                        val responseBody = response.body?.string() ?: ""
                        println("DEBUG: Cost fetch response: $responseBody")

                        // Parse using the nested structure
                        val responseAdapter: JsonAdapter<GenerationCostResponse> = moshi.adapter(GenerationCostResponse::class.java)
                        val costResponse = responseAdapter.fromJson(responseBody)

                        if (costResponse?.data?.total_cost != null) {
                            println("DEBUG: Parsed cost info: ${costResponse.data.total_cost}")
                            callback(costResponse.data.total_cost)
                        } else {
                            println("DEBUG: Cost info not available yet, retrying...")
                            retryIfNeeded()
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Error parsing cost response: ${e.message}, ${e.stackTraceToString()}")
                        retryIfNeeded()
                    }
                }

                fun retryIfNeeded() {
                    if (retryCount < maxRetries) {
                        retryCount++
                        // Exponential backoff with jitter
                        val delayMs = (baseDelayMs * Math.pow(1.5, retryCount.toDouble())).toLong() +
                                (Math.random() * 100).toLong()
                        println("DEBUG: Retry $retryCount/$maxRetries after ${delayMs}ms")

                        // Use a handler to post delayed execution
                        object : Thread() {
                            override fun run() {
                                try {
                                    sleep(delayMs)
                                    makeRequest()
                                } catch (e: InterruptedException) {
                                    println("DEBUG: Retry sleep interrupted")
                                    callback(null)
                                }
                            }
                        }.start()
                    } else {
                        println("DEBUG: Max retries ($maxRetries) reached, giving up")
                        callback(null)
                    }
                }
            })
        }

        // Start the first request
        makeRequest()
    }
    
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
                    val responseBody = response.body
                    if (responseBody == null) {
                        callback.onError("Empty response body")
                        return
                    }

                    // Create a stream adapter for parsing streaming responses
                    val streamAdapter: JsonAdapter<StreamResponse> = moshi.adapter(StreamResponse::class.java)
                    
                    // For handling the complete response
                    val fullResponseBuilder = StringBuilder()
                    var generationId = ""
                    
                    // Process the streaming response
                    val reader = responseBody.charStream().buffered()
                    
                    // Create a message to store in chat history once complete
                    val assistantMessage = com.example.llmplugin.settings.ChatMessage(
                        role = "assistant",
                        content = "",
                        generationId = ""
                    )
                    
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // Process each line
                        val currentLine = line ?: continue
                        
                        // Skip empty lines
                        if (currentLine.isBlank()) continue
                        
                        // Check for data prefix (SSE format)
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.substring(6).trim()
                            
                            // Check for the end of the stream
                            if (data == "[DONE]") {
                                // Stream is complete
                                break
                            }
                            
                            try {
                                // Parse the JSON chunk
                                val streamResponse = streamAdapter.fromJson(data)
                                
                                if (streamResponse != null) {
                                    // Store the generation ID if this is the first chunk
                                    if (generationId.isEmpty()) {
                                        generationId = streamResponse.id
                                        assistantMessage.generationId = generationId
                                    }
                                    
                                    // Process each choice in the response
                                    for (choice in streamResponse.choices) {
                                        // Get the content delta
                                        val content = choice.delta.content
                                        
                                        // If there's content, add it to the response and notify the callback
                                        if (content != null) {
                                            fullResponseBuilder.append(content)
                                            callback.onToken(content)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Error parsing stream chunk: ${e.message}")
                                // Continue processing even if one chunk fails
                            }
                        }
                    }
                    
                    // Stream is complete, update the message content
                    val fullResponse = fullResponseBuilder.toString()
                    assistantMessage.content = fullResponse
                    
                    // Add the message to chat history
                    chat.messages.add(assistantMessage)
                    
                    // Fetch cost information
                    if (generationId.isNotEmpty()) {
                        fetchGenerationCost(generationId) { fetchedCost ->
                            if (fetchedCost != null) {
                                assistantMessage.cost = fetchedCost
                            }
                            callback.onCostUpdate(generationId, fetchedCost)
                        }
                    }
                    
                    // Notify that the response is complete
                    callback.onComplete(fullResponse, generationId)
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
        messageHistory: List<Pair<String, String>> = emptyList(),
        stream: Boolean = true
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
            "stream" to stream
        )
        
        val adapter = moshi.adapter(Map::class.java) as JsonAdapter<Map<String, Any>>
        return adapter.toJson(requestMap)
    }
}
