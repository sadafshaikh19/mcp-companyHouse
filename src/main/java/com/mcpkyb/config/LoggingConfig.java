package com.mcpkyb.config;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class LoggingConfig {

    /**
     * Filter to add trace ID and span ID to MDC for structured logging
     */
    public static class TraceIdFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {

            try {
                // Get current span from OpenTelemetry
                Span currentSpan = Span.current();
                SpanContext spanContext = currentSpan.getSpanContext();

                // Add trace and span IDs to MDC for logging
                if (spanContext.isValid()) {
                    MDC.put("traceId", spanContext.getTraceId());
                    MDC.put("spanId", spanContext.getSpanId());
                } else {
                    // Fallback: generate a request ID if no span context
                    String requestId = UUID.randomUUID().toString().substring(0, 8);
                    MDC.put("traceId", requestId);
                    MDC.put("spanId", "unknown");
                }

                // Add request information to MDC
                MDC.put("method", request.getMethod());
                MDC.put("uri", request.getRequestURI());
                MDC.put("userAgent", request.getHeader("User-Agent"));

                filterChain.doFilter(request, response);
            } finally {
                // Clean up MDC
                MDC.remove("traceId");
                MDC.remove("spanId");
                MDC.remove("method");
                MDC.remove("uri");
                MDC.remove("userAgent");
            }
        }
    }
}
