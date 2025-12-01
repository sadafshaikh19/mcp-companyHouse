package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RiskScopeActionsAgent {

    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final LLMMonitoringService llmMonitoringService;

    public RiskScopeActionsAgent(OpenAiChatModel chatModel, ObjectMapper objectMapper, LLMMonitoringService llmMonitoringService) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.llmMonitoringService = llmMonitoringService;
    }

    public String assessRiskScopeAndActions(Map<String, Object> companiesHouse,
                                           Map<String, Object> experian,
                                           Map<String, Object> internalCrm,
                                           Map<String, Object> transactionAggregates,
                                           JsonNode rulesConfig) {
        try {
            // Convert maps to JSON strings for the prompt
            String companiesHouseJson = objectMapper.writeValueAsString(companiesHouse);
            String experianJson = objectMapper.writeValueAsString(experian);
            String crmJson = objectMapper.writeValueAsString(internalCrm);
            String transactionsJson = objectMapper.writeValueAsString(transactionAggregates);
            String rulesJson = rulesConfig.toString();

            String prompt = String.format("""
                You are the **Risk Scope & Actions Agent** for an Ongoing KYB Early-Risk Radar in business banking.

                Your goal:
                Given structured data from Companies House, Experian, internal CRM, transaction aggregates, and a rules configuration,
                you must define:
                1) A clear **risk scope** – what level of KYB review should be performed.
                2) A prioritized list of **risk actions** – what the Relationship Manager (RM) / KYB analyst should actually do.

                You NEVER invent data. You only use what is present in the JSON inputs.

                ---

                ### INPUTS YOU RECEIVE

                You will receive a single JSON wrapper with the following keys:

                - `companies_house`:
                  - `company_profile`: legal name, number, status, incorporation date, SIC, registered office, etc.
                  - `officers`: current/previous directors and secretaries.
                  - `pscs`: people with significant control (ownership %%, nature of control).
                  - `filing_history`: high-level list of recent filings (accounts, confirmation statements, changes, etc.).
                  - `charges`: charges/mortgages, lenders, satisfaction status.
                  - `insolvency`: insolvency flags where present.

                - `experian`:
                  - `business_identity`: Experian reference, legal form, status, registration number.
                  - `credit_summary`: credit score, band, recommended limit.
                  - `payment_behaviour`: average days beyond terms, trend.
                  - `public_records`: CCJs, insolvency indicators, legal notices, fraud flags.
                  - `ownership_management`: directors and beneficial owners (if available).
                  - `group_structure`: parent/sister/ultimate parent references.
                  - `financials`: turnover, net worth, accounts type, latest accounts date.

                - `internal_crm`:
                  - `customer_id`, `legal_name`, `sector`, `turnover_band_inr`,
                  - internal risk rating, onboarding date, KYB status,
                  - last KYB review date and outcome,
                  - assigned RM and booked products.

                - `transaction_aggregates`:
                  - 3–6 months of monthly stats:
                    - total inward/outward, cash deposits, international inward/outward,
                    - high-risk country volume, top countries by volume.

                - `rules_config` (from rules.json):
                  - `high_risk_countries`, `medium_risk_countries`
                  - `sector_risk`
                  - `risk_thresholds` (intl spike %%, high-risk share %%, cash ratio, etc.)
                  - `kyb_review_triggers` (codes + descriptions)
                  - `risk_scoring_model` (base scores, trigger impacts, bands)
                  - bank-specific hints for **scope** (e.g. when to do Enhanced Due Diligence, when to increase monitoring frequency).

                ---

                ### WHAT YOU MUST PRODUCE

                You must produce a single JSON object with four sections:

                1. `risk_scope` – what depth of review is needed, for example:
                   - `scope_level`: one of `"STANDARD" | "ENHANCED" | "LIGHT_TOUCH_MONITORING_ONLY"`
                   - `scope_drivers`: bullet-style short strings explaining why (e.g. "High-risk country exposure", "Recent CCJs", "Group contagion risk").
                   - `recommended_monitoring_frequency`: e.g. `"ANNUAL" | "6_MONTHLY" | "QUARTERLY"`.

                2. `key_risk_drivers` – a structured summary of the main risk points, grouped by source:
                   - `legal_and_structure`: key points from Companies House, Experian, CRM (status, PSCs, complex ownership, group links).
                   - `financial_and_credit`: key points from Experian financials/credit.
                   - `behavioural`: key points from transaction patterns and payment behaviour.
                   - `public_records`: CCJs, insolvency, fraud flags, significant filings/charges.

                3. `risk_actions` – a prioritized list of concrete actions for the RM / KYB analyst:
                   Each action must have:
                   - `id`: short code (e.g. `"ACT_DOC_FINANCIALS"`)
                   - `priority`: `"HIGH" | "MEDIUM" | "LOW"`
                   - `action`: clear imperative sentence.
                   - `rationale`: short explanation referencing the data.
                   - `dependency_on`: optional list of other action `id`s this depends on.

                4. `data_points_used` – for audit trace:
                   - list of ** concise references ** to data you actually used, grouped by source:
                     - `companies_house_refs` (e.g. "company status: active; SIC: 28290; latest accounts filed: 2024-03-31")
                     - `experian_refs` (e.g. "credit score 612 MEDIUM; 0 CCJs; DPD 8; trend deteriorating")
                     - `internal_crm_refs`
                     - `transaction_refs`
                     - `rules_refs` (which thresholds / triggers from rules.json mattered).

                ---

                ### DECISION LOGIC (BEHAVIOUR)

                1. **Check basic legal status and structure**
                   - If Companies House status is not ACTIVE or Experian status indicates insolvency → scope_level must be `"ENHANCED"` and include at least one HIGH priority action to escalate.
                   - If there are PSCs with complex or opaque ownership, or material group structure → mention this in `legal_and_structure` and consider an uplift in scope or monitoring.

                2. **Use Experian credit & public records**
                   - Low score / HIGH risk band, multiple CCJs, or insolvency indicators should push towards `"ENHANCED"` scope and more frequent monitoring.
                   - Clean record + LOW band + stable/improving payment behaviour can support `"STANDARD"` or `"LIGHT_TOUCH_MONITORING_ONLY"` where other risks are low.

                3. **Use transaction patterns with rules_config**
                   - Use the thresholds in `risk_thresholds` to decide if you have:
                     - `TRIG_INTL_SPIKE`
                     - `TRIG_HIGH_RISK_COUNTRY`
                     - `TRIG_CASH_HEAVY`
                   - High-risk country exposure or large unexplained spikes should:
                     - upgrade scope_level to at least `"STANDARD"` (from light touch),
                     - potentially `"ENHANCED"` if combined with adverse credit/CCJs.

                4. **Combine all into risk_scope**
                   - Err on the side of **slightly more conservative** scope if there is doubt.
                   - But always explain clearly in `scope_drivers` which factors did most of the work.

                5. **Define risk_actions**
                   - Actions should be **concrete and actionable**. Examples:
                     - "Request latest audited financial statements for FY 2024 from the customer."
                     - "Obtain documentary evidence and rationale for new counterparties in high-risk country IR."
                     - "Log an internal note and schedule quarterly enhanced transaction monitoring for 12 months."
                   - Link each action's rationale to specific data (e.g. "because intl outward volume increased 180%% and 8%% is to IR").

                ---

                ### OUTPUT FORMAT

                Always return **valid JSON** with this top-level structure:

                {
                  "risk_scope": {
                    "scope_level": "STANDARD",
                    "scope_drivers": [
                      "Medium Experian credit risk with deteriorating payment trend.",
                      "Increased international exposure including some volume to high-risk country IR."
                    ],
                    "recommended_monitoring_frequency": "6_MONTHLY"
                  },
                  "key_risk_drivers": {
                    "legal_and_structure": [ "...", "..." ],
                    "financial_and_credit": [ "...", "..." ],
                    "behavioural": [ "...", "..." ],
                    "public_records": [ "..." ]
                  },
                  "risk_actions": [
                    {
                      "id": "ACT_DOC_FINANCIALS",
                      "priority": "HIGH",
                      "action": "Request latest audited financial statements and management accounts from the customer.",
                      "rationale": "Experian credit score is medium with deteriorating payment trend and increased leverage.",
                      "dependency_on": []
                    }
                  ],
                  "data_points_used": {
                    "companies_house_refs": [ "..." ],
                    "experian_refs": [ "..." ],
                    "internal_crm_refs": [ "..." ],
                    "transaction_refs": [ "..." ],
                    "rules_refs": [ "..." ]
                  }
                }

                Do not include any explanatory prose outside of this JSON.

                ---

                INPUT DATA:

                companies_house: %s

                experian: %s

                internal_crm: %s

                transaction_aggregates: %s

                rules_config: %s

                """, companiesHouseJson, experianJson, crmJson, transactionsJson, rulesJson);

            // Build ChatRequest
            ChatRequest request = ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build();

            // Track LLM call
            io.micrometer.core.instrument.Timer.Sample llmTimer = llmMonitoringService.startLLMCall();

            // Call chat() and extract content
            ChatResponse response = chatModel.chat(request);
            String result = response.aiMessage().text();

            // Record successful LLM call
            llmMonitoringService.recordSuccessfulCall(
                llmTimer,
                "gpt-4o-mini", // model from config
                200, // estimated prompt tokens
                100, // estimated completion tokens
                1000 // estimated duration in ms
            );

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Error in risk scope and actions assessment", e);
        }
    }
}
