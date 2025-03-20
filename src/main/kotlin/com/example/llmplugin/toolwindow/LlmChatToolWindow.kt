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

import javax.swing.text.html.HTML
import javax.swing.text.StyleConstants

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
        foreground = if (isDarkTheme()) JBColor(
            Color(0xFF6B68),
            Color(0xFF6B68)
        ) else JBColor(Color(0xE53935), Color(0xE53935))
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

        // Set up basic document and editor kit without CSS styling
        this.editorKit = editorKit
        
        // Set background color based on theme
        background = if (isDarkTheme()) JBColor(Color(0x252526), Color(0x252526)) else JBColor.WHITE

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
        toolTipText =
            "When checked, previous messages will be included in the context sent to the LLM"
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
                val selectedChat =
                    chatContextComboBox.selectedItem as com.example.llmplugin.settings.ChatData
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
            // Create new chat and refresh UI state
            val newChatId = openRouterClient.createNewChat()
            updateChatContextComboBox()

            // Force selection of new chat in the combo box
            chatContextComboBox.selectedItem = openRouterClient.getCurrentChat()
            // Clear display and add fresh welcome message
            clearChatHistory()
            openRouterClient.addAssistantMessage("New chat started. How can I help you?")
            addMessageToChat(
                "Assistant",
                "New chat started. How can I help you?",
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

            // Add message with cost if available
            addMessageToChat(
                sender,
                message.content,
                cssClass,
                message.generationId ?: "",
                message.cost
            )

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
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
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

            // Model row
            val modelRowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                        Color(0xF5F5F5), Color(0xF5F5F5)
                    )
                add(JLabel("Model:").apply {
                    foreground = if (isDarkTheme()) JBColor(
                        Color(0xA0A0A0),
                        Color(0xA0A0A0)
                    ) else JBColor.GRAY
                })
                add(modelSelectorComboBox)
            }

            // Checkbox row
            val checkboxRowPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                        Color(0xF5F5F5), Color(0xF5F5F5)
                    )
                border = EmptyBorder(0, 0, 10, 0)
                add(copyPromptOnlyCheckBox)
                add(includeMessageHistoryCheckBox)
            }

            // Vertical panel for rows
            val rowsPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(modelRowPanel)
                add(checkboxRowPanel)
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

            add(rowsPanel, BorderLayout.NORTH)
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
    private fun replaceLastAssistantMessage(newContent: String, generationId: String = "", cost: Double? = null) {
        try {
            val doc = chatHistoryPane.document as HTMLDocument

            if (lastAssistantMessageElement != null) {
                // Replace the content of the last assistant message
                val startOffset = lastAssistantMessageElement!!.startOffset
                val endOffset = lastAssistantMessageElement!!.endOffset

                try {
                    doc.remove(startOffset, endOffset - startOffset)
                    val kit = chatHistoryPane.editorKit as HTMLEditorKit

                    // Create a very simple HTML structure with minimal styling
                    val html = """
                        <p><b>Assistant</b>${if (cost != null) " ($${String.format("%.5f", cost)})" else ""}</p>
                        <p>${processMessage(newContent)}</p>
                        <hr>
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
                    addMessageToChat("Assistant", newContent, "assistant", generationId, cost)
                }
            } else {
                // Fallback: add as new message
                addMessageToChat("Assistant", newContent, "assistant", generationId, cost)
            }
        } catch (e: Exception) {
            // Log the error but don't crash
            println("Error replacing assistant message: ${e.message}")
            e.printStackTrace()
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

                    override fun onComplete(fullResponse: String, generationId: String) {
                        // Final update to the assistant's message
                        SwingUtilities.invokeLater {
                            replaceLastAssistantMessage(fullResponse, generationId)
                        }
                    }

                    override fun onError(error: String) {
                        // Show error message
                        SwingUtilities.invokeLater {
                            replaceLastAssistantMessage("Error: $error")
                        }
                    }

                    override fun onCostUpdate(generationId: String, cost: Double?) {
                        println("DEBUG: onCostUpdate called with ID: $generationId, cost: $cost")
                        if (cost != null && generationId.isNotEmpty()) {
                            SwingUtilities.invokeLater {
                                updateMessageCost(generationId, cost)
                            }
                        }
                    }
                })
        }
    }



    private fun updateMessageCost(generationId: String, cost: Double) {
        println("DEBUG: Updating message cost for ID: $generationId, cost: $cost")


        // Update the UI
        SwingUtilities.invokeLater {
            try {
                // Find the chat containing this message ID
                val chat = openRouterClient.getCurrentChat()
                if (chat != null) {
                    // Update the cost in the message object
                    for (message in chat.messages) {
                        if (message.generationId == generationId) {
                            message.cost = cost
                            println("DEBUG: Updated cost in message object: $generationId")
                            break
                        }
                    }
                }

                // Reload the entire chat to show updated costs
                // This is a simpler approach than trying to update individual elements
                loadChatHistory()

                println("DEBUG: Chat history reloaded with costs")
            } catch (e: Exception) {
                println("DEBUG: Error updating cost: ${e.message}")
                e.printStackTrace()
            }
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
        try {
            // First, escape HTML
            var processed = escapeHtml(message)

            // Replace newlines with <br> tags to preserve line breaks
            processed = processed.replace("\n", "<br>")

            // Process code blocks with extremely simple HTML
            val codeBlockPattern = Regex("```([a-zA-Z0-9]*)?\\s*<br>([\\s\\S]*?)<br>```")
            processed = processed.replace(codeBlockPattern) { matchResult ->
                val language = matchResult.groupValues[1].trim()
                val code = matchResult.groupValues[2].replace("<br>", "\n")

                // Generate a unique ID for this code snippet
                val codeId = "code-" + UUID.randomUUID().toString()
                codeSnippets[codeId] = code

                // Create very simple HTML for code blocks
                """
                <p><b>Code${if (language.isNotEmpty()) " ($language)" else ""}:</b> <a href="copy:$codeId">[Copy]</a></p>
                <pre>${code.replace("\n", "<br>")}</pre>
                """.trimIndent()
            }

            // Process inline code with minimal styling
            val inlineCodePattern = Regex("`([^`]+)`")
            processed = processed.replace(inlineCodePattern) { matchResult ->
                "<code>${matchResult.groupValues[1]}</code>"
            }

            // Process basic formatting
            processed = processed.replace(Regex("\\*\\*([^*]+)\\*\\*")) { matchResult ->
                "<b>${matchResult.groupValues[1]}</b>"
            }
            processed = processed.replace(Regex("\\*([^*]+)\\*")) { matchResult ->
                "<i>${matchResult.groupValues[1]}</i>"
            }

            // Process lists with minimal formatting
            processed = processed.replace(Regex("<br>- ([^<]+)")) { matchResult ->
                "<br>• ${matchResult.groupValues[1]}"
            }
            processed = processed.replace(Regex("<br>(\\d+)\\. ([^<]+)")) { matchResult ->
                "<br>${matchResult.groupValues[1]}. ${matchResult.groupValues[2]}"
            }

            return processed
        } catch (e: Exception) {
            // If any error occurs during processing, return the original message with basic escaping
            println("Error processing message: ${e.message}")
            e.printStackTrace()
            return escapeHtml(message).replace("\n", "<br>")
        }
    }

    /**
     * Add a message to the chat history
     */
    private fun addMessageToChat(
        sender: String,
        message: String,
        cssClass: String,
        generationId: String = "",
        cost: Double? = null
    ) {
        try {
            val doc = chatHistoryPane.document as HTMLDocument
            val kit = chatHistoryPane.editorKit as HTMLEditorKit

            // Process message to format it properly
            val processedMessage = if (cssClass.startsWith("assistant")) {
                processMessage(message)
            } else {
                // For user messages, just handle newlines and escape HTML
                escapeHtml(message).replace("\n", "<br>")
            }

            // Create a very simple HTML structure with minimal styling
            val html = """
                <p><b>$sender</b>${if (cost != null) " ($${String.format("%.5f", cost)})" else ""}</p>
                <p>$processedMessage</p>
                <hr>
            """.trimIndent()

            kit.insertHTML(doc, doc.length, html, 0, 0, null)

            // Store reference to the last assistant message
            if (cssClass == "assistant-thinking") {
                val root = doc.defaultRootElement
                val index = root.elementCount - 1
                if (index >= 0) {
                    lastAssistantMessageElement = root.getElement(index)
                }
            }

            // Scroll to bottom
            chatHistoryPane.caretPosition = doc.length
        } catch (e: Exception) {
            // Log the error but don't crash
            println("Error adding message to chat: ${e.message}")
            e.printStackTrace()
        }
    }

}
