package ca.corbett.mcp;

public enum McpError {
    PARSE_ERROR(-32700),
    RESOURCE_NOT_FOUND(-32002),
    PROMPT_NOT_FOUND(-32003),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603);

    private final int code;

    McpError(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
