package com.wfh.drawio.ai.tools;

/**
 * @Title: ToolDefinition
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 21:00
 * @description:
 */
public class ToolDefinition {

    private String name;
    private String description;
    private Class<?> inputSchema;
    private Class<?> outputType;

    private ToolDefinition() {}

    public static Builder builder() {
        return new Builder();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Class<?> getInputSchema() { return inputSchema; }
    public Class<?> getOutputType() { return outputType; }

    public static class Builder {
        private final ToolDefinition def = new ToolDefinition();

        public Builder name(String name) {
            def.name = name;
            return this;
        }

        public Builder description(String description) {
            def.description = description;
            return this;
        }

        public Builder inputSchema(Class<?> schema) {
            def.inputSchema = schema;
            return this;
        }

        public Builder outputType(Class<?> outputType) {
            def.outputType = outputType;
            return this;
        }

        public ToolDefinition build() {
            return def;
        }
    }

}
