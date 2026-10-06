package com.budgettracker.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByHouseholdIdAndProviderId(UUID householdId, String providerId);

    List<TransactionEntity> findByHouseholdIdAndPostedDateBetweenOrderByPostedDateDescCreatedAtDesc(
            UUID householdId, LocalDate from, LocalDate to);

    List<TransactionEntity> findByHouseholdIdOrderByPostedDateDescCreatedAtDesc(UUID householdId);

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.categoryId = :categoryId, t.categoryOverride = :override WHERE t.id = :id")
    void updateCategory(@Param("id") UUID id,
                        @Param("categoryId") UUID categoryId,
                        @Param("override") boolean override);

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.categoryId = :toId WHERE t.categoryId = :fromId")
    void reassignCategory(@Param("fromId") UUID fromId, @Param("toId") UUID toId);

    List<TransactionEntity> findByHouseholdIdAndPendingFalseAndIsInternalTransferFalse(UUID householdId);

    @Modifying
    @Query("UPDATE TransactionEntity t SET t.isInternalTransfer = true, t.transferGroupId = :groupId " +
           "WHERE t.id = :id1 OR t.id = :id2")
    void markAsTransferPair(@Param("id1") UUID id1, @Param("id2") UUID id2, @Param("groupId") UUID groupId);

    @Query("SELECT t.categoryId, SUM(t.amount) FROM TransactionEntity t " +
           "WHERE t.householdId = :hid " +
           "AND t.postedDate >= :from AND t.postedDate <= :to " +
           "AND t.isInternalTransfer = false " +
           "AND t.pending = false " +
           "GROUP BY t.categoryId")
    List<Object[]> sumByCategoryForPeriod(@Param("hid") UUID householdId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);
}
