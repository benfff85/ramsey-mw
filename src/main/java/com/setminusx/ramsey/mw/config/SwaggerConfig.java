package com.setminusx.ramsey.mw.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Slf4j
//@Configuration
public class SwaggerConfig {

    @Bean
    public CorsFilter corsFilter() {
        log.info("Configuring CORS filter");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Allow anyone and anything access. Probably ok for Swagger spec
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        CorsConfiguration config2 = new CorsConfiguration().applyPermitDefaultValues();

        source.registerCorsConfiguration("/v3/api-docs", config2);
        return new CorsFilter(source);
    }

}
