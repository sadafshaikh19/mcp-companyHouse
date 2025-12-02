package com.mcpkyb.controller;

import com.mcpkyb.mcp.client.McpOrchestratorAgent;
import com.mcpkyb.service.CompanyHouseAgent;
import com.mcpkyb.service.SentimentAnalysisAgent;
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

    @Autowired
    private SentimentAnalysisAgent sentimentAnalysisAgent;

    @Autowired
    private CompanyHouseAgent companyHouseAgent;
    
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

    @GetMapping("/sentiment/{customerId}")
    public ResponseEntity<Map<String, Object>> analyzeSentiment(@PathVariable String customerId) {
        try {
            Map<String, Object> response = sentimentAnalysisAgent.analyzeSentimentByCustomerId(customerId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Sentiment analysis failed",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/companyHouse/{customerId}")
    public ResponseEntity<Map<String, Object>> searchCompanyHouse(@PathVariable String customerId) {
        try {
            var companies = companyHouseAgent.searchCompanyByCustomerId(customerId);
            Map<String, Object> response = Map.of(
                "customerId", customerId,
                "companies", companies,
                "totalResults", companies.size()
            );
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Company House search failed",
                "message", e.getMessage(),
                "customerId", customerId
            ));
        }
    }
}

