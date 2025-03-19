package com.example.llmplugin.toolwindow

import com.example.llmplugin.api.OpenRouterClient
import com.example.llmplugin.parser.FileReferenceParser
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.Element
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

/**
 * Main UI component for the LLM Chat tool window.
 */
class LlmChatToolWindow(private val project: Project) {
    private val chatHistoryPane = JTextPane().apply {
        contentType = "text/html"
        isEditable = false

        // Apply CSS styles
        val editorKit = HTMLEditorKit()
        document = HTMLDocument()

        // Apply basic CSS directly instead of loading from resource file
        val styleSheet = editorKit.styleSheet
        styleSheet.addRule("""
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background-color: #f5f5f5; }
            .user { background-color: #e1f5fe; padding: 10px; border-radius: 8px; margin-bottom: 10px; }
            .assistant { background-color: #f1f1f1; padding: 10px; border-radius: 8px; margin-bottom: 10px; }
            .assistant-thinking { background-color: #f1f1f1; padding: 10px; border-radius: 8px; margin-bottom: 10px; opacity: 0.7; }
            pre { white-space: pre-wrap; word-wrap: break-word; margin: 5px 0; font-family: monospace; font-size: 0.9em; }
            strong { font-weight: bold; margin-bottom: 5px; display: block; }
        """)

        this.editorKit = editorKit
    }

    private val promptField = JBTextField().apply {
        emptyText.text = "Type your prompt here (use @file.txt to include file contents)"
    }

    private val sendButton = JButton("Send").apply {
        addActionListener { sendPrompt() }
    }

    private val fileReferenceParser = FileReferenceParser(project)
    private val openRouterClient = OpenRouterClient()

    // Keep track of the last assistant message element
    private var lastAssistantMessageElement: Element? = null
    private var assistantMessageContent = StringBuilder()

    init {
        // Add key listener to promptField for Enter key
        promptField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    sendPrompt()
                }
            }
        })
    }

    fun getContent(): JPanel {
        val panel = JPanel(BorderLayout())

        // Chat history area with scroll
        val scrollPane = JBScrollPane(chatHistoryPane).apply {
            preferredSize = Dimension(300, 500)
            border = JBUI.Borders.empty(10)
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // Input area
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 10, 10, 10)
            add(promptField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }
        panel.add(inputPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun sendPrompt() {
        val promptText = promptField.text
        if (promptText.isBlank()) return

        // Add user message to chat
        addMessageToChat("You", promptText, "user")

        // Clear input field
        promptField.text = ""

        // Reset assistant message content
        assistantMessageContent = StringBuilder()

        // Parse file references
        val (processedPrompt, fileContents) = fileReferenceParser.parseFileReferences(promptText)

        // Send to OpenRouter API
        openRouterClient.sendPrompt(project, processedPrompt, fileContents, object : OpenRouterClient.ResponseCallback {
            override fun onStart() {
                // Add placeholder for assistant response
                SwingUtilities.invokeLater {
                    addMessageToChat("Assistant", "Thinking...", "assistant-thinking")
                }
            }

            override fun onToken(token: String) {
                // Update the assistant's message with the new token
                SwingUtilities.invokeLater {
                    updateLastAssistantMessage(token)
                }
            }

            override fun onComplete(fullResponse: String) {
                // Final update to the assistant's message
                SwingUtilities.invokeLater {
                    replaceLastAssistantMessage(fullResponse)
                }
            }

            override fun onError(error: String) {
                // Show error message
                SwingUtilities.invokeLater {
                    replaceLastAssistantMessage("Error: $error")
                }
            }
        })
    }

    private fun addMessageToChat(sender: String, message: String, cssClass: String) {
        val doc = chatHistoryPane.document as HTMLDocument
        val kit = chatHistoryPane.editorKit as HTMLEditorKit

        val html = """
            <div class="$cssClass">
                <strong>$sender:</strong>
                <pre>${escapeHtml(message)}</pre>
            </div>
        """.trimIndent()

        kit.insertHTML(doc, doc.length, html, 0, 0, null)

        // Store reference to the last assistant message
        if (cssClass == "assistant-thinking") {
            val root = doc.defaultRootElement
            val index = root.elementCount - 1
            lastAssistantMessageElement = root.getElement(index)
        }

        // Scroll to bottom
        chatHistoryPane.caretPosition = doc.length
    }

    private fun updateLastAssistantMessage(token: String) {
        assistantMessageContent.append(token)
        replaceLastAssistantMessage(assistantMessageContent.toString())
    }

    private fun replaceLastAssistantMessage(newContent: String) {
        val doc = chatHistoryPane.document as HTMLDocument

        if (lastAssistantMessageElement != null) {
            // Replace the content of the last assistant message
            val startOffset = lastAssistantMessageElement!!.startOffset
            val endOffset = lastAssistantMessageElement!!.endOffset

            try {
                doc.remove(startOffset, endOffset - startOffset)
                val kit = chatHistoryPane.editorKit as HTMLEditorKit

                val html = """
                    <div class="assistant">
                        <strong>Assistant:</strong>
                        <pre>${escapeHtml(newContent)}</pre>
                    </div>
                """.trimIndent()

                kit.insertHTML(doc, startOffset, html, 0, 0, null)

                // Update the reference to the new element
                val root = doc.defaultRootElement
                val index = root.getElementIndex(startOffset)
                if (index >= 0) {
                    lastAssistantMessageElement = root.getElement(index)
                } else {
                    lastAssistantMessageElement = null
                }

                // Scroll to bottom
                chatHistoryPane.caretPosition = doc.length
            } catch (e: Exception) {
                // Fallback: add as new message
                lastAssistantMessageElement = null
                addMessageToChat("Assistant", newContent, "assistant")
            }
        } else {
            // Fallback: add as new message
            addMessageToChat("Assistant", newContent, "assistant")
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br/>")
    }
}
