package ca.corbett.mcp;

import java.util.Map;

/**
 * Implement this interface to create a tool that can be called by the McpServer.
 * Register your tool via the McpServer.registerTool() method.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public interface McpTool {

    /**
     * Return a non-blank name for your tool. Should be short and alphanumeric, with no spaces.
     */
    String getName();

    /**
     * Return a helpful description of what your tool does.
     */
    String getDescription();

    /**
     * Define the expected input parameters for your tool.
     * TODO give an example or two, as this one might be non-obvious.
     */
    Map<String, Object> getInputSchema();

    /**
     * This is invoked by McpServer when your tool is called.
     * If you wish to signal a tool call failure, throw any Exception with a helpful message.
     * McpServer will catch it and return your message to the caller.
     */
    String execute(Map<String, Object> input) throws Exception;
}
