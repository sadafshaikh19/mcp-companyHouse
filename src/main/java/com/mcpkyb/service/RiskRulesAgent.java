package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;

@Service
public class RiskRulesAgent {

    // Flexible formatter: supports yyyy-MM and yyyy-MM-dd
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM[-dd]");

    public Map<String, Object> assessRisk(Map<String, Object> entityProfile,
                                          Map<String, Object> partySummary,
                                          Map<String, Object> groupContext,
                                          Map<String, Object> transactionInsights,
                                          String journeyType,
                                          JsonNode rules) {

        JsonNode riskModel = rules.path("risk_scoring_model");
        JsonNode triggerImpactsNode = riskModel.path("trigger_score_impacts");
        Map<String, TriggerDefinition> triggerDefinitions = indexTriggerDefinitions(rules.path("kyb_review_triggers"));

        int baseScore = determineBaseScore(entityProfile, rules);
        List<Map<String, Object>> triggerImpacts = new ArrayList<>();
        List<Map<String, Object>> triggersFired = new ArrayList<>();
        int totalScore = baseScore;

        // Evaluate triggers
        if (isHighRiskSector(entityProfile, rules)) {
            totalScore += addTrigger("TRIG_SECTOR_HIGH_RISK",
                    "Sector classified as high risk per rules.",
                    triggerImpacts, triggersFired, triggerImpactsNode, triggerDefinitions);
        }

        if (isKybOverdue(entityProfile, rules)) {
            totalScore += addTrigger("TRIG_KYB_OVERDUE",
                    buildKybOverdueReason(entityProfile, rules),
                    triggerImpacts, triggersFired, triggerImpactsNode, triggerDefinitions);
        }

        totalScore += evaluateTransactionTriggers(transactionInsights,
                triggerImpacts, triggersFired, triggerImpactsNode, triggerDefinitions, rules);

        String riskBand = determineBand(totalScore, riskModel.path("bands"));

        Map<String, Object> scoreBreakdown = new HashMap<>();
        scoreBreakdown.put("base_score", baseScore);
        scoreBreakdown.put("trigger_impacts", triggerImpacts);

        String overallReasoning = buildReasoning(baseScore, triggersFired, riskBand, entityProfile, journeyType);

        Map<String, Object> assessment = new HashMap<>();
        assessment.put("risk_band", riskBand);
        assessment.put("score", totalScore);
        assessment.put("journey_type", journeyType);
        assessment.put("triggers_fired", triggersFired);
        assessment.put("score_breakdown", scoreBreakdown);
        assessment.put("overall_reasoning", overallReasoning);

        return assessment;
    }

    private Map<String, TriggerDefinition> indexTriggerDefinitions(JsonNode triggersNode) {
        Map<String, TriggerDefinition> map = new HashMap<>();
        if (triggersNode.isArray()) {
            for (JsonNode node : triggersNode) {
                String code = node.path("code").asText();
                String severity = node.path("severity").asText("MEDIUM");
                map.put(code, new TriggerDefinition(code, severity, node.path("description").asText("")));
            }
        }
        return map;
    }

    private int determineBaseScore(Map<String, Object> entityProfile, JsonNode rules) {
        String internalRisk = safeUpper(entityProfile.getOrDefault("internal_risk_rating", "MEDIUM"));
        String sector = String.valueOf(entityProfile.getOrDefault("sector", "UNKNOWN"));
        String sectorRisk = safeUpper(rules.path("sector_risk").path(sector).asText(internalRisk));

        JsonNode baseScores = rules.path("risk_scoring_model").path("base_scores");
        int baseInternal = baseScores.path(internalRisk).asInt(20);
        int baseSector = baseScores.path(sectorRisk).asInt(baseInternal);

        return Math.max(baseInternal, baseSector);
    }

    private boolean isHighRiskSector(Map<String, Object> entityProfile, JsonNode rules) {
        String sector = String.valueOf(entityProfile.getOrDefault("sector", "UNKNOWN"));
        String sectorRisk = rules.path("sector_risk").path(sector).asText("MEDIUM");
        return "HIGH".equalsIgnoreCase(sectorRisk);
    }

    /**
     * Simplified KYB overdue check using flexible date parsing
     */
    private boolean isKybOverdue(Map<String, Object> entityProfile, JsonNode rules) {
        Object lastReviewObj = entityProfile.get("kyb_last_review_date");
        if (!(lastReviewObj instanceof String lastReview) || lastReview.isBlank()) {
            return false;
        }

        LocalDate lastReviewDate = parseFlexibleDate(lastReview);
        if (lastReviewDate == null) {
            return false;
        }

        long monthsElapsed = Period.between(lastReviewDate, LocalDate.now()).toTotalMonths();
        boolean highRisk = "HIGH".equalsIgnoreCase(String.valueOf(entityProfile.getOrDefault("internal_risk_rating", "MEDIUM")));
        int limit = rules.path("risk_thresholds").path(
                highRisk ? "months_without_kyb_review_for_high_risk" : "months_without_kyb_review_for_others")
                .asInt(highRisk ? 12 : 18);

        return monthsElapsed > limit;
    }

    private String buildKybOverdueReason(Map<String, Object> entityProfile, JsonNode rules) {
        Object lastReviewObj = entityProfile.get("kyb_last_review_date");
        String lastReview = lastReviewObj instanceof String ? (String) lastReviewObj : "unknown date";
        boolean highRisk = "HIGH".equalsIgnoreCase(String.valueOf(entityProfile.getOrDefault("internal_risk_rating", "MEDIUM")));
        int limit = rules.path("risk_thresholds").path(
                highRisk ? "months_without_kyb_review_for_high_risk" : "months_without_kyb_review_for_others")
                .asInt(highRisk ? 12 : 18);
        return "Last KYB review on " + lastReview + " exceeds " + limit + " month limit.";
    }

    private LocalDate parseFlexibleDate(String input) {
        try {
            TemporalAccessor parsed = DATE_FORMAT.parse(input);
            if (parsed.isSupported(ChronoField.DAY_OF_MONTH)) {
                return LocalDate.from(parsed);
            } else {
                return YearMonth.from(parsed).atDay(1);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private int evaluateTransactionTriggers(Map<String, Object> transactionInsights,
                                            List<Map<String, Object>> triggerImpacts,
                                            List<Map<String, Object>> triggersFired,
                                            JsonNode impactNode,
                                            Map<String, TriggerDefinition> triggerDefinitions,
                                            JsonNode rules) {
        if (transactionInsights == null) {
            return 0;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = transactionInsights.containsKey("supporting_metrics")
                ? (Map<String, Object>) transactionInsights.get("supporting_metrics")
                : new HashMap<>();

        double intlChange = toDouble(metrics.get("intl_outward_change_pct"));
        double highRiskShare = toDouble(metrics.get("high_risk_country_share_pct"));
        double cashRatio = toDouble(metrics.get("cash_deposit_ratio_pct"));

        JsonNode thresholds = rules.path("risk_thresholds");
        double intlThreshold = thresholds.path("intl_outward_mom_spike_pct").asDouble(100);
        double highRiskThreshold = thresholds.path("high_risk_country_volume_ratio").asDouble(0.05) * 100;
        double cashThreshold = thresholds.path("cash_deposit_to_turnover_ratio").asDouble(0.30) * 100;

        int delta = 0;
        if (intlChange > intlThreshold) {
            delta += addTrigger("TRIG_INTL_SPIKE",
                    "International outward payments up approx " + Math.round(intlChange) + "% MoM.",
                    triggerImpacts, triggersFired, impactNode, triggerDefinitions);
        }
        if (highRiskShare > highRiskThreshold) {
            delta += addTrigger("TRIG_HIGH_RISK_COUNTRY",
                    "High-risk country share approx " + Math.round(highRiskShare) + "% of outward flows.",
                    triggerImpacts, triggersFired, impactNode, triggerDefinitions);
        }
        if (cashRatio > cashThreshold) {
            delta += addTrigger("TRIG_CASH_HEAVY",
                    "Cash deposits around " + Math.round(cashRatio) + "% of outward amounts.",
                    triggerImpacts, triggersFired, impactNode, triggerDefinitions);
        }
        return delta;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private int addTrigger(String code,
                           String reason,
                           List<Map<String, Object>> triggerImpacts,
                           List<Map<String, Object>> triggersFired,
                           JsonNode impactNode,
                           Map<String, TriggerDefinition> triggerDefinitions) {
        int delta = impactNode.path(code).asInt(0);

        Map<String, Object> triggerEntry = new HashMap<>();
        TriggerDefinition definition = triggerDefinitions.getOrDefault(code,
                new TriggerDefinition(code, "MEDIUM", ""));
        triggerEntry.put("code", code);
        triggerEntry.put("severity", definition.severity());
        triggerEntry.put("reason", reason);
        triggersFired.add(triggerEntry);

        Map<String, Object> impactEntry = new HashMap<>();
        impactEntry.put("code", code);
        impactEntry.put("delta", delta);
        triggerImpacts.add(impactEntry);

        return delta;
    }

    private String determineBand(int score, JsonNode bands) {
        if (bands.isArray()) {
            for (JsonNode band : bands) {
                int min = band.path("min_score").asInt();
                int max = band.path("max_score").asInt();
                if (score >= min && score <= max) {
                    return band.path("risk_band").asText("AMBER");
                }
            }
        }
        return "AMBER";
    }

    private String buildReasoning(int baseScore,
                                  List<Map<String, Object>> triggersFired,
                                  String band,
                                  Map<String, Object> entityProfile,
                                  String journeyType) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("Base score ").append(baseScore)
                .append(" derived from internal rating ")
                .append(entityProfile.getOrDefault("internal_risk_rating", "MEDIUM"))
                .append(" for journey type ").append(journeyType).append(". ");
        if (triggersFired.isEmpty()) {
            reasoning.append("No additional triggers fired. ");
        } else {
            reasoning.append("Triggers fired: ");
            List<String> triggerSummaries = new ArrayList<>();
            for (Map<String, Object> trigger : triggersFired) {
                triggerSummaries.add(trigger.get("code") + " (" + trigger.get("reason") + ")");
            }
            reasoning.append(String.join("; ", triggerSummaries)).append(". ");
        }
        reasoning.append("Final band ").append(band).append(".");
        return reasoning.toString();
    }

    private String safeUpper(Object value) {
        return String.valueOf(value).toUpperCase(Locale.ROOT);
    }

    private record TriggerDefinition(String code, String severity, String description) {}
}