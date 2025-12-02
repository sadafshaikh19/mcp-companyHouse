package com.mcpkyb.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpkyb.utils.JsonLoader;
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
        Map<String, Object> result;
        try {
            result = mcpClient.callRunKYBTool(customerId);
        } catch (IOException e) {
            // Fallback: use direct orchestration if runKYB tool fails
            result = runKYBDirect(customerId);
        }

        // Add organization_structure from crm.json
        try {
            JsonNode crmData = JsonLoader.loadJson("crm.json");
            JsonNode customers = crmData.get("customers");
            if (customers != null && customers.isArray()) {
                for (JsonNode customer : customers) {
                    if (customerId.equals(customer.get("customer_id").asText())) {
                        JsonNode orgStructure = customer.get("organization_structure");
                        if (orgStructure != null) {
                            result.put("organization_structure", orgStructure.asText());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the entire request
            System.err.println("Failed to load organization_structure from crm.json: " + e.getMessage());
        }

        return result;
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

