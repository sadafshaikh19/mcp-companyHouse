package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class CustomerPartyProfileAgent {
    
    @Autowired
    private OpenAiChatModel chatModel;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public Map<String, Object> getEntityAndPartyProfile(String customerId, String journeyType) throws IOException {
        JsonNode crmData = JsonLoader.loadJson("crm.json");
        JsonNode partiesData = JsonLoader.loadJson("parties.json");
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
        
        JsonNode partyRecords = loadPartyRecords(partiesData, customerId);
        List<Map<String, Object>> fallbackParties = buildPartyProfiles(partyRecords, customerId, customer);
        String fallbackObservations = buildPartyObservations(fallbackParties, journeyType);
        
        String systemPrompt = """
            You are the Customer & Party Profile Agent in a KYB Early-Risk Radar.
            
            Your job is to prepare a clean, consolidated view of the legal entity and associated natural persons.
            
            You receive:
            - journey_type from the Journey Classifier Agent
            - CRM customer record (legal name, legal form, sector, turnover band, onboarding date, KYB status, last review outcome, RM name)
            - a list of parties (directors, beneficial owners, partners, signatories) with KYC & risk attributes (PEP, sanctions, residency, individual risk rating)
            
            You must:
            1. Build an entity_profile with only KYB-relevant attributes (structured JSON, no noise).
            2. Build a party_summary with:
               - parties: each party must have party_id, name, role, key_flags (list), and risk_label (Low/Medium/High)
               - key_observations: short paragraph highlighting any concerns (PEP, high-risk residency, complex ownership, etc.)
            
            Do NOT analyse transactions or assign an overall KYB risk band.
            Return ONLY valid JSON with the structure:
            {
              "entity_profile": { ... },
              "party_summary": {
                "parties": [ ... ],
                "key_observations": "text"
              }
            }
        """;
        
        String prettyCustomer = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(customer);
        String partiesJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(partyRecords != null ? partyRecords : objectMapper.createArrayNode());
        
        String userPrompt = String.format("""
            Journey Type: %s
            
            CRM Customer Record:
            %s
            
            Party Records:
            %s
            
            Provide the structured JSON exactly in the requested format.
        """, journeyType, prettyCustomer, partiesJson);
        
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
        
        Map<String, Object> result = new HashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsedResult = objectMapper.readValue(jsonStr, Map.class);
            result = parsedResult;
        } catch (Exception e) {
            // Fallback to structured summary
            result.put("entity_profile", createFallbackEntityProfile(customer, journeyType));
            result.put("party_summary", buildFallbackPartySummary(fallbackParties, fallbackObservations));
        }
        
        Map<String, Object> entityProfile = ensureEntityProfile(result.get("entity_profile"), customer, journeyType);
        Map<String, Object> partySummary = ensurePartySummary(result.get("party_summary"), fallbackParties, fallbackObservations);
        
        Map<String, Object> structuredResult = new HashMap<>();
        structuredResult.put("entity_profile", entityProfile);
        structuredResult.put("party_summary", partySummary);
        return structuredResult;
    }
    
    private JsonNode loadPartyRecords(JsonNode partiesData, String customerId) {
        if (partiesData == null || partiesData.isMissingNode()) {
            return objectMapper.createArrayNode();
        }
        JsonNode customersNode = partiesData.path("customers");
        JsonNode partyNode = customersNode.path(customerId);
        return partyNode.isMissingNode() ? objectMapper.createArrayNode() : partyNode;
    }
    
    private Map<String, Object> ensureEntityProfile(Object obj, JsonNode customer, String journeyType) {
        if (obj instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) map;
            profile.putIfAbsent("journey_type", journeyType);
            profile.putIfAbsent("customer_id", customer.path("customer_id").asText());
            profile.putIfAbsent("legal_name", customer.path("legal_name").asText());
            return profile;
        }
        return createFallbackEntityProfile(customer, journeyType);
    }
    
    private Map<String, Object> ensurePartySummary(Object obj,
                                                   List<Map<String, Object>> fallbackParties,
                                                   String fallbackObservations) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("parties", fallbackParties);
        summary.put("key_observations", fallbackObservations);
        
        if (obj instanceof Map<?, ?> incomingMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> incoming = (Map<String, Object>) incomingMap;
            Object partiesObj = incoming.get("parties");
            if (partiesObj instanceof List<?> list) {
                List<Map<String, Object>> normalized = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> partyMap) {
                        normalized.add(normalizePartyRecord(partyMap));
                    }
                }
                if (!normalized.isEmpty()) {
                    summary.put("parties", normalized);
                }
            }
            Object observationsObj = incoming.get("key_observations");
            if (observationsObj instanceof String str && !str.isBlank()) {
                summary.put("key_observations", str.trim());
            }
        } else if (obj instanceof String observations && !observations.isBlank()) {
            summary.put("key_observations", observations.trim());
        }
        
        return summary;
    }
    
    private Map<String, Object> normalizePartyRecord(Map<?, ?> partyMap) {
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("party_id", partyMap.containsKey("party_id") ? partyMap.get("party_id") : "UNKNOWN");
        normalized.put("name", partyMap.containsKey("name") ? partyMap.get("name") : "UNKNOWN");
        normalized.put("role", partyMap.containsKey("role") ? partyMap.get("role") : "UNKNOWN");
        normalized.put("risk_label", partyMap.containsKey("risk_label") ? partyMap.get("risk_label") : "MEDIUM");
        Object flagsObj = partyMap.get("key_flags");
        List<String> flags = new ArrayList<>();
        if (flagsObj instanceof List<?> flagList) {
            for (Object flag : flagList) {
                flags.add(flag.toString());
            }
        }
        normalized.put("key_flags", flags);
        return normalized;
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
    
    private Map<String, Object> createFallbackEntityProfile(JsonNode customer, String journeyType) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("journey_type", journeyType);
        profile.put("legal_name", customer.has("legal_name") ? customer.get("legal_name").asText() : "");
        profile.put("short_name", customer.has("short_name") ? customer.get("short_name").asText() : "");
        profile.put("customer_id", customer.has("customer_id") ? customer.get("customer_id").asText() : "");
        profile.put("sector", customer.has("sector") ? customer.get("sector").asText() : "");
        profile.put("sub_sector", customer.has("sub_sector") ? customer.get("sub_sector").asText() : "");
        profile.put("country_of_incorporation", customer.has("country_of_incorporation") ? customer.get("country_of_incorporation").asText() : "");
        profile.put("primary_operating_country", customer.has("primary_operating_country") ? customer.get("primary_operating_country").asText() : "");
        profile.put("onboarding_date", customer.has("onboarding_date") ? customer.get("onboarding_date").asText() : "");
        profile.put("turnover_band", customer.has("turnover_band_inr") ? customer.get("turnover_band_inr").asText() : "");
        profile.put("internal_risk_rating", customer.has("internal_risk_rating") ? customer.get("internal_risk_rating").asText() : "");
        profile.put("pep_flag", customer.has("pep_flag") ? customer.get("pep_flag").asBoolean() : false);
        profile.put("sanctions_flag", customer.has("sanctions_flag") ? customer.get("sanctions_flag").asBoolean() : false);
        profile.put("kyb_status", customer.has("kyb_status") ? customer.get("kyb_status").asText() : "");
        profile.put("products", customer.has("products") ? customer.get("products") : new ArrayList<>());
        return profile;
    }
    
    private List<Map<String, Object>> buildPartyProfiles(JsonNode partiesNode, String customerId, JsonNode customer) {
        List<Map<String, Object>> parties = new ArrayList<>();
        if (partiesNode != null && partiesNode.isArray()) {
            for (JsonNode party : partiesNode) {
                Map<String, Object> record = new HashMap<>();
                record.put("party_id", party.path("party_id").asText(customerId + "-P"));
                record.put("name", party.path("name").asText("Unknown Party"));
                record.put("role", party.path("role").asText("UNKNOWN"));
                record.put("risk_label", party.path("risk_label").asText("MEDIUM"));
                record.put("key_flags", deriveFlags(party));
                parties.add(record);
            }
        }
        
        if (parties.isEmpty()) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("party_id", customerId + "-P01");
            fallback.put("name", customer.path("legal_name").asText("Unknown") + " Principal");
            fallback.put("role", "Beneficial Owner");
            fallback.put("risk_label", "MEDIUM");
            fallback.put("key_flags", List.of("DATA_GAP_PARTIES"));
            parties.add(fallback);
        }
        return parties;
    }
    
    private List<String> deriveFlags(JsonNode party) {
        List<String> flags = new ArrayList<>();
        if (party.path("pep").asBoolean(false)) {
            flags.add("PEP");
        }
        if (party.path("sanctions").asBoolean(false)) {
            flags.add("SANCTIONS_HIT");
        }
        String residency = party.path("residency").asText("").toUpperCase();
        if (residency.equals("AE") || residency.equals("IR") || residency.equals("RU")) {
            flags.add("HIGH_RISK_RESIDENCY_" + residency);
        }
        return flags;
    }
    
    private String buildPartyObservations(List<Map<String, Object>> parties, String journeyType) {
        long highRiskCount = parties.stream()
                .filter(p -> "HIGH".equalsIgnoreCase(String.valueOf(p.getOrDefault("risk_label", ""))))
                .count();
        long pepCount = parties.stream()
                .filter(p -> {
                    Object flags = p.get("key_flags");
                    if (flags instanceof List<?> list) {
                        return list.stream().anyMatch(flag -> "PEP".equals(flag));
                    }
                    return false;
                })
                .count();
        StringBuilder observations = new StringBuilder();
        observations.append("Journey type ").append(journeyType).append(". ");
        observations.append("Identified ").append(parties.size()).append(" parties with ")
                .append(highRiskCount).append(" high-risk and ").append(pepCount).append(" PEP flags.");
        return observations.toString();
    }
    
    private Map<String, Object> buildFallbackPartySummary(List<Map<String, Object>> parties, String observations) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("parties", parties);
        summary.put("key_observations", observations);
        return summary;
    }
}

