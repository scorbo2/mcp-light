package ca.corbett.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
class McpResourceContent {
    public String uri;
    public String mimeType;
    public String text;
    public String blob;

    public McpResourceContent(String uri, String mimeType, String text, String blob) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.text = text;
        this.blob = blob;
    }
}
