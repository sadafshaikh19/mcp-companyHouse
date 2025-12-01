package com.mcpkyb.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpkyb.mcp.model.McpMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/mcp")
public class McpServerController {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private McpServerService mcpServerService;
    
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    @PostMapping(value = "/message", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpMessage> handleMessage(@RequestBody McpMessage request) {
        try {
            McpMessage response = mcpServerService.handleMessage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            McpMessage errorResponse = new McpMessage();
            errorResponse.setJsonrpc("2.0");
            errorResponse.setId(request.getId());
            com.mcpkyb.mcp.model.McpError error = new com.mcpkyb.mcp.model.McpError();
            error.setCode(-32603);
            error.setMessage("Internal error: " + e.getMessage());
            errorResponse.setError(error);
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseEndpoint() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String sessionId = UUID.randomUUID().toString();
        emitters.put(sessionId, emitter);
        
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError((ex) -> emitters.remove(sessionId));
        
        // Send initial connection message
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("sessionId", sessionId)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        
        return emitter;
    }
    
    @PostMapping(value = "/sse/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Map<String, String>> sendSseMessage(@RequestBody McpMessage request) {
        try {
            McpMessage response = mcpServerService.handleMessage(request);
            String responseJson = objectMapper.writeValueAsString(response);
            
            // Send to all connected emitters
            emitters.values().forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name("message")
                        .data(responseJson));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            
            return ResponseEntity.ok(Map.of("status", "sent"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
    
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "mcp-kyb-server"));
    }
}

