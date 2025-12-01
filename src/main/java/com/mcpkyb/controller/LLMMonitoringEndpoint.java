package com.mcpkyb.controller;

import com.mcpkyb.service.LLMMonitoringService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom actuator endpoint for LLM monitoring statistics
 */
@Component
@Endpoint(id = "llmStats")
public class LLMMonitoringEndpoint {

    private final LLMMonitoringService llmMonitoringService;

    public LLMMonitoringEndpoint(LLMMonitoringService llmMonitoringService) {
        this.llmMonitoringService = llmMonitoringService;
    }

    @ReadOperation
    public Map<String, Object> getLLMStatistics() {
        LLMMonitoringService.LLMStatistics stats = llmMonitoringService.getStatistics();

        Map<String, Object> result = new HashMap<>();
        result.put("totalTokens", stats.totalTokens);
        result.put("promptTokens", stats.promptTokens);
        result.put("completionTokens", stats.completionTokens);
        result.put("totalCostUSD", stats.totalCostUSD);
        result.put("description", "Cumulative LLM usage statistics since application start");

        return result;
    }
}
