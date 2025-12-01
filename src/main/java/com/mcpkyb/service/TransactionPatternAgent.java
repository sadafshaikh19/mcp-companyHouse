package com.mcpkyb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mcpkyb.utils.JsonLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;

@Service
public class TransactionPatternAgent {

    // Flexible formatter: supports yyyy-MM and yyyy-MM-dd
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM[-dd]");

    public String analyzeTransactions(String customerId) throws IOException {
        JsonNode txRoot = JsonLoader.loadJson("transactions.json");
        JsonNode txData = txRoot.path("customers").path(customerId);

        if (txData.isMissingNode()) {
            throw new IOException("No transaction data found for " + customerId);
        }

        List<JsonNode> monthlyStats = extractMonthlyStats(txData);
        if (monthlyStats.size() < 2) {
            throw new IOException("Insufficient transaction history for " + customerId);
        }

        JsonNode rules = JsonLoader.loadJson("rules.json");
        JsonNode thresholds = rules.path("risk_thresholds");
        double intlSpikeThreshold = thresholds.path("intl_outward_mom_spike_pct").asDouble(100);
        double highRiskShareThreshold = thresholds.path("high_risk_country_volume_ratio").asDouble(0.05) * 100;
        double cashDepositThreshold = thresholds.path("cash_deposit_to_turnover_ratio").asDouble(0.30) * 100;

        JsonNode latest = monthlyStats.get(monthlyStats.size() - 1);
        JsonNode previous = monthlyStats.get(monthlyStats.size() - 2);

        double latestIntlOut = latest.path("intl_outward_amount").asDouble(0);
        double prevIntlOut = previous.path("intl_outward_amount").asDouble(0);
        double intlChangePct = computePctChange(prevIntlOut, latestIntlOut);

        double latestTotalOut = latest.path("total_outward_amount").asDouble(0);
        double highRiskVolume = latest.path("high_risk_country_volume").asDouble(0);
        double highRiskSharePct = latestTotalOut > 0 ? (highRiskVolume / latestTotalOut) * 100 : 0;

        double cashDeposits = latest.path("cash_deposits_amount").asDouble(0);
        double cashRatioPct = latestTotalOut > 0 ? (cashDeposits / latestTotalOut) * 100 : 0;

        List<String> candidateTriggers = new ArrayList<>();
        if (intlChangePct > intlSpikeThreshold) {
            candidateTriggers.add("TRIG_INTL_SPIKE");
        }
        if (highRiskSharePct > highRiskShareThreshold) {
            candidateTriggers.add("TRIG_HIGH_RISK_COUNTRY");
        }
        if (cashRatioPct > cashDepositThreshold) {
            candidateTriggers.add("TRIG_CASH_HEAVY");
        }

        String summary = buildSummary(monthlyStats, intlChangePct, highRiskSharePct, cashRatioPct, candidateTriggers);

        Map<String, Object> supportingMetrics = new HashMap<>();
        supportingMetrics.put("intl_outward_change_pct", Math.round(intlChangePct));
        supportingMetrics.put("high_risk_country_share_pct", Math.round(highRiskSharePct));
        supportingMetrics.put("cash_deposit_ratio_pct", Math.round(cashRatioPct));
        supportingMetrics.put("period_covered_months", monthlyStats.size());
        supportingMetrics.put("latest_period", latest.path("period").asText());

        Map<String, Object> insights = new HashMap<>();
        insights.put("summary", summary);
        insights.put("candidate_triggers", candidateTriggers);
        insights.put("supporting_metrics", supportingMetrics);

        Map<String, Object> response = new HashMap<>();
        response.put("transaction_insights", insights);
        return new ObjectMapper().writeValueAsString(response);
    }

    /**
     * Extracts and sorts monthly stats by period using flexible date parsing.
     */
    private List<JsonNode> extractMonthlyStats(JsonNode txData) {
        List<JsonNode> stats = new ArrayList<>();
        ArrayNode monthly = (ArrayNode) txData.path("monthly_stats");
        for (JsonNode node : monthly) {
            stats.add(node);
        }
        stats.sort(Comparator.comparing(node -> parsePeriod(node.path("period").asText())));
        // Keep last 6 months max
        if (stats.size() > 6) {
            stats = stats.subList(stats.size() - 6, stats.size());
        }
        return stats;
    }

    /**
     * Flexible date parsing: supports yyyy-MM and yyyy-MM-dd.
     * Defaults to first day of month if day is missing.
     */
    private LocalDate parsePeriod(String period) {
        try {
            TemporalAccessor parsed = PERIOD_FORMAT.parse(period);
            if (parsed.isSupported(ChronoField.DAY_OF_MONTH)) {
                return LocalDate.from(parsed);
            } else {
                return YearMonth.from(parsed).atDay(1);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid period format: " + period);
        }
    }

    private double computePctChange(double previous, double latest) {
        if (previous <= 0) {
            return latest > 0 ? 100 : 0;
        }
        return ((latest - previous) / previous) * 100;
    }

    private String buildSummary(List<JsonNode> stats,
                                double intlChangePct,
                                double highRiskSharePct,
                                double cashRatioPct,
                                List<String> triggers) {
        JsonNode latest = stats.get(stats.size() - 1);
        String latestPeriod = latest.path("period").asText();
        StringBuilder summary = new StringBuilder();
        summary.append("Across ").append(stats.size()).append(" months ending ").append(latestPeriod)
               .append(", outward volumes reached INR ")
               .append(formatAmount(latest.path("total_outward_amount").asDouble(0)))
               .append(". ");
        summary.append("International outward payments changed approx. ")
               .append(Math.round(intlChangePct)).append("% month-on-month. ");
        summary.append("High-risk country share at ").append(Math.round(highRiskSharePct)).append("%; ")
               .append("cash deposits represent ~").append(Math.round(cashRatioPct)).append("% of outward flows. ");
        if (triggers.isEmpty()) {
            summary.append("No major trigger-worthy anomalies detected.");
        } else {
            summary.append("Candidate triggers identified: ").append(String.join(", ", triggers)).append(".");
        }
        return summary.toString();
    }

    private String formatAmount(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1f Mn", amount / 1_000_000);
        }
        return String.format("%.0f", amount);
    }
}