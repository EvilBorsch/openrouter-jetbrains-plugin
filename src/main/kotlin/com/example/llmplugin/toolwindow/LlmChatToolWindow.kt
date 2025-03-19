package com.example.llmplugin.toolwindow

import com.example.llmplugin.api.OpenRouterClient
import com.example.llmplugin.parser.FileReferenceParser
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.Element
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit
import java.util.UUID

/**
 * Main UI component for the LLM Chat tool window with dark theme support.
 */
class LlmChatToolWindow(private val project: Project) {
    // Map to store code snippets by ID for copy functionality
    private val codeSnippets = mutableMapOf<String, String>()

    // Components
    private val chatHistoryPane = JTextPane().apply {
        contentType = "text/html"
        isEditable = false
        border = EmptyBorder(10, 10, 10, 10)

        // Apply CSS styles
        val editorKit = HTMLEditorKit()
        document = HTMLDocument()

        // Apply CSS styling with dark theme support
        val styleSheet = editorKit.styleSheet
        styleSheet.addRule("""
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                margin: 0;
                padding: 0;
                background-color: ${if (isDarkTheme()) "#252526" else "#ffffff"};
                color: ${if (isDarkTheme()) "#d4d4d4" else "#333333"};
            }
            
            .message {
                margin-bottom: 16px;
                max-width: 85%;
                border-radius: 8px;
                box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
                overflow: hidden;
            }
            
            .user {
                background-color: ${if (isDarkTheme()) "#2b5797" else "#e1f5fe"};
                color: ${if (isDarkTheme()) "#ffffff" else "#0d47a1"};
                margin-left: auto;
                margin-right: 0;
            }
            
            .assistant {
                background-color: ${if (isDarkTheme()) "#333333" else "#f1f1f1"};
                color: ${if (isDarkTheme()) "#d4d4d4" else "#333333"};
                margin-right: auto;
                margin-left: 0;
            }
            
            .assistant-thinking {
                background-color: ${if (isDarkTheme()) "#333333" else "#f1f1f1"};
                color: ${if (isDarkTheme()) "#d4d4d4" else "#333333"};
                margin-right: auto;
                margin-left: 0;
                opacity: 0.7;
            }
            
            .prompt-only {
                background-color: ${if (isDarkTheme()) "#5c4b00" else "#ffe0b2"};
                color: ${if (isDarkTheme()) "#f0e6c8" else "#663c00"};
                margin: 0 auto;
                max-width: 70%;
            }
            
            .message-header {
                padding: 6px 12px;
                font-weight: bold;
                font-size: 12px;
                border-bottom: 1px solid ${if (isDarkTheme()) "#444444" else "#e0e0e0"};
            }
            
            .user .message-header {
                background-color: ${if (isDarkTheme()) "#1e3e6b" else "#bbdefb"};
            }
            
            .assistant .message-header {
                background-color: ${if (isDarkTheme()) "#3e3e3e" else "#e0e0e0"};
            }
            
            .assistant-thinking .message-header {
                background-color: ${if (isDarkTheme()) "#3e3e3e" else "#e0e0e0"};
            }
            
            .prompt-only .message-header {
                background-color: ${if (isDarkTheme()) "#705c00" else "#ffcc80"};
            }
            
            .message-content {
                padding: 8px 12px;
                line-height: 1.5;
            }
            
            pre {
                white-space: pre-wrap;
                word-wrap: break-word;
                margin: 8px 0;
                font-family: 'JetBrains Mono', monospace;
                font-size: 12px;
            }
            
            .code-block {
                position: relative;
                background-color: ${if (isDarkTheme()) "#1e1e1e" else "#f5f5f5"};
                border: 1px solid ${if (isDarkTheme()) "#444444" else "#ddd"};
                border-radius: 4px;
                padding: 8px 12px;
                margin: 8px 0;
                font-family: 'JetBrains Mono', monospace;
            }
            
            .code-language {
                position: absolute;
                top: 2px;
                right: 36px;
                font-size: 10px;
                color: ${if (isDarkTheme()) "#a0a0a0" else "#757575"};
                padding: 2px 6px;
            }
            
            .copy-btn {
                position: absolute;
                top: 2px;
                right: 2px;
                background-color: ${if (isDarkTheme()) "#3e3e3e" else "#e0e0e0"};
                color: ${if (isDarkTheme()) "#d4d4d4" else "#333333"};
                border: none;
                border-radius: 3px;
                padding: 2px 6px;
                font-size: 10px;
                cursor: pointer;
            }
            
            .copy-btn:hover {
                background-color: ${if (isDarkTheme()) "#505050" else "#bdbdbd"};
                color: ${if (isDarkTheme()) "#ffffff" else "#000000"};
            }
        """)

        this.editorKit = editorKit

        // Add hyperlink listener for code copy functionality
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

    // Create scroll pane with custom background color based on theme
    private val scrollPane = JBScrollPane(chatHistoryPane).apply {
        border = EmptyBorder(0, 0, 0, 0)
        verticalScrollBar.unitIncrement = 16
        background = if (isDarkTheme()) JBColor(Color(0x252526), Color(0x252526)) else JBColor.WHITE
        chatHistoryPane.background = if (isDarkTheme()) JBColor(Color(0x252526), Color(0x252526)) else JBColor.WHITE
    }

    // Modern-looking text area for input
    private val promptTextArea = JTextArea().apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                if (isDarkTheme()) JBColor(Color(0x3E3E3E), Color(0x3E3E3E)) else JBColor.LIGHT_GRAY, 1
            ),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        )
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        font = JBUI.Fonts.create(Font.SANS_SERIF, 13)
        background = if (isDarkTheme()) JBColor(Color(0x333333), Color(0x333333)) else JBColor.WHITE
        foreground = if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
        caretColor = if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK

        // Set placeholder text
        text = "Type your prompt here (use @file.txt to include file contents)"
        addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusGained(e: java.awt.event.FocusEvent?) {
                if (text == "Type your prompt here (use @file.txt to include file contents)") {
                    text = ""
                }
            }

            override fun focusLost(e: java.awt.event.FocusEvent?) {
                if (text.trim().isEmpty()) {
                    text = "Type your prompt here (use @file.txt to include file contents)"
                }
            }
        })

        // Add key listener for Enter key
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    sendPrompt()
                }
            }
        })
    }

    // Modern-looking send button with icon support
    private val sendButton = JButton("Send").apply {
        preferredSize = Dimension(100, 36)
        background = if (isDarkTheme()) JBColor(Color(0x0E639C), Color(0x0E639C)) else JBColor(Color(0x4285F4), Color(0x4285F4))
        foreground = JBColor.WHITE
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
        isFocusPainted = false
        cursor = Cursor(Cursor.HAND_CURSOR)

        addActionListener { sendPrompt() }

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                background = if (isDarkTheme()) JBColor(Color(0x1177BB), Color(0x1177BB)) else JBColor(Color(0x3367D6), Color(0x3367D6))
            }

            override fun mouseExited(e: MouseEvent?) {
                background = if (isDarkTheme()) JBColor(Color(0x0E639C), Color(0x0E639C)) else JBColor(Color(0x4285F4), Color(0x4285F4))
            }
        })
    }

    // Checkbox for "Copy prompt only" mode with modern styling
    private val copyPromptOnlyCheckBox = JBCheckBox("Copy prompt only").apply {
        toolTipText = "When checked, prompts will be copied to clipboard instead of being sent to OpenRouter"
        foreground = if (isDarkTheme()) JBColor(Color(0xA0A0A0), Color(0xA0A0A0)) else JBColor.GRAY
        isOpaque = false
    }

    // Panel for the notice when copy mode is enabled
    private val noticePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0,
                if (isDarkTheme()) JBColor(Color(0x3E3E3E), Color(0x3E3E3E)) else JBColor.LIGHT_GRAY
            ),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        )
        background = if (isDarkTheme()) JBColor(Color(0x332700), Color(0x332700)) else JBColor(Color(0xFFF8E1), Color(0xFFF8E1))
        isVisible = false

        val label = JLabel("Copy mode enabled - messages will be copied to clipboard instead of being sent to API")
        label.foreground = if (isDarkTheme()) JBColor(Color(0xFFCA28), Color(0xFFCA28)) else JBColor(Color(0xF57F17), Color(0xF57F17))
        label.font = JBUI.Fonts.create(Font.SANS_SERIF, 11)
        add(label)
    }

    private val fileReferenceParser = FileReferenceParser(project)
    private val openRouterClient = OpenRouterClient()

    // Keep track of the last assistant message element
    private var lastAssistantMessageElement: Element? = null
    private var assistantMessageContent = StringBuilder()

    // Store original file references from the prompt
    private val fileReferenceRegex = Regex("@([\\w.-/]+)")

    init {
        // Set listener for checkbox to show/hide notice panel
        copyPromptOnlyCheckBox.addActionListener {
            noticePanel.isVisible = copyPromptOnlyCheckBox.isSelected
        }
    }

    /**
     * Check if dark theme is active in the IDE
     */
    private fun isDarkTheme(): Boolean {
        return UIUtil.isUnderDarcula()
    }

    /**
     * Build the main content panel
     */
    fun getContent(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = if (isDarkTheme()) JBColor(Color(0x1E1E1E), Color(0x1E1E1E)) else JBColor.WHITE

        // Add header panel with title
        val headerPanel = JPanel(BorderLayout()).apply {
            background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(Color(0xF5F5F5), Color(0xF5F5F5))
            border = MatteBorder(0, 0, 1, 0,
                if (isDarkTheme()) JBColor(Color(0x3E3E3E), Color(0x3E3E3E)) else JBColor.LIGHT_GRAY
            )
            preferredSize = Dimension(0, 40)

            val title = JLabel("LLM Chat")
            title.font = JBUI.Fonts.create(Font.SANS_SERIF, 14).asBold()
            title.foreground = if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
            title.border = EmptyBorder(0, 15, 0, 0)

            add(title, BorderLayout.WEST)
        }

        panel.add(headerPanel, BorderLayout.NORTH)

        // Chat history area with scroll
        panel.add(scrollPane, BorderLayout.CENTER)

        // Input area panel
        val inputPanel = JPanel(BorderLayout()).apply {
            background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(Color(0xF5F5F5), Color(0xF5F5F5))
            border = EmptyBorder(10, 15, 15, 15)

            // Options panel for checkbox
            val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(Color(0xF5F5F5), Color(0xF5F5F5))
                border = EmptyBorder(0, 0, 10, 0)
                add(copyPromptOnlyCheckBox)
            }

            // Input field and send button
            val promptPanel = JPanel(BorderLayout(10, 0)).apply {
                background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(Color(0xF5F5F5), Color(0xF5F5F5))
                add(promptTextArea, BorderLayout.CENTER)
                add(sendButton, BorderLayout.EAST)
            }

            add(optionsPanel, BorderLayout.NORTH)
            add(promptPanel, BorderLayout.CENTER)
            add(noticePanel, BorderLayout.SOUTH)
        }

        panel.add(inputPanel, BorderLayout.SOUTH)

        // Add a welcome message
        addMessageToChat("Assistant",
            "Hello! I'm your AI assistant. How can I help you today? You can reference files with @filename.ext syntax.",
            "assistant")

        return panel
    }

    /**
     * Send prompt to the API or copy to clipboard
     */
    private fun sendPrompt() {
        val promptText = promptTextArea.text
        // Skip if placeholder text or empty
        if (promptText.isBlank() || promptText == "Type your prompt here (use @file.txt to include file contents)") return

        // Add user message to chat
        addMessageToChat("You", promptText, "user")

        // Extract file references to preserve the original references
        val fileReferences = extractFileReferences(promptText)

        // Clear input field
        promptTextArea.text = ""

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
            SwingUtilities.invokeLater {
                Messages.showInfoMessage(
                    "The prompt has been copied to clipboard and was not sent to OpenRouter.",
                    "Prompt Copied"
                )
            }
        } else {
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
    }

    /**
     * Extract file references from the prompt to preserve the original format
     */
    private fun extractFileReferences(prompt: String): List<String> {
        val references = mutableListOf<String>()
        val matches = fileReferenceRegex.findAll(prompt)

        for (match in matches) {
            references.add(match.groupValues[1])
        }

        return references
    }

    /**
     * Create a human-readable prompt for copying
     */
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

    /**
     * Add a message to the chat history
     */
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
            <div class="message $cssClass">
                <div class="message-header">$sender</div>
                <div class="message-content">$processedMessage</div>
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

    /**
     * Process the message to add copy buttons to code blocks
     */
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
                ${if (language.isNotEmpty()) "<div class=\"code-language\">$language</div>" else ""}
                <a href="copy:$codeId" class="copy-btn">Copy</a>
                <pre>$code</pre>
            </div>
            """.trimIndent()
        }
    }

    /**
     * Copy the code snippet with the given ID to the clipboard
     */
    private fun copyCodeSnippet(codeId: String) {
        codeSnippets[codeId]?.let { code ->
            val selection = StringSelection(code)
            CopyPasteManager.getInstance().setContents(selection)

            // Show a notification
            SwingUtilities.invokeLater {
                Messages.showInfoMessage(
                    "Code snippet copied to clipboard.",
                    "Code Copied"
                )
            }
        }
    }

    /**
     * Update the last assistant message with new content
     */
    private fun updateLastAssistantMessage(token: String) {
        assistantMessageContent.append(token)
        replaceLastAssistantMessage(assistantMessageContent.toString())
    }

    /**
     * Replace the last assistant message with new content
     */
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
                    <div class="message assistant">
                        <div class="message-header">Assistant</div>
                        <div class="message-content">${processCodeBlocks(newContent)}</div>
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

    /**
     * Escape HTML special characters to prevent XSS and rendering issues
     */
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
