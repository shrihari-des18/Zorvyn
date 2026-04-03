package com.shrihari.smartpaybackend.expense;

import com.shrihari.smartpaybackend.group.Group;
import com.shrihari.smartpaybackend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne
    @JoinColumn(name = "paid_by")
    private User paidBy;

    private String description;
    private String category;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private boolean isCancelled = false;

    private LocalDateTime createdAt;
}

