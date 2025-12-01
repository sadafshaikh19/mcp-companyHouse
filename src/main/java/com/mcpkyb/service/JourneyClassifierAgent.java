package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpkyb.utils.JsonLoader;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class JourneyClassifierAgent {
    
    @Autowired
    private OpenAiChatModel chatModel;

    @Autowired
    private LLMMonitoringService llmMonitoringService;
    
    public Map<String, Object> classifyJourney(String customerId) throws IOException {
        JsonNode crmData = JsonLoader.loadJson("crm.json");
        JsonNode customer = null;
        
        for (JsonNode c : crmData.get("customers")) {
            if (c.get("customer_id").asText().equals(customerId)) {
                customer = c;
                break;
            }
        }
        
        if (customer == null) {
            throw new IOException("Customer not found: " + customerId);
        }
        
        String systemPrompt = """
            You are a Journey Classifier Agent for KYB Early-Risk Radar.
            
            Your task is to classify the customer journey type based on legal entity structure.
            
            Journey types:
            1. SOLE_TRADER - Individual operating as sole proprietor
            2. LIMITED_COMPANY_SINGLE - Single-party limited company
            3. LIMITED_COMPANY_MULTI - Multi-party limited company
            4. PARTNERSHIP_LLP - Partnership or Limited Liability Partnership
            5. GROUP - Group entity with linked/sister entities
            
            You must analyze the legal name, structure indicators, and return a JSON object with:
            - journey_type: one of the above types
            - has_linked_customers: boolean
            - num_parties: integer (estimated number of parties/beneficial owners)
            - reasoning: brief explanation
            
            Return ONLY valid JSON, no additional text.
        """;
        
        String userPrompt = String.format("""
            Classify the journey type for this customer:
            %s
            
            Focus on:
            - Legal name structure (LLP, Limited, Private Limited, etc.)
            - Entity type indicators
            - Potential for linked entities
        """, customer.toString());
        
        ChatRequest request = ChatRequest.builder()
                .messages(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
                )
                .build();
        
        // Track LLM call
        io.micrometer.core.instrument.Timer.Sample llmTimer = llmMonitoringService.startLLMCall();

        ChatResponse response = chatModel.chat(request);
        String responseText = response.aiMessage().text();

        // Record successful LLM call
        llmMonitoringService.recordSuccessfulCall(
            llmTimer,
            "gpt-4o-mini", // model from config
            200, // estimated prompt tokens
            100, // estimated completion tokens
            1000 // estimated duration in ms
        );
        
        // Parse JSON from response (handle markdown code blocks if present)
        String jsonStr = responseText.trim();
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7);
        }
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3);
        }
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
        }
        jsonStr = jsonStr.trim();
        
        // Simple JSON parsing (in production, use proper JSON library)
        Map<String, Object> result = new HashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedResult = mapper.readValue(jsonStr, Map.class);
            result = parsedResult;
        } catch (Exception e) {
            // Fallback parsing
            result.put("journey_type", inferJourneyType(customer));
            result.put("has_linked_customers", false);
            result.put("num_parties", 1);
            result.put("reasoning", "Automated classification based on entity structure");
        }
        
        return result;
    }
    
    private String inferJourneyType(JsonNode customer) {
        String legalName = customer.get("legal_name").asText().toUpperCase();
        
        if (legalName.contains("LLP")) {
            return "PARTNERSHIP_LLP";
        } else if (legalName.contains("LIMITED") || legalName.contains("PVT") || legalName.contains("PRIVATE")) {
            // Default to single-party, could be enhanced with actual party data
            return "LIMITED_COMPANY_SINGLE";
        } else {
            return "SOLE_TRADER";
        }
    }
}

