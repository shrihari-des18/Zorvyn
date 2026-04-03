package com.shrihari.smartpaybackend.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByPaymentReference(String paymentReference);
    List<Settlement> findByGroup_Id(Long groupId);
    List<Settlement> findByToUser_IdAndStatus(Long toUserId, SettlementStatus status);
}
