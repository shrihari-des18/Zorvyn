package com.shrihari.smartpaybackend.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByGroup_Id(Long groupId);
    List<LedgerEntry> findByReferenceTypeAndReferenceId(
            ReferenceType referenceType,
            Long referenceId
    );

    @Query("""
        SELECT COALESCE(SUM(l.amount), 0)
        FROM LedgerEntry l
        WHERE l.group.id = :groupId
        AND l.toUser.id = :userId
    """)
    BigDecimal sumIncoming(Long groupId, Long userId);

    @Query("""
        SELECT COALESCE(SUM(l.amount), 0)
        FROM LedgerEntry l
        WHERE l.group.id = :groupId
        AND l.fromUser.id = :userId
    """)
    BigDecimal sumOutgoing(Long groupId, Long userId);

    List<LedgerEntry> findByFromUser_IdOrToUser_Id(
            Long fromUserId,
            Long toUserId
    );
    @Query("""
SELECT le.fromUser.id, le.fromUser.fullName,
       SUM(CASE WHEN le.referenceType = 'EXPENSE' THEN le.amount
                WHEN le.referenceType = 'SETTLEMENT' THEN -le.amount
                ELSE 0 END)
FROM LedgerEntry le
WHERE le.toUser.id = :userId
AND le.fromUser.id != le.toUser.id
GROUP BY le.fromUser.id, le.fromUser.fullName
HAVING SUM(CASE WHEN le.referenceType = 'EXPENSE' THEN le.amount
                WHEN le.referenceType = 'SETTLEMENT' THEN -le.amount
                ELSE 0 END) > 0
""")
    List<Object[]> findWhoOwesMe(@Param("userId") Long userId);

    @Query("""
SELECT le.toUser.id, le.toUser.fullName,
       SUM(CASE WHEN le.referenceType = 'EXPENSE' THEN le.amount
                WHEN le.referenceType = 'SETTLEMENT' THEN -le.amount
                ELSE 0 END)
FROM LedgerEntry le
WHERE le.fromUser.id = :userId
AND le.fromUser.id != le.toUser.id
GROUP BY le.toUser.id, le.toUser.fullName
HAVING SUM(CASE WHEN le.referenceType = 'EXPENSE' THEN le.amount
                WHEN le.referenceType = 'SETTLEMENT' THEN -le.amount
                ELSE 0 END) > 0
""")
    List<Object[]> findWhomIOwe(@Param("userId") Long userId);

    @Query("""
    SELECT COALESCE(SUM(le.amount), 0)
    FROM LedgerEntry le
    WHERE le.toUser.id = :userId
""")
    BigDecimal getTotalIncoming(Long userId);


    @Query("""
    SELECT COALESCE(SUM(le.amount), 0)
    FROM LedgerEntry le
    WHERE le.fromUser.id = :userId
""")
    BigDecimal getTotalOutgoing(Long userId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
            "WHERE e.fromUser.id = :userId " +
            "AND e.createdAt >= :from AND e.createdAt < :to")
    BigDecimal sumOutgoingBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e " +
            "WHERE e.toUser.id = :userId " +
            "AND e.createdAt >= :from AND e.createdAt < :to " +
            "AND e.fromUser.id != e.toUser.id")
    BigDecimal sumIncomingBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(e) FROM LedgerEntry e " +
            "WHERE e.toUser.id = :userId " +
            "AND e.createdAt >= :from")
    int countReceiverTransactions24h(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(DISTINCT e.fromUser.id) FROM LedgerEntry e " +
            "WHERE e.toUser.id = :userId " +
            "AND e.createdAt >= :from")
    int countUniqueSenders24h(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(e) FROM LedgerEntry e " +
            "WHERE (e.fromUser.id = :senderId AND e.toUser.id = :receiverId) " +
            "OR (e.fromUser.id = :receiverId AND e.toUser.id = :senderId)")
    int countPreviousConnections(
            @Param("senderId") Long senderId,
            @Param("receiverId") Long receiverId);

    @Query("SELECT COALESCE(AVG(e.amount), 0) FROM LedgerEntry e " +
            "WHERE e.fromUser.id = :userId " +
            "AND e.createdAt >= :from")
    BigDecimal avgTransactionAmount7d(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from);
}
