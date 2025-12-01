package com.mcpkyb.controller;

import com.mcpkyb.service.LLMMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for LLM monitoring
 */
@RestController
public class LLMMonitoringController {

    private final LLMMonitoringService llmMonitoringService;

    public LLMMonitoringController(LLMMonitoringService llmMonitoringService) {
        this.llmMonitoringService = llmMonitoringService;
    }

    @GetMapping("/api/llm/stats")
    public ResponseEntity<Map<String, Object>> getLLMStats() {
        LLMMonitoringService.LLMStatistics stats = llmMonitoringService.getStatistics();

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", System.currentTimeMillis());
        response.put("statistics", Map.of(
            "totalTokens", stats.totalTokens,
            "promptTokens", stats.promptTokens,
            "completionTokens", stats.completionTokens,
            "totalCostUSD", stats.totalCostUSD,
            "averageCostPerToken", stats.totalTokens > 0 ? stats.totalCostUSD / stats.totalTokens : 0
        ));

        return ResponseEntity.ok(response);
    }
}
