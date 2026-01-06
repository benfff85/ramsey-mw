package com.setminusx.ramsey.mw.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that generates a trace ID for each incoming HTTP request.
 * The trace ID is stored in MDC and appears in logs as [traceId,spanId].
 */
@Component
@Order(1)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        try {
            String traceId = extractOrGenerateTraceId(request);
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String extractOrGenerateTraceId(ServletRequest request) {
        if (request instanceof HttpServletRequest httpRequest) {
            String headerTraceId = httpRequest.getHeader(TRACE_ID_HEADER);
            if (headerTraceId != null && !headerTraceId.isBlank()) {
                return headerTraceId;
            }
        }
        return UUID.randomUUID().toString();
    }
}
