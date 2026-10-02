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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EnrollmentService {

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
     * Claims a SimpleFin setup token, fetches accounts, and persists them.
     * Idempotent: if the same access credential (identified by its SHA-256 fingerprint)
     * is already stored for this household, the encrypted credential is refreshed and
     * the existing active accounts are returned without re-fetching.
     */
    public List<BankAccount> connect(HouseholdContext ctx, String setupToken) {
        String accessCredential = bankData.claim(setupToken);
        String connectionId = stableId(accessCredential);

        Optional<TellerEnrollment> existing = enrollments.findByTellerId(connectionId);
        if (existing.isPresent()) {
            TellerEnrollment current = existing.get();
            if (!current.householdId().equals(ctx.householdId())) {
                throw new ConflictException("This connection belongs to another household.");
            }
            enrollments.save(new TellerEnrollment(current.id(), current.householdId(),
                    current.tellerId(), current.institution(),
                    encryption.encrypt(accessCredential), current.createdAt()));
            return accounts.findActiveByHouseholdId(ctx.householdId()).stream()
                    .filter(a -> a.enrollmentId().equals(current.id()))
                    .toList();
        }

        List<BankDataProvider.ProviderAccount> providerAccounts = bankData.fetchAccounts(accessCredential);
        String institution = providerAccounts.isEmpty() ? "Unknown"
                : providerAccounts.get(0).institution();

        Instant now = clock.instant();
        TellerEnrollment enrollment = enrollments.save(new TellerEnrollment(
                UUID.randomUUID(), ctx.householdId(), connectionId,
                institution, encryption.encrypt(accessCredential), now));

        List<BankAccount> result = new ArrayList<>();
        for (BankDataProvider.ProviderAccount pa : providerAccounts) {
            result.add(accounts.save(new BankAccount(
                    UUID.randomUUID(), ctx.householdId(), enrollment.id(),
                    pa.id(), pa.institution(), pa.name(), pa.type(), pa.subtype(),
                    pa.lastFour(), pa.currency() != null ? pa.currency() : "USD",
                    pa.balanceAvailable(), pa.balanceLedger(), now,
                    "active", now, null)));
        }
        return result;
    }

    /**
     * Soft-deletes the account. Deletes the parent connection when its last
     * active account is removed.
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

    /** SHA-256 fingerprint of the access credential — used as the unique connection key. */
    private static String stableId(String accessCredential) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(accessCredential.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
