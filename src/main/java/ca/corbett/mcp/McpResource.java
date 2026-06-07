package ca.corbett.mcp;

/**
 * Implement this interface to create a resource that can be called by the McpServer.
 * Register your resource via the McpServer.registerResource() method.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public interface McpResource {

    /**
     * Return a non-blank name for your resource. Should be short and alphanumeric, with no spaces.
     */
    String getName();

    /**
     * It is recommended, but not required, that you provide a helpful description of your resource here.
     */
    String getDescription();

    /**
     * Return the URI for your resource.
     * This can either be a fixed string like "myapp://some/resource" or
     * a URI template like "myapp://some/resource/{id}" if you want to support path parameters.
     */
    String getUri();

    /**
     * Return true if the given URI matches your resource's URI or URI template.
     * For example, if getUri() returns "myapp://some/resource/{id}", then matchesUri("myapp://some/resource/123")
     * should return true.
     */
    boolean matchesUri(String uri);

    /**
     * Return your content in string form (text/plain, application/json, etc.).
     * If your contents are binary, you must base64-encode them and return the
     * appropriate MIME type here (image/png, application/pdf, etc.).
     * Remember to return true from isBinary() if your content is base64-encoded.
     */
    String getMimeType();

    /**
     * Returns the actual content of your resource in String form.
     * If your content is binary, it must be base64-encoded.
     * <p>
     * If an error occurs while fetching your content, throw any Exception
     * with a helpful message. McpServer will catch it and return your message to the caller.
     * </p>
     * <p><b>IMPORTANT</b> - this method runs on the HTTP handler thread,
     * so ideally it should execute quickly. We don't do async requests here.</p>
     */
    String getContent(String requestedUri) throws Exception;

    /**
     * If your contents are base64-encoded binary, return true here.
     */
    default boolean isBinary() { return false; }
}
