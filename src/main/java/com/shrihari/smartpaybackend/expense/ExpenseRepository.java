package com.shrihari.smartpaybackend.expense;


import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroup_Id(Long groupId);

    List<Expense> findByGroup_IdAndPaidBy_Id(Long groupId, Long userId);

    List<Expense> findByPaidBy_Id(Long userId);
    List<Expense> findByPaidBy_IdAndIsCancelledFalse(Long userId);
    List<Expense> findByGroup_IdAndIsCancelledFalse(Long groupId);

    Page<Expense> findByGroup_IdAndIsCancelledFalse(Long groupId, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId " +
            "AND e.isCancelled = false " +
            "AND (:category IS NULL OR e.category = :category) " +
            "AND (:from IS NULL OR e.createdAt >= :from) " +
            "AND (:to IS NULL OR e.createdAt <= :to)")
    Page<Expense> findWithFilters(
            @Param("groupId") Long groupId,
            @Param("category") String category,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId " +
            "AND e.isCancelled = false " +
            "AND (LOWER(e.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(e.category) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Expense> searchExpenses(
            @Param("groupId") Long groupId,
            @Param("query") String query
    );
}
