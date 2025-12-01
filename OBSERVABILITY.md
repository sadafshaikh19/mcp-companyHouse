# Observability Setup for MCP KYB LangChain Service

This document describes the comprehensive observability implementation added to the MCP KYB LangChain service, providing distributed tracing, metrics collection, structured logging, and monitoring capabilities.

## Overview

The observability setup includes:

- **Distributed Tracing**: OpenTelemetry integration for request tracing across the application
- **Metrics Collection**: Micrometer-based metrics with Prometheus export
- **Structured Logging**: MDC-based logging with trace/span IDs
- **LangChain4j Observability**: Built-in tracing for AI model interactions
- **Spring Boot Actuator**: Health checks, metrics endpoints, and monitoring

## Components

### 1. OpenTelemetry Configuration (`ObservabilityConfig.java`)

- Manual OpenTelemetry SDK configuration (no Spring Boot starter)
- Configures OTLP exporter for trace collection
- Sets up service resource attributes
- Provides Tracer bean for manual instrumentation

### 2. LLM Observability Configuration (`LLMObservabilityConfig.java`)

- Custom LangChain4j listeners for LLM monitoring
- Automatic capture of token usage and costs
- Integration with distributed tracing
- Error tracking and performance monitoring

**Configuration Properties:**
```properties
otel.exporter.otlp.endpoint=http://localhost:4317
otel.service.name=mcp-kyb-langchain-service
otel.service.version=1.0.0
```

### 2. LangChain4j Observability Integration

The `LangChainConfig.java` has been updated to include:
- `TracingChatModelListener`: Automatically traces AI model calls
- `LoggingChatModelListener`: Logs model interactions

### 3. Structured Logging (`LoggingConfig.java` & `WebConfig.java`)

- **TraceIdFilter**: Adds trace/span IDs to MDC for every request
- **Log Pattern**: Includes trace/span IDs in log output

**Log Format:**
```
%5p [service-name,traceId,spanId] - message
```

### 4. Metrics Service (`MetricsService.java`)

Provides custom metrics for KYB operations:

- `kyb.risk_assessments.total`: Total risk assessments
- `kyb.risk_assessments.green/amber/red`: Risk assessment results by color
- `kyb.agent.operations`: Timer for agent operations
- Custom gauge registration with value suppliers
- Simple value recording with counters

### 5. LLM Monitoring Service (`LLMMonitoringService.java`)

Comprehensive monitoring for Large Language Model operations:

#### Token Usage Metrics
- `llm.tokens.prompt`: Distribution of prompt tokens per request
- `llm.tokens.completion`: Distribution of completion tokens per request
- `llm.tokens.total`: Distribution of total tokens per request

#### Cost Metrics
- `llm.cost.total`: Total LLM API costs in USD
- `llm.cost.per_request`: Distribution of costs per LLM request

#### Performance Metrics
- `llm.call.duration`: Duration of LLM API calls
- `llm.calls.total`: Total number of LLM API calls
- `llm.calls.successful`: Number of successful LLM API calls
- `llm.calls.failed`: Number of failed LLM API calls

#### Model Usage Metrics
- `llm.model.gpt4.calls`: Number of GPT-4 model calls
- `llm.model.gpt35.calls`: Number of GPT-3.5 model calls
- `llm.model.other.calls`: Number of other model calls

#### Error Tracking
- `llm.errors`: Counters for different error types by model

### 5. Enhanced RiskComplianceAgent

The `RiskComplianceAgent` now includes:
- OpenTelemetry span creation and management
- Automatic metrics recording
- Structured logging with context
- Error handling with span status updates

## Setup and Usage

### Prerequisites

1. **OpenTelemetry Collector** (optional but recommended):
   ```bash
   # Run OTEL Collector to receive traces
   docker run -p 4317:4317 otel/opentelemetry-collector:latest
   ```

2. **Prometheus** (for metrics):
   ```bash
   # Add your service to prometheus.yml
   - job_name: 'kyb-service'
     static_configs:
       - targets: ['localhost:8080']
   ```

### Running the Application

```bash
mvn spring-boot:run
```

### Accessing Observability Data

#### Health and Info Endpoints
- `GET /actuator/health` - Application health status
- `GET /actuator/info` - Application information

#### Metrics Endpoints
- `GET /actuator/metrics` - Available metrics
- `GET /actuator/prometheus` - Prometheus-formatted metrics
- `GET /actuator/metrics/kyb.risk_assessments.total` - Specific metric

#### Tracing
- Traces are exported to configured OTLP endpoint (default: `http://localhost:4317`)
- Use Jaeger, Zipkin, or other trace visualization tools

### Example Log Output

```
INFO  [mcp-kyb-langchain-service,8f5c2d1e3a4b5c6d,2f8e9d1c3b4a5f6e] - Starting risk assessment for profile summary length: 1250, transaction insights length: 3400
DEBUG [mcp-kyb-langchain-service,8f5c2d1e3a4b5c6d,2f8e9d1c3b4a5f6e] - Sending chat request to OpenAI model
INFO  [mcp-kyb-langchain-service,8f5c2d1e3a4b5c6d,2f8e9d1c3b4a5f6e] - Risk assessment completed with result: AMBER
```

## Configuration Options

### Tracing Configuration

```properties
# Tracing sampling (1.0 = 100% of requests)
management.tracing.sampling.probability=1.0

# Disable tracing for specific endpoints
management.tracing.enabled=false
```

### Metrics Configuration

```properties
# Enable Prometheus metrics export
management.metrics.export.prometheus.enabled=true

# Custom metrics tags
management.metrics.tags.application=mcp-kyb-service
```

### Logging Configuration

```properties
# Log levels
logging.level.com.mcpkyb=DEBUG
logging.level.io.opentelemetry=INFO

# Custom log pattern
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
```

### LLM Monitoring Configuration

```properties
# LLM Monitoring Settings
llm.monitoring.enabled=true
llm.monitoring.log.prompts=true
llm.monitoring.log.responses=true
llm.monitoring.cost.alert.threshold=10.0

# LLM Logging Level
logging.level.com.mcpkyb.service.LLMMonitoringService=INFO
```

## Monitoring and Alerting

### Recommended Alerts

1. **High Error Rate**: Alert when risk assessment errors exceed threshold
2. **Slow Response Times**: Alert when agent operations take too long
3. **High RED Risk Assessments**: Business logic alerts for compliance
4. **High LLM Costs**: Alert when LLM API costs exceed daily threshold
5. **High LLM Error Rate**: Alert when LLM call failure rate exceeds threshold
6. **Slow LLM Responses**: Alert when LLM call latency exceeds acceptable limits

### Example Prometheus Queries

```promql
# Risk assessment rate by color
rate(kyb_risk_assessments_green_total[5m]) / rate(kyb_risk_assessments_total[5m])

# Agent operation latency
histogram_quantile(0.95, rate(kyb_agent_operations_bucket[5m]))

# LLM Cost per minute
rate(llm_cost_total[5m])

# LLM Error rate
rate(llm_calls_failed_total[5m]) / rate(llm_calls_total[5m])

# LLM Token usage rate
rate(llm_tokens_total_sum[5m]) / rate(llm_tokens_total_count[5m])

# LLM P95 latency
histogram_quantile(0.95, rate(llm_call_duration_bucket[5m]))
```

## Troubleshooting

### Common Issues

1. **No traces appearing**: Check OTLP endpoint configuration and connectivity
2. **Missing metrics**: Verify Prometheus endpoint configuration
3. **Log correlation issues**: Ensure TraceIdFilter is properly registered
4. **OpenTelemetry dependency issues**: Uses manual configuration instead of Spring Boot starter for compatibility
5. **Gauge method compilation errors**: Fixed to use proper Micrometer gauge API with object and value function

### Debug Mode

Enable debug logging to see detailed observability operations:

```properties
logging.level.com.mcpkyb=DEBUG
logging.level.io.opentelemetry=DEBUG
```

## Performance Considerations

- Tracing adds minimal overhead when properly configured
- Metrics collection is asynchronous and non-blocking
- Structured logging MDC operations are lightweight
- Consider adjusting sampling rates in production for high-traffic scenarios

## Future Enhancements

- Integration with distributed tracing UIs (Jaeger, Zipkin)
- Custom dashboards for KYB-specific metrics
- Alert manager integration
- Log aggregation with ELK stack
