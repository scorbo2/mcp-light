package ca.corbett.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {
    public String jsonrpc = "2.0";
    public Object id;
    public Object result;
    public Map<String, Object> error;
}
