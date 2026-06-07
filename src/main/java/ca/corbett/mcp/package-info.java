package ca.corbett.mcp;
/**
 * Contains a lightweight Java library that can be used to implement an embedded
 * MCP server in a Java application. Refer to the
 * <a href="https://github.com/scorbo2/mcp-light">Project GitHub page</a> for more information.
 * <p><b>Brief usage guide</b></p>
 * <ol>
 *     <li>Implement the {@link ca.corbett.mcp.McpTool} interface to define tools that can be called by clients.</li>
 *     <li>Implement the {@link ca.corbett.mcp.McpResource} interface to define resources that can
 *     be accessed by clients. Resources are typically used to serve static files, but they can also
 *     be used to serve dynamic content such as HTML templates.</li>
 *     <li>Create an instance of {@link ca.corbett.mcp.McpServer} and register your tools and
 *     resources with it using the registerTool() and registerResource() methods.</li>
 *     <li>Start the server using the start() method. Your tools and resources will
 *     then be accessible to clients via JSON-RPC requests sent to the server's endpoint
 *     (port 8080 on /mcp by default).</li>
 * </ol>
 */