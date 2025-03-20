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

    // Get settings with lazy initialization to avoid early service access
    private val settings by lazy { service<LlmPluginSettings>() }

    // Initialize the FileReferenceParser lazily
    private val fileReferenceParser by lazy { FileReferenceParser(project) }

    // Initialize the OpenRouterClient lazily
    private val openRouterClient by lazy { OpenRouterClient() }

    // Model selector combo box
    private val modelSelectorComboBox by lazy {
        JComboBox<String>().apply {
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
            background =
                if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
            foreground =
                if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
        }
    }

    // Lazily initialize all other UI components
    private val chatContextComboBox by lazy {
        JComboBox<com.example.llmplugin.settings.ChatData>().apply {
            maximumSize = Dimension(200, 30)
            preferredSize = Dimension(200, 30)
            background =
                if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
            foreground =
                if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK

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
    }

    private val newChatButton by lazy {
        JButton("+").apply {
            toolTipText = "Create new chat"
            preferredSize = Dimension(30, 30)
            background =
                if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
            foreground =
                if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
            isFocusPainted = false
            cursor = Cursor(Cursor.HAND_CURSOR)
        }
    }

    private val deleteChatButton by lazy {
        JButton("×").apply {
            toolTipText = "Delete current chat"
            preferredSize = Dimension(30, 30)
            background =
                if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D)) else JBColor.WHITE
            foreground = if (isDarkTheme()) JBColor(
                Color(0xFF6B68),
                Color(0xFF6B68)
            ) else JBColor(Color(0xE53935), Color(0xE53935))
            isFocusPainted = false
            cursor = Cursor(Cursor.HAND_CURSOR)
        }
    }

    // Components
    // In your chatHistoryPane initialization
    // In your chatHistoryPane initialization
    // In your chatHistoryPane initialization
    private val chatHistoryPane by lazy {
        JTextPane().apply {
            contentType = "text/html"
            isEditable = false
            border = EmptyBorder(10, 10, 10, 10)

            // Set dark background explicitly if in dark theme
            if (isDarkTheme()) {
                background = JBColor(Color(0x2B2B2B), Color(0x2B2B2B))
                foreground = JBColor(Color(0xBBBBBB), Color(0xBBBBBB))
            } else {
                background = JBColor(Color(0xF0F0F0), Color(0xF0F0F0))
                foreground = JBColor(Color(0x333333), Color(0x333333))
            }

            // Apply CSS styles with improved design and dark theme support
            val editorKit = HTMLEditorKit()
            document = HTMLDocument()
            this.editorKit = editorKit

            // Add stylesheet with explicit dark theme support
            val styleSheet = editorKit.styleSheet

            // Set global styles
            styleSheet.addRule("""
            body { 
                font-family: 'JetBrains Sans', sans-serif; 
                margin: 10px;
                padding: 0;
                ${if (isDarkTheme()) "background-color: #2B2B2B; color: #BBBBBB;"
            else "background-color: #F0F0F0; color: #333333;"}
            }
        """)

            // Message container styling
            styleSheet.addRule("""
            .message-container {
                display: block;
                position: relative;
                margin: 20px 0;
                max-width: 85%;
                clear: both;
            }
        """)

            // User message styling
            styleSheet.addRule("""
            .user-bubble {
                float: right;
                ${if (isDarkTheme())
                "background-color: #313D4F; color: #FFFFFF;"
            else
                "background-color: #E3F2FD; color: #1A1A1A;"}
                border-radius: 18px 18px 4px 18px;
                padding: 12px 16px;
                box-shadow: 0 1px 2px rgba(0,0,0,0.2);
                margin-left: 50px;
            }
        """)

            // Assistant message styling
            styleSheet.addRule("""
            .assistant-bubble {
                float: left;
                ${if (isDarkTheme())
                "background-color: #3C3F41; color: #FFFFFF;"
            else
                "background-color: #FFFFFF; color: #1A1A1A;"}
                border-radius: 18px 18px 18px 4px;
                padding: 12px 16px;
                box-shadow: 0 1px 2px rgba(0,0,0,0.2);
                margin-right: 50px;
            }
        """)

            // Message sender styling
            styleSheet.addRule("""
            .sender {
                font-weight: bold;
                margin-bottom: 8px;
                font-size: 14px;
                ${if (isDarkTheme())
                ".user-bubble & { color: #82AAFF; } .assistant-bubble & { color: #C3E88D; }"
            else
                ".user-bubble & { color: #1565C0; } .assistant-bubble & { color: #2E7D32; }"}
            }
        """)

            // Code block styling
            styleSheet.addRule("""
            pre {
                ${if (isDarkTheme())
                "background-color: #2A2A2A; border: 1px solid #424242; color: #A9B7C6;"
            else
                "background-color: #F8F9FA; border: 1px solid #E0E0E0; color: #333333;"}
                border-radius: 8px;
                padding: 12px;
                margin: 10px 0;
                font-family: 'JetBrains Mono', monospace;
                font-size: 13px;
                white-space: pre-wrap !important;
                word-wrap: break-word;
                overflow-x: auto;
            }
        """)

            // Code header styling
            styleSheet.addRule("""
            .code-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 8px 12px;
                ${if (isDarkTheme())
                "background-color: #383838; border: 1px solid #424242; color: #BBBBBB;"
            else
                "background-color: #EEEEEE; border: 1px solid #E0E0E0; color: #555555;"}
                border-top-left-radius: 8px;
                border-top-right-radius: 8px;
                border-bottom: none;
                font-size: 12px;
            }
        """)

            // Code block with header
            styleSheet.addRule("""
            .code-block {
                margin: 10px 0;
            }
            
            .code-block pre {
                margin-top: 0;
                border-top-left-radius: 0;
                border-top-right-radius: 0;
            }
        """)

            // Copy button styling
            styleSheet.addRule("""
            .copy-button {
                ${if (isDarkTheme())
                "color: #64B5F6;"
            else
                "color: #1565C0;"}
                cursor: pointer;
                text-decoration: underline;
                font-size: 12px;
            }
        """)

            // Inline code styling
            styleSheet.addRule("""
            code {
                ${if (isDarkTheme())
                "background-color: #383838; color: #A9B7C6;"
            else
                "background-color: #F0F0F0; color: #333333;"}
                padding: 2px 4px;
                border-radius: 3px;
                font-family: 'JetBrains Mono', monospace;
                font-size: 90%;
                white-space: pre-wrap !important;
            }
        """)

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

            // Set font
            font = JBUI.Fonts.create("JetBrains Sans", 13)
        }
    }

    // Create scroll pane with custom background color based on theme
    // Create scroll pane with correct background color for dark theme
    private val scrollPane by lazy {
        JBScrollPane(chatHistoryPane).apply {
            border = EmptyBorder(0, 0, 0, 0)
            verticalScrollBar.unitIncrement = 16

            // Set background explicitly based on theme
            if (isDarkTheme()) {
                background = JBColor(Color(0x2B2B2B), Color(0x2B2B2B))
                viewport.background = JBColor(Color(0x2B2B2B), Color(0x2B2B2B))
            } else {
                background = JBColor(Color(0xF0F0F0), Color(0xF0F0F0))
                viewport.background = JBColor(Color(0xF0F0F0), Color(0xF0F0F0))
            }
        }
    }

    // Modern-looking text area for input
    private val promptTextArea by lazy {
        JTextArea().apply {
            // Create a more modern border with rounded corners
            val borderColor = if (isDarkTheme()) JBColor(Color(0x3D3D3D), Color(0x3D3D3D))
            else JBColor(Color(0xDFDFDF), Color(0xDFDFDF))
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            )
            lineWrap = true
            wrapStyleWord = true
            rows = 3
            font = JBUI.Fonts.create("JetBrains Sans", 13)
            background =
                if (isDarkTheme()) JBColor(Color(0x333333), Color(0x333333)) else JBColor.WHITE
            foreground =
                if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK
            caretColor =
                if (isDarkTheme()) JBColor(Color(0xD4D4D4), Color(0xD4D4D4)) else JBColor.BLACK

            // Set placeholder text with a more subtle color
            text = "Type your prompt here (use @file.txt to include file contents)"
            foreground = if (isDarkTheme()) JBColor(
                Color(0x787878),
                Color(0x787878)
            ) else JBColor(Color(0x787878), Color(0x787878))

            addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusGained(e: java.awt.event.FocusEvent?) {
                    if (text == "Type your prompt here (use @file.txt to include file contents)") {
                        text = ""
                        foreground = if (isDarkTheme()) JBColor(
                            Color(0xD4D4D4),
                            Color(0xD4D4D4)
                        ) else JBColor.BLACK
                    }
                }

                override fun focusLost(e: java.awt.event.FocusEvent?) {
                    if (text.trim().isEmpty()) {
                        text = "Type your prompt here (use @file.txt to include file contents)"
                        foreground =
                            if (isDarkTheme()) JBColor(
                                Color(0x787878),
                                Color(0x787878)
                            ) else JBColor(
                                Color(0x787878),
                                Color(0x787878)
                            )
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
    }

    // Modern-looking send button with icon support
    private val sendButton by lazy {
        JButton("Send").apply {
            preferredSize = Dimension(100, 36)

            // Use JetBrains UI colors for better integration
            val accentColor = if (isDarkTheme()) JBColor(Color(0x0E639C), Color(0x0E639C))
            else JBColor(Color(0x4285F4), Color(0x4285F4))
            val hoverColor = if (isDarkTheme()) JBColor(Color(0x1177BB), Color(0x1177BB))
            else JBColor(Color(0x3367D6), Color(0x3367D6))

            background = accentColor
            foreground = JBColor.WHITE

            // Create a more modern border with rounded corners
            border = JBUI.Borders.empty(0, 10)
            isFocusPainted = false
            cursor = Cursor(Cursor.HAND_CURSOR)

            // Use JetBrains Sans font for better integration
            font = JBUI.Fonts.create("JetBrains Sans", 13).asBold()

            addActionListener { sendPrompt() }

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    background = hoverColor
                }

                override fun mouseExited(e: MouseEvent?) {
                    background = accentColor
                }
            })
        }
    }

    // Checkbox for "Copy prompt only" mode with modern styling
    private val copyPromptOnlyCheckBox by lazy {
        JBCheckBox("Copy prompt only").apply {
            toolTipText =
                "When checked, prompts will be copied to clipboard instead of being sent to OpenRouter"
            foreground =
                if (isDarkTheme()) JBColor(Color(0xA0A0A0), Color(0xA0A0A0)) else JBColor.GRAY
            isOpaque = false
        }
    }

    // Checkbox for "Include message history" mode
    private val includeMessageHistoryCheckBox by lazy {
        JBCheckBox("Include message history").apply {
            toolTipText =
                "When checked, previous messages will be included in the context sent to the LLM"
            foreground =
                if (isDarkTheme()) JBColor(Color(0xA0A0A0), Color(0xA0A0A0)) else JBColor.GRAY
            isOpaque = false
            isSelected = settings.includeMessageHistory
        }
    }

    // Panel for the notice when copy mode is enabled
    private val noticePanel by lazy {
        JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    1, 0, 0, 0,
                    if (isDarkTheme()) JBColor(
                        Color(0x3E3E3E),
                        Color(0x3E3E3E)
                    ) else JBColor.LIGHT_GRAY
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
    }

    // Keep track of the last assistant message element
    private var lastAssistantMessageElement: Element? = null
    private var assistantMessageContent = StringBuilder()

    // Store original file references from the prompt
    private val fileReferenceRegex = Regex("@([\\w.-/]+)")

    init {
        try {
            // Set listener for checkbox to show/hide notice panel
            copyPromptOnlyCheckBox.addActionListener {
                noticePanel.isVisible = copyPromptOnlyCheckBox.isSelected
            }

            // Set listener for include message history checkbox
            includeMessageHistoryCheckBox.addActionListener {
                settings.includeMessageHistory = includeMessageHistoryCheckBox.isSelected
            }

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
        } catch (e: Exception) {
            // Log initialization errors but don't crash
            e.printStackTrace()
        }
    }

    /**
     * Updates the chat context combo box with available chats
     */
    private fun updateChatContextComboBox() {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads the chat history for the current chat
     */
    private fun loadChatHistory() {
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Clears the chat history display
     */
    private fun clearChatHistory() {
        try {
            val doc = chatHistoryPane.document as HTMLDocument
            doc.remove(0, doc.length)
            lastAssistantMessageElement = null
            assistantMessageContent = StringBuilder()
            codeSnippets.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if dark theme is active in the IDE
     */
    /**
     * Check if dark theme is active in the IDE
     */
    private fun isDarkTheme(): Boolean {
        // Multiple checks to ensure accurate theme detection
        return UIUtil.isUnderDarcula() ||
                JBColor.isBright() == false ||
                UIManager.getColor("Panel.background")?.let {
                    it.red < 100 && it.green < 100 && it.blue < 100
                } ?: false
    }

    /**
     * Build the main content panel
     */
    fun getContent(): JPanel {
        try {
            val panel = JPanel(BorderLayout())
            panel.background =
                if (isDarkTheme()) JBColor(Color(0x1E1E1E), Color(0x1E1E1E)) else JBColor.WHITE

            // Add header panel with title and model selector
            val headerPanel = JPanel(BorderLayout()).apply {
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
                        Color(0xF5F5F5), Color(0xF5F5F5)
                    )
                border = MatteBorder(
                    0, 0, 1, 0,
                    if (isDarkTheme()) JBColor(
                        Color(0x3E3E3E),
                        Color(0x3E3E3E)
                    ) else JBColor.LIGHT_GRAY
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
                        if (isDarkTheme()) JBColor(
                            Color(0xD4D4D4),
                            Color(0xD4D4D4)
                        ) else JBColor.BLACK
                    title.border = EmptyBorder(0, 5, 0, 0)

                    add(title)
                    add(chatContextComboBox)
                    add(newChatButton)
                    add(deleteChatButton)
                }

                add(leftPanel, BorderLayout.WEST)
            }

            panel.add(headerPanel, BorderLayout.NORTH)

            // Chat history area with scroll
            panel.add(scrollPane, BorderLayout.CENTER)

            // Input area panel
            val inputPanel = JPanel(BorderLayout()).apply {
                background =
                    if (isDarkTheme()) JBColor(Color(0x2D2D2D), Color(0x2D2D2D)) else JBColor(
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

            // Initialize chat contexts and add a welcome message
            SwingUtilities.invokeLater {
                try {
                    // Initialize the chat context combo box
                    updateChatContextComboBox()

                    // Add a welcome message
                    addMessageToChat(
                        "Assistant",
                        "Hello! I'm your AI assistant. How can I help you today? You can reference files with @filename.ext syntax.",
                        "assistant"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return panel
        } catch (e: Exception) {
            // If initialization fails, display error panel
            e.printStackTrace()
            val errorPanel = JPanel(BorderLayout())
            errorPanel.add(
                JLabel("Error initializing chat window. Please check logs."),
                BorderLayout.CENTER
            )
            return errorPanel
        }
    }

    /**
     * Copy the code snippet with the given ID to the clipboard
     */
    private fun copyCodeSnippet(codeId: String) {
        try {
            codeSnippets[codeId]?.let { code ->
                // Use StringSelection with the raw code string
                val selection = StringSelection(code)
                CopyPasteManager.getInstance().setContents(selection)

                // Show a notification
                SwingUtilities.invokeLater {
                    Messages.showInfoMessage(
                        "Code snippet copied to clipboard with preserved formatting.",
                        "Code Copied"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Update the last assistant message with new content
     */
    private var tokenBuffer = StringBuilder()
    private var isThinking = true  // Track if we're in thinking mode
    private var tokenCount = 0     // Track number of tokens to batch updates

    private fun updateLastAssistantMessage(token: String) {
        try {
            // Add token to buffers
            tokenBuffer.append(token)
            assistantMessageContent.append(token)
            tokenCount++

            // Check if we're still in thinking mode
            if (isThinking) {
                // If we get meaningful content, transition out of thinking mode
                val content = tokenBuffer.toString().trim()
                if (content.isNotEmpty()) {
                    isThinking = false
                    SwingUtilities.invokeLater {
                        // Replace "Thinking..." with the first batch of content
                        replaceLastAssistantMessage(assistantMessageContent.toString())
                    }
                    tokenBuffer.clear()
                    tokenCount = 0
                }
            } else {
                // Only update UI periodically to avoid spam (every 10 tokens or when we have a sentence)
                val shouldUpdate = tokenCount >= 10 ||
                        tokenBuffer.toString().contains(".") ||
                        tokenBuffer.toString().contains("\n")

                if (shouldUpdate) {
                    SwingUtilities.invokeLater {
                        replaceLastAssistantMessage(assistantMessageContent.toString())
                    }
                    tokenBuffer.clear()
                    tokenCount = 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Replace the last assistant message with new content
     */
    private fun replaceLastAssistantMessage(
        newContent: String,
        generationId: String = "",
        cost: Double? = null
    ) {
        try {
            val doc = chatHistoryPane.document as HTMLDocument

            if (lastAssistantMessageElement != null) {
                // Replace the content of the last assistant message
                val startOffset = lastAssistantMessageElement!!.startOffset
                val endOffset = lastAssistantMessageElement!!.endOffset

                try {
                    doc.remove(startOffset, endOffset - startOffset)
                    val kit = chatHistoryPane.editorKit as HTMLEditorKit

                    // Create updated HTML with improved style
                    val costDisplay = if (cost != null)
                        " <span style='color:${if (isDarkTheme()) "#81C784" else "#4CAF50"}; font-size:12px; background-color:rgba(76,175,80,0.1); padding:3px 6px; border-radius:10px; margin-left:6px;'>$${String.format("%.5f", cost)}</span>"
                    else ""

                    val html = """
                <div class="message-container">
                    <div class="assistant-bubble">
                        <div class="sender">Assistant$costDisplay</div>
                        <div class="content">${processMessage(newContent)}</div>
                    </div>
                    <div style="clear:both;"></div>
                </div>
                """

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
                    e.printStackTrace()
                    lastAssistantMessageElement = null
                    addMessageToChat("Assistant", newContent, "assistant", generationId, cost)
                }
            } else {
                // Fallback: add as new message
                addMessageToChat("Assistant", newContent, "assistant", generationId, cost)
            }
        } catch (e: Exception) {
            // Log the error but don't crash
            e.printStackTrace()
            // Try to add as a new message instead
            try {
                addMessageToChat("Assistant", newContent, "assistant", generationId, cost)
            } catch (innerEx: Exception) {
                innerEx.printStackTrace()
            }
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
        try {
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
                val readablePrompt =
                    createReadablePrompt(processedPrompt, fileContents, fileReferences)

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
                            // Reset buffers and state
                            tokenBuffer = StringBuilder()
                            assistantMessageContent = StringBuilder()
                            isThinking = true
                            tokenCount = 0

                            // Add "Thinking..." message
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
                            // Final update with the complete response
                            SwingUtilities.invokeLater {
                                tokenBuffer.clear()
                                tokenCount = 0
                                isThinking = false
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
                            if (cost != null && generationId.isNotEmpty()) {
                                SwingUtilities.invokeLater {
                                    updateMessageCost(generationId, cost)
                                }
                            }
                        }
                    })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Display error to user
            Messages.showErrorDialog(
                "Error sending prompt: ${e.message}",
                "Error"
            )
        }
    }

    private fun updateMessageCost(generationId: String, cost: Double) {
        try {
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
                                break
                            }
                        }
                    }

                    // Reload the entire chat to show updated costs
                    // This is a simpler approach than trying to update individual elements
                    loadChatHistory()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Extract file references from the prompt text
     */
    private fun extractFileReferences(text: String): List<String> {
        val matches = fileReferenceRegex.findAll(text)
        return matches.map { it.groupValues[1] }.toList()
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
        sb.append(prompt)
        sb.append("\n\n")

        // Add file contents
        for (fileRef in fileReferences) {
            val content = fileContents[fileRef]
            if (content != null) {
                sb.append("--- File: $fileRef ---\n")
                sb.append(content)
                sb.append("\n\n")
            }
        }

        return sb.toString()
    }

    /**
     * Process message content for display
     */
    /**
     * Process message content for display with proper formatting
     */
    private fun processMessage(content: String): String {
        try {
            // Process code blocks
            var processedContent = content

            // Match code blocks with language specification
            val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\\s*\\n([\\s\\S]*?)```")
            val codeBlocks = codeBlockRegex.findAll(processedContent)

            for (match in codeBlocks) {
                val language = match.groupValues[1].trim().ifEmpty { "text" }
                val code = match.groupValues[2]
                val codeId = UUID.randomUUID().toString()

                // Store code for copy functionality
                codeSnippets[codeId] = code

                // Create a more attractive code block with header
                // Critical: Replace \n with <br> but preserve the actual newlines as well
                // This ensures newlines are preserved when selecting with a mouse

                // First convert all newlines to <br>
                val htmlFormattedCode = escapeHtml(code).replace("\n", "&#10;<br>")

                val codeHtml = """
                <div class="code-block">
                    <div class="code-header">
                        <span>${language}</span>
                        <a href="copy:$codeId" class="copy-button">Copy</a>
                    </div>
                    <pre style="white-space:pre-wrap; word-wrap:break-word;"><code style="white-space:pre-wrap !important;">$htmlFormattedCode</code></pre>
                </div>
            """

                processedContent = processedContent.replace(match.value, codeHtml)
            }

            // Process markdown-style formatting
            // Bold text
            processedContent =
                processedContent.replace(Regex("\\*\\*(.*?)\\*\\*|__(.*?)__")) { matchResult ->
                    val text = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
                    "<strong>$text</strong>"
                }

            // Process inline code
            processedContent = processedContent.replace(Regex("`([^`]+)`")) { matchResult ->
                // Use the same approach for inline code to preserve newlines
                val codeText = escapeHtml(matchResult.groupValues[1]).replace("\n", "&#10;<br>")
                "<code style='background-color:#F0F0F0; padding:2px 4px; border-radius:3px; font-family:\"JetBrains Mono\", monospace; white-space:pre-wrap;'>$codeText</code>"
            }

            // Convert line breaks to <br> tags
            processedContent = processedContent.replace("\n", "<br>")

            return processedContent
        } catch (e: Exception) {
            e.printStackTrace()
            return content.replace("\n", "<br>")
        }
    }

    /**
     * Add a message to the chat history with proper styling
     */
    private fun addMessageToChat(
        sender: String,
        content: String,
        cssClass: String,
        generationId: String = "",
        cost: Double? = null
    ) {
        try {
            val doc = chatHistoryPane.document as HTMLDocument
            val kit = chatHistoryPane.editorKit as HTMLEditorKit

            // Create a more visually appealing message
            val bubbleClass = if (cssClass == "user") "user-bubble" else "assistant-bubble"

            val costDisplay = if (cost != null)
                " <span style='color:${if (isDarkTheme()) "#81C784" else "#4CAF50"}; font-size:12px; background-color:rgba(76,175,80,0.1); padding:3px 6px; border-radius:10px; margin-left:6px;'>$${String.format("%.5f", cost)}</span>"
            else ""

            val html = """
        <div class="message-container">
            <div class="$bubbleClass">
                <div class="sender">$sender$costDisplay</div>
                <div class="content">${processMessage(content)}</div>
            </div>
            <div style="clear:both;"></div>
        </div>
        """

            kit.insertHTML(doc, doc.length, html, 0, 0, null)

            // If this is an assistant message, store a reference to it
            if (cssClass == "assistant" || cssClass == "assistant-thinking") {
                val root = doc.defaultRootElement
                val index = root.elementCount - 1
                if (index >= 0) {
                    lastAssistantMessageElement = root.getElement(index)
                }
            }

            // Scroll to bottom
            chatHistoryPane.caretPosition = doc.length
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val doc = chatHistoryPane.document
                doc.insertString(doc.length, "\n$sender: $content\n", null)
                chatHistoryPane.caretPosition = doc.length
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}


