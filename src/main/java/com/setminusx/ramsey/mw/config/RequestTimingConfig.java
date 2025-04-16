package com.setminusx.ramsey.mw.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RequestTimingConfig implements WebMvcConfigurer, WebGraphQlInterceptor {

    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
                return true;
            }

            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
                long duration = System.currentTimeMillis() - startTime;
                log.info("REST Request {} {} completed in {}ms with status {}", 
                    request.getMethod(), 
                    request.getRequestURI(), 
                    duration,
                    response.getStatus());
            }
        });
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        long startTime = System.currentTimeMillis();
        return chain.next(request).map(response -> {
            long duration = System.currentTimeMillis() - startTime;
            log.info("GraphQL Request {} completed in {}ms with {} errors", 
                request.getOperationName() != null ? request.getOperationName() : "anonymous",
                duration,
                response.getErrors().size());
            return response;
        });
    }
}
