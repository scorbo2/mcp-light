package ca.corbett.mcp;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * An alternative to {@link McpRequest} that is sent by llama-ui.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class McpRequest2 {
    public String serverName;
    public Request request;

    public class Request {
        public String url;
        public String method;
        public Headers headers;
        public Body body;
        public List<String> jsonRpcMethods;

        public class Headers {
            public String accept;

            @JsonAlias("content-type")
            public String contentType;

            @JsonAlias("mcp-protocol-version")
            public String mcpProtocolVersion;
        }

        public class Body {
            public String kind;
            public int size;
        }
    }
}
