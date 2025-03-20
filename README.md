# LLM Plugin for JetBrains IDEs

A JetBrains IDE plugin that allows you to reference project files in prompts sent to OpenRouter LLM API.

## Features

- Chat UI with input field and send button
- File reference syntax like `@my_file.txt` to include file contents
- Support for folder references (`@my_folder`) with recursive parsing
- Integration with OpenRouter API
- Streaming responses from the LLM

## How to Use


### easy installation
[llmplugin-1.0-SNAPSHOT.zip](build/distributions/llmplugin-1.0-SNAPSHOT.zip)

get zip with plugin and 
- Go to Settings/Preferences > Plugins
- Click "Install Plugin from Disk..."

### Installation

1. Build the plugin from source:
   ```
   ./gradlew buildPlugin
   ```
2. Install the plugin in your JetBrains IDE:
   - Go to Settings/Preferences > Plugins
   - Click "Install Plugin from Disk..."
   - Select the built plugin ZIP file from `build/distributions/`

### Configuration

1. Open Settings/Preferences > Tools > LLM Plugin Settings
2. Enter your OpenRouter API key
3. Configure the OpenRouter base URL (default: `https://openrouter.ai/api/v1`)
4. Select your preferred model

### Using the Plugin

1. Open the LLM Chat tool window (View > Tool Windows > LLM Chat)
2. Type your prompt in the input field
3. Reference files using the `@` syntax:
   - `@file.txt` to include a specific file
   - `@folder` to include all files in a folder (recursively)
4. Press Enter or click Send to submit your prompt
5. View the streaming response in the chat window

## Examples

```
Tell me what this code does: @src/main/kotlin/com/example/MyClass.kt
```

```
Explain the architecture of my project: @src
```

## Development

### Prerequisites

- JDK 17 or later
- Gradle 7.6 or later

### Initial Setup

Before building the plugin for the first time, you need to initialize the Gradle wrapper:

```
gradle wrapper --gradle-version 7.6
```

This will download the proper Gradle wrapper JAR file.

### Building

```
./gradlew buildPlugin
```

### Running in Development Mode

```
./gradlew runIde
```

## Troubleshooting

### Build Issues

If you encounter build errors related to the Gradle IntelliJ plugin, try the following:

1. If you see an error like `Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain`, you need to initialize the Gradle wrapper first:
   ```
   gradle wrapper --gradle-version 7.6
   ```

2. If you see an error like `class org.jetbrains.intellij.MemoizedProvider overrides final method...`, it's likely a compatibility issue between Gradle and the IntelliJ plugin. The project is configured to use compatible versions.

3. Run `./gradlew clean` before building again

### Runtime Issues

If the plugin doesn't work as expected:

1. Check that you've configured a valid OpenRouter API key in the settings
2. Verify that the file paths you're referencing with `@` syntax are correct
3. Check the IDE log for any error messages (Help > Show Log in Explorer/Finder)

## License

This project is licensed under the MIT License - see the LICENSE file for details.
