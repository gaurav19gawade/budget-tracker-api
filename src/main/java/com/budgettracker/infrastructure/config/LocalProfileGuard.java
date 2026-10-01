package com.budgettracker.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Safety net: the "local" profile disables authentication, so it must never run
 * against a remote database (for example the Supabase production project).
 */
@Component
@Profile("local")
public class LocalProfileGuard {

    private final String datasourceUrl;

    public LocalProfileGuard(@Value("${spring.datasource.url}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    void refuseNonLocalDatabase() {
        boolean local = datasourceUrl.contains("//localhost") || datasourceUrl.contains("//127.0.0.1");
        if (!local) {
            throw new IllegalStateException(
                "Profile 'local' disables authentication and may only be used with a localhost database.");
        }
    }
}
