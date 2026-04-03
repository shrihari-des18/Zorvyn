package com.shrihari.smartpaybackend.report;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScamReportRepository extends JpaRepository<ScamReport, Long> {

    int countByReported_Id(Long reportedId);

    boolean existsByReporter_IdAndReported_Id(Long reporterId, Long reportedId);
}