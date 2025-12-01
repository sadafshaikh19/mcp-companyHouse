package com.mcpkyb.mcp.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class McpOrchestratorAgent {
    
    @Autowired
    private McpClient mcpClient;
    
    public Map<String, Object> runKYB(String customerId) throws IOException {
        // Initialize MCP connection
        if (!mcpClient.healthCheck()) {
            throw new IOException("MCP server is not available");
        }
        
        // Use the runKYB tool directly which handles complete workflow via ConductorAgent
        // This is much more reliable than orchestrating individual tools
        try {
            Map<String, Object> result = mcpClient.callRunKYBTool(customerId);
            return result;
        } catch (IOException e) {
            // Fallback: use direct orchestration if runKYB tool fails
            return runKYBDirect(customerId);
        }
    }
    
    // Direct orchestration fallback method (if runKYB tool fails)
    private Map<String, Object> runKYBDirect(String customerId) throws IOException {
        Map<String, Object> results = new HashMap<>();
        
        String profileSummary = mcpClient.callTool("getCustomerProfile", 
                Map.of("customerId", customerId));
        results.put("profile", profileSummary);
        
        String transactionSummary = mcpClient.callTool("analyzeTransactions", 
                Map.of("customerId", customerId));
        results.put("transactions", transactionSummary);
        
        String riskAssessment = mcpClient.callTool("assessRisk", 
                Map.of("profileSummary", profileSummary, 
                       "transactionSummary", transactionSummary));
        results.put("risk", riskAssessment);
        
        String kybNote = mcpClient.callTool("generateKYBNote", 
                Map.of("profileSummary", profileSummary,
                       "transactionSummary", transactionSummary,
                       "riskAssessment", riskAssessment));
        results.put("kybNote", kybNote);
        
        return results;
    }
}

