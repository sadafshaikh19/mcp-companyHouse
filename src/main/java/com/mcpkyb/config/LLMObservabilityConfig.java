package com.mcpkyb.config;

import com.mcpkyb.service.LLMMonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for LLM observability.
 * Note: Automatic listener integration is disabled due to LangChain4j version compatibility.
 * LLM monitoring is available through manual service calls in agents.
 */
@Configuration
public class LLMObservabilityConfig {

    private static final Logger logger = LoggerFactory.getLogger(LLMObservabilityConfig.class);

    // LLM monitoring service is available as a bean for manual usage in agents
    // Automatic listener integration removed due to interface compatibility issues
}
