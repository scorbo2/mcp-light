package ca.corbett.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * An extremely lightweight MCP server implementation for simple tool calling.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class McpServer {

    public static final String DEFAULT_PATH = "/mcp";
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_THREADS = 4;
    public static final int REQUEST_SIZE_LIMIT = 1024 * 1024 * 10; // 10 MB

    /**
     * This is OUR version, not the MCP protocol version that we understand.
     */
    public static final String VERSION = "1.0";

    /**
     * This is the version of the MCP protocol that we will report to clients.
     */
    public static final String MCP_VERSION = "2024-10-07";

    /**
     * We insist that tool names be alphanumeric with no spaces.
     * As far as I know, the protocol only insists that the first character should be a letter,
     * but we'll go a bit further and only allow letters, numbers, underscores, and hyphens.
     */
    private static final Pattern ALPHA_NUMERIC_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");

    private static final Logger log = Logger.getLogger(McpServer.class.getName());

    // Configure Jackson for lenient field matching (common in MCP clients)
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final int port;
    private final int threads;
    private final String path;
    private HttpServer server;
    private final List<McpTool> tools = new CopyOnWriteArrayList<>();
    private final List<McpResource> resources = new CopyOnWriteArrayList<>();

    public McpServer() {
        this(DEFAULT_PORT, DEFAULT_PATH, DEFAULT_THREADS);
    }

    /**
     * You can override the default port with this constructor.
     * A port of 0 means that a random available port will be selected (good for testing).
     */
    public McpServer(int port) {
        this(port, DEFAULT_PATH, DEFAULT_THREADS);
    }

    public McpServer(int port, String path) {
        this(port, path, DEFAULT_THREADS);
    }

    public McpServer(int port, String path, int threads) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        if (threads < 1) {
            throw new IllegalArgumentException("Threads must be at least 1");
        }
        if (path == null || path.isBlank()) {
            log.warning("McpServer: ignoring blank path, defaulting to \"" + DEFAULT_PATH + "\"");
            path = DEFAULT_PATH;
        }
        this.port = port;
        this.threads = threads;
        this.path = path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Returns the port we're currently listening on, or the configured port if we're not currently running.
     * If you constructed this instance with a port of 0 (random), this method will return the port
     * that actually got assigned. Otherwise, you should always get back the port that you configured.
     * (Or the default port if you didn't specify one at all).
     */
    public synchronized int getPort() {
        if (server != null && server.getAddress() != null) {
            return server.getAddress().getPort();
        }
        return port;
    }

    /**
     * Returns the base path we're listening on. This is always the configured path,
     * regardless of whether we're currently running or not.
     */
    public String getBasePath() {
        return path;
    }

    /**
     * Registers a tool that clients can call via the "tools/call" method.
     * The tool name must be non-null and unique among registered tools, and must match our ALPHA_NUMERIC_PATTERN.
     * Tools <i>should</i> have a description, but this is not enforced here.
     *
     * @param tool The tool to register. Must not be null, and must have a valid name.
     * @return true if the tool was successfully registered, or false if a tool with the same name is already registered.
     * @throws IllegalArgumentException if you provide a null tool or one with an invalid name.
     */
    public boolean registerTool(McpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }
        String toolName = tool.getName();
        if (toolName == null) {
            throw new IllegalArgumentException("Tool name cannot be null");
        }

        // Check it against our pattern (this also ensures it's at least 1 character long):
        if (!ALPHA_NUMERIC_PATTERN.matcher(toolName).matches()) {
            throw new IllegalArgumentException("Tool name must start with a letter and then only contain letters, " +
                                                       "numbers, hyphens, or underscores. Invalid name: \"" + toolName + "\"");
        }

        // We won't insist on a description, but we'll nag the caller if it's missing:
        if (tool.getDescription() == null || tool.getDescription().isBlank()) {
            log.warning("McpServer: tool \"" + toolName
                                + "\" has no description. It's recommended to provide one for better client integration.");
        }

        if (tools.stream().anyMatch(t -> t.getName().equals(tool.getName()))) {
            log.warning("McpServer: tool with name \"" + tool.getName() + "\" is already registered, ignoring.");
            return false;
        }
        tools.add(tool);
        log.info("McpServer: registered tool \"" + tool.getName() + "\"");
        return true;
    }

    /**
     * Unregisters a tool by name (case-sensitive).
     * Returns true if a tool was actually removed, or false if no tool with the given name was found.
     *
     * @param toolName The name of the tool to remove. Must not be null or blank.
     * @return true if a tool was removed, or false if no tool with the given name was found.
     * @throws IllegalArgumentException if the tool name is null or blank.
     */
    public boolean unregisterTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
        boolean removed = tools.removeIf(t -> t.getName().equals(toolName));
        if (removed) {
            log.info("McpServer: unregistered tool \"" + toolName + "\"");
        } else {
            log.warning("McpServer: no tool with name \"" + toolName + "\" found to unregister.");
        }
        return removed;
    }

    /**
     * Registers a resource that clients can access via its URI. The resource can be anything,
     * but it must be represented in string format (binary data being base64-encoded) and have
     * an appropriate MIME type.
     *
     * @param resource The resource to register. Must not be null, and must have a valid name and URI.
     * @return true upon successful registration; false if a resource with the same name or URI is already registered.
     * @throws IllegalArgumentException if you provide a null resource or one with an invalid name, uri, or MIME type.
     */
    public boolean registerResource(McpResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource cannot be null");
        }
        String resourceName = resource.getName();
        if (resourceName == null || resource.getUri() == null) {
            throw new IllegalArgumentException("Resource name and URI cannot be null");
        }
        // Check it against our pattern (this also ensures it's at least 1 character long):
        if (!ALPHA_NUMERIC_PATTERN.matcher(resourceName).matches()) {
            throw new IllegalArgumentException("Resource name must start with a letter and then only contain letters, "
                                                       + "numbers, hyphens, or underscores. Invalid name: \""
                                                       + resourceName + "\"");
        }
        if (resource.getMimeType() == null || resource.getMimeType().isBlank()) {
            throw new IllegalArgumentException("Resource \"" + resourceName + "\" has no MIME type. " +
                                                       "A valid MIME type is required to register a resource.");
        }
        if (resources.stream().anyMatch(r -> r.getName().equals(resource.getName()))) {
            log.warning(
                    "McpServer: resource with name \"" + resource.getName() + "\" is already registered, ignoring.");
            return false;
        }
        if (resources.stream().anyMatch(r -> r.getUri().equals(resource.getUri()))) {
            log.warning("McpServer: resource with URI \"" + resource.getUri() + "\" is already registered, ignoring.");
            return false;
        }
        resources.add(resource);
        log.info("McpServer: registered resource \"" + resource.getName() + "\"");
        return true;
    }

    /**
     * Unregisters a resource by name (case-sensitive).
     *
     * @param resourceName The name of the resource to remove. Must not be null or blank.
     * @return true if a resource was removed, or false if no resource with the given name was found.
     * @throws IllegalArgumentException if the resource name is null or blank.
     */
    public boolean unregisterResource(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("Resource name cannot be null or blank");
        }
        boolean removed = resources.removeIf(r -> r.getName().equals(resourceName));
        if (removed) {
            log.info("McpServer: unregistered resource \"" + resourceName + "\"");
        }
        else {
            log.warning("McpServer: no resource with name \"" + resourceName + "\" found to unregister.");
        }
        return removed;
    }

    public synchronized boolean isUp() {
        return server != null && server.getAddress() != null;
    }

    public synchronized void start() throws IOException {
        if (isUp()) {
            log.warning("McpServer: ignoring request to start while already running.");
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(threads));
        server.createContext(path, new McpHandler());
        server.start();
        log.info("McpServer: running on http://localhost:" + server.getAddress().getPort() + path);
    }

    public synchronized void stop() {
        if (!isUp()) {
            log.warning("McpServer: ignoring request to stop while not running.");
            return;
        }
        server.stop(0);
        server = null;
        log.info("McpServer: stopped.");
    }

    /**
     * The protocol requires clients to send an "initialize" message when they first connect.
     * We'll log it and send a standard response.
     */
    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        Object clientInfo = params != null ? params.get("clientInfo") : null;
        if (clientInfo instanceof Map<?,?> clientInfoMap) {
            Object clientName = clientInfoMap.get("name");
            Object clientVersion = clientInfoMap.get("version");
            log.info("McpServer: received initialize from client \"" + clientName + "\" version \"" + clientVersion + "\"");
        }
        return Map.of(
                "protocolVersion", MCP_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false),
                        "resources", Map.of("listChanged", false, "subscribe", false)
                ),
                "serverInfo", Map.of("name", "mcp-light", "version", VERSION)
        );
    }

    /**
     * Fancy MCP servers can notify clients if their tools list changes dynamically.
     * We don't support that yet. Instead, we return a list of whatever tools are
     * registered at the moment the client requests the tools list.
     */
    private Map<String, Object> handleToolsList() {
        List<Map<String, Object>> toolDefs = new ArrayList<>();
        for (McpTool tool : tools) {
            toolDefs.add(Map.of(
                    "name", tool.getName(),
                    "description", tool.getDescription(),
                    "inputSchema", tool.getInputSchema()
            ));
        }
        return Map.of("tools", toolDefs);
    }

    /**
     * Returns a tool result (or error result, if the tool failed) for the given tool with the given arguments.
     * If the return is null, the given tool name was not found in our tools list.
     */
    private Map<String, Object> handleToolCall(String toolName, Map<String, Object> args) {
        if (toolName == null || toolName.isBlank()) {
            log.warning("McpServer: received tool call with blank tool name.");
            return null;
        }
        McpTool tool = tools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null);
        if (tool == null) {
            return null;
        }

        String result;
        boolean isError = false;
        try {
            log.info("McpServer: executing tool \"" + toolName + "\"");
            result = tool.execute(args != null ? args : Map.of());
        }
        catch (Exception e) {
            isError = true;
            result = "Tool execution failed: " + e.getMessage();
        }
        return Map.of("content", List.of(
                new McpToolContent("text", result, isError)
        ));
    }

    /**
     * Generates a list of our registered resources in the format expected by clients.
     *
     * @param templates whether to look for URI templates or fixed URIs.
     */
    private Map<String, Object> handleResourceList(boolean templates) {
        List<Map<String, Object>> resourceDefs = new ArrayList<>();
        for (McpResource resource : resources) {
            if (templates && !resource.getUri().contains("{")) {
                continue;
            }
            if (!templates && resource.getUri().contains("{")) {
                continue;
            }
            String description = resource.getDescription() == null ? "" : resource.getDescription();
            String uriKeyName = templates ? "uriTemplate" : "uri";
            resourceDefs.add(Map.of(
                    "name", resource.getName(),
                    "description", description,
                    uriKeyName, resource.getUri(),
                    "mimeType", resource.getMimeType()
            ));
        }
        String keyName = templates ? "resourceTemplates" : "resources";
        return Map.of(keyName, resourceDefs);
    }

    /**
     * Returns a resource response (or error response if the fetch failed) for the resource at the given URI.
     * If the return is null, no registered resource matched the given URI.
     *
     * @param uri The URI of the resource to fetch.
     * @return The resource response (or error response), or null if no such resource was found.
     */
    private Map<String, Object> handleResourceFetch(String uri) {
        if (uri == null || uri.isBlank()) {
            log.warning("McpServer: received resource fetch with blank URI.");
            return Map.of("error", "URI cannot be blank");
        }
        McpResource resource = resources.stream()
                                        .filter(r -> r.matchesUri(uri))
                                        .findFirst()
                                        .orElse(null);
        if (resource == null) {
            return null;
        }
        try {
            log.info("McpServer: fetching resource at URI: " + uri);
            String content = resource.getContent(uri);
            McpResourceContent resourceContent = new McpResourceContent(uri,
                                                                        resource.getMimeType(),
                                                                        resource.isBinary() ? null : content,
                                                                        resource.isBinary() ? content : null);
            return Map.of("contents", List.of(resourceContent));
        }
        catch (Exception e) {
            return Map.of("error", "Failed to fetch resource content: " + e.getMessage());
        }
    }

    /**
     * We check the Content-Length header to ensure all of the following:
     * <ul>
     *     <li>It exists.</li>
     *     <li>It is a valid positive integer.</li>
     *     <li>It is smaller than our REQUEST_SIZE_LIMIT (not currently configurable).</li>
     * </ul>
     *
     * @return 0 if all checks passed, or an HTTP 4xx error code if any check failed.
     */
    private int checkRequestLength(String contentLength) {
        if (contentLength == null) {
            log.warning("McpServer: received POST request with no Content-Length header.");
            return 411; // LENGTH REQUIRED
        }
        try {
            int length = Integer.parseInt(contentLength);
            if (length <= 0) {
                log.warning("McpServer: received POST request with non-positive Content-Length: " + contentLength);
                return 400; // BAD REQUEST
            }
            if (length > REQUEST_SIZE_LIMIT) {
                log.warning("McpServer: received POST request with excessively large Content-Length: " + contentLength);
                return 413; // PAYLOAD TOO LARGE
            }
        }
        catch (NumberFormatException e) {
            log.warning("McpServer: received POST request with invalid Content-Length: " + contentLength);
            return 400; // BAD REQUEST
        }
        return 0; // OK
    }

    /**
     * We check the Content-Type header to ensure it exists and starts with "application/json" (case-insensitive).
     *
     * @return 0 if the check passed, or an HTTP 415 error code if the check failed.
     */
    private int checkContentType(String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            log.warning("McpServer: received POST request with invalid Content-Type: " + contentType);
            return 415; // UNSUPPORTED MEDIA TYPE
        }
        return 0; // OK
    }

    /**
     * Sends a no-content response with the given status code and optionally logs
     * a message. We don't close the exchange here - caller must do that.
     */
    private void sendEarlyResponse(int code, HttpExchange exchange, String logMessage) {
        try {
            exchange.sendResponseHeaders(code, -1);
            if (logMessage != null) {
                log.warning("McpServer: " + logMessage);
            }
        }
        catch (IOException ioe) {
            log.severe("McpServer: failed to send early response: " + ioe.getMessage());
        }
    }

    class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers to every response, just to be safe. This is important for browser-based clients,
            // such as llama-ui, when it's running on another machine or port.
            // These should be harmless to include for non-browser clients.
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");

            try {
                // We only support POST and OPTIONS:
                if (!"POST".equals(exchange.getRequestMethod())
                        && !"OPTIONS".equals(exchange.getRequestMethod())) {
                    sendEarlyResponse(405, exchange,
                                      "received unsupported HTTP method: " + exchange.getRequestMethod());
                    return;
                }

                // If it's an OPTIONS request, we handle it and return early:
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    sendEarlyResponse(204, exchange, "received OPTIONS request, sending CORS headers.");
                    return;
                }

                // Do some basic sanity checks on the request before we proceed:
                int errorCode = checkContentType(exchange.getRequestHeaders().getFirst("Content-Type"));
                if (errorCode != 0) {
                    sendEarlyResponse(errorCode, exchange, "received POST request with invalid Content-Type header: "
                            + exchange.getRequestHeaders().getFirst("Content-Type"));
                    return;
                }
                errorCode = checkRequestLength(exchange.getRequestHeaders().getFirst("Content-Length"));
                if (errorCode != 0) {
                    sendEarlyResponse(errorCode, exchange, "received POST request with invalid Content-Length header: "
                            + exchange.getRequestHeaders().getFirst("Content-Length"));
                    return;
                }

                // Now parse the body and we're ready:
                McpResponse response = new McpResponse();
                McpRequest request;
                String method;
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    request = MAPPER.readValue(body, McpRequest.class);
                    response.id = request.id;
                    method = request.method;

                    // If method is null, we might be looking at a llama-ui request, which has a different structure.
                    if (method == null) {
                        log.warning(
                                "McpServer: received request with no method field, trying to parse as McpRequest2 (llama-ui format).");
                        McpRequest2 request2 = MAPPER.readValue(body, McpRequest2.class);
                        if (request2.request == null || request2.request.jsonRpcMethods == null) {
                            throw new IOException("Missing required fields in request body");
                        }
                        response.id = request2.request.url; // llama-ui doesn't have a formal "id", but the URL is okay.
                        request = new McpRequest();
                        request.method = request2.request.jsonRpcMethods.get(0);
                        method = request.method; // just to be sure we're consistent
                        request.params = request2.request.body != null && "application/json".equals(
                                request2.request.headers.contentType)
                                ? MAPPER.convertValue(request2.request.body, Map.class)
                                : Map.of();
                        log.info("McpServer: parsed request as McpRequest2 with method \"" + method + "\"");
                    }
                    else {
                        log.info("McpServer: received request for method \"" + method + "\"");
                    }
                }
                catch (IOException e) {
                    log.warning("McpServer: failed to parse request body: " + e.getMessage());
                    byte[] respBytes = ("Failed to parse request body: " + e.getMessage()).getBytes(
                            StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    exchange.sendResponseHeaders(400, respBytes.length); // BAD REQUEST
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respBytes);
                    }
                    return;
                }

                try {
                    if ("initialize".equals(method)) {
                        response.result = handleInitialize(request.params);
                    }
                    else if ("initialized".equals(method) || "notifications/initialized".equals(method)) {
                        // The protocol requires clients to send this after initialization,
                        // but we really don't care:
                        response.result = Map.of();
                    }
                    else if ("ping".equals(method)) {
                        // The protocol requires us to send an empty response if we receive a ping:
                        response.result = Map.of();
                    }
                    else if ("tools/list".equals(method)) {
                        response.result = handleToolsList();
                    }
                    else if ("resources/list".equals(method)) {
                        response.result = handleResourceList(false);
                    }
                    else if ("resources/templates/list".equals(method)) {
                        response.result = handleResourceList(true);
                    }
                    else if ("resources/read".equals(method)) {
                        Map<String, Object> params = request.params != null ? request.params : Map.of();
                        String uri = params.get("uri") instanceof String ? (String)params.get("uri") : null;
                        if (uri == null || uri.isBlank()) {
                            log.severe("McpServer: received resource read request with missing or blank URI.");
                            response.error = Map.of("code", McpError.INVALID_PARAMS.getCode(),
                                                    "message", "Missing or blank 'uri' parameter");
                        }
                        else {
                            Map<String, Object> result = handleResourceFetch(uri);
                            if (result == null) {
                                log.severe("McpServer: no resource found matching URI: " + uri);
                                response.error = Map.of("code", McpError.RESOURCE_NOT_FOUND.getCode(),
                                                        "message", "No such resource: " + uri);
                            }
                            else if (result.get("error") != null) {
                                response.error = Map.of("code", McpError.INTERNAL_ERROR.getCode(),
                                                        "message", result.get("error"));
                            }
                            else {
                                response.result = result;
                            }
                        }
                    }
                    else if ("tools/call".equals(method)) {
                        Map<String, Object> params = request.params != null ? request.params : Map.of();
                        String toolName = params.get("name") instanceof String ? (String)params.get("name") : null;
                        Object arguments = params.get("arguments");
                        Map<String, Object> toolArgs = Map.of();
                        if (arguments instanceof Map<?, ?> args) {
                            // noinspection unchecked
                            toolArgs = (Map<String, Object>)args;
                        }
                        else {
                            log.warning("McpServer: expected tool call arguments to be a map, but got: " + arguments);
                        }
                        Map<String, Object> toolResult = handleToolCall(toolName, toolArgs);
                        if (toolResult == null) {
                            log.severe("McpServer: no tool found with name: " + toolName);
                            response.error = Map.of("code", McpError.INVALID_PARAMS.getCode(),
                                                    "message", "No such tool: " + toolName);
                        }
                        else {
                            response.result = toolResult;
                        }
                    }
                    else {
                        log.severe("McpServer: received request with unknown method: " + method);
                        response.error = Map.of("code", McpError.METHOD_NOT_FOUND.getCode(),
                                                "message", "Method not found: " + method);
                    }
                }
                catch (Exception e) {
                    log.severe("McpServer: error while handling method \"" + method + "\": " + e.getMessage());
                    response.error = Map.of("code", McpError.INVALID_REQUEST.getCode(), "message", e.getMessage());
                }

                byte[] respBytes = MAPPER.writeValueAsBytes(response);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            }
            catch (Exception e) {
                // This catch-all acts as a safety net to ensure that clients always
                // get SOMETHING back, even if it's just a mysterious 500 error with no body.
                // We check the exchange's response code first to avoid trying to send
                // a response if we've already sent one.
                if (exchange.getResponseCode() == -1) {
                    sendEarlyResponse(500, exchange, "Unexpected error while handling request: " + e.getMessage());
                }
            }
            finally {
                // Ensure the exchange is closed even if something went wrong:
                exchange.close();
            }
        }
    }
}
