package com.mcpkyb.agents;

import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;

public interface KYBAgents {

    @Tool("Fetch customer profile summary")
    String getCustomerProfile(String customerId);

    @Tool("Analyze transaction patterns for anomalies")
    String analyzeTransactions(String customerId);

    @Tool("Assess KYB risk based on profile and transactions")
    String assessRisk(String profile, String txInsights) throws IOException;

    @Tool("Generate KYB narrative and action plan")
    String generateKYBNote(String profile, String txInsights, String riskJson);
}
