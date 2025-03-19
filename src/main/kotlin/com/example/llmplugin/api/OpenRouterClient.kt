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
        
        // Build the request JSON
        val requestJson = buildRequestJson(prompt, systemMessage, settings.selectedModel)
        
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
    private fun buildRequestJson(prompt: String, systemMessage: String, model: String): String {
        val requestMap = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemMessage),
                mapOf("role" to "user", "content" to prompt)
            ),
            "stream" to false
        )
        
        val adapter = moshi.adapter(Map::class.java) as JsonAdapter<Map<String, Any>>
        return adapter.toJson(requestMap)
    }
}
