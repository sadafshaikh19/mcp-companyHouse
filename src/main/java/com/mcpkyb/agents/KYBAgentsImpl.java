package com.mcpkyb.agents;

import com.mcpkyb.service.CustomerProfileAgent;
import com.mcpkyb.service.KYBNoteAgent;
import com.mcpkyb.service.RiskComplianceAgent;
import com.mcpkyb.service.TransactionPatternAgent;
import com.mcpkyb.utils.JsonLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class KYBAgentsImpl implements KYBAgents {

    @Autowired
    CustomerProfileAgent profileAgent;
    @Autowired
    TransactionPatternAgent txAgent;
    @Autowired
    RiskComplianceAgent riskAgent;
    @Autowired
    KYBNoteAgent kybNoteAgent;

    @Override
    public String getCustomerProfile(String customerId) {
        try {
            return profileAgent.getProfileSummary(customerId);
        } catch (IOException e) {
            return "Error fetching profile";
        }
    }

    @Override
    public String analyzeTransactions(String customerId) {
        try {
            return txAgent.analyzeTransactions(customerId);
        } catch (IOException e) {
            return "Error analyzing transactions";
        }
    }

    @Override
    public String assessRisk(String profile, String txInsights) throws IOException {
        //String rulesJson = JsonLoader.loadJson("rules.json").toString();
        return riskAgent.assessRisk(profile, txInsights,JsonLoader.loadJson("rules.json"));
    }

    @Override
    public String generateKYBNote(String profile, String txInsights, String riskJson) {
        return kybNoteAgent.generateKYBNote(profile, txInsights, riskJson);
    }
}
