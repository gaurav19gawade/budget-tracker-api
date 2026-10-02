package com.budgettracker.infrastructure.config;

import com.budgettracker.infrastructure.simplefin.SimpleFinClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SimpleFinConfig {

    @Bean
    SimpleFinClient simpleFinClient() {
        // Plain HTTPS — SimpleFin Bridge does not use mTLS.
        RestClient http = RestClient.builder().build();
        return new SimpleFinClient(http);
    }
}
