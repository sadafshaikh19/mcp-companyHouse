package com.mcpkyb.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class CompaniesHouseClient {

    private final WebClient webClient;

    @Value("${companieshouse.api-key:}")
    private String apiKey;

    public CompaniesHouseClient(@Value("${companieshouse.base-url:https://api.company-information.service.gov.uk}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Search companies by query string using Companies House Search API.
     * Returns a list of full company data as JsonNode objects containing all available fields.
     */
    public List<JsonNode> searchCompanies(String query, int limit) {
        // Build request using uri builder below

        List<JsonNode> results = new ArrayList<>();

        JsonNode response = webClient.get()
                .uri(builder -> builder.path("/search/companies").queryParam("q", query).build())
                .headers(h -> {
                    // set Basic auth header properly per-request because default header was placeholder
                    String basic = java.util.Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
                    h.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response != null && response.has("items")) {
            for (JsonNode item : response.get("items")) {
                results.add(item);
                if (results.size() >= limit) break;
            }
        }

        return results;
    }

    /**
     * Search companies with pagination parameters supported by Companies House API.
     * itemsPerPage maps to `items_per_page` and startIndex maps to `start_index`.
     * Returns full company data as JsonNode objects.
     */
    public List<JsonNode> searchCompanies(String query, int itemsPerPage, int startIndex) {
        List<JsonNode> results = new ArrayList<>();

        JsonNode response = webClient.get()
                .uri(builder -> builder.path("/search/companies")
                        .queryParam("q", query)
                        .queryParam("items_per_page", Integer.toString(itemsPerPage))
                        .queryParam("start_index", Integer.toString(startIndex))
                        .build())
                .headers(h -> {
                    String basic = java.util.Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
                    h.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response != null && response.has("items")) {
            for (JsonNode item : response.get("items")) {
                results.add(item);
            }
        }

        return results;
    }

    /**
     * Retrieve company profile for a given company number.
     * Returns the raw JSON node from Companies House API. Caller may map to a POJO if desired.
     */
    public JsonNode getCompanyProfile(String companyNumber) {
        return webClient.get()
                .uri(builder -> builder.path("/company/{company_number}").build(companyNumber))
                .headers(h -> {
                    String basic = java.util.Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
                    h.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }
}
