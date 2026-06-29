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
     * Null/empty is allowed, but not recommended - give users of your tool an idea of what it does!
     */
    String getDescription();

    /**
     * Define the expected input parameters for your tool.
     * Null is perfectly acceptable if your tool takes no parameters, but an empty map is preferred.
     * <p>
     * <b>EXAMPLE:</b>
     * </p>
     * <pre>
     * return Map.of(
     *   "type", "object",
     *   "properties", Map.of(
     *     "arg1", Map.of(
     *       "type", "string",
     *       "description", "The first argument (mandatory)."
     *     ),
     *     "arg2", Map.of(
     *       "type", "integer",
     *       "description", "The second argument (optional)."
     *     )
     *   ),
     *   // Optionally mark an argument as required:
     *   "required", new String[]{"arg1"}
     * );
     * </pre>
     */
    Map<String, Object> getInputSchema();

    /**
     * This is invoked by McpServer when your tool is called.
     * If you wish to signal a tool call failure, throw any Exception with a helpful message.
     * McpServer will catch it and return your message to the caller.
     */
    String execute(Map<String, Object> input) throws Exception;
}
