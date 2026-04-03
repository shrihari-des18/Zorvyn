package com.shrihari.smartpaybackend.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.ledger.LedgerRepository;
import com.shrihari.smartpaybackend.report.ScamReportRepository;
import com.shrihari.smartpaybackend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskService {

    private final LedgerRepository ledgerRepository;
    private final ScamReportRepository scamReportRepository;

    @Value(" https://akenzz-smartpay.hf.space/evaluate-risk")
    private String riskApiUrl;

    public RiskCheckResponse evaluate(
            User sender,
            User receiver,
            BigDecimal amount) {

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime last24h = now.minusHours(24);
            LocalDateTime last7d = now.minusDays(7);

            // Compute all fields
            int hourOfDay = now.getHour();

            int isWeekend = (now.getDayOfWeek() == DayOfWeek.SATURDAY
                    || now.getDayOfWeek() == DayOfWeek.SUNDAY) ? 1 : 0;

            int receiverAccountAgeDays = receiver.getCreatedAt() != null
                    ? (int) ChronoUnit.DAYS.between(receiver.getCreatedAt(), now)
                    : 0;

            int receiverReportCount = scamReportRepository
                    .countByReported_Id(receiver.getId());

            int receiverTxCount24h = ledgerRepository
                    .countReceiverTransactions24h(receiver.getId(), last24h);

            int receiverUniqueSenders24h = ledgerRepository
                    .countUniqueSenders24h(receiver.getId(), last24h);

            int previousConnectionsCount = ledgerRepository
                    .countPreviousConnections(sender.getId(), receiver.getId());

            BigDecimal avgTransactionAmount7d = ledgerRepository
                    .avgTransactionAmount7d(sender.getId(), last7d);

            RiskCheckRequest request = RiskCheckRequest.builder()
                    .amount(amount)
                    .hourOfDay(hourOfDay)
                    .isWeekend(isWeekend)
                    .receiverAccountAgeDays(receiverAccountAgeDays)
                    .receiverReportCount(receiverReportCount)
                    .receiverTxCount24h(receiverTxCount24h)
                    .receiverUniqueSenders24h(receiverUniqueSenders24h)
                    .previousConnectionsCount(previousConnectionsCount)
                    .avgTransactionAmount7d(avgTransactionAmount7d)
                    .build();

            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule());

            String requestBody = mapper.writeValueAsString(request);

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(riskApiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                log.warn("Risk API returned status: {}. Allowing transaction.",
                        response.statusCode());
                return safeFallback();
            }

            return mapper.readValue(response.body(), RiskCheckResponse.class);

        } catch (Exception e) {
            log.error("Risk API call failed: {}. Allowing transaction.", e.getMessage());
            return safeFallback();
        }
    }

    // ─── Change behavior here in future ───────────────────────────────
    public void evaluateAndBlock(User sender, User receiver, BigDecimal amount) {

        RiskCheckResponse risk = evaluate(sender, receiver, amount);

        // Currently: block only if is_blocked = true from AI
        // To block on HIGH too → add: || "HIGH".equals(risk.getRiskLevel())
        // To never block → remove the if block entirely
        if (risk.isBlocked()) {
            throw new RiskBlockedException(
                    risk.getMessage(),
                    risk.getRiskLevel(),
                    risk.getFraudRiskScore(),
                    risk.getRiskReasons()
            );
        }
    }
    // ──────────────────────────────────────────────────────────────────

    private RiskCheckResponse safeFallback() {
        RiskCheckResponse safe = new RiskCheckResponse();
        safe.setFraudRiskScore(0.0);
        safe.setRiskLevel("SAFE");
        safe.setBlocked(false);
        safe.setMessage("Risk check unavailable. Transaction allowed.");
        safe.setRiskReasons(java.util.List.of());
        return safe;
    }
}