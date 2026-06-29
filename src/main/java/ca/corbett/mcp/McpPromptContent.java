package ca.corbett.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the content of a prompt message according to the MCP protocol.
 * Content can be text or image (binary).
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpPromptContent {

    public String type;
    public String text;
    public String data;
    public String mimeType;

    public McpPromptContent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public McpPromptContent(String type, String data, String mimeType) {
        this.type = type;
        this.data = data;
        this.mimeType = mimeType;
    }
}
