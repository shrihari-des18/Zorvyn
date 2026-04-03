package com.shrihari.smartpaybackend.personalexpense;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface PersonalExpenseRepository extends JpaRepository<PersonalExpense, Long> {

    List<PersonalExpense> findByUser_IdOrderByExpenseDateDesc(Long userId);

    List<PersonalExpense> findByUser_IdAndExpenseDateBetween(
            Long userId,
            LocalDateTime from,
            LocalDateTime to
    );
    List<PersonalExpense> findByUserId(Long userId);

    Page<PersonalExpense> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT e FROM PersonalExpense e WHERE e.user.id = :userId " +
            "AND (:category IS NULL OR e.category = :category) " +
            "AND (:from IS NULL OR e.expenseDate >= :from) " +
            "AND (:to IS NULL OR e.expenseDate <= :to)")
    Page<PersonalExpense> findWithFilters(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}