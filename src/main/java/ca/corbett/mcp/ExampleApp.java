package ca.corbett.mcp;

import java.util.Map;

/**
 * Contains a complete example of how to use the MCP library to host tools and resources.
 * Run this class to spin up an MCP server on port 8080,
 * and then you can send JSON-RPC requests to it at "http://localhost:8080/mcp".
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ExampleApp {

    public static void main(String[] args) {
        McpServer server = new McpServer(8080);
        server.registerTool(new ExampleTool());
        server.registerResource(new ExampleResource());
        server.registerResource(new ExampleTemplateResource());
        try {
            server.start();
        }
        catch (Exception e) {
            System.err.println("Failed to start MCP server: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Register a shutdown hook to gracefully stop the server when the application is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        System.out.println("Server started on port 8080. Press Ctrl+C to stop.");
    }

    /**
     * A very simple example tool that returns a fixed string regardless of input.
     */
    static class ExampleTool implements McpTool {

        /**
         * Name is mandatory, and must be unique across registered tools.
         * The name must begin with a letter and can only contain letters, numbers, hyphens, and underscores.
         */
        @Override
        public String getName() {
            return "exampleTool";
        }

        /**
         * The description is optional, but highly recommended.
         */
        @Override
        public String getDescription() {
            return "A tool to serve as an example of how to implement the McpTool interface.";
        }

        /**
         * Here you can define the input arguments for your tools, and specify which ones are mandatory.
         * The examples here are just to illustrate the structure.
         * You can return an empty map if your tool doesn't take any arguments.
         */
        @Override
        public Map<String, Object> getInputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "arg1", Map.of(
                                    "type", "string",
                                    "description", "The first argument (mandatory)."
                            ),
                            "arg2", Map.of(
                                    "type", "integer",
                                    "description", "The second argument (optional)."
                            )
                    ),
                    "required", new String[]{"arg1"}
            );
        }

        /**
         * This method will be invoked by McpServer when your tool is called.
         * The input map will contain the validated argument list supplied by the caller.
         * Note that this method executes on the Http thread!
         * We currently only handle synchronous requests.
         */
        @Override
        public String execute(Map<String, Object> input) throws Exception {
            return "Successfully executed the example tool!";
        }
    }

    /**
     * A very simple example of a static resource.
     */
    static class ExampleResource implements McpResource {

        /**
         * Name is mandatory, and must be unique across registered resources.
         * The result for resource names are the same as for tool names:
         * they must begin with a letter and can only contain letters, numbers, hyphens, and underscores.
         */
        @Override
        public String getName() {
            return "exampleResource";
        }

        /**
         * Description is optional but highly recommended.
         */
        @Override
        public String getDescription() {
            return "An example of a static resource.";
        }

        /**
         * The URI must be unique across registered resources.
         * This can be whatever you like, as long as it is not null or blank.
         */
        @Override
        public String getUri() {
            return "example://example/resource";
        }

        /**
         * Your implementation should report whether the given URI matches this resource.
         * This is how McpServer determines which resource to serve for a given request.
         * In the case of a static resource (that is, no template), this is an easy string comparison.
         */
        @Override
        public boolean matchesUri(String uri) {
            return uri != null && uri.equals(getUri());
        }

        /**
         * You must return a MIME type that accurately describes the content you are serving.
         * Binary content is fine, but you have to base64 encode it and return it as a string.
         */
        @Override
        public String getMimeType() {
            return "text/plain";
        }

        /**
         * The string version of your content. If your content is binary, you should base64 encode it
         * and return the encoded string. Note that you also have to implement the isBinary() method
         * and return true! By default it returns false.
         */
        @Override
        public String getContent(String requestedUri) throws Exception {
            return "Hello! This is a static resource being served at URI: " + requestedUri;
        }
    }

    /**
     * An example of a templated resource. The URI can contain a variable that can be supplied by
     * the caller, and you can look up a resource dynamically based on that parameter.
     */
    static class ExampleTemplateResource implements McpResource {

        @Override
        public String getName() {
            return "exampleTemplateResource";
        }

        @Override
        public String getDescription() {
            return "An example of a resource that uses a URI template.";
        }

        @Override
        public String getUri() {
            return "example://example/template/{id}";
        }

        /**
         * Our implementation of matchesUri() is a little more complicated now, because we have to
         * check that the given URI is a good match for our template.
         */
        @Override
        public boolean matchesUri(String uri) {
            return uri != null
                    && uri.startsWith("example://example/template/")
                    && uri.length() > "example://example/template/".length();
        }

        @Override
        public String getMimeType() {
            return "text/plain";
        }

        /**
         * In this example, we will return the requested id as part of the response, just to show
         * that the response can vary depending on input. Your implementation can do a database
         * lookup or dynamically create something or whatever else you need.
         */
        @Override
        public String getContent(String requestedUri) throws Exception {
            String id = requestedUri.substring("example://example/template/".length());
            return "Hello! This resource was served from a template. You requested the ID: " + id;
        }
    }
}
