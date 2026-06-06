package ca.corbett.mcp;

import java.util.Map;

public class McpRequest {
    public String jsonrpc = "2.0";
    public Object id;
    public String method;
    public Map<String, Object> params;
}
