package ca.corbett.mcp;

/**
 * Represents an argument that a caller can supply to a prompt when retrieving it.
 * Each argument has a name, a description, and a required flag.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class McpPromptArgument {

    private final String name;
    private final String description;
    private final boolean required;

    public McpPromptArgument(String name, String description, boolean required) {
        this.name = name;
        this.description = description;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }
}
