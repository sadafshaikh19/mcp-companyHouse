package com.mcpkyb.mcp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpkyb.mcp.model.*;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class McpClient {
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverBaseUrl;
    
    public McpClient(@Value("${mcp.server.url:http://localhost:8080/mcp}") String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    public List<McpTool> listTools() throws IOException {
        McpMessage request = new McpMessage();
        request.setJsonrpc("2.0");
        request.setId(UUID.randomUUID().toString());
        request.setMethod("tools/list");
        request.setParams(Map.of());
        
        McpMessage response = sendMessage(request);
        
        if (response.getError() != null) {
            throw new IOException("Error listing tools: " + response.getError().getMessage());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolsList = (List<Map<String, Object>>) result.get("tools");
        
        List<McpTool> tools = new ArrayList<>();
        for (Map<String, Object> toolMap : toolsList) {
            McpTool tool = objectMapper.convertValue(toolMap, McpTool.class);
            tools.add(tool);
        }
        
        return tools;
    }
    
    public String callTool(String toolName, Map<String, Object> arguments) throws IOException {
        McpMessage request = new McpMessage();
        request.setJsonrpc("2.0");
        request.setId(UUID.randomUUID().toString());
        request.setMethod("tools/call");
        request.setParams(Map.of(
            "name", toolName,
            "arguments", arguments
        ));
        
        McpMessage response = sendMessage(request);
        
        if (response.getError() != null) {
            throw new IOException("Error calling tool " + toolName + ": " + response.getError().getMessage());
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        
        // For runKYB tool, return structured JSON directly if available
        if ("runKYB".equals(toolName)) {
            // Check if structured field exists (contains full JSON)
            Object structured = result.get("structured");
            if (structured != null) {
                // Return structured JSON as string
                try {
                    return objectMapper.writeValueAsString(structured);
                } catch (JsonProcessingException e) {
                    // Fall through to text extraction
                }
            }
            // Also check if fields are at root level (new format)
            if (result.containsKey("journey_type") || result.containsKey("entity_profile")) {
                // All required fields are at root level, return the full result
                try {
                    return objectMapper.writeValueAsString(result);
                } catch (JsonProcessingException e) {
                    // Fall through to text extraction
                }
            }
        }
        
        // Standard content extraction
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        
        if (content != null && !content.isEmpty()) {
            Map<String, Object> firstContent = content.get(0);
            String text = (String) firstContent.get("text");
            
            // For runKYB, if text is JSON, return it directly
            if ("runKYB".equals(toolName) && text != null && text.trim().startsWith("{")) {
                return text;
            }
            
            return text;
        }
        
        return result.toString();
    }
    
    /**
     * Call runKYB tool and return structured Map instead of String.
     * This is a convenience method for runKYB tool that returns full structured output.
     */
    public Map<String, Object> callRunKYBTool(String customerId) throws IOException {
        String resultJson = callTool("runKYB", Map.of("customerId", customerId));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(resultJson, Map.class);
            return result;
        } catch (JsonProcessingException e) {
            throw new IOException("Error parsing runKYB result", e);
        }
    }
    
    private McpMessage sendMessage(McpMessage request) throws IOException {
        String jsonRequest;
        try {
            jsonRequest = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IOException("Error serializing request", e);
        }
        
        RequestBody body = RequestBody.create(
            jsonRequest,
            MediaType.parse("application/json")
        );
        
        Request httpRequest = new Request.Builder()
                .url(serverBaseUrl + "/message")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            
            String responseBody = response.body().string();
            try {
                return objectMapper.readValue(responseBody, McpMessage.class);
            } catch (JsonProcessingException e) {
                throw new IOException("Error deserializing response", e);
            }
        }
    }
    
    public boolean healthCheck() {
        try {
            Request request = new Request.Builder()
                    .url(serverBaseUrl + "/health")
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }
}

