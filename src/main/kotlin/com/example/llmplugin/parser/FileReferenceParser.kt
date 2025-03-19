package com.example.llmplugin.parser

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.IOException

/**
 * Parses file references in the format @file.txt or @folder from the prompt
 * and extracts their contents.
 */
class FileReferenceParser(private val project: Project) {
    
    /**
     * Parses file references from the prompt and returns the processed prompt
     * along with a map of file paths to their contents.
     */
    fun parseFileReferences(prompt: String): Pair<String, Map<String, String>> {
        val fileContents = mutableMapOf<String, String>()
        val filePattern = Regex("@([\\w.-/]+)")
        
        // Find all file references
        val matches = filePattern.findAll(prompt)
        
        // Process each file reference
        for (match in matches) {
            val filePath = match.groupValues[1]
            try {
                val content = getFileOrFolderContent(filePath)
                fileContents[filePath] = content
            } catch (e: Exception) {
                // If file not found or can't be read, add an error message
                fileContents[filePath] = "Error reading file: ${e.message}"
            }
        }
        
        // Return the original prompt and the file contents
        return Pair(prompt, fileContents)
    }
    
    /**
     * Gets the content of a file or recursively processes a folder.
     */
    private fun getFileOrFolderContent(path: String): String {
        val basePath = project.basePath ?: return "Error: Project base path not found"
        val fullPath = "$basePath/$path"
        
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl("file://$fullPath")
            ?: throw IOException("File or folder not found: $path")
        
        return if (virtualFile.isDirectory) {
            // Process directory recursively
            processDirectory(virtualFile, 0)
        } else {
            // Read file content
            String(virtualFile.contentsToByteArray())
        }
    }
    
    /**
     * Recursively processes a directory and returns its structure and file contents.
     */
    private fun processDirectory(directory: VirtualFile, level: Int): String {
        val indent = "  ".repeat(level)
        val sb = StringBuilder()
        
        sb.append("$indent📁 ${directory.name}/\n")
        
        for (child in directory.children) {
            if (child.isDirectory) {
                // Recursively process subdirectory
                sb.append(processDirectory(child, level + 1))
            } else {
                // Add file with its content
                sb.append("$indent  📄 ${child.name}\n")
                try {
                    val content = String(child.contentsToByteArray())
                    sb.append("$indent  Content:\n")
                    
                    // Add indented content
                    val indentedContent = content.lines()
                        .joinToString("\n") { "$indent    $it" }
                    sb.append(indentedContent)
                    sb.append("\n\n")
                } catch (e: Exception) {
                    sb.append("$indent  Error reading file: ${e.message}\n\n")
                }
            }
        }
        
        return sb.toString()
    }
}
