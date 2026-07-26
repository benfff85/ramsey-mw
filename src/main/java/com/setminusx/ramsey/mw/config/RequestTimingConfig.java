package com.setminusx.ramsey.mw.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Times every request, but only reports the ones worth reading.
 *
 * <p>One INFO line per request was affordable when workers polled every few seconds. It stopped
 * being affordable once the pair-move hoist raised the stage rate: every worker in the fleet polls
 * {@code /fleets/{fleet}/active-stage} each cycle, which by itself accounted for ~73 lines/sec —
 * about two thirds of everything this service logged, essentially all of it reporting that a 1 ms
 * call took 1 ms.
 *
 * <p>What the timing log is genuinely good for is catching an endpoint that has become slow (it is
 * how a 20–28 ms per-cycle fleet lookup was found). That is preserved: anything at or above
 * {@code slowThresholdMs}, and anything that did not return 2xx, still logs at INFO. The rest drops
 * to DEBUG, recoverable by raising the level rather than by a code change.
 */
@Slf4j
@Component
@Profile({ "local", "dev" })
public class RequestTimingConfig implements WebMvcConfigurer {

    private static final String START_TIME_ATTRIBUTE = "startTime";

    /**
     * Requests at or above this many milliseconds log at INFO. Env: REQUEST_LOG_SLOW_MS.
     * The default sits well above the healthy cost of the polling endpoints (~1 ms) and below the
     * campaign progression fetch (~370 ms), so a regression in either is still visible.
     */
    @Value("${ramsey.request-log.slow-threshold-ms:250}")
    private long slowThresholdMs;

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
                Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);
                if (startTime == null) {
                    return;
                }
                long duration = System.currentTimeMillis() - (Long) startTime;
                int status = response.getStatus();
                boolean notable = duration >= slowThresholdMs || status < 200 || status >= 300;

                if (notable) {
                    log.info("REST Request {} {} completed in {}ms with status {}",
                            request.getMethod(), request.getRequestURI(), duration, status);
                } else if (log.isDebugEnabled()) {
                    log.debug("REST Request {} {} completed in {}ms with status {}",
                            request.getMethod(), request.getRequestURI(), duration, status);
                }
            }
        });
    }
}
