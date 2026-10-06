package com.budgettracker.application;

import com.budgettracker.application.port.HouseholdRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures every household has default categories on startup.
 * Idempotent — households that already have categories are skipped.
 */
@Component
class DefaultCategorySeeder implements ApplicationRunner {

    private final HouseholdRepository households;
    private final CategoryService categoryService;

    DefaultCategorySeeder(HouseholdRepository households, CategoryService categoryService) {
        this.households = households;
        this.categoryService = categoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        households.findAllIds().forEach(categoryService::seedDefaultsIfNone);
    }
}
