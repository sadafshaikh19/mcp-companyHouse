# MCP KYB LangChain Service

A comprehensive Know Your Business (KYB) multi-agent workflow system built with Spring Boot, LangChain4j, and advanced observability features.

## 🚀 Quick Start

### Environment Setup

**Set OpenAI API Key:**
```bash
# Windows
setx OPENAI_API_KEY "your-openai-api-key"

# Linux/macOS
export OPENAI_API_KEY="your-openai-api-key"
```

### Running the Application

```bash
mvn spring-boot:run
```

## 📡 API Endpoints

### MCP (Model Context Protocol) Endpoints

**Risk Assessment:**
```bash
curl --location 'http://localhost:8080/mcp/message' \
--header 'Content-Type: application/json' \
--data '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "assessRiskScopeAndActions",
      "arguments": {
        "customerId": "CUST-0001"
      }
    }
}'
```

## 📊 Observability Endpoints

The service includes comprehensive observability with distributed tracing, metrics, and monitoring.

### Health & Info

- **Health Check:** `GET /actuator/health`
  - Returns application health status
  - Shows detailed health information when `management.endpoint.health.show-details=always`

- **Application Info:** `GET /actuator/info`
  - Returns application information and metadata

### Metrics & Monitoring

#### Available Metrics Overview
- **All Metrics:** `GET /actuator/metrics`
  - Returns list of all available metric names

#### Prometheus Metrics
- **Prometheus Format:** `GET /actuator/prometheus`
  - Exports all metrics in Prometheus format for scraping

#### Individual Metric Queries

##### Custom KYB Business Metrics
- **Total Risk Assessments:** `GET /actuator/metrics/kyb.risk_assessments.total`
- **Green Risk Assessments:** `GET /actuator/metrics/kyb.risk_assessments.green`
- **Amber Risk Assessments:** `GET /actuator/metrics/kyb.risk_assessments.amber`
- **Red Risk Assessments:** `GET /actuator/metrics/kyb.risk_assessments.red`
- **Agent Operations Timer:** `GET /actuator/metrics/kyb.agent.operations`
- **Risk Assessment Timer:** `GET /actuator/metrics/kyb.agent.risk_assessment`

##### LLM (Large Language Model) Metrics
- **Total LLM Calls:** `GET /actuator/metrics/llm.calls.total`
- **Successful LLM Calls:** `GET /actuator/metrics/llm.calls.successful`
- **Failed LLM Calls:** `GET /actuator/metrics/llm.calls.failed`
- **Prompt Tokens:** `GET /actuator/metrics/llm.tokens.prompt`
- **Completion Tokens:** `GET /actuator/metrics/llm.tokens.completion`
- **Total Tokens:** `GET /actuator/metrics/llm.tokens.total`
- **Total LLM Cost:** `GET /actuator/metrics/llm.cost.total`
- **Cost Per Request:** `GET /actuator/metrics/llm.cost.per_request`
- **LLM Call Duration:** `GET /actuator/metrics/llm.call.duration`
- **GPT-4 Calls:** `GET /actuator/metrics/llm.model.gpt4.calls`
- **GPT-3.5 Calls:** `GET /actuator/metrics/llm.model.gpt35.calls`

##### Built-in Spring Boot Metrics
- **HTTP Server Requests:** `GET /actuator/metrics/http.server.requests`
- **JVM Memory Usage:** `GET /actuator/metrics/jvm.memory.used`
- **JVM Memory Max:** `GET /actuator/metrics/jvm.memory.max`
- **JVM Threads:** `GET /actuator/metrics/jvm.threads.live`
- **System CPU Usage:** `GET /actuator/metrics/system.cpu.usage`
- **Garbage Collection:** `GET /actuator/metrics/jvm.gc.pause`

#### Tracing
- **Distributed Traces:** `GET /actuator/traces`
  - Returns recent trace information (when available)

#### LLM Monitoring
- **LLM Statistics:** `GET /actuator/llm-stats`
  - Returns cumulative LLM usage statistics (tokens, costs)
- **LLM Stats API:** `GET /api/llm/stats`
  - REST API endpoint for LLM statistics

### Configuration

**Application Properties:**
```properties
# Server Configuration
server.port=8080
server.address=0.0.0.0

# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY}
openai.model.name=gpt-4o-mini

# OpenTelemetry Configuration
otel.exporter.otlp.endpoint=http://localhost:4317
otel.service.name=mcp-kyb-langchain-service
otel.service.version=1.0.0

# Actuator Configuration
management.endpoints.web.exposure.include=health,info,metrics,prometheus,traces,llm-stats
management.endpoint.health.show-details=always
management.tracing.sampling.probability=1.0

# LLM Monitoring Configuration
llm.monitoring.enabled=true
llm.monitoring.log.prompts=true
llm.monitoring.log.responses=true
llm.monitoring.cost.alert.threshold=10.0
```

## 🏗️ Architecture

### Components

- **Multi-Agent System:** Orchestrates KYB workflow across specialized agents
- **LangChain4j Integration:** Leverages AI models for intelligent decision making
- **LLM Monitoring:** Comprehensive tracking of token usage, costs, and performance
- **Distributed Tracing:** OpenTelemetry-based observability
- **Metrics Collection:** Micrometer-based performance monitoring
- **Structured Logging:** MDC-based log correlation

### Agents

- **RiskComplianceAgent:** Performs risk assessments with AI analysis
- **CustomerPartyProfileAgent:** Analyzes customer party profiles
- **TransactionPatternAgent:** Identifies transaction patterns and anomalies
- **JourneyClassifierAgent:** Classifies customer journey stages
- **KYBNoteAgent:** Manages KYB documentation and notes

## 🔍 Monitoring & Debugging

### Log Correlation
All logs include trace and span IDs for distributed request tracking:
```
INFO [mcp-kyb-langchain-service,8f5c2d1e3a4b5c6d,2f8e9d1c3b4a5f6e] - Risk assessment completed with result: AMBER
```

### LLM Monitoring

The service provides comprehensive monitoring of Large Language Model operations:

#### Cost Tracking
- **Real-time cost calculation** based on OpenAI pricing
- **Cost alerts** when thresholds are exceeded
- **Per-request cost analysis** with percentiles

#### Token Usage Analytics
- **Prompt vs completion token tracking**
- **Total token consumption monitoring**
- **Token efficiency metrics**

#### Performance Monitoring
- **LLM call latency** with percentile distributions
- **Success/failure rates** by model type
- **Model usage statistics** (GPT-4, GPT-3.5, etc.)

#### Example LLM Metrics
```bash
# Check LLM call success rate
curl http://localhost:8080/actuator/metrics/llm.calls.successful

# Monitor token usage
curl http://localhost:8080/actuator/metrics/llm.tokens.total

# Track costs
curl http://localhost:8080/actuator/metrics/llm.cost.total

# Get cumulative statistics
curl http://localhost:8080/actuator/llm-stats
```

### External Monitoring Setup

**OpenTelemetry Collector (for traces):**
```bash
docker run -p 4317:4317 otel/opentelemetry-collector:latest
```

**Prometheus (for metrics):**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'kyb-service'
    static_configs:
      - targets: ['localhost:8080']
```

## 📚 Additional Documentation

- **[Observability Guide](./OBSERVABILITY.md)** - Detailed observability setup and configuration
- **API Documentation** - Available at `/swagger-ui.html` when running

## 🛠️ Development

### Prerequisites
- Java 21+
- Maven 3.8+
- OpenAI API Key

### Build & Test
```bash
mvn clean compile
mvn test
mvn clean install
```

### Key Dependencies
- **Spring Boot 3.2.6** - Application framework
- **LangChain4j 1.8.0** - AI integration
- **OpenTelemetry 1.39.0** - Distributed tracing
- **Micrometer** - Metrics collection
- **Spring Boot Actuator** - Monitoring endpoints