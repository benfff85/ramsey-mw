package com.setminusx.ramsey.mw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ramsey")
public class RamseyProperties {

    private RamseyProperties() {
        // Masking constructor
    }

    @Data
    @Component
    @ConfigurationProperties(prefix = "ramsey.startup")
    public static class Startup {

        private Startup() {
            // Masking constructor
        }

        @Data
        @Component
        @ConfigurationProperties(prefix = "ramsey.startup.initialize-test-data")
        public static class InitializeTestData {

            private InitializeTestData() {
                // Masking constructor
            }

            /**
             * Indicates if test data should be pulled from production API endpoints at application startup for database population.
             * This is unrelated to DDL execution or data.sql execution.
             */
            private boolean enabled;

        }

    }



}
