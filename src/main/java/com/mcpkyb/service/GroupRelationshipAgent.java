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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroupRelationshipAgent {
    
    @Autowired
    private OpenAiChatModel chatModel;
    
    public Map<String, Object> getGroupContext(String customerId, boolean hasLinkedCustomers) throws IOException {
        // If no linked customers, return null context
        if (!hasLinkedCustomers) {
            return null;
        }
        
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
            You are a Group / Relationship Agent for KYB Early-Risk Radar.
            
            Your task is to identify and analyze group relationships, sister entities, and linked customers.
            
            Return a JSON object with:
            - linked_entities: list of linked/sister entity IDs (if any)
            - group_structure: description of group structure
            - relationship_types: types of relationships (ownership, common directors, etc.)
            - aggregate_risk_indicators: risk indicators across the group
            
            If no linked entities are found in the data, return null.
            Return ONLY valid JSON.
        """;
        
        String userPrompt = String.format("""
            Analyze group relationships for this customer:
            %s
            
            Search for potential linked entities in the customer database. Look for:
            - Similar legal names
            - Common sectors
            - Shared attributes that might indicate group relationships
        """, customer.toString());
        
        ChatRequest request = ChatRequest.builder()
                .messages(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt)
                )
                .build();
        
        ChatResponse response = chatModel.chat(request);
        String responseText = response.aiMessage().text();
        
        // Parse JSON from response
        String jsonStr = cleanJsonResponse(responseText);
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = mapper.readValue(jsonStr, Map.class);
            
            // If no meaningful relationships found, return null
            if (result.isEmpty() || (result.containsKey("linked_entities") && 
                ((List<?>) result.get("linked_entities")).isEmpty())) {
                return null;
            }
            
            return result;
        } catch (Exception e) {
            // Check all customers for potential links
            return checkForLinkedEntities(customer, crmData);
        }
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
    
    private Map<String, Object> checkForLinkedEntities(JsonNode customer, JsonNode crmData) {
        String customerSector = customer.get("sector").asText();
        String customerShortName = customer.get("short_name").asText();
        
        List<String> linkedIds = new ArrayList<>();
        
        // Simple heuristic: find customers in same sector with similar names
        for (JsonNode c : crmData.get("customers")) {
            if (!c.get("customer_id").asText().equals(customer.get("customer_id").asText())) {
                String otherSector = c.get("sector").asText();
                String otherShortName = c.get("short_name").asText();
                
                if (otherSector.equals(customerSector) && 
                    (otherShortName.toLowerCase().contains(customerShortName.toLowerCase()) ||
                     customerShortName.toLowerCase().contains(otherShortName.toLowerCase()))) {
                    linkedIds.add(c.get("customer_id").asText());
                }
            }
        }
        
        if (linkedIds.isEmpty()) {
            return null;
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("linked_entities", linkedIds);
        result.put("group_structure", "Potential group relationship identified by sector and name similarity");
        result.put("relationship_types", List.of("SECTOR_SIMILARITY", "NAME_SIMILARITY"));
        result.put("aggregate_risk_indicators", "Risk assessment should consider group-wide exposure");
        
        return result;
    }
}

