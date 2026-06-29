package ca.corbett.mcp;

import java.util.List;
import java.util.Map;

/**
 * Implement this interface to create a prompt that can be retrieved by the McpServer.
 * Register your prompt via the McpServer.registerPrompt() method.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public interface McpPrompt {

    /**
     * Return a non-blank name for your prompt. Should be short and alphanumeric, with no spaces.
     */
    String getName();

    /**
     * Return a helpful description of what your prompt does.
     */
    String getDescription();

    /**
     * Return the list of arguments that can be supplied by the caller to customize the prompt.
     * Each argument should have a name, description, and optional required flag.
     * Return an empty list if your prompt takes no arguments.
     */
    List<McpPromptArgument> getArguments();

    /**
     * Return the messages that make up this prompt. These will be returned to the caller
     * and typically represent a system or user message template.
     *
     * @param arguments The arguments supplied by the caller, or an empty map if none were provided.
     * @return A list of messages that form the prompt.
     * @throws Exception If an error occurs while generating the prompt messages.
     */
    List<McpPromptMessage> getMessages(Map<String, Object> arguments) throws Exception;
}
