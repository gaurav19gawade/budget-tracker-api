package com.budgettracker.application;

import com.budgettracker.application.identity.HouseholdContext;
import com.budgettracker.application.port.BankAccountRepository;
import com.budgettracker.application.port.BankDataProvider;
import com.budgettracker.application.port.EnrollmentRepository;
import com.budgettracker.domain.BankAccount;
import com.budgettracker.domain.TellerEnrollment;
import com.budgettracker.domain.error.ConflictException;
import com.budgettracker.domain.error.NotFoundException;
import com.budgettracker.infrastructure.security.TokenEncryptionService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollments;
    private final BankAccountRepository accounts;
    private final BankDataProvider bankData;
    private final TokenEncryptionService encryption;
    private final Clock clock;

    public EnrollmentService(EnrollmentRepository enrollments,
                             BankAccountRepository accounts,
                             BankDataProvider bankData,
                             TokenEncryptionService encryption,
                             Clock clock) {
        this.enrollments = enrollments;
        this.accounts = accounts;
        this.bankData = bankData;
        this.encryption = encryption;
        this.clock = clock;
    }

    /**
     * Stores a Teller enrollment and the accounts that belong to it.
     * Idempotent: if the enrollment already belongs to this household, updates the
     * encrypted token and returns the existing active accounts without re-fetching.
     */
    public List<BankAccount> connect(HouseholdContext ctx, String tellerId, String accessToken) {
        Optional<TellerEnrollment> existing = enrollments.findByTellerId(tellerId);
        if (existing.isPresent()) {
            TellerEnrollment current = existing.get();
            if (!current.householdId().equals(ctx.householdId())) {
                throw new ConflictException("This enrollment belongs to another household.");
            }
            // Idempotent re-connect: refresh encrypted token, return existing accounts.
            enrollments.save(new TellerEnrollment(current.id(), current.householdId(),
                    current.tellerId(), current.institution(),
                    encryption.encrypt(accessToken), current.createdAt()));
            return accounts.findActiveByHouseholdId(ctx.householdId()).stream()
                    .filter(a -> a.enrollmentId().equals(current.id()))
                    .toList();
        }

        // New enrollment: fetch accounts from Teller, then persist everything.
        List<BankDataProvider.ProviderAccount> providerAccounts = bankData.fetchAccounts(accessToken);
        String institution = providerAccounts.isEmpty() ? "Unknown"
                : providerAccounts.get(0).institution();

        Instant now = clock.instant();
        TellerEnrollment enrollment = enrollments.save(new TellerEnrollment(
                UUID.randomUUID(), ctx.householdId(), tellerId,
                institution, encryption.encrypt(accessToken), now));

        List<BankAccount> result = new ArrayList<>();
        for (BankDataProvider.ProviderAccount pa : providerAccounts) {
            BankDataProvider.ProviderBalance balance = fetchBalanceSafely(accessToken, pa.id());
            result.add(accounts.save(new BankAccount(
                    UUID.randomUUID(), ctx.householdId(), enrollment.id(),
                    pa.id(), pa.institution(), pa.name(), pa.type(), pa.subtype(),
                    pa.lastFour(), pa.currency() != null ? pa.currency() : "USD",
                    balance.available(), balance.ledger(), now,
                    "active", now, null)));
        }
        return result;
    }

    /**
     * Soft-deletes the account. Deletes the parent enrollment when its last
     * active account is removed (revokes the Teller token at the enrollment level).
     */
    public void disconnect(HouseholdContext ctx, UUID accountId) {
        BankAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found."));
        if (!account.householdId().equals(ctx.householdId())) {
            throw new NotFoundException("Account not found.");
        }
        if ("removed".equals(account.status())) {
            throw new ConflictException("Account is already removed.");
        }

        Instant now = clock.instant();
        accounts.markRemoved(accountId, now);

        boolean lastActive = accounts.findByEnrollmentId(account.enrollmentId()).stream()
                .filter(a -> !"removed".equals(a.status()))
                .filter(a -> !a.id().equals(accountId))
                .findAny()
                .isEmpty();
        if (lastActive) {
            enrollments.delete(account.enrollmentId());
        }
    }

    private BankDataProvider.ProviderBalance fetchBalanceSafely(String accessToken, String accountId) {
        try {
            return bankData.fetchBalance(accessToken, accountId);
        } catch (Exception e) {
            log.warn("Could not fetch balance for account {}: {}", accountId, e.getMessage());
            return new BankDataProvider.ProviderBalance(null, null);
        }
    }
}
