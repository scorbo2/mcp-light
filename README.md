# mcp-light

## What is this?

This is an academic/hobby project meant to help learn about the MCP protocol and how it works.

### Project goals:

- produce an extremely lightweight MCP server implementation for Java applications.
- minimal third party dependencies! Only Jackson for the JSON handling.
- implement a reasonable percentage of the MCP spec (enough to gain a better understanding of the protocol)
- produce an actual, working, extensible MCP server that could actually be used in a real application.
- at an absolute bare minimum, it should be possible to connect with MCP Inspector, read resources, and call tools.

## Basic usage

The generated jar file is executable, and comes with a very simple built-in example server implementation.
To run it, simply clone the project, build with Maven/Java 25, and run the generated jar:

```shell
git clone https://github.com/scorbo2/mcp-light.git
cd mcp-light
mvn clean package
java -jar target/mcp-light-1.1.jar

# Or, start on a different port (default 8080):
java -jar target/mcp-light-1.1.jar 9090
```

This will start a very simple MCP server with the following features:

- a tool called `exampleTool` that takes two dummy arguments and returns a fixed string.
- a static resource at `example://example/resource` that returns a fixed string.
- a templated resource at `example://example/template/{id}` that returns a string containing the id you provided.
- support for OPTIONS requests, allowing you to connect from browser-based clients such as `llama-ui`.
- support for MCP "ping" requests.

Once the server is up and running, you should be able to connect to it from
[MCP Inspector](https://github.com/modelcontextprotocol/inspector) to list the tools and resources. Or, you
can wire up the server to some other MCP client.

### Example connection: OpenCode

You can modify your `~/.config/opencode/opencode.json` file to include a connection to your local MCP server:

```json
{
  // existing opencode config here...
    
  "mcp": {
    "mcp-light": {
      "type": "remote",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Now, when you start OpenCode, it should connect to your server. You can ask it to invoke tools, as in this example:

```
Prompt: Please call the `mcp-light_exampleTool` with an arg1 value of `hello` and report the results.                    

Thought: 388ms                                                                                                   
                                                                                                                  
The user wants me to call the mcp-light_exampleTool with arg1="hello".                                           
                                                                                                                  
⚙ mcp-light_exampleTool [arg1=hello]                                                                             
                                                                                                                  
Thought: 295ms                                                                                                   
                                                                                                                  
The tool executed successfully but didn't return much detail. Let me report the result to the user.              
                                                                                                                  
The mcp-light_exampleTool executed successfully. The tool returned a message: "Successfully executed the         
example tool!"
```   

## Using in code

The library is available in Maven Central, so you can just add it as a dependency in your project:

```xml
<dependency>
    <groupId>ca.corbett</groupId>
    <artifactId>mcp-light</artifactId>
    <version>1.1</version>
</dependency>
```

Once the library is imported into your Java project, you can implement the following interfaces as needed:

- [McpTool](src/main/java/ca/corbett/mcp/McpTool.java) to define tools that can be called by clients.
- [McpResource](src/main/java/ca/corbett/mcp/McpResource.java) to expose resources that clients can read.

Then, you can create an instance of [McpServer](src/main/java/ca/corbett/mcp/McpServer.java) and register your
tools and resources to it before starting the server:

```java
McpServer server = new McpServer();
server.registerTool(new MyTool());
server.registerResource(new MyResource());
server.start();
```

You should add code to ensure your server is stopped correctly. An easy way to do this is to add a shutdown hook:

```java
Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
```

## Manual testing

While your server is up, you can use any REST client (like Bruno, Postman, or even curl) to send JSON-RPC requests
to it, by manually specifying the request body. For example, to send an `initialize` request via curl:

```shell
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2024-11-05",
          "capabilities": {
            "roots": {
              "listChanged": true
            },
            "sampling": {}
          },
          "clientInfo": {
            "name": "ExampleClient",
            "version": "1.0.0"
          }
        }
      }'
```

This approach allows you to see the raw responses directly from your server, if you want to get a better
look at what's happening under the hood.

But generally, testing with an actual client like MCP Inspector is just easier.

## Is this production-grade?

No. This is a hobby project. Use it for fun and learning!

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## More info

- GitHub repo: https://github.com/scorbo2/mcp-light
- Issues page: https://github.com/scorbo2/mcp-light/issues
- MCP spec: https://modelcontextprotocol.io/specification/
