package com.mcpkyb.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Counters for business metrics
    private final Counter riskAssessmentsTotal;
    private final Counter riskAssessmentsGreen;
    private final Counter riskAssessmentsAmber;
    private final Counter riskAssessmentsRed;

    // Timer for agent operations
    private final Timer agentOperationsTimer;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.riskAssessmentsTotal = Counter.builder("kyb.risk_assessments.total")
                .description("Total number of risk assessments performed")
                .register(meterRegistry);

        this.riskAssessmentsGreen = Counter.builder("kyb.risk_assessments.green")
                .description("Number of GREEN risk assessments")
                .register(meterRegistry);

        this.riskAssessmentsAmber = Counter.builder("kyb.risk_assessments.amber")
                .description("Number of AMBER risk assessments")
                .register(meterRegistry);

        this.riskAssessmentsRed = Counter.builder("kyb.risk_assessments.red")
                .description("Number of RED risk assessments")
                .register(meterRegistry);

        // Initialize timer
        this.agentOperationsTimer = Timer.builder("kyb.agent.operations")
                .description("Timer for agent operations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * Records a risk assessment with the given risk color
     */
    public void recordRiskAssessment(String riskColor) {
        riskAssessmentsTotal.increment();

        switch (riskColor.toUpperCase()) {
            case "GREEN":
                riskAssessmentsGreen.increment();
                break;
            case "AMBER":
                riskAssessmentsAmber.increment();
                break;
            case "RED":
                riskAssessmentsRed.increment();
                break;
            default:
                // Unknown risk color - still count it in total
                break;
        }
    }

    /**
     * Records the duration of an agent operation
     */
    public void recordAgentOperationDuration(long durationMs, String operationName, boolean success) {
        agentOperationsTimer.record(Duration.ofMillis(durationMs));
    }

    /**
     * Creates a sample for timing an operation
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Records a custom counter metric
     */
    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a custom gauge metric with a supplier
     * Note: Gauges need an object and a function to extract the value
     */
    public <T> void registerGauge(String name, T obj, java.util.function.ToDoubleFunction<T> valueFunction, String... tags) {
        meterRegistry.gauge(name, Tags.of(tags), obj, valueFunction);
    }

    /**
     * Records a simple value metric using a counter with tags
     */
    public void recordValue(String name, double value, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment(value);
    }

    /**
     * Gets the meter registry for advanced metric operations
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
