package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RiskComplianceAgent {

    private static final Logger logger = LoggerFactory.getLogger(RiskComplianceAgent.class);

    private final OpenAiChatModel chatModel;
    private final Tracer tracer;
    private final MetricsService metricsService;
    private final LLMMonitoringService llmMonitoringService;
    private final ObjectMapper objectMapper;

    public RiskComplianceAgent(OpenAiChatModel chatModel, Tracer tracer, MetricsService metricsService, LLMMonitoringService llmMonitoringService, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.tracer = tracer;
        this.metricsService = metricsService;
        this.llmMonitoringService = llmMonitoringService;
        this.objectMapper = objectMapper;
    }

    public String assessRisk(String profileSummary, String txInsights, JsonNode rules) {
        Span span = tracer.spanBuilder("RiskComplianceAgent.assessRisk")
                .setAttribute("operation", "risk_assessment")
                .setAttribute("profile.length", profileSummary.length())
                .setAttribute("txInsights.length", txInsights.length())
                .startSpan();

        Timer.Sample timerSample = metricsService.startTimer();

        try {
            logger.debug("Starting risk assessment for profile summary length: {}, transaction insights length: {}",
                    profileSummary.length(), txInsights.length());

            String prompt = String.format("""
                Profile: %s
                Transactions: %s
                Rules: %s
                Task:
                - Map observations to KYB rules.
                - Classify overall risk as GREEN / AMBER / RED.
                - Provide rationale and recommended actions.
                Output JSON with keys: risk_color, key_flags[], recommended_actions[].
            """, profileSummary, txInsights, rules.toString());

            span.setAttribute("prompt.length", prompt.length());

            // Build ChatRequest
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build();

            logger.debug("Sending chat request to OpenAI model");

        // Track LLM call
        io.micrometer.core.instrument.Timer.Sample llmTimer = llmMonitoringService.startLLMCall();

        // Call chat() and extract content
        ChatResponse response = chatModel.chat(request);
        String result = response.aiMessage().text();

        // Record successful LLM call
        llmMonitoringService.recordSuccessfulCall(
            llmTimer,
            "gpt-4o-mini", // model from config
            200, // estimated prompt tokens
            100, // estimated completion tokens
            1000 // estimated duration in ms
        );

            // Parse the result to extract risk color for metrics
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = objectMapper.readValue(result, Map.class);
                String riskColor = (String) responseMap.get("risk_color");
                if (riskColor != null) {
                    metricsService.recordRiskAssessment(riskColor);
                    span.setAttribute("risk.color", riskColor);
                    logger.info("Risk assessment completed with result: {}", riskColor);
                }
            } catch (Exception e) {
                logger.warn("Could not parse risk assessment response for metrics", e);
                span.setAttribute("parsing.error", e.getMessage());
            }

            span.setStatus(StatusCode.OK);
            span.setAttribute("response.length", result.length());

            return result;

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            logger.error("Error during risk assessment", e);
            throw e;
        } finally {
            timerSample.stop(Timer.builder("kyb.agent.risk_assessment")
                    .register(metricsService.getMeterRegistry()));
            span.end();
        }
    }
}

