package com.mcpkyb.controller;

import com.mcpkyb.mcp.client.McpOrchestratorAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/kyb/mcp")
public class McpOrchestratorController {
    
    @Autowired
    private McpOrchestratorAgent orchestratorAgent;
    
    @GetMapping("/run/{customerId}")
    public ResponseEntity<Map<String, Object>> runKYB(@PathVariable String customerId) {
        try {
            Map<String, Object> response = orchestratorAgent.runKYB(customerId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "KYB workflow failed",
                "message", e.getMessage()
            ));
        }
    }
}

