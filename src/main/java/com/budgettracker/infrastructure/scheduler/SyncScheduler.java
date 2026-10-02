package com.budgettracker.infrastructure.scheduler;

import com.budgettracker.application.SyncService;
import com.budgettracker.application.port.HouseholdRepository;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs a nightly transaction sync for all households at 2:00 AM Eastern Time.
 * ShedLock ensures exactly one Railway instance fires even when scaled horizontally.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;
    private final HouseholdRepository householdRepository;

    public SyncScheduler(SyncService syncService, HouseholdRepository householdRepository) {
        this.syncService = syncService;
        this.householdRepository = householdRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?", zone = "America/New_York")
    @SchedulerLock(name = "nightly_sync", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void nightlySync() {
        List<UUID> householdIds = householdRepository.findAllIds();
        log.info("Nightly sync started for {} household(s)", householdIds.size());
        for (UUID id : householdIds) {
            try {
                SyncService.SyncStats stats = syncService.syncHousehold(id);
                log.info("Household {}: {} new, {} updated transactions",
                        id, stats.newTransactions(), stats.updatedTransactions());
            } catch (Exception e) {
                log.error("Nightly sync failed for household {}", id, e);
            }
        }
        log.info("Nightly sync complete");
    }
}
