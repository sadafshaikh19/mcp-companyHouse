package com.mcpkyb.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual LangSmith tracing service using REST API
 * Use this until the Java SDK supports tracing
 */
@Service
public class LangSmithManualTracing {

    @Value("${langsmith.api.key:}")
    private String apiKey;

    @Value("${langsmith.project.name:mcp-kyb-langchain}")
    private String projectName;

    public LangSmithManualTracing() {
        System.out.println("🔧 LangSmithManualTracing initialized");
        System.out.println("🔑 API Key configured: " + (apiKey != null && !apiKey.isEmpty() ? "Yes" : "No"));
        System.out.println("📁 Project: " + projectName);
    }

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "https://api.smith.langchain.com";

    /**
     * Send a trace to LangSmith manually
     */
    public void sendTrace(String runId, String runType, Map<String, Object> inputs,
                         Map<String, Object> outputs, long startTime, long endTime) {

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("❌ LangSmith API key not configured - skipping trace");
            return;
        }

        System.out.println("🔑 Using LangSmith API key: " + apiKey.substring(0, 10) + "...");
        System.out.println("📁 Project: " + projectName);

        // Validate inputs
        if (runId == null || runId.trim().isEmpty()) {
            System.out.println("❌ Run ID is required");
            return;
        }
        if (inputs == null || outputs == null) {
            System.out.println("❌ Inputs and outputs are required");
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", apiKey); // LangSmith uses X-API-Key header
            headers.set("Content-Type", "application/json");

            // LangSmith expects timestamps in seconds, not milliseconds
            double startTimeSeconds = startTime / 1000.0;
            double endTimeSeconds = endTime / 1000.0;

            Map<String, Object> traceData = new HashMap<>();
            traceData.put("id", runId);
            traceData.put("name", "LLM Call - " + runType);
            traceData.put("run_type", runType);
            traceData.put("inputs", inputs);
            traceData.put("outputs", outputs);
            traceData.put("start_time", startTimeSeconds);
            traceData.put("end_time", endTimeSeconds);

            // Try different project/session field names - some APIs use 'session_id' instead
            traceData.put("session_name", projectName);

            // Add minimal extra data
            Map<String, Object> extra = new HashMap<>();
            extra.put("project", projectName);
            extra.put("service", "mcp-kyb-langchain");
            traceData.put("extra", extra);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(traceData, headers);

            // Try the correct endpoint
            String url = baseUrl + "/runs";
            System.out.println("📤 Sending trace to LangSmith: " + url);
            System.out.println("🔑 Auth header: X-API-Key: " + apiKey.substring(0, 15) + "...");
            System.out.println("📊 Trace data: " + traceData);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("✅ Trace sent to LangSmith successfully: " + runId);
            } else {
                System.out.println("❌ Failed to send trace to LangSmith:");
                System.out.println("   Status: " + response.getStatusCode());
                System.out.println("   Response: " + response.getBody());
                System.out.println("   URL: " + url);
                System.out.println("   Request payload size: " + traceData.toString().length() + " chars");
            }

        } catch (Exception e) {
            System.out.println("❌ Error sending trace to LangSmith: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Helper method to create LLM call traces
     */
    public void traceLLMCall(String prompt, String response, int promptTokens,
                           int completionTokens, double cost, long durationMs) {

        // Generate a proper UUID for LangSmith (matches the pattern: 8-4-4-4-12 hex chars)
        String runId = java.util.UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis() - durationMs;
        long endTime = System.currentTimeMillis();

        Map<String, Object> inputs = Map.of("prompt", prompt);
        Map<String, Object> outputs = Map.of(
            "response", response,
            "prompt_tokens", promptTokens,
            "completion_tokens", completionTokens,
            "cost", cost
        );

        sendTrace(runId, "llm", inputs, outputs, startTime, endTime);
    }
}
