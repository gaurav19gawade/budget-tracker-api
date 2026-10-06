package com.budgettracker.application;

import com.budgettracker.application.port.TransactionRepository;
import com.budgettracker.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects internal transfer pairs across household accounts and marks both sides so
 * they are excluded from budget and analytics calculations.
 *
 * <p>Matching criteria (all must hold):
 * <ul>
 *   <li>Opposite sign, same absolute amount
 *   <li>Different account within the same household
 *   <li>Posted date within ±3 days of each other
 *   <li>Exactly one candidate (ambiguous pairs are skipped for manual review)
 * </ul>
 */
@Service
public class TransferMatcher {

    private static final int WINDOW_DAYS = 3;

    private final TransactionRepository transactions;

    public TransferMatcher(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /**
     * Scans all unmatched posted transactions for the household and links any transfer pairs found.
     *
     * @return number of pairs linked (each pair counts as 1)
     */
    @Transactional
    public int matchTransfers(UUID householdId) {
        List<Transaction> candidates = transactions.findPostedNonTransferByHouseholdId(householdId);
        Set<UUID> matched = new HashSet<>();
        int pairs = 0;

        for (Transaction tx : candidates) {
            if (matched.contains(tx.id()) || tx.postedDate() == null) continue;

            BigDecimal target = tx.amount().negate();
            LocalDate windowStart = tx.postedDate().minusDays(WINDOW_DAYS);
            LocalDate windowEnd   = tx.postedDate().plusDays(WINDOW_DAYS);

            List<Transaction> counterparts = candidates.stream()
                    .filter(c -> !matched.contains(c.id()))
                    .filter(c -> !c.id().equals(tx.id()))
                    .filter(c -> !c.accountId().equals(tx.accountId()))
                    .filter(c -> c.amount().compareTo(target) == 0)
                    .filter(c -> c.postedDate() != null
                            && !c.postedDate().isBefore(windowStart)
                            && !c.postedDate().isAfter(windowEnd))
                    .toList();

            if (counterparts.size() == 1) {
                Transaction counterpart = counterparts.get(0);
                // Also verify uniqueness from the counterpart's perspective to prevent
                // asymmetric matching (e.g. one -$100 paired against two +$100).
                if (counterpart.postedDate() != null) {
                    BigDecimal reverseTarget = counterpart.amount().negate();
                    LocalDate cStart = counterpart.postedDate().minusDays(WINDOW_DAYS);
                    LocalDate cEnd   = counterpart.postedDate().plusDays(WINDOW_DAYS);
                    long reverseCount = candidates.stream()
                            .filter(c -> !matched.contains(c.id()))
                            .filter(c -> !c.id().equals(counterpart.id()))
                            .filter(c -> !c.accountId().equals(counterpart.accountId()))
                            .filter(c -> c.amount().compareTo(reverseTarget) == 0)
                            .filter(c -> c.postedDate() != null
                                    && !c.postedDate().isBefore(cStart)
                                    && !c.postedDate().isAfter(cEnd))
                            .count();
                    if (reverseCount == 1) {
                        UUID groupId = UUID.randomUUID();
                        transactions.markAsTransferPair(tx.id(), counterpart.id(), groupId);
                        matched.add(tx.id());
                        matched.add(counterpart.id());
                        pairs++;
                    }
                }
            }
            // 0 candidates → normal transaction; >1 → ambiguous, skip for manual review
        }
        return pairs;
    }
}
