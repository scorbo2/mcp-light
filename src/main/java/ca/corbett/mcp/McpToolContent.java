package ca.corbett.mcp;

class McpToolContent {
    public String type;
    public String text;
    public boolean isError;
    public McpToolContent(String type, String text, boolean isError) {
        this.type = type;
        this.text = text;
        this.isError = isError;
    }
}
