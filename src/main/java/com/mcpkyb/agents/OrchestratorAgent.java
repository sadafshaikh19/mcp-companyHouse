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
public class OrchestratorAgent {

    @Autowired private CustomerProfileAgent profileAgent;
    @Autowired private TransactionPatternAgent txAgent;
    @Autowired private RiskComplianceAgent riskAgent;
    @Autowired private KYBNoteAgent noteAgent;


    public String runKYB(String customerJson, String txJson, String rulesJson) throws IOException, IOException {
        // Step 1: Get profile summary
        String profile = profileAgent.getProfileSummary(customerJson);

        // Step 2: Analyze transactions
        String transactions = txAgent.analyzeTransactions(txJson);

        // Step 3: Assess risk
        String risk = riskAgent.assessRisk(profile, transactions, JsonLoader.loadJson("rules.json"));

        // Step 4: Generate KYB note
        String kybNote = noteAgent.generateKYBNote(profile, transactions, risk);

        // Combine into structured JSON
        return String.format("""
            {
              "profile": "%s",
              "transactions": "%s",
              "risk": "%s",
              "kyb_note": "%s"
            }
        """, escape(profile), escape(transactions), escape(risk), escape(kybNote));
    }

    private String escape(String text) {
        return text.replace("\"", "\\\"").replace("\n", " ");
    }
}

