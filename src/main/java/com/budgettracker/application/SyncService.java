package com.budgettracker.application;

import com.budgettracker.application.port.BankAccountRepository;
import com.budgettracker.application.port.BankDataProvider;
import com.budgettracker.application.port.CategoryRuleRepository;
import com.budgettracker.application.port.EnrollmentRepository;
import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.domain.CategoryRule;
import com.budgettracker.domain.BankAccount;
import com.budgettracker.domain.TellerEnrollment;
import com.budgettracker.domain.Transaction;
import com.budgettracker.infrastructure.security.TokenEncryptionService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    /** How far back to fetch on the first-ever sync. */
    private static final int INITIAL_LOOKBACK_DAYS = 90;

    /** Buffer added before the earliest last_synced_at to catch late-posting transactions. */
    private static final int LOOKBACK_BUFFER_DAYS = 1;

    private final EnrollmentRepository enrollments;
    private final BankAccountRepository accounts;
    private final TransactionRepository transactions;
    private final BankDataProvider bankData;
    private final CategoryRuleRepository categoryRules;
    private final Categorizer categorizer;
    private final TransferMatcher transferMatcher;
    private final TokenEncryptionService encryption;
    private final Clock clock;

    public SyncService(EnrollmentRepository enrollments,
                       BankAccountRepository accounts,
                       TransactionRepository transactions,
                       BankDataProvider bankData,
                       CategoryRuleRepository categoryRules,
                       Categorizer categorizer,
                       TransferMatcher transferMatcher,
                       TokenEncryptionService encryption,
                       Clock clock) {
        this.enrollments = enrollments;
        this.accounts = accounts;
        this.transactions = transactions;
        this.bankData = bankData;
        this.categoryRules = categoryRules;
        this.categorizer = categorizer;
        this.transferMatcher = transferMatcher;
        this.encryption = encryption;
        this.clock = clock;
    }

    public record SyncStats(int newTransactions, int updatedTransactions, Instant syncedAt) {
    }

    /**
     * Syncs all active bank connections for the given household.
     * Idempotent: re-running produces the same result (transactions are upserted by provider id).
     */
    public SyncStats syncHousehold(UUID householdId) {
        List<TellerEnrollment> enrollmentList = enrollments.findByHouseholdId(householdId);
        int totalNew = 0;
        int totalUpdated = 0;

        for (TellerEnrollment enrollment : enrollmentList) {
            try {
                SyncStats s = syncEnrollment(enrollment);
                totalNew += s.newTransactions();
                totalUpdated += s.updatedTransactions();
            } catch (Exception e) {
                log.error("Sync failed for enrollment {} (household {}): {}",
                        enrollment.id(), householdId, e.getMessage(), e);
            }
        }

        transferMatcher.matchTransfers(householdId);

        return new SyncStats(totalNew, totalUpdated, clock.instant());
    }

    private SyncStats syncEnrollment(TellerEnrollment enrollment) {
        String accessCredential = encryption.decrypt(enrollment.encryptedToken());

        List<BankAccount> activeAccounts = accounts.findByEnrollmentId(enrollment.id())
                .stream()
                .filter(a -> "active".equals(a.status()))
                .toList();

        Instant since = activeAccounts.stream()
                .map(BankAccount::lastSyncedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(t -> t.minus(LOOKBACK_BUFFER_DAYS, ChronoUnit.DAYS))
                .orElseGet(() -> clock.instant().minus(INITIAL_LOOKBACK_DAYS, ChronoUnit.DAYS));

        // Track whether this is an initial sync before fetching, so we can decide
        // whether to advance last_synced_at if SimpleFin returns 0 transactions.
        // On a first-ever sync SimpleFin may not have finished backfilling the bank,
        // so we must not advance the window until we actually receive data.
        boolean isInitialSync = activeAccounts.stream()
                .allMatch(a -> a.lastSyncedAt() == null);

        BankDataProvider.SyncResult result =
                bankData.fetchTransactionsWithBalances(accessCredential, since);

        Instant now = clock.instant();

        // Load rules once for this household so we can auto-categorise every new transaction.
        List<CategoryRule> ruleList = categoryRules.findByHouseholdId(enrollment.householdId());

        // Upsert transactions first so we know whether any were received before
        // deciding whether to stamp last_synced_at on the accounts below.
        int newCount = 0;
        int updatedCount = 0;
        for (BankDataProvider.ProviderTransaction pt : result.transactions()) {
            BankAccount account = activeAccounts.stream()
                    .filter(a -> a.tellerId().equals(pt.accountId()))
                    .findFirst()
                    .orElse(null);
            if (account == null) {
                log.warn("No active account found for provider account id {}; skipping transaction {}",
                        pt.accountId(), pt.id());
                continue;
            }
            UUID categoryId = categorizer.categorize(
                    pt.payee(), pt.description(), pt.amount(), account.id(), ruleList);
            Transaction tx = new Transaction(
                    UUID.randomUUID(),
                    enrollment.householdId(),
                    account.id(),
                    pt.id(),
                    pt.amount(),
                    pt.currency() != null ? pt.currency() : account.currency(),
                    pt.description(),
                    pt.payee(),
                    pt.memo(),
                    pt.postedDate(),
                    pt.transactedAt(),
                    pt.pending(),
                    false,
                    null,
                    categoryId,
                    false,
                    now,
                    now);
            boolean isNew = transactions.upsert(tx);
            if (isNew) newCount++;
            else updatedCount++;
        }

        // Advance last_synced_at only when it is safe to do so: either this is an
        // incremental sync (last_synced_at was already set, so the 90-day window is
        // already gone) or we actually received transactions on this initial sync.
        // Keeping it null lets the next sync retry the full INITIAL_LOOKBACK_DAYS
        // window, which is important when SimpleFin finishes backfilling after the
        // first connection.
        boolean gotTransactions = (newCount + updatedCount) > 0;
        Instant nextLastSyncedAt = (!isInitialSync || gotTransactions) ? now : null;

        // Update account balances (and last_synced_at when appropriate).
        for (BankDataProvider.ProviderAccount pa : result.accounts()) {
            activeAccounts.stream()
                    .filter(a -> a.tellerId().equals(pa.id()))
                    .findFirst()
                    .ifPresent(a -> accounts.save(new BankAccount(
                            a.id(), a.householdId(), a.enrollmentId(), a.tellerId(),
                            pa.institution(), pa.name(), a.type(), a.subtype(),
                            a.lastFour(), a.currency(),
                            pa.balanceAvailable(), pa.balanceLedger(),
                            nextLastSyncedAt, a.status(), a.createdAt(), a.removedAt())));
        }

        return new SyncStats(newCount, updatedCount, now);
    }
}
