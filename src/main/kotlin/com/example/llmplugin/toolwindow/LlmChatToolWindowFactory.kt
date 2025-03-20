package com.example.llmplugin.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.SwingUtilities

/**
 * Factory for creating the LLM Chat tool window.
 * Fixed to ensure proper initialization.
 */
class LlmChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create UI on the Event Dispatch Thread to avoid initialization issues
        SwingUtilities.invokeLater {
            try {
                val chatToolWindow = LlmChatToolWindow(project)
                val contentFactory = ContentFactory.getInstance()
                val content = contentFactory.createContent(chatToolWindow.getContent(), null, false)
                toolWindow.contentManager.addContent(content)
            } catch (e: Exception) {
                // Log any errors during initialization
                e.printStackTrace()
            }
        }
    }
}