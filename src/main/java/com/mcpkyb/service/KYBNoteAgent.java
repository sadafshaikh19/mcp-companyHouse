package com.mcpkyb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KYBNoteAgent {

    @Autowired
    private OpenAiChatModel chatModel;

    @Autowired
    private LLMMonitoringService llmMonitoringService;
    
    public Map<String, Object> generateKYBNoteWithActions(String profile, String txInsights, 
                                                          Map<String, Object> riskAssessment) {
        String systemPrompt = """
            You are a KYB Note & Action Plan Agent for KYB Early-Risk Radar.
            
            Your task is to generate a well-written KYB narrative and a clear list of recommended actions for the Relationship Manager.
            
            Return a JSON object with:
            - kyb_note: A professional narrative paragraph summarizing the KYB assessment
            - recommended_actions: An array of clear, actionable items for the RM
            
            The narrative should be:
            - Professional and concise
            - Suitable for RM consumption
            - Include key risk indicators
            - Mention any notable patterns or concerns
            
            Recommended actions should be:
            - Clear and specific
            - Actionable
            - Prioritized if possible
            
            Return ONLY valid JSON.
        """;
        
        String userPrompt = String.format("""
            Profile: %s
            
            Transaction Insights: %s
            
            Risk Assessment: %s
            
            Generate the KYB note and recommended actions.
        """, profile, txInsights, riskAssessment.toString());
        
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
        
        // Parse JSON from response
        String jsonStr = cleanJsonResponse(responseText);
        
        Map<String, Object> result;
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedResult = mapper.readValue(jsonStr, Map.class);
            result = parsedResult;
        } catch (Exception e) {
            // Fallback: generate structured output
            result = generateFallbackNote(profile, txInsights, riskAssessment);
        }
        
        // Ensure required fields
        if (!result.containsKey("kyb_note")) {
            result.put("kyb_note", generateNarrative(profile, txInsights, riskAssessment));
        }
        if (!result.containsKey("recommended_actions")) {
            result.put("recommended_actions", generateDefaultActions(riskAssessment));
        }
        
        return result;
    }
    
    private String cleanJsonResponse(String responseText) {
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
        return jsonStr.trim();
    }
    
    private Map<String, Object> generateFallbackNote(String profile, String txInsights, 
                                                     Map<String, Object> riskAssessment) {
        Map<String, Object> result = new HashMap<>();
        result.put("kyb_note", generateNarrative(profile, txInsights, riskAssessment));
        result.put("recommended_actions", generateDefaultActions(riskAssessment));
        return result;
    }
    
    private String generateNarrative(String profile, String txInsights, Map<String, Object> riskAssessment) {
        String band = (String) riskAssessment.getOrDefault("band", "AMBER");
        return String.format(
            "KYB Risk Assessment: %s risk band identified. Profile analysis indicates %s. " +
            "Transaction pattern analysis shows %s. Key risk indicators: %s. " +
            "Assessment score: %d. Requires RM attention based on risk profile.",
            band,
            profile.length() > 100 ? profile.substring(0, 100) + "..." : profile,
            txInsights.length() > 150 ? txInsights.substring(0, 150) + "..." : txInsights,
            riskAssessment.getOrDefault("triggers", new ArrayList<>()).toString(),
            riskAssessment.getOrDefault("score", 20)
        );
    }
    
    @SuppressWarnings("unchecked")
    private List<String> generateDefaultActions(Map<String, Object> riskAssessment) {
        List<String> actions = new ArrayList<>();
        String band = (String) riskAssessment.getOrDefault("band", "AMBER");
        List<String> triggers = (List<String>) riskAssessment.getOrDefault("triggers", new ArrayList<>());
        
        if (band.equals("RED")) {
            actions.add("Urgent: Schedule immediate KYB review meeting with customer");
            actions.add("Escalate to senior risk team for enhanced due diligence");
        } else if (band.equals("AMBER")) {
            actions.add("Schedule KYB review within next 30 days");
            actions.add("Request additional documentation to address identified concerns");
        } else {
            actions.add("Continue regular monitoring per standard KYB review cycle");
        }
        
        if (triggers.contains("TRIG_HIGH_RISK_COUNTRY")) {
            actions.add("Verify transaction rationale for high-risk country payments");
        }
        if (triggers.contains("TRIG_CASH_HEAVY")) {
            actions.add("Validate cash deposit sources and ensure compliance with cash handling policies");
        }
        if (triggers.contains("TRIG_KYB_OVERDUE")) {
            actions.add("Complete overdue KYB review documentation immediately");
        }
        
        return actions;
    }
    
    /**
     * Backward-compatible method that accepts String risk assessment.
     * Converts to Map format and calls generateKYBNoteWithActions.
     */
    public String generateKYBNote(String profile, String txInsights, String riskJson) {
        // Parse riskJson string to Map if it's JSON, otherwise create a simple map
        Map<String, Object> riskAssessment;
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(riskJson, Map.class);
            riskAssessment = parsed;
        } catch (Exception e) {
            // If not valid JSON, create a simple map from the string
            riskAssessment = new HashMap<>();
            riskAssessment.put("band", "AMBER");
            riskAssessment.put("score", 20);
            riskAssessment.put("triggers", new ArrayList<>());
            riskAssessment.put("reasoning", riskJson);
        }
        
        Map<String, Object> result = generateKYBNoteWithActions(profile, txInsights, riskAssessment);
        return (String) result.getOrDefault("kyb_note", "");
    }
}
