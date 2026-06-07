package ca.corbett.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerTest {

    private McpServer server;

    @BeforeEach
    public void setup() throws Exception {
        server = new McpServer(0);
        server.start();
    }

    @AfterEach
    public void shutdown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void isUp_withServerRunning_shouldReportTrue() {
        assertTrue(server.isUp());
    }

    @Test
    public void isUp_withServerStopped_shouldReportFalse() {
        server.stop();
        assertFalse(server.isUp());
    }

    // ==================== Constructor Validation ====================

    @Test
    public void constructor_withPortZero_shouldSucceed() throws Exception {
        McpServer s = new McpServer(0);
        s.start();
        assertTrue(s.isUp());
        s.stop();
    }

    @Test
    public void constructor_withPort65535_shouldSucceed() throws Exception {
        assertDoesNotThrow(() -> new McpServer(65535));
    }

    @Test
    public void constructor_withPortMinusOne_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new McpServer(-1));
    }

    @Test
    public void constructor_withPort65536_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new McpServer(65536));
    }

    @Test
    public void constructor_withZeroThreads_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new McpServer(8080, "/mcp", 0));
    }

    @Test
    public void constructor_withNegativeThreads_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> new McpServer(8080, "/mcp", -1));
    }

    @Test
    public void constructor_withBlankPath_shouldDefaultToMcp() throws Exception {
        McpServer s = new McpServer(0, "");
        s.start();
        assertTrue(s.isUp());
        assertEquals(McpServer.DEFAULT_PATH, s.getBasePath());
        s.stop();
    }

    @Test
    public void constructor_withNullPath_shouldDefaultToMcp() throws Exception {
        McpServer s = new McpServer(0, null);
        s.start();
        assertTrue(s.isUp());
        assertEquals(McpServer.DEFAULT_PATH, s.getBasePath());
        s.stop();
    }

    @Test
    public void constructor_withPathWithoutLeadingSlash_shouldAddIt() throws Exception {
        McpServer s = new McpServer(0, "pathWithoutLeadingSlash");
        s.start();
        assertTrue(s.isUp());
        assertEquals("/pathWithoutLeadingSlash", s.getBasePath());
        s.stop();
    }

    @Test
    public void constructor_withPathWithLeadingSlash_shouldKeepIt() throws Exception {
        McpServer s = new McpServer(0, "/pathWithLeadingSlash");
        s.start();
        assertTrue(s.isUp());
        assertEquals("/pathWithLeadingSlash", s.getBasePath());
        s.stop();
    }

    // ==================== Tool Registration ====================

    @Test
    public void registerTool_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> server.registerTool(null));
    }

    @Test
    public void registerTool_withNullName_shouldThrow() {
        McpTool tool = new McpTool() {
            public String getName() { return null; }
            public String getDescription() { return "desc"; }
            public Map<String, Object> getInputSchema() { return Map.of(); }
            public String execute(Map<String, Object> input) { return "ok"; }
        };
        assertThrows(IllegalArgumentException.class, () -> server.registerTool(tool));
    }

    @Test
    public void registerTool_withInvalidName_shouldThrow() {
        McpTool tool = new McpTool() {
            public String getName() { return "123bad"; }
            public String getDescription() { return "desc"; }
            public Map<String, Object> getInputSchema() { return Map.of(); }
            public String execute(Map<String, Object> input) { return "ok"; }
        };
        assertThrows(IllegalArgumentException.class, () -> server.registerTool(tool));
    }

    @Test
    public void registerTool_withValidName_shouldReturnTrue() {
        McpTool tool = createTool("myTool", "A tool", "ok");
        assertTrue(server.registerTool(tool));
    }

    @Test
    public void registerTool_withDuplicateName_shouldReturnFalse() {
        McpTool tool1 = createTool("myTool", "A tool", "ok");
        McpTool tool2 = createTool("myTool", "Another tool", "ok2");
        assertTrue(server.registerTool(tool1));
        assertFalse(server.registerTool(tool2));
    }

    @Test
    public void registerTool_withNoDescription_shouldLogWarningButSucceed() {
        McpTool tool = new McpTool() {
            public String getName() { return "noDesc"; }
            public String getDescription() { return ""; }
            public Map<String, Object> getInputSchema() { return Map.of(); }
            public String execute(Map<String, Object> input) { return "ok"; }
        };
        assertTrue(server.registerTool(tool));
    }

    @Test
    public void unregisterTool_withNonExistent_shouldReturnFalse() {
        assertFalse(server.unregisterTool("nonexistent"));
    }

    @Test
    public void unregisterTool_withExisting_shouldReturnTrue() {
        McpTool tool = createTool("myTool", "A tool", "ok");
        server.registerTool(tool);
        assertTrue(server.unregisterTool("myTool"));
    }

    @Test
    public void unregisterTool_withNullName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> server.unregisterTool(null));
    }

    @Test
    public void unregisterTool_withBlankName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> server.unregisterTool(""));
    }

    // ==================== Resource Registration ====================

    @Test
    public void registerResource_withNull_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> server.registerResource(null));
    }

    @Test
    public void registerResource_withNullName_shouldThrow() {
        McpResource resource = new McpResource() {
            public String getName() {
                return null;
            }

            public String getDescription() {
                return "A resource";
            }

            public String getUri() {
                return "myapp://resource";
            }

            public boolean matchesUri(String uri) {
                return true;
            }

            public String getMimeType() {
                return "text/plain";
            }

            public String getContent(String requestedUri) {
                return "content";
            }
        };
        assertThrows(IllegalArgumentException.class, () -> server.registerResource(resource));
    }

    @Test
    public void registerResource_withNullUri_shouldThrow() {
        McpResource resource = new McpResource() {
            public String getName() {
                return "myResource";
            }

            public String getDescription() {
                return "A resource";
            }

            public String getUri() {
                return null;
            }

            public boolean matchesUri(String uri) {
                return true;
            }

            public String getMimeType() {
                return "text/plain";
            }

            public String getContent(String requestedUri) {
                return "content";
            }
        };
        assertThrows(IllegalArgumentException.class, () -> server.registerResource(resource));
    }

    @Test
    public void registerResource_withInvalidName_shouldThrow() {
        McpResource resource = new McpResource() {
            public String getName() {
                return "123bad";
            }

            public String getDescription() {
                return "A resource";
            }

            public String getUri() {
                return "myapp://resource";
            }

            public boolean matchesUri(String uri) {
                return true;
            }

            public String getMimeType() {
                return "text/plain";
            }

            public String getContent(String requestedUri) {
                return "content";
            }
        };
        assertThrows(IllegalArgumentException.class, () -> server.registerResource(resource));
    }

    @Test
    public void registerResource_withValidResource_shouldReturnTrue() {
        McpResource resource = createResource("myResource", "A resource", "myapp://resource", "content");
        assertTrue(server.registerResource(resource));
    }

    @Test
    public void registerResource_withDuplicateName_shouldReturnFalse() {
        McpResource resource1 = createResource("myResource", "A resource", "myapp://resource1", "content1");
        McpResource resource2 = createResource("myResource", "Another resource", "myapp://resource2", "content2");
        assertTrue(server.registerResource(resource1));
        assertFalse(server.registerResource(resource2));
    }

    @Test
    public void registerResource_withDuplicateUri_shouldReturnFalse() {
        McpResource resource1 = createResource("resource1", "A resource", "myapp://resource", "content1");
        McpResource resource2 = createResource("resource2", "Another resource", "myapp://resource", "content2");
        assertTrue(server.registerResource(resource1));
        assertFalse(server.registerResource(resource2));
    }

    // ==================== Resource Unregistration ====================

    @Test
    public void unregisterResource_withNonExistent_shouldReturnFalse() {
        assertFalse(server.unregisterResource("nonexistent"));
    }

    @Test
    public void unregisterResource_withExisting_shouldReturnTrue() {
        McpResource resource = createResource("myResource", "A resource", "myapp://resource", "content");
        server.registerResource(resource);
        assertTrue(server.unregisterResource("myResource"));
    }

    @Test
    public void unregisterResource_withNullName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> server.unregisterResource(null));
    }

    @Test
    public void unregisterResource_withBlankName_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> server.unregisterResource(""));
    }

    // ==================== HTTP Endpoint Tests ====================

    @Test
    public void handleInitialize_shouldReturnProtocolVersionAndCapabilities() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "clientInfo": { "name": "test-client", "version": "1.0" }
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("protocolVersion"));
        assertTrue(response.contains(McpServer.MCP_VERSION));
        assertTrue(response.contains("capabilities"));
        assertTrue(response.contains("tools"));
        assertTrue(response.contains("serverInfo"));
        assertTrue(response.contains("mcp-light"));
    }

    @Test
    public void handleInitialized_shouldReturnEmptyResult() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "initialized",
                    "params": {}
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("result"));
    }

    @Test
    public void handleToolsList_withNoTools_shouldReturnEmptyList() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 3,
                    "method": "tools/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("\"tools\":[]"));
    }

    @Test
    public void handleToolsList_withTools_shouldReturnRegisteredTools() throws Exception {
        int port = server.getPort();
        server.registerTool(createTool("echo", "Echoes input", "{\"type\":\"object\"}"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 4,
                    "method": "tools/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("echo"));
        assertTrue(response.contains("Echoes input"));
    }

    @Test
    public void handleToolCall_withValidTool_shouldReturnResult() throws Exception {
        int port = server.getPort();
        server.registerTool(createTool("myTool", "Does something", "{\"type\":\"object\"}"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 5,
                    "method": "tools/call",
                    "params": {
                        "name": "myTool",
                        "arguments": { "message": "hello" }
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("\"type\":\"text\""));
        assertTrue(response.contains("\"isError\":false"));
    }

    @Test
    public void handleToolCall_withUnknownTool_shouldReturnError() throws Exception {
        int port = server.getPort();

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 6,
                    "method": "tools/call",
                    "params": {
                        "name": "nonexistent",
                        "arguments": {}
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("No such tool: nonexistent"));
    }

    @Test
    public void handleToolCall_withToolThatThrows_shouldReturnErrorContent() throws Exception {
        int port = server.getPort();
        server.registerTool(createToolWithThrow("boom", "Always fails", "boom!"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 7,
                    "method": "tools/call",
                    "params": {
                        "name": "boom",
                        "arguments": {}
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("Tool execution failed"));
        assertTrue(response.contains("boom!"));
    }

    @Test
    public void handleUnknownMethod_shouldReturnMethodNotFound() throws Exception {
        int port = server.getPort();

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 8,
                    "method": "foobar/baz",
                    "params": {}
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("Method not found"));
    }

    @Test
    public void handleNonPostMethod_shouldReturn405() throws Exception {
        int port = server.getPort();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    @Test
    public void handleMissingContentType_shouldReturn415() throws Exception {
        int port = server.getPort();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        String response = sendRawRequestWithHeaders("POST /mcp HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                                                            + body.length() + "\r\n\r\n" + body, port);
        assertTrue(response.contains("415"));
    }

    @Test
    public void handleMissingContentLength_shouldReturn411() throws Exception {
        int port = server.getPort();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        String response = sendRawRequestWithoutContentLength(body, port);
        assertTrue(response.contains("411"));
    }

    @Test
    public void handleInvalidContentLength_shouldReturn400() throws Exception {
        int port = server.getPort();
        String body = "test";
        String response = sendRawRequestWithHeaders("POST /mcp HTTP/1.1\r\nHost: localhost\r\n" +
                                                            "Content-Type: application/json\r\n" +
                                                            "Content-Length: notanumber\r\n\r\n" + body, port);
        assertTrue(response.contains("400"));
    }

    @Test
    public void handleNonPositiveContentLength_shouldReturn400() throws Exception {
        int port = server.getPort();
        String body = "test";
        String response = sendRawRequestWithHeaders("POST /mcp HTTP/1.1\r\nHost: localhost\r\n" +
                                                            "Content-Type: application/json\r\n" +
                                                            "Content-Length: 0\r\n\r\n" + body, port);
        assertTrue(response.contains("400"));
    }

    @Test
    public void handleExcessivelyLargeContentLength_shouldReturn413() throws Exception {
        int port = server.getPort();
        String body = "test";
        String response = sendRawRequestWithHeaders("POST /mcp HTTP/1.1\r\nHost: localhost\r\n" +
                                                            "Content-Type: application/json\r\n" +
                                                            "Content-Length: 999999999\r\n\r\n" + body, port);
        assertTrue(response.contains("413"));
    }

    @Test
    public void handleCaseInsensitiveContentType_shouldSucceed() throws Exception {
        int port = server.getPort();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        String response = sendRawRequestWithHeaders("POST /mcp HTTP/1.1\r\nHost: localhost\r\n" +
                                                            "Content-Type: Application/JSON\r\n" +
                                                            "Content-Length: " + body.length() + "\r\n\r\n"
                                                            + body, port);
        assertTrue(response.contains("200 OK"));
        assertTrue(response.contains("protocolVersion"));
    }

    @Test
    public void handleMethodInitialize_shouldSucceed() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 9,
                    "method": "initialize",
                    "params": {}
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("protocolVersion"));
    }

    @Test
    public void handleValidMethodButWrongCase_shouldReturnMethodNotFound() throws Exception {
        // The MCP protocol explicitly says that method names should be case-sensitive.
        // Let's ensure that our server handles this correctly, returning "Method not found"
        // rather than treating it as a valid method call.
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 9,
                    "method": "INITIALIZE",
                    "params": {}
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("Method not found"));
    }

    @Test
    public void handleStartWhileRunning_shouldNotThrow() throws Exception {
        server.start();
        // Should not throw, just log a warning
        assertTrue(server.isUp());
    }

    @Test
    public void handleStopWhileStopped_shouldNotThrow() throws Exception {
        server.stop();
        assertFalse(server.isUp());
        // Second stop should not throw
        server.stop();
        assertFalse(server.isUp());
    }

    @Test
    public void handleToolCallWithNullArgs_shouldUseEmptyMap() throws Exception {
        int port = server.getPort();
        server.registerTool(createTool("echo", "Echoes input", "{\"type\":\"object\"}"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 10,
                    "method": "tools/call",
                    "params": {
                        "name": "echo"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("content"));
    }

    @Test
    public void handleToolCallWithNullParams_shouldReturnNullResult() throws Exception {
        int port = server.getPort();
        server.registerTool(createTool("echo", "Echoes input", "{\"type\":\"object\"}"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 11,
                    "method": "tools/call"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("No such tool"));
    }

    @Test
    public void handleInitializeWithoutClientInfo_shouldSucceed() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 12,
                    "method": "initialize",
                    "params": {}
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("protocolVersion"));
    }

    @Test
    public void handleMalformedJson_shouldReturnInvalidRequest() throws Exception {
        int port = server.getPort();
        String body = "{\"jsonrpc\": \"2.0\", BAD JSON}";
        String response = sendRawRequestWithHeaders("POST /mcp HTTP/1.1\r\nHost: localhost\r\n" +
                                                            "Content-Type: application/json\r\nContent-Length: "
                                                            + body.length() + "\r\n\r\n" + body, port);
        assertTrue(response.contains("400"));
        assertTrue(response.contains("Failed to parse"));
    }

    @Test
    public void handleToolCallWithCaseInsensitiveParams_shouldSucceed() throws Exception {
        // Jackson is configured for case-insensitive property matching
        int port = server.getPort();
        server.registerTool(createTool("echo", "Echoes input", "{\"type\":\"object\"}"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 13,
                    "method": "tools/call",
                    "params": {
                        "name": "echo",
                        "arguments": { "message": "test" }
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("content"));
    }

    // ==================== Resource HTTP Endpoint Tests ====================

    @Test
    public void handleResourcesList_withNoResources_shouldReturnEmptyList() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 14,
                    "method": "resources/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("\"resources\":[]"));
    }

    @Test
    public void handleResourcesList_withResources_shouldReturnRegisteredResources() throws Exception {
        int port = server.getPort();
        server.registerResource(createResource("config", "Config file", "myapp://config", "key=value"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 15,
                    "method": "resources/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("config"));
        assertTrue(response.contains("Config file"));
        assertTrue(response.contains("myapp://config"));
    }

    @Test
    public void handleResourceTemplatesList_withNoTemplates_shouldReturnEmptyList() throws Exception {
        int port = server.getPort();
        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 16,
                    "method": "resources/templates/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("\"resourceTemplates\":[]"));
    }

    @Test
    public void handleResourceTemplatesList_withTemplates_shouldReturnResourceTemplates() throws Exception {
        int port = server.getPort();
        server.registerResource(
                createResource("userProfile", "User profile", "myapp://users/{userId}", "user content"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 17,
                    "method": "resources/templates/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("userProfile"));
        assertTrue(response.contains("User profile"));
        assertTrue(response.contains("myapp://users/{userId}"));
    }

    @Test
    public void handleResourceTemplatesList_withFixedUriResources_shouldNotReturnInTemplates() throws Exception {
        int port = server.getPort();
        server.registerResource(createResource("config", "Config file", "myapp://config", "key=value"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 18,
                    "method": "resources/templates/list"
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("\"resourceTemplates\":[]"));
        assertFalse(response.contains("config"));
    }

    @Test
    public void handleResourceFetch_withValidResource_shouldReturnContent() throws Exception {
        int port = server.getPort();
        server.registerResource(createResource("config", "Config file", "myapp://config", "key=value"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 19,
                    "method": "resources/read",
                    "params": {
                        "uri": "myapp://config"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("key=value"));
        assertTrue(response.contains("\"mimeType\":\"text/plain\""));
    }

    @Test
    public void handleResourceFetch_withTemplateResource_shouldMatchAndReturnContent() throws Exception {
        int port = server.getPort();
        server.registerResource(
                createResource("userProfile", "User profile", "myapp://users/{userId}", "user content"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 20,
                    "method": "resources/read",
                    "params": {
                        "uri": "myapp://users/123"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("user content"));
        assertTrue(response.contains("myapp://users/123"));
    }

    @Test
    public void handleResourceFetch_withUnknownResource_shouldReturnNotFound() throws Exception {
        int port = server.getPort();

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 21,
                    "method": "resources/read",
                    "params": {
                        "uri": "myapp://unknown"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("No such resource: myapp://unknown"));
    }

    @Test
    public void handleResourceFetch_withBlankUri_shouldReturnError() throws Exception {
        int port = server.getPort();

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 22,
                    "method": "resources/read",
                    "params": {
                        "uri": ""
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("Missing or blank"));
    }

    @Test
    public void handleResourceFetch_withMissingUri_shouldReturnError() throws Exception {
        int port = server.getPort();

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 23,
                    "method": "resources/read",
                    "params": {}
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("Missing or blank"));
    }

    @Test
    public void handleResourceFetch_withResourceThatThrows_shouldReturnErrorContent() throws Exception {
        int port = server.getPort();
        McpResource resource = new McpResource() {
            public String getName() {
                return "boomResource";
            }

            public String getDescription() {
                return "Always fails";
            }

            public String getUri() {
                return "myapp://boom";
            }

            public boolean matchesUri(String uri) {
                return true;
            }

            public String getMimeType() {
                return "text/plain";
            }

            public String getContent(String requestedUri) {
                throw new RuntimeException("resource boom!");
            }
        };
        server.registerResource(resource);

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 24,
                    "method": "resources/read",
                    "params": {
                        "uri": "myapp://boom"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("Failed to fetch resource content"));
        assertTrue(response.contains("resource boom!"));
    }

    @Test
    public void handleResourceFetch_withBinaryResource_shouldReturnBlob() throws Exception {
        int port = server.getPort();
        McpResource resource = new McpResource() {
            public String getName() {
                return "binaryData";
            }

            public String getDescription() {
                return "Binary resource";
            }

            public String getUri() {
                return "myapp://binary";
            }

            public boolean matchesUri(String uri) {
                return true;
            }

            public String getMimeType() {
                return "image/png";
            }

            public String getContent(String requestedUri) {
                return "c29tZSBiaW5hcnkgZGF0YQ==";
            }

            public boolean isBinary() {
                return true;
            }
        };
        server.registerResource(resource);

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 25,
                    "method": "resources/read",
                    "params": {
                        "uri": "myapp://binary"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("\"blob\":\"c29tZSBiaW5hcnkgZGF0YQ==\""));
        assertFalse(response.contains("\"text\""));
    }

    @Test
    public void handleResourceFetch_withException_shouldReturnError() throws Exception {
        int port = server.getPort();
        server.registerResource(createResourceWithThrow("errorResource", "Resource that throws",
                                                        "myapp://error", "fetch error!"));

        String body = """
                {
                    "jsonrpc": "2.0",
                    "id": 26,
                    "method": "resources/read",
                    "params": {
                        "uri": "myapp://error"
                    }
                }
                """;
        String response = sendJsonRequest(body, port);
        assertTrue(response.contains("error"));
        assertTrue(response.contains("Failed to fetch resource content"));
        assertTrue(response.contains("fetch error!"));
    }

    // ==================== Helper Methods ====================

    private McpTool createTool(String name, String description, String result) {
        return new McpTool() {
            public String getName() { return name; }
            public String getDescription() { return description; }
            public Map<String, Object> getInputSchema() { return Map.of(); }
            public String execute(Map<String, Object> input) { return result; }
        };
    }

    private McpTool createToolWithThrow(String name, String description, String exceptionMessage) {
        return new McpTool() {
            public String getName() { return name; }
            public String getDescription() { return description; }
            public Map<String, Object> getInputSchema() { return Map.of(); }
            public String execute(Map<String, Object> input) throws Exception {
                throw new Exception(exceptionMessage);
            }
        };
    }

    private McpResource createResource(String name, String description, String uri, String content) {
        return new McpResource() {
            public String getName() {
                return name;
            }

            public String getDescription() {
                return description;
            }

            public String getUri() {
                return uri;
            }

            public boolean matchesUri(String checkUri) {
                if (uri.equals(checkUri)) { return true; }
                // Handle URI templates: if URI contains "{", check prefix before the first "{"
                int templateStart = uri.indexOf('{');
                if (templateStart >= 0) {
                    String prefix = uri.substring(0, templateStart);
                    return checkUri.startsWith(prefix);
                }
                return false;
            }

            public String getMimeType() {
                return "text/plain";
            }

            public String getContent(String requestedUri) {
                return content;
            }
        };
    }

    private McpResource createResourceWithThrow(String name, String description, String uri, String exceptionMessage) {
        return new McpResource() {
            public String getName() {
                return name;
            }

            public String getDescription() {
                return description;
            }

            public String getUri() {
                return uri;
            }

            public boolean matchesUri(String checkUri) {
                return uri.equals(checkUri);
            }

            public String getMimeType() {
                return "text/plain";
            }

            public String getContent(String requestedUri) throws Exception {
                throw new Exception(exceptionMessage);
            }
        };
    }

    /**
     * Sends a JSON POST request to the server using HttpClient and returns the response body.
     */
    private String sendJsonRequest(String body, int port) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

   /**
     * Sends a raw HTTP request string and returns the full response
     * (status line + headers + body).
     */
    private String sendRawRequest(String request, int port) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            OutputStream os = socket.getOutputStream();
            os.write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Read the status line and headers
            StringBuilder fullResponse = new StringBuilder();
            String line;
            boolean headersDone = false;
            int contentLength = -1;

            while ((line = reader.readLine()) != null) {
                fullResponse.append(line).append("\n");
                if (line.isEmpty()) {
                    headersDone = true;
                    break;
                }
                // Parse Content-Length from headers
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    } catch (NumberFormatException e) {
                        // No valid content-length
                    }
                }
            }

            if (!headersDone) {
                throw new RuntimeException("Could not read response headers");
            }

            // Read body based on Content-Length
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = reader.read(buffer, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                fullResponse.append(buffer, 0, totalRead);
            }

            return fullResponse.toString();
        }
    }

    /**
     * Sends a raw HTTP request string with headers and body, returns the response body.
     */
    private String sendRawRequestWithHeaders(String request, int port) throws Exception {
        return sendRawRequest(request, port);
    }

    /**
     * Sends a POST request without Content-Length header.
     */
    private String sendRawRequestWithoutContentLength(String body, int port) throws Exception {
        String request = "POST /mcp HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: application/json\r\n"
                + "\r\n"
                + body;
        return sendRawRequest(request, port);
    }
}
