package com.example.llmplugin.toolwindow

import com.example.llmplugin.api.OpenRouterClient
import com.example.llmplugin.parser.FileReferenceParser
import com.example.llmplugin.settings.LlmPluginSettings
import com.intellij.openapi.components.service
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
import java.awt.Component
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

    // Get settings
    private val settings = service<LlmPluginSettings>()

    // Model selector combo box
    private val modelSelectorComboBox = JComboBox<String>().apply {
        // Add the available models
        for (model in settings.availableModels) {
            addItem(model)
        }
        // Add custom models
        for (model in settings.customModels) {
            addItem(model)
        }

        // Set the selected model
        selectedItem = settings.selectedModel

        // Listen for changes
        addActionListener {
            val selectedModel = selectedItem as String
            settings.selectedModel = selectedModel
        }

        // Style the combobox
        maximumSize = Dimension(250, 30)
        preferredSize = Dimension(250, 30)
        background = if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
        foreground = if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
    }

    // Chat context components
    private val chatContextComboBox = JComboBox<com.example.llmplugin.settings.ChatData>().apply {
        maximumSize = Dimension(200, 30)
        preferredSize = Dimension(200, 30)
        background = if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
        foreground = if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
        
        // Custom renderer to show chat names instead of toString()
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is com.example.llmplugin.settings.ChatData) {
                    text = value.name
                }
                return this
            }
        }
    }
    
    private val newChatButton = JButton("+").apply {
        toolTipText = "Create new chat"
        preferredSize = Dimension(30, 30)
        background = if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
        foreground = if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
        isFocusPainted = false
        cursor = Cursor(Cursor.HAND_CURSOR)
    }
    
    private val deleteChatButton = JButton("×").apply {
        toolTipText = "Delete current chat"
        preferredSize = Dimension(30, 30)
        background = if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
        foreground = if (isDarkTheme()) JBColor(Color(0xFF6B68), Color(0xFF6B68)) else JBColor(Color(0xE53935), Color(0xE53935))
        isFocusPainted = false
        cursor = Cursor(Cursor.HAND_CURSOR)
    }
    
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
        styleSheet.addRule(
            """
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
            
            
                .inline-code {
        font-family: 'JetBrains Mono', Consolas, monospace;
        background-color: ${if (isDarkTheme()) "#2d2d2d" else "#f0f0f0"};
        padding: 1px 4px;
        border-radius: 3px;
        font-size: 90%;
    }
    
    em {
        font-style: italic;
    }
    
    strong {
        font-weight: bold;
    }
    
    ul, ol {
        margin-left: 20px;
        margin-top: 5px;
        margin-bottom: 5px;
    }
    
    li {
        margin-bottom: 3px;
    }

        """
        )

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
        chatHistoryPane.background =
            if (isDarkTheme()) JBColor(Color(0x252526), Color(0x252526)) else JBColor.WHITE
    }

    // Modern-looking text area for input
    private val promptTextArea = JTextArea().apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                if (isDarkTheme()) JBColor(
                    Color(0x3E3E3E),
                    Color(0x3E3E3E)
                ) else JBColor.LIGHT_GRAY, 1
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
        background = if (isDarkTheme()) JBColor(
            Color(0x0E639C),
            Color(0x0E639C)
        ) else JBColor(Color(0x4285F4), Color(0x4285F4))
        foreground = JBColor.WHITE
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
        isFocusPainted = false
        cursor = Cursor(Cursor.HAND_CURSOR)

        addActionListener { sendPrompt() }

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                background =
                    if (isDarkTheme()) JBColor(Color(0x1177BB), Color(0x1177BB)) else JBColor(
                        Color(0x3367D6), Color(0x3367D6)
                    )
            }

            override fun mouseExited(e: MouseEvent?) {
                background =
                    if (isDarkTheme()) JBColor(Color(0x0E639C), Color(0x0E639C)) else JBColor(
                        Color(0x4285F4), Color(0x4285F4)
                    )
            }
        })
    }

    // Checkbox for "Copy prompt only" mode with modern styling
    private val copyPromptOnlyCheckBox = JBCheckBox("Copy prompt only").apply {
        toolTipText =
            "When checked, prompts will be copied to clipboard instead of being sent to OpenRouter"
        foreground = if (isDarkTheme()) JBColor(Color(0xA0A0A0), Color(0xA0A0A0)) else JBColor.GRAY
        isOpaque = false
    }
    
    // Checkbox for "Include message history" mode
    private val includeMessageHistoryCheckBox = JBCheckBox("Include message history").apply {
        toolTipText = "When checked, previous messages will be included in the context sent to the LLM"
        foreground = if (isDarkTheme()) JBColor(Color(0xA0A0A0), Color(0xA0A0A0)) else JBColor.GRAY
        isOpaque = false
        isSelected = settings.includeMessageHistory
    }

    // Panel for the notice when copy mode is enabled
    private val noticePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(
                1, 0, 0, 0,
                if (isDarkTheme()) JBColor(Color(0x3E3E3E), Color(0x3E3E3E)) else JBColor.LIGHT_GRAY
            ),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        )
        background = if (isDarkTheme()) JBColor(
            Color(0x332700),
            Color(0x332700)
        ) else JBColor(Color(0xFFF8E1), Color(0xFFF8E1))
        isVisible = false

        val label =
            JLabel("Copy mode enabled - messages will be copied to clipboard instead of being sent to API")
        label.foreground = if (isDarkTheme()) JBColor(
            Color(0xFFCA28),
            Color(0xFFCA28)
        ) else JBColor(Color(0xF57F17), Color(0xF57F17))
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
        
        // Set listener for include message history checkbox
        includeMessageHistoryCheckBox.addActionListener {
            settings.includeMessageHistory = includeMessageHistoryCheckBox.isSelected
        }
        
        // Initialize chat context combo box
        updateChatContextComboBox()
        
        // Set up chat context combo box listener
        chatContextComboBox.addActionListener {
            if (chatContextComboBox.selectedItem != null) {
                val selectedChat = chatContextComboBox.selectedItem as com.example.llmplugin.settings.ChatData
                openRouterClient.switchChat(selectedChat.id)
                loadChatHistory()
            }
        }
        
        // Set up delete chat button
        deleteChatButton.addActionListener {
            val currentChat = openRouterClient.getCurrentChat() ?: return@addActionListener
            
            // Don't allow deleting the default chat
            if (currentChat.id == "default") {
                Messages.showWarningDialog(
                    "The default chat cannot be deleted.",
                    "Cannot Delete Chat"
                )
                return@addActionListener
            }
            
            // Confirm deletion
            val result = Messages.showYesNoDialog(
                "Are you sure you want to delete the chat '${currentChat.name}'?",
                "Delete Chat",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                openRouterClient.deleteChat(currentChat.id)
                updateChatContextComboBox()
            }
        }
        
        // Set up new chat button
        newChatButton.addActionListener {
            val newChatId = openRouterClient.createNewChat()
            updateChatContextComboBox()
            clearChatHistory()
            addMessageToChat(
                "Assistant",
                "Created new chat. How can I help you?",
                "assistant"
            )
        }
    }
    
    /**
     * Updates the chat context combo box with available chats
     */
    private fun updateChatContextComboBox() {
        chatContextComboBox.removeAllItems()
        
        // Add all available chats
        for (chat in openRouterClient.getAvailableChats()) {
            chatContextComboBox.addItem(chat)
        }
        
        // Select the current chat
        val currentChatId = openRouterClient.getCurrentChatId()
        for (i in 0 until chatContextComboBox.itemCount) {
            val chat = chatContextComboBox.getItemAt(i)
            if (chat.id == currentChatId) {
                chatContextComboBox.selectedIndex = i
                break
            }
        }
        
        // Update chat history display
        loadChatHistory()
    }
    
    /**
     * Loads the chat history for the current chat
     */
    private fun loadChatHistory() {
        // Clear current display
        clearChatHistory()
        
        // Get current chat
        val chat = openRouterClient.getCurrentChat() ?: return
        
        // Add messages to display
        for (message in chat.messages) {
            val sender = if (message.role == "user") "You" else "Assistant"
            val cssClass = if (message.role == "user") "user" else "assistant"
            addMessageToChat(sender, message.content, cssClass)
        }
    }
    
    /**
     * Clears the chat history display
     */
    private fun clearChatHistory() {
        val doc = chatHistoryPane.document as HTMLDocument
        doc.remove(0, doc.length)
        lastAssistantMessageElement = null
        assistantMessageContent = StringBuilder()
        codeSnippets.clear()
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
        panel.background =
            if (isDarkTheme()) JBColor(Color(0x1E1E1E), Color(0x1E1E1E)) else JBColor.WHITE

        // Add header panel with title and model selector
        val headerPanel = JPanel(BorderLayout()).apply {
            background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                Color(0xF5F5F5), Color(0xF5F5F5)
            )
            border = MatteBorder(
                0, 0, 1, 0,
                if (isDarkTheme()) JBColor(Color(0x3E3E3E), Color(0x3E3E3E)) else JBColor.LIGHT_GRAY
            )
            preferredSize = Dimension(0, 40)

            // Left side with title and chat context controls
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
                background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                    Color(0xF5F5F5), Color(0xF5F5F5)
                )
                
                val title = JLabel("LLM Chat")
                title.font = JBUI.Fonts.create(Font.SANS_SERIF, 14).asBold()
                title.foreground =
                    if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
                title.border = EmptyBorder(0, 5, 0, 0)
                
                add(title)
                add(chatContextComboBox)
                add(newChatButton)
                add(deleteChatButton)
            }
            
            add(leftPanel, BorderLayout.WEST)

            // Add model selector to the right side of the header
            val modelPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                    Color(0xF5F5F5), Color(0xF5F5F5)
                )
                add(JLabel("Model:").apply {
                    foreground = if (isDarkTheme()) JBColor(Color(0xA0A0A0), Color(0xA0A0A0)) else JBColor.GRAY
                })
                add(modelSelectorComboBox)
            }
            add(modelPanel, BorderLayout.EAST)
        }

        panel.add(headerPanel, BorderLayout.NORTH)

        panel.add(headerPanel, BorderLayout.NORTH)

        // Chat history area with scroll
        panel.add(scrollPane, BorderLayout.CENTER)

        // Input area panel
        val inputPanel = JPanel(BorderLayout()).apply {
            background = if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                Color(0xF5F5F5), Color(0xF5F5F5)
            )
            border = EmptyBorder(10, 15, 15, 15)

            // Options panel for checkboxes
            val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                        Color(0xF5F5F5), Color(0xF5F5F5)
                    )
                border = EmptyBorder(0, 0, 10, 0)
                add(copyPromptOnlyCheckBox)
                add(includeMessageHistoryCheckBox)
            }

            // Input field and send button
            val promptPanel = JPanel(BorderLayout(10, 0)).apply {
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                        Color(0xF5F5F5), Color(0xF5F5F5)
                    )
                add(promptTextArea, BorderLayout.CENTER)
                add(sendButton, BorderLayout.EAST)
            }

            add(optionsPanel, BorderLayout.NORTH)
            add(promptPanel, BorderLayout.CENTER)
            add(noticePanel, BorderLayout.SOUTH)
        }

        panel.add(inputPanel, BorderLayout.SOUTH)

        // Add a welcome message
        addMessageToChat(
            "Assistant",
            "Hello! I'm your AI assistant. How can I help you today? You can reference files with @filename.ext syntax.",
            "assistant"
        )

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
            openRouterClient.sendPrompt(
                project,
                processedPrompt,
                fileContents,
                object : OpenRouterClient.ResponseCallback {
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
    private fun createReadablePrompt(
        prompt: String,
        fileContents: Map<String, String>,
        fileReferences: List<String>
    ): String {
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
     * Process the message to properly format markdown elements including code blocks
     */
    private fun processMessage(message: String): String {
        // First, escape HTML
        var processed = escapeHtml(message)

        // Replace newlines with <br> tags to preserve line breaks
        processed = processed.replace("\n", "<br>")

        // Process code blocks
        val codeBlockPattern = Regex("```([a-zA-Z0-9]*)?\\s*<br>([\\s\\S]*?)<br>```")
        processed = processed.replace(codeBlockPattern) { matchResult ->
            val language = matchResult.groupValues[1].trim()
            // Replace <br> tags back to newlines within code blocks
            val code = matchResult.groupValues[2].replace("<br>", "\n")

            // Generate a unique ID for this code snippet
            val codeId = "code-" + UUID.randomUUID().toString()

            // Store the code in our map for later copying
            codeSnippets[codeId] = code

            // Create HTML with a copy button
            """
        <div class="code-block">
            ${if (language.isNotEmpty()) "<div class=\"code-language\">$language</div>" else ""}
            <a href="copy:$codeId" class="copy-btn">Copy</a>
            <pre>${code.replace("\n", "<br>")}</pre>
        </div>
        """.trimIndent()
        }

        // Process inline code
        val inlineCodePattern = Regex("`([^`]+)`")
        processed = processed.replace(inlineCodePattern) { matchResult ->
            val code = matchResult.groupValues[1]
            "<span class=\"inline-code\">$code</span>"
        }

        // Process bold text
        processed = processed.replace(Regex("\\*\\*([^*]+)\\*\\*")) { matchResult ->
            "<strong>${matchResult.groupValues[1]}</strong>"
        }

        // Process italic text
        processed = processed.replace(Regex("\\*([^*]+)\\*")) { matchResult ->
            "<em>${matchResult.groupValues[1]}</em>"
        }

        // Process unordered lists
        processed = processed.replace(Regex("<br>- ([^<]+)")) { matchResult ->
            "<br>• ${matchResult.groupValues[1]}"
        }

        // Process ordered lists (simple implementation)
        processed = processed.replace(Regex("<br>(\\d+)\\. ([^<]+)")) { matchResult ->
            "<br>${matchResult.groupValues[1]}. ${matchResult.groupValues[2]}"
        }

        return processed
    }

    /**
     * Add a message to the chat history
     */
    private fun addMessageToChat(sender: String, message: String, cssClass: String) {
        val doc = chatHistoryPane.document as HTMLDocument
        val kit = chatHistoryPane.editorKit as HTMLEditorKit

        // Process message to format it properly
        val processedMessage = if (cssClass.startsWith("assistant")) {
            processMessage(message)
        } else {
            // For user messages, just handle newlines and escape HTML
            escapeHtml(message).replace("\n", "<br>")
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
        <div class="message-content">${processMessage(newContent)}</div>
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
