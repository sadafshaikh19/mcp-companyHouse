package com.mcpkyb.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpTool {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("inputSchema")
    private ToolInputSchema inputSchema;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolInputSchema {
        @JsonProperty("type")
        private String type = "object";
        
        @JsonProperty("properties")
        private Map<String, PropertySchema> properties;
        
        @JsonProperty("required")
        private List<String> required;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PropertySchema {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("description")
        private String description;
    }
}

