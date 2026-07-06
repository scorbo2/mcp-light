# AGENTS.md

## Quick commands

| Task | Command                                    |
|------|--------------------------------------------|
| Build | `mvn clean package`                        |
| Run (default port 8080) | `java -jar target/mcp-light-1.1.jar`       |
| Run (custom port) | `java -jar target/mcp-light-1.1.jar 9090` |
| Tests | `mvn test`                                 |
| Compile only | `mvn compile`                              |

## Architecture

Single package `ca.corbett.mcp`. No framework — bare Java 25 + `com.sun.net.httpserver` + Jackson.

- **`McpServer`** — HTTP server (port/path configurable). Registers tools/resources, routes JSON-RPC methods.
- **`McpTool`** — interface to implement for callable tools. `execute()` runs on the HTTP handler thread (synchronous).
- **`McpResource`** — interface to implement for readable resources. `getContent()` runs on the HTTP handler thread. Supports binary (base64) via `isBinary()`.
- **`ExampleApp`** — runnable entry point with sample tool and resources. Set as main class in the jar manifest.

## Gotchas

- **Tool/resource names** must match `^[a-zA-Z][a-zA-Z0-9_-]*$` (enforced at registration).
- **Jackson** is configured for case-insensitive property matching and ignoring unknown properties — MCP clients may send slightly malformed JSON.
- **Port 0** in the constructor = random available port (useful for tests). Use `server.getPort()` to discover it.
- **`McpServer`** is thread-safe for register/unregister via `CopyOnWriteArrayList`.
- **GPG signing** is skipped by default (`gpg.skip=true`). Set to `false` for Sonatype Central publishing.
- **Sonatype publishing** is skipped by default (`skipPublishing=true`). Set to `false` to enable.
- Tests use port 0 and discover the actual port via `server.getPort()`. Never hardcode a port in tests.
- No lint, formatter, or typecheck steps beyond `mvn compile` / `mvn test`. Code style follows IntelliJ defaults (`.editorconfig`).
