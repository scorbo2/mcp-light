# Copilot Instructions

## Commands

| Task | Command |
|------|---------|
| Build | `mvn clean package` |
| Test (full suite) | `mvn test` |
| Test (single test) | `mvn test -Dtest=McpServerTest#isUp_withServerRunning_shouldReportTrue` |
| Compile only | `mvn compile` |
| Run (default port 8080) | `java -jar target/mcp-light-1.0.jar` |
| Run (custom port) | `java -jar target/mcp-light-1.0.jar 9090` |

No separate lint or format step — `mvn compile` is the check. Code style follows IntelliJ defaults (`.editorconfig`).

## Architecture

Single package `ca.corbett.mcp`. No framework — bare Java 25 + `com.sun.net.httpserver` + Jackson (only two dependencies).

**Core classes:**
- **`McpServer`** — HTTP server. Accepts port/path/threads in constructor. Routes JSON-RPC 2.0 methods (`initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `ping`). Supports OPTIONS for CORS.
- **`McpTool`** — interface for callable tools. Implement `getName()`, `getDescription()`, `getInputSchema()`, and `execute(Map<String, Object> input)`.
- **`McpResource`** — interface for readable resources. Implement `getName()`, `getUri()`, `matchesUri(String)`, `getMimeType()`, and `getContent(String requestedUri)`. Optional `isBinary()` for base64 content.
- **`ExampleApp`** — runnable main class (set in jar manifest). Shows how to wire up a server with tools and resources.
- **`McpRequest` / `McpRequest2`** — Jackson-mapped request models.
- **`McpResponse` / `McpError` / `McpToolContent` / `McpResourceContent`** — response/error payload models.

**Request flow:** `McpServer` receives HTTP POST → reads body → deserializes JSON-RPC → dispatches by `method` field → serializes response → writes HTTP reply.

## Key Conventions

**Naming:** Tool and resource names must match `^[a-zA-Z][a-zA-Z0-9_-]*$` — enforced at registration and will throw `IllegalArgumentException`.

**Testing:** Always use port 0 in tests (`new McpServer(0)`), then discover the actual port via `server.getPort()`. Never hardcode a port in tests.

**Thread model:** `execute()` (tools) and `getContent()` (resources) run on the HTTP handler thread — keep them synchronous and fast. No async support.

**Thread safety:** `McpServer` uses `CopyOnWriteArrayList` for tools and resources, so `registerTool()`/`registerResource()`/`unregisterTool()`/`unregisterResource()` are safe to call at any time.

**Jackson config:** `ACCEPT_CASE_INSENSITIVE_PROPERTIES` + `FAIL_ON_UNKNOWN_PROPERTIES=false` — handles slightly malformed MCP client JSON.

**Binary resources:** Return base64-encoded content from `getContent()` and override `isBinary()` to return `true`.

**Templated resources:** `getUri()` returns a URI template (e.g., `myapp://res/{id}`). `matchesUri()` must implement the matching logic — the server calls it to dispatch resource reads.

**Shutdown:** Register a shutdown hook with `Runtime.getRuntime().addShutdownHook(new Thread(server::stop))`.

**Publishing:** GPG signing (`gpg.skip=true`) and Sonatype Central publishing (`skipPublishing=true`) are both disabled by default in `pom.xml`.
