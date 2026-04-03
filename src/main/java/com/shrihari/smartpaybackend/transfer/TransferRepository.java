package com.shrihari.smartpaybackend.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByToUser_IdAndStatus(Long toUserId, TransferStatus status);

    List<Transfer> findByFromUser_IdOrToUser_IdOrderByCreatedAtDesc(
            Long fromUserId,
            Long toUserId
    );
}