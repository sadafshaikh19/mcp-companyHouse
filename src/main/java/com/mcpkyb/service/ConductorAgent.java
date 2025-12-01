package com.mcpkyb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpkyb.utils.JsonLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class ConductorAgent {

    @Autowired
    private JourneyClassifierAgent journeyClassifierAgent;
    
    @Autowired
    private CustomerPartyProfileAgent customerPartyProfileAgent;
    
    @Autowired
    private GroupRelationshipAgent groupRelationshipAgent;
    
    @Autowired
    private TransactionPatternAgent transactionAgent;
    
    @Autowired
    private RiskRulesAgent riskRulesAgent;
    
    @Autowired
    private KYBNoteAgent kybNoteAgent;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> runKYB(String customerId) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        // Step 1: Journey Classification
        Map<String, Object> journeyClassification = journeyClassifierAgent.classifyJourney(customerId);
        String journeyType = extractString(journeyClassification, "journey_type", "LIMITED_COMPANY_SINGLE");
        Boolean hasLinkedCustomers = extractBoolean(journeyClassification, "has_linked_customers", false);
        result.put("journey_type", journeyType);
        
        // Step 2: Customer & Party Profile
        Map<String, Object> entityAndParty = customerPartyProfileAgent.getEntityAndPartyProfile(customerId, journeyType);
        Map<String, Object> entityProfile = extractMapFromObject(entityAndParty.get("entity_profile"), new HashMap<>());
        Map<String, Object> partySummary = normalizePartySummary(entityAndParty.get("party_summary"));
        result.put("entity_profile", entityProfile);
        result.put("party_summary", partySummary);
        
        // Step 3: Group/Relationship Context (conditional)
        if (hasLinkedCustomers) {
            Map<String, Object> groupContext = groupRelationshipAgent.getGroupContext(customerId, true);
            result.put("group_context", groupContext);
        } else {
            result.put("group_context", null);
        }
        
        // Step 4: Transaction Pattern Analysis
        String transactionInsightsJson = transactionAgent.analyzeTransactions(customerId);
        Map<String, Object> txEnvelope = parseJsonToMap(transactionInsightsJson, Map.of("transaction_insights", defaultTransactionInsights()));
        Map<String, Object> transactionInsights = extractMapFromObject(txEnvelope.get("transaction_insights"), defaultTransactionInsights());
        result.put("transaction_insights", transactionInsights);
        String transactionSummary = extractStringFromObject(transactionInsights.get("summary"), "Transaction analysis not available");
        
        // Step 5: Risk & Rules Assessment
        String profileSummary = formatProfileSummary(entityProfile, partySummary);
        com.fasterxml.jackson.databind.JsonNode rules = JsonLoader.loadJson("rules.json");
        
        Map<String, Object> riskAssessment = riskRulesAgent.assessRisk(
                entityProfile,
                partySummary,
                extractMapFromObject(result.get("group_context"), new HashMap<>()),
                transactionInsights,
                journeyType,
                rules);
        result.put("risk_assessment", riskAssessment);
        
        // Step 6: KYB Note & Action Plan
        Map<String, Object> kybNoteAndActions = kybNoteAgent.generateKYBNoteWithActions(
            profileSummary,
            transactionSummary,
            riskAssessment
        );
        
        String kybNote = extractString(kybNoteAndActions, "kyb_note", "");
        List<String> recommendedActions = extractStringList(kybNoteAndActions, "recommended_actions", new ArrayList<>());
        
        result.put("kyb_note", kybNote);
        result.put("recommended_actions", recommendedActions);
        
        // Validate and ensure all required fields are present
        ensureRequiredFields(result, hasLinkedCustomers, journeyType);
        
        // Audit trail: Add metadata about which agents were called
        Map<String, Object> auditTrail = new HashMap<>();
        List<String> agentsCalled = new ArrayList<>();
        agentsCalled.add("JourneyClassifierAgent");
        agentsCalled.add("CustomerPartyProfileAgent");
        if (hasLinkedCustomers) {
            agentsCalled.add("GroupRelationshipAgent");
        }
        agentsCalled.add("TransactionPatternAgent");
        agentsCalled.add("RiskRulesAgent");
        agentsCalled.add("KYBNoteAgent");
        
        auditTrail.put("agents_called", agentsCalled);
        auditTrail.put("customer_id", customerId);
        auditTrail.put("timestamp", new Date().toString());
        result.put("_audit_trail", auditTrail);
        
        return result;
    }
    
    /**
     * Ensures all required fields are present and properly structured in the result.
     * This method validates the output structure as per requirements.
     */
    private void ensureRequiredFields(Map<String, Object> result, boolean hasLinkedCustomers, String journeyType) {
        // Ensure journey_type
        if (!result.containsKey("journey_type") || result.get("journey_type") == null) {
            result.put("journey_type", "LIMITED_COMPANY_SINGLE");
        }
        
        // Ensure entity_profile (must be an object, not null)
        Map<String, Object> entityProfile = extractMapFromObject(result.get("entity_profile"), new HashMap<>());
        result.put("entity_profile", entityProfile);
        
        // Ensure party_summary (structured object)
        Map<String, Object> partySummary = normalizePartySummary(result.get("party_summary"));
        result.put("party_summary", partySummary);
        
        // Ensure group_context (nullable, but key must be present)
        if (!result.containsKey("group_context")) {
            result.put("group_context", null);
        }
        
        // Ensure transaction_insights is structured
        Map<String, Object> transactionInsights = extractMapFromObject(result.get("transaction_insights"), defaultTransactionInsights());
        Map<String, Object> supportingMetrics = extractMapFromObject(
                transactionInsights.get("supporting_metrics"), new HashMap<>());
        transactionInsights.put("supporting_metrics", supportingMetrics);
        transactionInsights.putIfAbsent("summary", "Transaction analysis not available");
        transactionInsights.putIfAbsent("candidate_triggers", new ArrayList<String>());
        result.put("transaction_insights", transactionInsights);
        
        // Ensure risk_assessment (must be an object with: band, score, triggers, reasoning)
        Map<String, Object> riskAssessment = extractMapFromObject(
                result.get("risk_assessment"), defaultRiskAssessment(journeyType));
        riskAssessment.putIfAbsent("risk_band", "AMBER");
        riskAssessment.putIfAbsent("score", 20);
        riskAssessment.putIfAbsent("journey_type", journeyType);
        Object triggersObj = riskAssessment.get("triggers_fired");
        if (!(triggersObj instanceof List)) {
            riskAssessment.put("triggers_fired", new ArrayList<Map<String, Object>>());
        }
        Map<String, Object> scoreBreakdown = extractMapFromObject(
                riskAssessment.get("score_breakdown"), new HashMap<>());
        scoreBreakdown.putIfAbsent("base_score", 20);
        Object impacts = scoreBreakdown.get("trigger_impacts");
        if (!(impacts instanceof List)) {
            scoreBreakdown.put("trigger_impacts", new ArrayList<Map<String, Object>>());
        }
        riskAssessment.put("score_breakdown", scoreBreakdown);
        riskAssessment.putIfAbsent("overall_reasoning", "Risk assessment completed");
        result.put("risk_assessment", riskAssessment);
        
        // Ensure kyb_note (must be a string)
        if (!result.containsKey("kyb_note") || result.get("kyb_note") == null) {
            result.put("kyb_note", "");
        }
        
        // Ensure recommended_actions (must be a list)
        if (!result.containsKey("recommended_actions") || result.get("recommended_actions") == null) {
            result.put("recommended_actions", new ArrayList<String>());
        }
        
        // Ensure recommended_actions is actually a List (handle if it's a String)
        Object recActions = result.get("recommended_actions");
        if (!(recActions instanceof List)) {
            result.put("recommended_actions", new ArrayList<String>());
        }
        
        // Log structure for debugging (optional - can be removed in production)
        // System.out.println("Output structure validated. Keys: " + result.keySet());
    }
    
    /**
     * Returns a summary of the output structure for verification.
     * This helps ensure all required fields are present.
     */
    public Map<String, String> getOutputStructureSummary() {
        Map<String, String> structure = new HashMap<>();
        structure.put("journey_type", "string - Journey type classification");
        structure.put("entity_profile", "object - Structured entity information");
        structure.put("party_summary", "object - Parties list and key observations");
        structure.put("group_context", "object|null - Group relationship context (nullable)");
        structure.put("transaction_insights", "string - Transaction pattern analysis");
        structure.put("risk_assessment", "object - Contains: risk_band, score, journey_type, triggers_fired[], score_breakdown, overall_reasoning");
        structure.put("kyb_note", "string - KYB narrative for RM");
        structure.put("recommended_actions", "array - List of actionable items for RM");
        structure.put("_audit_trail", "object - Metadata about agents called (optional)");
        return structure;
    }
    
    /**
     * Safely extract a String value from a Map, handling type conversions.
     */
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String) {
            return (String) value;
        }
        // If it's a Map or other object, convert to JSON string
        if (value instanceof Map) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return value.toString();
    }
    
    /**
     * Safely extract a Boolean value from a Map, handling type conversions.
     */
    private Boolean extractBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        // Try to convert other types
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return defaultValue;
    }
    
    /**
     * Safely extract a Map-like value from an Object.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMapFromObject(Object value, Map<String, Object> defaultValue) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return defaultValue;
    }
    
    /**
     * Safely extract a List<String> value from a Map, handling type conversions.
     */
    private List<String> extractStringList(Map<String, Object> map, String key, List<String> defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                } else {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return defaultValue;
    }
    
    private String formatProfileSummary(Map<String, Object> entityProfile, Map<String, Object> partySummary) {
        StringBuilder summary = new StringBuilder();
        summary.append("Entity: ").append(entityProfile.getOrDefault("legal_name", "Unknown")).append(". ");
        summary.append("Customer ID: ").append(entityProfile.getOrDefault("customer_id", "Unknown")).append(". ");
        summary.append("Sector: ").append(entityProfile.getOrDefault("sector", "Unknown")).append(" - ")
               .append(entityProfile.getOrDefault("sub_sector", "Unknown")).append(". ");
        summary.append("Turnover Band: ").append(entityProfile.getOrDefault("turnover_band", "Unknown")).append(". ");
        summary.append("Internal Risk Rating: ").append(entityProfile.getOrDefault("internal_risk_rating", "Unknown")).append(". ");
        summary.append("Onboarding Date: ").append(entityProfile.getOrDefault("onboarding_date", "Unknown")).append(". ");
        summary.append("PEP Flag: ").append(entityProfile.getOrDefault("pep_flag", false)).append(". ");
        summary.append("Sanctions Flag: ").append(entityProfile.getOrDefault("sanctions_flag", false)).append(". ");
        summary.append("Party Observations: ")
               .append(extractStringFromObject(partySummary.get("key_observations"), "Party information not available"));
        return summary.toString();
    }
    
    private Map<String, Object> normalizePartySummary(Object partySummaryObj) {
        Map<String, Object> summary = defaultPartySummary();
        if (partySummaryObj instanceof Map<?, ?> incomingMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> incoming = (Map<String, Object>) incomingMap;
            Object partiesObj = incoming.get("parties");
            if (partiesObj instanceof List<?>) {
                List<Map<String, Object>> normalizedParties = new ArrayList<>();
                for (Object item : (List<?>) partiesObj) {
                    if (item instanceof Map<?, ?> partyMap) {
                        normalizedParties.add(normalizePartyRecord((Map<?, ?>) partyMap));
                    }
                }
                if (!normalizedParties.isEmpty()) {
                    summary.put("parties", normalizedParties);
                }
            }
            summary.put("key_observations", extractStringFromObject(incoming.get("key_observations"),
                    (String) summary.get("key_observations")));
        } else if (partySummaryObj instanceof String str && !str.isBlank()) {
            summary.put("key_observations", str.trim());
        }
        return summary;
    }
    
    private Map<String, Object> defaultPartySummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("parties", new ArrayList<Map<String, Object>>());
        summary.put("key_observations", "Party information not available");
        return summary;
    }
    
    private Map<String, Object> parseJsonToMap(String json, Map<String, Object> defaultValue) {
        if (json == null || json.isBlank()) {
            return defaultValue;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Map<String, Object> defaultTransactionInsights() {
        Map<String, Object> insights = new HashMap<>();
        insights.put("summary", "Transaction analysis not available");
        insights.put("candidate_triggers", new ArrayList<String>());
        insights.put("supporting_metrics", new HashMap<String, Object>());
        return insights;
    }

    private Map<String, Object> defaultRiskAssessment(String journeyType) {
        Map<String, Object> assessment = new HashMap<>();
        assessment.put("risk_band", "AMBER");
        assessment.put("score", 20);
        assessment.put("journey_type", journeyType);
        assessment.put("triggers_fired", new ArrayList<Map<String, Object>>());
        Map<String, Object> scoreBreakdown = new HashMap<>();
        scoreBreakdown.put("base_score", 20);
        scoreBreakdown.put("trigger_impacts", new ArrayList<Map<String, Object>>());
        assessment.put("score_breakdown", scoreBreakdown);
        assessment.put("overall_reasoning", "Risk assessment completed");
        return assessment;
    }
    
    private Map<String, Object> normalizePartyRecord(Map<?, ?> partyMap) {
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("party_id", partyMap.containsKey("party_id") ? partyMap.get("party_id") : "UNKNOWN");
        normalized.put("name", partyMap.containsKey("name") ? partyMap.get("name") : "UNKNOWN");
        normalized.put("role", partyMap.containsKey("role") ? partyMap.get("role") : "UNKNOWN");
        normalized.put("risk_label", partyMap.containsKey("risk_label") ? partyMap.get("risk_label") : "MEDIUM");
        Object flagsObj = partyMap.get("key_flags");
        List<String> flags = new ArrayList<>();
        if (flagsObj instanceof List<?>) {
            for (Object flag : (List<?>) flagsObj) {
                flags.add(String.valueOf(flag));
            }
        }
        normalized.put("key_flags", flags);
        return normalized;
    }
    
    private String extractStringFromObject(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String str) {
            return str;
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return value.toString();
    }
    
    /**
     * Orchestrate KYB workflow using OpenAI to coordinate agent calls.
     * This method uses the orchestrator's system prompt to guide the AI in coordinating agents.
     */
    public Map<String, Object> runKYBWithOrchestration(String customerId) throws IOException {
        // For now, we use direct orchestration. In the future, this could use OpenAI
        // to dynamically decide which agents to call and in what order.
        return runKYB(customerId);
    }
}
