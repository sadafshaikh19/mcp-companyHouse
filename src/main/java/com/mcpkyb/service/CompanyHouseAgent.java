package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpkyb.mcp.client.CompaniesHouseClient;
import com.mcpkyb.utils.JsonLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class CompanyHouseAgent {

    @Autowired
    private CompaniesHouseClient companiesHouseClient;

    public List<JsonNode> searchCompanyByCustomerId(String customerId) throws IOException {
        // Load CRM data and find the customer
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

        // Get the legal name to search for
        String legalName = customer.get("legal_name").asText();
        if (legalName == null || legalName.trim().isEmpty()) {
            throw new IOException("Legal name not found for customer: " + customerId);
        }

        // Search Companies House with the legal name
        return companiesHouseClient.searchCompanies(legalName, 5);
    }
}
