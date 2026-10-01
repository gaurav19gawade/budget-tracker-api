package com.budgettracker.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** One injectable clock so time-dependent logic (invite expiry) is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
