package com.mcpkyb.mcp.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcpkyb.mcp.model.*;
import com.mcpkyb.service.*;
import com.mcpkyb.utils.JsonLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

@Service
public class McpServerService {
    
    @Autowired
    private CustomerProfileAgent profileAgent;
    
    @Autowired
    private TransactionPatternAgent transactionAgent;
    
    @Autowired
    private RiskComplianceAgent riskAgent;
    
    @Autowired
    private KYBNoteAgent kybNoteAgent;
    
    @Autowired
    private ConductorAgent conductorAgent;

    @Autowired
    private RiskScopeActionsAgent riskScopeActionsAgent;
    
    public List<McpTool> listTools() {
        List<McpTool> tools = new ArrayList<>();
        
        // Tool 1: getCustomerProfile
        Map<String, McpTool.PropertySchema> profileProperties = new HashMap<>();
        profileProperties.put("customerId", new McpTool.PropertySchema("string", "Customer ID to fetch profile for"));
        
        McpTool.ToolInputSchema profileSchema = new McpTool.ToolInputSchema();
        profileSchema.setType("object");
        profileSchema.setProperties(profileProperties);
        profileSchema.setRequired(List.of("customerId"));
        
        McpTool profileTool = new McpTool();
        profileTool.setName("getCustomerProfile");
        profileTool.setDescription("Fetch customer profile summary from CRM data");
        profileTool.setInputSchema(profileSchema);
        tools.add(profileTool);
        
        // Tool 2: analyzeTransactions
        Map<String, McpTool.PropertySchema> txProperties = new HashMap<>();
        txProperties.put("customerId", new McpTool.PropertySchema("string", "Customer ID to analyze transactions for"));
        
        McpTool.ToolInputSchema txSchema = new McpTool.ToolInputSchema();
        txSchema.setType("object");
        txSchema.setProperties(txProperties);
        txSchema.setRequired(List.of("customerId"));
        
        McpTool txTool = new McpTool();
        txTool.setName("analyzeTransactions");
        txTool.setDescription("Analyze transaction patterns for anomalies and risk indicators");
        txTool.setInputSchema(txSchema);
        tools.add(txTool);
        
        // Tool 3: assessRisk
        Map<String, McpTool.PropertySchema> riskProperties = new HashMap<>();
        riskProperties.put("profileSummary", new McpTool.PropertySchema("string", "Customer profile summary"));
        riskProperties.put("transactionSummary", new McpTool.PropertySchema("string", "Transaction analysis summary"));
        
        McpTool.ToolInputSchema riskSchema = new McpTool.ToolInputSchema();
        riskSchema.setType("object");
        riskSchema.setProperties(riskProperties);
        riskSchema.setRequired(List.of("profileSummary", "transactionSummary"));
        
        McpTool riskTool = new McpTool();
        riskTool.setName("assessRisk");
        riskTool.setDescription("Assess KYB risk based on profile and transaction patterns");
        riskTool.setInputSchema(riskSchema);
        tools.add(riskTool);
        
        // Tool 4: generateKYBNote
        Map<String, McpTool.PropertySchema> noteProperties = new HashMap<>();
        noteProperties.put("profileSummary", new McpTool.PropertySchema("string", "Customer profile summary"));
        noteProperties.put("transactionSummary", new McpTool.PropertySchema("string", "Transaction analysis summary"));
        noteProperties.put("riskAssessment", new McpTool.PropertySchema("string", "Risk assessment result"));
        
        McpTool.ToolInputSchema noteSchema = new McpTool.ToolInputSchema();
        noteSchema.setType("object");
        noteSchema.setProperties(noteProperties);
        noteSchema.setRequired(List.of("profileSummary", "transactionSummary", "riskAssessment"));
        
        McpTool noteTool = new McpTool();
        noteTool.setName("generateKYBNote");
        noteTool.setDescription("Generate KYB narrative and action plan");
        noteTool.setInputSchema(noteSchema);
        tools.add(noteTool);
        
        // Tool 5: runKYB (Complete KYB Workflow)
        Map<String, McpTool.PropertySchema> kybProperties = new HashMap<>();
        kybProperties.put("customerId", new McpTool.PropertySchema("string", "Customer ID to run complete KYB workflow for"));
        
        McpTool.ToolInputSchema kybSchema = new McpTool.ToolInputSchema();
        kybSchema.setType("object");
        kybSchema.setProperties(kybProperties);
        kybSchema.setRequired(List.of("customerId"));
        
        McpTool kybTool = new McpTool();
        kybTool.setName("runKYB");
        kybTool.setDescription("Execute complete KYB Early-Risk Radar workflow. Returns structured JSON with journey_type, entity_profile, party_summary, group_context, transaction_insights, risk_assessment (band, score, triggers, reasoning), kyb_note, and recommended_actions.");
        kybTool.setInputSchema(kybSchema);
        tools.add(kybTool);

        // Tool 6: assessRiskScopeAndActions
        Map<String, McpTool.PropertySchema> riskScopeProperties = new HashMap<>();
        riskScopeProperties.put("customerId", new McpTool.PropertySchema("string", "Customer ID to assess risk scope and actions for"));

        McpTool.ToolInputSchema riskScopeSchema = new McpTool.ToolInputSchema();
        riskScopeSchema.setType("object");
        riskScopeSchema.setProperties(riskScopeProperties);
        riskScopeSchema.setRequired(List.of("customerId"));

        McpTool riskScopeTool = new McpTool();
        riskScopeTool.setName("assessRiskScopeAndActions");
        riskScopeTool.setDescription("Assess risk scope and recommend specific actions for KYB review based on Companies House, Experian, CRM, transaction data, and rules configuration. Returns structured JSON with risk_scope, key_risk_drivers, risk_actions, and data_points_used.");
        riskScopeTool.setInputSchema(riskScopeSchema);
        tools.add(riskScopeTool);

        return tools;
    }
    
    public Object callTool(String toolName, Map<String, Object> arguments) {
        try {
            switch (toolName) {
                case "getCustomerProfile":
                    String customerId = (String) arguments.get("customerId");
                    if (customerId == null) {
                        throw new IllegalArgumentException("customerId is required");
                    }
                    return profileAgent.getProfileSummary(customerId);
                    
                case "analyzeTransactions":
                    customerId = (String) arguments.get("customerId");
                    if (customerId == null) {
                        throw new IllegalArgumentException("customerId is required");
                    }
                    return transactionAgent.analyzeTransactions(customerId);
                    
                case "assessRisk":
                    String profileSummary = (String) arguments.get("profileSummary");
                    String transactionSummary = (String) arguments.get("transactionSummary");
                    if (profileSummary == null || transactionSummary == null) {
                        throw new IllegalArgumentException("profileSummary and transactionSummary are required");
                    }
                    return riskAgent.assessRisk(profileSummary, transactionSummary, JsonLoader.loadJson("rules.json"));
                    
                case "generateKYBNote":
                    profileSummary = (String) arguments.get("profileSummary");
                    transactionSummary = (String) arguments.get("transactionSummary");
                    Object riskAssessmentObj = arguments.get("riskAssessment");
                    if (profileSummary == null || transactionSummary == null || riskAssessmentObj == null) {
                        throw new IllegalArgumentException("profileSummary, transactionSummary, and riskAssessment are required");
                    }
                    // Handle both String and Map formats
                    if (riskAssessmentObj instanceof String) {
                        return kybNoteAgent.generateKYBNote(profileSummary, transactionSummary, (String) riskAssessmentObj);
                    } else if (riskAssessmentObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> riskAssessmentMap = (Map<String, Object>) riskAssessmentObj;
                        Map<String, Object> result = kybNoteAgent.generateKYBNoteWithActions(profileSummary, transactionSummary, riskAssessmentMap);
                        return result.getOrDefault("kyb_note", "").toString();
                    } else {
                        return kybNoteAgent.generateKYBNote(profileSummary, transactionSummary, riskAssessmentObj.toString());
                    }
                    
                case "runKYB":
                    customerId = (String) arguments.get("customerId");
                    if (customerId == null) {
                        throw new IllegalArgumentException("customerId is required");
                    }
                    // Call ConductorAgent which orchestrates the complete workflow
                    Map<String, Object> kybResult = conductorAgent.runKYB(customerId);
                    // Return the complete structured result as JSON string
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    try {
                        return mapper.writeValueAsString(kybResult);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new RuntimeException("Error serializing KYB result", e);
                    }

                case "assessRiskScopeAndActions":
                    customerId = (String) arguments.get("customerId");
                    if (customerId == null) {
                        throw new IllegalArgumentException("customerId is required");
                    }
                    // Fetch data from JSON files and call the risk scope agent
                    Map<String, Object> companiesHouseData = getCompaniesHouseData(customerId);
                    Map<String, Object> experianData = getExperianData(customerId);
                    Map<String, Object> crmData = getCrmData(customerId);
                    Map<String, Object> transactionData = getTransactionData(customerId);
                    JsonNode rulesData = JsonLoader.loadJson("rules.json");

                    return riskScopeActionsAgent.assessRiskScopeAndActions(
                        companiesHouseData, experianData, crmData, transactionData, rulesData
                    );

                default:
                    throw new IllegalArgumentException("Unknown tool: " + toolName);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error executing tool: " + toolName, e);
        }
    }
    
    public McpMessage handleMessage(McpMessage request) throws JsonProcessingException {
        McpMessage response = new McpMessage();
        response.setJsonrpc("2.0");
        response.setId(request.getId());
        
        try {
            switch (request.getMethod()) {
                case "tools/list":
                    ToolsListResponse toolsList = new ToolsListResponse();
                    toolsList.setTools(listTools());
                    response.setResult(toolsList);
                    break;
                    
                case "tools/call":
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) request.getParams();
                    String name = (String) params.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
                    
                    Object result = callTool(name, arguments);
                    Map<String, Object> toolResult = new HashMap<>();
                    
                    // For runKYB and assessRiskScopeAndActions tools, return structured data directly
                    if (("runKYB".equals(name) || "assessRiskScopeAndActions".equals(name)) && result instanceof String) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> jsonResult = mapper.readValue((String) result, Map.class);

                            // Include full structured result at root level for easy access
                            // This allows MCP clients to get the complete structured output directly
                            for (Map.Entry<String, Object> entry : jsonResult.entrySet()) {
                                toolResult.put(entry.getKey(), entry.getValue());
                            }

                        } catch (Exception e) {
                            // Fallback to string representation
                            toolResult.put("content", Collections.singletonList(Map.of(
                                "type", "text",
                                "text", result.toString()
                            )));
                            toolResult.put("error", "Failed to parse structured output: " + e.getMessage());
                        }
                    } else {
                        toolResult.put("content", Collections.singletonList(Map.of(
                            "type", "text",
                            "text", result.toString()
                        )));
                    }
                    response.setResult(toolResult);
                    break;
                    
                case "initialize":
                    Map<String, Object> initResult = new HashMap<>();
                    initResult.put("protocolVersion", "2024-11-05");
                    initResult.put("capabilities", Map.of("tools", Map.of()));
                    initResult.put("serverInfo", Map.of(
                        "name", "mcp-kyb-server",
                        "version", "1.0.0"
                    ));
                    response.setResult(initResult);
                    break;
                    
                default:
                    McpError error = new McpError();
                    error.setCode(-32601);
                    error.setMessage("Method not found: " + request.getMethod());
                    response.setError(error);
                    break;
            }
        } catch (Exception e) {
            McpError error = new McpError();
            error.setCode(-32603);
            error.setMessage("Internal error: " + e.getMessage());
            response.setError(error);
        }
        
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCompaniesHouseData(String customerId) {
        try {
            JsonNode companiesHouseJson = JsonLoader.loadJson("companyHouse.json");
            JsonNode businesses = companiesHouseJson.get("businesses");
            if (businesses != null && businesses.isArray()) {
                for (JsonNode business : businesses) {
                    if (customerId.equals(business.get("customer_id").asText())) {
                        return new ObjectMapper().convertValue(business, Map.class);
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map if data not found
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getExperianData(String customerId) {
        try {
            JsonNode companiesHouseJson = JsonLoader.loadJson("companyHouse.json");
            JsonNode businesses = companiesHouseJson.get("businesses");
            if (businesses != null && businesses.isArray()) {
                for (JsonNode business : businesses) {
                    if (customerId.equals(business.get("customer_id").asText())) {
                        return new ObjectMapper().convertValue(business, Map.class);
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map if data not found
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCrmData(String customerId) {
        try {
            JsonNode crmJson = JsonLoader.loadJson("crm.json");
            JsonNode customers = crmJson.get("customers");
            if (customers != null && customers.isArray()) {
                for (JsonNode customer : customers) {
                    if (customerId.equals(customer.get("customer_id").asText())) {
                        return new ObjectMapper().convertValue(customer, Map.class);
                    }
                }
            }
        } catch (Exception e) {
            // Return empty map if data not found
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTransactionData(String customerId) {
        try {
            JsonNode transactionsJson = JsonLoader.loadJson("transactions.json");
            JsonNode customers = transactionsJson.get("customers");
            if (customers != null && customers.has(customerId)) {
                JsonNode customerData = customers.get(customerId);
                Map<String, Object> result = new ObjectMapper().convertValue(customerData, Map.class);
                result.put("customer_id", customerId);
                return result;
            }
        } catch (Exception e) {
            // Return empty map if data not found
        }
        return Map.of();
    }
}

