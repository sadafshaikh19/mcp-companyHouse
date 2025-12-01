package com.mcpkyb.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for monitoring LLM (Large Language Model) operations including:
 * - Token usage tracking
 * - Cost calculation
 * - Performance metrics (latency, throughput)
 * - Error rates
 * - Model-specific statistics
 */
@Service
public class LLMMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(LLMMonitoringService.class);

    private final MeterRegistry meterRegistry;

    // Counters for LLM operations
    private final Counter totalLLMCalls;
    private final Counter successfulLLMCalls;
    private final Counter failedLLMCalls;

    // Token usage metrics
    private final DistributionSummary promptTokensSummary;
    private final DistributionSummary completionTokensSummary;
    private final DistributionSummary totalTokensSummary;

    // Cost tracking
    private final Counter totalCostCounter;
    private final DistributionSummary costPerRequestSummary;

    // Performance metrics
    private final Timer llmCallTimer;

    // Model-specific metrics
    private final Counter gpt4Calls;
    private final Counter gpt35Calls;
    private final Counter otherModelCalls;

    // Atomic counters for cumulative tracking
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalCostMicroDollars = new AtomicLong(0); // Cost in micro-dollars (millionths of a dollar)

    private final LangSmithManualTracing langSmithTracing;

    // Cost rates per 1K tokens (in USD)
    private static final double GPT_4_INPUT_COST_PER_1K = 0.03;
    private static final double GPT_4_OUTPUT_COST_PER_1K = 0.06;
    private static final double GPT_35_TURBO_INPUT_COST_PER_1K = 0.0015;
    private static final double GPT_35_TURBO_OUTPUT_COST_PER_1K = 0.002;

    public LLMMonitoringService(MeterRegistry meterRegistry, LangSmithManualTracing langSmithTracing) {
        this.meterRegistry = meterRegistry;
        this.langSmithTracing = langSmithTracing;

        // Initialize counters
        this.totalLLMCalls = Counter.builder("llm.calls.total")
                .description("Total number of LLM API calls")
                .register(meterRegistry);

        this.successfulLLMCalls = Counter.builder("llm.calls.successful")
                .description("Number of successful LLM API calls")
                .register(meterRegistry);

        this.failedLLMCalls = Counter.builder("llm.calls.failed")
                .description("Number of failed LLM API calls")
                .register(meterRegistry);

        // Token usage summaries
        this.promptTokensSummary = DistributionSummary.builder("llm.tokens.prompt")
                .description("Distribution of prompt tokens per request")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.completionTokensSummary = DistributionSummary.builder("llm.tokens.completion")
                .description("Distribution of completion tokens per request")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.totalTokensSummary = DistributionSummary.builder("llm.tokens.total")
                .description("Distribution of total tokens per request")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // Cost tracking
        this.totalCostCounter = Counter.builder("llm.cost.total")
                .description("Total LLM API costs in USD")
                .register(meterRegistry);

        this.costPerRequestSummary = DistributionSummary.builder("llm.cost.per_request")
                .description("Distribution of costs per LLM request in USD")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // Performance timer
        this.llmCallTimer = Timer.builder("llm.call.duration")
                .description("Duration of LLM API calls")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // Model-specific counters
        this.gpt4Calls = Counter.builder("llm.model.gpt4.calls")
                .description("Number of GPT-4 model calls")
                .register(meterRegistry);

        this.gpt35Calls = Counter.builder("llm.model.gpt35.calls")
                .description("Number of GPT-3.5 model calls")
                .register(meterRegistry);

        this.otherModelCalls = Counter.builder("llm.model.other.calls")
                .description("Number of other model calls")
                .register(meterRegistry);
    }

    /**
     * Records the start of an LLM call
     */
    public Timer.Sample startLLMCall() {
        return Timer.start(meterRegistry);
    }

    /**
     * Records a successful LLM call with token usage and model information
     */
    public void recordSuccessfulCall(Timer.Sample timerSample, String model, int promptTokens, int completionTokens, long durationMs) {
        totalLLMCalls.increment();
        successfulLLMCalls.increment();

        int totalTokens = promptTokens + completionTokens;

        // Record token usage
        promptTokensSummary.record(promptTokens);
        completionTokensSummary.record(completionTokens);
        totalTokensSummary.record(totalTokens);

        // Update cumulative counters
        totalTokensUsed.addAndGet(totalTokens);
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);

        // Calculate and record cost
        double cost = calculateCost(model, promptTokens, completionTokens);
        totalCostCounter.increment(cost);
        costPerRequestSummary.record(cost);
        totalCostMicroDollars.addAndGet((long)(cost * 1_000_000));

        // Record model usage
        recordModelUsage(model);

        // Record timing
        timerSample.stop(llmCallTimer);

        logger.debug("LLM call completed - Model: {}, Prompt tokens: {}, Completion tokens: {}, Total tokens: {}, Cost: ${}, Duration: {}ms",
                model, promptTokens, completionTokens, totalTokens, String.format("%.6f", cost), durationMs);

        // Send trace to LangSmith
        try {
            langSmithTracing.traceLLMCall(
                "LLM call for model: " + model,
                "Response generated with " + completionTokens + " tokens",
                promptTokens,
                completionTokens,
                cost,
                durationMs
            );
        } catch (Exception e) {
            logger.debug("Failed to send trace to LangSmith: {}", e.getMessage());
        }
    }

    /**
     * Records a failed LLM call
     */
    public void recordFailedCall(Timer.Sample timerSample, String model, String errorType, long durationMs) {
        totalLLMCalls.increment();
        failedLLMCalls.increment();

        // Record model usage even for failed calls
        recordModelUsage(model);

        // Record timing
        timerSample.stop(llmCallTimer);

        // Record error as a counter with tags
        Counter.builder("llm.errors")
                .tag("model", model)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();

        logger.warn("LLM call failed - Model: {}, Error: {}, Duration: {}ms", model, errorType, durationMs);
    }

    /**
     * Records model usage statistics
     */
    private void recordModelUsage(String model) {
        if (model.toLowerCase().contains("gpt-4")) {
            gpt4Calls.increment();
        } else if (model.toLowerCase().contains("gpt-3.5")) {
            gpt35Calls.increment();
        } else {
            otherModelCalls.increment();
        }
    }

    /**
     * Calculates the cost of an LLM call based on model and token usage
     */
    private double calculateCost(String model, int promptTokens, int completionTokens) {
        double inputCostPer1K, outputCostPer1K;

        if (model.toLowerCase().contains("gpt-4")) {
            inputCostPer1K = GPT_4_INPUT_COST_PER_1K;
            outputCostPer1K = GPT_4_OUTPUT_COST_PER_1K;
        } else if (model.toLowerCase().contains("gpt-3.5")) {
            inputCostPer1K = GPT_35_TURBO_INPUT_COST_PER_1K;
            outputCostPer1K = GPT_35_TURBO_OUTPUT_COST_PER_1K;
        } else {
            // Default to GPT-3.5 pricing for unknown models
            inputCostPer1K = GPT_35_TURBO_INPUT_COST_PER_1K;
            outputCostPer1K = GPT_35_TURBO_OUTPUT_COST_PER_1K;
        }

        double inputCost = (promptTokens / 1000.0) * inputCostPer1K;
        double outputCost = (completionTokens / 1000.0) * outputCostPer1K;

        return inputCost + outputCost;
    }

    /**
     * Gets cumulative statistics
     */
    public LLMStatistics getStatistics() {
        return new LLMStatistics(
                totalTokensUsed.get(),
                totalPromptTokens.get(),
                totalCompletionTokens.get(),
                totalCostMicroDollars.get() / 1_000_000.0
        );
    }

    /**
     * Data class for LLM statistics
     */
    public static class LLMStatistics {
        public final long totalTokens;
        public final long promptTokens;
        public final long completionTokens;
        public final double totalCostUSD;

        public LLMStatistics(long totalTokens, long promptTokens, long completionTokens, double totalCostUSD) {
            this.totalTokens = totalTokens;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalCostUSD = totalCostUSD;
        }
    }
}
