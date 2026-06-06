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
    private static final String VERSION = "1.0.0";

    /**
     * This is the version of the MCP protocol that we will report to clients.
     */
    private static final String MCP_VERSION = "2024-10-07";

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

    public McpServer() {
        this(DEFAULT_PORT, DEFAULT_PATH, DEFAULT_THREADS);
    }

    public McpServer(int port) {
        this(port, DEFAULT_PATH, DEFAULT_THREADS);
    }

    public McpServer(int port, String path) {
        this(port, path, DEFAULT_THREADS);
    }

    public McpServer(int port, String path, int threads) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
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
            log.warning(
                    "McpServer: tool \"" + toolName + "\" has no description. It's recommended to provide one for better client integration.");
        }

        if (tools.stream().anyMatch(t -> t.getName().equals(tool.getName()))) {
            log.warning("McpServer: tool with name \"" + tool.getName() + "\" is already registered, ignoring.");
            return false;
        }
        tools.add(tool);
        log.info("McpServer: registered tool \"" + tool.getName() + "\"");
        return true;
    }

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
        log.info("McpServer: running on http://localhost:"+port+path);
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
                "capabilities", Map.of("tools", Map.of("listChanged", false)),
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

    class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // We only support POST:
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1); // METHOD NOT ALLOWED
                return;
            }

            // Do some basic sanity checks on the request before we proceed:
            int errorCode = checkContentType(exchange.getRequestHeaders().getFirst("Content-Type"));
            if (errorCode != 0) {
                exchange.sendResponseHeaders(errorCode, -1);
                return;
            }
            errorCode = checkRequestLength(exchange.getRequestHeaders().getFirst("Content-Length"));
            if (errorCode != 0) {
                exchange.sendResponseHeaders(errorCode, -1);
                return;
            }

            McpResponse response = new McpResponse();
            try {
                // Now parse the body and we're ready:
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                McpRequest request = MAPPER.readValue(body, McpRequest.class);
                response.id = request.id;
                String method = request.method;

                if ("initialize".equals(method)) {
                    response.result = handleInitialize(request.params);
                } else if ("initialized".equals(method)) {
                    // The protocol requires clients to send this after initialization,
                    // but we really don't care:
                    response.result = Map.of();
                }
                else if ("tools/list".equals(method)) {
                    response.result = handleToolsList();
                } else if ("tools/call".equals(method)) {
                    Map<String, Object> params = request.params != null ? request.params : Map.of();
                    String toolName = params.get("name") instanceof String ? (String)params.get("name") : null;
                    Object arguments = params.get("arguments");
                    Map<String, Object> toolArgs = Map.of();
                    if (arguments instanceof Map<?,?> args) {
                        // noinspection unchecked
                        toolArgs = (Map<String, Object>) args;
                    }
                    else {
                        log.warning("McpServer: expected tool call arguments to be a map, but got: " + arguments);
                    }
                    Map<String, Object> toolResult = handleToolCall(toolName, toolArgs);
                    if (toolResult == null) {
                        response.error = Map.of("code", McpError.INVALID_PARAMS.getCode(),
                                                "message", "No such tool: " + toolName);
                    }
                    else {
                        response.result = toolResult;
                    }
                } else {
                    response.error = Map.of("code", McpError.METHOD_NOT_FOUND.getCode(),
                                            "message", "Method not found: " + method);
                }
            } catch (Exception e) {
                response.error = Map.of("code", McpError.INVALID_REQUEST.getCode(), "message", e.getMessage());
            }

            byte[] respBytes = MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        }
    }
}
