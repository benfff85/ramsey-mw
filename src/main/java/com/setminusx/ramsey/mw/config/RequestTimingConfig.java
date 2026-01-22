package com.setminusx.ramsey.mw.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Component
@Profile({ "local", "dev" })
public class RequestTimingConfig implements WebMvcConfigurer {

    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                    @NotNull Object handler) {
                request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
                return true;
            }

            @Override
            public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                    @NotNull Object handler, Exception ex) {
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
}
