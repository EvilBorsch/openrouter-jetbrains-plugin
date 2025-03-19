package com.example.llmplugin.toolwindow

import com.example.llmplugin.api.OpenRouterClient
import com.example.llmplugin.parser.FileReferenceParser
import com.intellij.openapi.components.service
import com.example.llmplugin.settings.LlmPluginSettings
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.Element
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import com.intellij.openapi.ui.Messages
import java.util.UUID
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

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
            .prompt-only { background-color: #ffe0b2; padding: 10px; border-radius: 8px; margin-bottom: 10px; }
            pre { white-space: pre-wrap; word-wrap: break-word; margin: 5px 0; font-family: monospace; font-size: 0.9em; }
            .code-block { position: relative; background-color: #f5f5f5; border: 1px solid #ddd; border-radius: 4px; padding: 8px; margin: 8px 0; }
            .copy-btn { position: absolute; top: 5px; right: 5px; background-color: #4CAF50; color: white; border: none; border-radius: 3px; padding: 3px 8px; font-size: 12px; cursor: pointer; }
            .copy-btn:hover { background-color: #45a049; }
            strong { font-weight: bold; margin-bottom: 5px; display: block; }
        """)

        this.editorKit = editorKit

        // Add hyperlink listener for code copy links
        addHyperlinkListener(object : HyperlinkListener {
            override fun hyperlinkUpdate(e: HyperlinkEvent) {
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    if (e.description.startsWith("copy:")) {
                        val codeId = e.description.substring(5)
                        copyCodeSnippet(codeId)
                    }
                }
            }
        })
    }

    // Map to store code snippets by ID
    private val codeSnippets = mutableMapOf<String, String>()

    private val promptField = JBTextField().apply {
        emptyText.text = "Type your prompt here (use @file.txt to include file contents)"
    }

    private val sendButton = JButton("Send").apply {
        addActionListener { sendPrompt() }
    }

    // Add checkbox for "Copy prompt only" mode
    private val copyPromptOnlyCheckBox = JBCheckBox("Copy prompt only").apply {
        toolTipText = "When checked, prompts will be copied to clipboard instead of being sent to OpenRouter"
    }

    private val fileReferenceParser = FileReferenceParser(project)
    private val openRouterClient = OpenRouterClient()

    // Keep track of the last assistant message element
    private var lastAssistantMessageElement: Element? = null
    private var assistantMessageContent = StringBuilder()

    // Store original file references from the prompt
    private val fileReferenceRegex = Regex("@([\\w.-/]+)")

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

        // Input area with checkbox
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 10, 10, 10)

            // Add checkbox to the left side
            val optionsPanel = JPanel().apply {
                add(copyPromptOnlyCheckBox)
            }

            add(optionsPanel, BorderLayout.NORTH)
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

        // Extract file references to preserve the original references
        val fileReferences = extractFileReferences(promptText)

        // Clear input field
        promptField.text = ""

        // Reset assistant message content
        assistantMessageContent = StringBuilder()

        // Parse file references
        val (processedPrompt, fileContents) = fileReferenceParser.parseFileReferences(promptText)

        if (copyPromptOnlyCheckBox.isSelected) {
            // Create a human-readable prompt for copying
            val readablePrompt = createReadablePrompt(processedPrompt, fileContents, fileReferences)

            // Copy to clipboard
            val selection = StringSelection(readablePrompt)
            CopyPasteManager.getInstance().setContents(selection)

            // Display the prompt in chat
            addMessageToChat("Prompt (Copied to Clipboard)", readablePrompt, "prompt-only")

            // Notify user
            Messages.showInfoMessage(
                "The prompt has been copied to clipboard and was not sent to OpenRouter.",
                "Prompt Copied"
            )
        } else {
            // Send to OpenRouter API normally
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
    }

    // Extract file references from the prompt to preserve the original format
    private fun extractFileReferences(prompt: String): List<String> {
        val references = mutableListOf<String>()
        val matches = fileReferenceRegex.findAll(prompt)

        for (match in matches) {
            references.add(match.groupValues[1])
        }

        return references
    }

    // Create a human-readable prompt for copying
    private fun createReadablePrompt(prompt: String, fileContents: Map<String, String>, fileReferences: List<String>): String {
        val sb = StringBuilder()

        // Format system message
        if (fileContents.isNotEmpty()) {
            sb.appendLine("I'm sharing the following files with you:")
            sb.appendLine()

            // Use the original file references with './' prefix
            for (reference in fileReferences) {
                if (fileContents.containsKey(reference)) {
                    // Format as relative path with './' prefix
                    sb.appendLine("FILE: ./${reference}")
                    sb.appendLine("```")
                    sb.appendLine(fileContents[reference])
                    sb.appendLine("```")
                    sb.appendLine()
                }
            }

            sb.appendLine("Please refer to these files when answering my question.")
            sb.appendLine()
        }

        // Add the user's prompt, replacing @file references with ./file for clarity
        val processedPrompt = fileReferenceRegex.replace(prompt) {
            "./${it.groupValues[1]}"
        }

        sb.append(processedPrompt)

        return sb.toString()
    }

    private fun addMessageToChat(sender: String, message: String, cssClass: String) {
        val doc = chatHistoryPane.document as HTMLDocument
        val kit = chatHistoryPane.editorKit as HTMLEditorKit

        // Process message to add copy buttons to code blocks
        val processedMessage = if (cssClass == "assistant") {
            processCodeBlocks(message)
        } else {
            escapeHtml(message)
        }

        val html = """
            <div class="$cssClass">
                <strong>$sender:</strong>
                <pre>${processedMessage}</pre>
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

    // Process the message to add copy buttons to code blocks
    private fun processCodeBlocks(message: String): String {
        // Pattern to match markdown code blocks (both with language specifier and without)
        val codeBlockPattern = Regex("```([a-zA-Z0-9]*)?\\s*\\n([\\s\\S]*?)\\n```")

        return escapeHtml(message).replace(codeBlockPattern) { matchResult ->
            val language = matchResult.groupValues[1].trim()
            val code = matchResult.groupValues[2]

            // Generate a unique ID for this code snippet
            val codeId = "code-" + UUID.randomUUID().toString()

            // Store the code in our map for later copying
            codeSnippets[codeId] = code

            // Create HTML with a copy button
            """
            <div class="code-block">
                <a href="copy:$codeId" class="copy-btn">Copy</a>
                ${if (language.isNotEmpty()) "<span><strong>$language</strong></span>" else ""}
                <pre>$code</pre>
            </div>
            """.trimIndent()
        }
    }

    // Copy the code snippet with the given ID to the clipboard
    private fun copyCodeSnippet(codeId: String) {
        codeSnippets[codeId]?.let { code ->
            val selection = StringSelection(code)
            CopyPasteManager.getInstance().setContents(selection)

            // Show a notification
            Messages.showInfoMessage(
                "Code snippet copied to clipboard.",
                "Code Copied"
            )
        }
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
                        <pre>${processCodeBlocks(newContent)}</pre>
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
    }
}
