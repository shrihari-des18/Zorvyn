package com.shrihari.smartpaybackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeepAliveScheduler {

    private final JdbcTemplate jdbcTemplate;

    // Runs every 4 minutes
    @Scheduled(fixedRate = 240000)
    public void keepAlive() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.debug("Neon keep-alive ping successful");
        } catch (Exception e) {
            log.warn("Neon keep-alive ping failed: {}", e.getMessage());
        }
    }
}