package ca.corbett.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a message returned by a prompt's getMessages() method.
 * Each message has a role and a content object per the MCP protocol spec.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpPromptMessage {

    public String role;
    public McpPromptContent content;

    public McpPromptMessage(String role, String text) {
        this.role = role;
        this.content = new McpPromptContent("text", text);
    }

    public McpPromptMessage(String role, McpPromptContent content) {
        this.role = role;
        this.content = content;
    }
}
