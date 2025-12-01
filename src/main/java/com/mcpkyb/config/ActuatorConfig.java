package com.mcpkyb.config;

import com.mcpkyb.service.MetricsService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActuatorConfig {

    /**
     * Custom health indicator for KYB service specific checks
     */
    @Bean
    public HealthIndicator kybServiceHealthIndicator(MetricsService metricsService) {
        return () -> {
            // You can add custom health checks here
            // For example, check if the service has processed recent requests
            // or if external dependencies are available

            return Health.up()
                    .withDetail("service", "KYB LangChain Service")
                    .withDetail("metrics.enabled", true)
                    .build();
        };
    }
}
