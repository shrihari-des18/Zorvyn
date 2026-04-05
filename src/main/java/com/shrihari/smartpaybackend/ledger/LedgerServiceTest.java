package com.shrihari.smartpaybackend.ledger;

import com.shrihari.smartpaybackend.expense.ExpenseRepository;
import com.shrihari.smartpaybackend.group.GroupMemberRepository;
import com.shrihari.smartpaybackend.ledger.dto.GroupBalanceResponse;
import com.shrihari.smartpaybackend.ledger.dto.UserDebtSummaryResponse;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LedgerService ledgerService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .fullName("Test User")
                .email("test@example.com")
                .build();
    }

    // --- Balance Tests ---

    @Test
    void netBalance_shouldReturnPositive_whenIncomingExceedsOutgoing() {

        when(ledgerRepository.sumIncoming(1L, 1L))
                .thenReturn(BigDecimal.valueOf(1000));
        when(ledgerRepository.sumOutgoing(1L, 1L))
                .thenReturn(BigDecimal.valueOf(400));

        BigDecimal balance = ledgerService.getUserBalance(1L, 1L);

        assertEquals(BigDecimal.valueOf(600), balance);
    }

    @Test
    void netBalance_shouldReturnNegative_whenOutgoingExceedsIncoming() {

        when(ledgerRepository.sumIncoming(1L, 1L))
                .thenReturn(BigDecimal.valueOf(200));
        when(ledgerRepository.sumOutgoing(1L, 1L))
                .thenReturn(BigDecimal.valueOf(800));

        BigDecimal balance = ledgerService.getUserBalance(1L, 1L);

        assertEquals(BigDecimal.valueOf(-600), balance);
    }

    @Test
    void netBalance_shouldReturnZero_whenIncomingEqualsOutgoing() {

        when(ledgerRepository.sumIncoming(1L, 1L))
                .thenReturn(BigDecimal.valueOf(500));
        when(ledgerRepository.sumOutgoing(1L, 1L))
                .thenReturn(BigDecimal.valueOf(500));

        BigDecimal balance = ledgerService.getUserBalance(1L, 1L);

        assertEquals(BigDecimal.valueOf(0), balance);
    }

    // --- Who Owes Me Tests ---

    @Test
    void getWhoOwesMe_shouldReturnCorrectDebtors() {

        List<Object[]> whoOwesMeRows = new ArrayList<>();
        whoOwesMeRows.add(new Object[]{2L, "Jane Smith", BigDecimal.valueOf(500)});
        when(ledgerRepository.findWhoOwesMe(1L))
                .thenReturn(whoOwesMeRows);

        List<UserDebtSummaryResponse> result = ledgerService.getWhoOwesMe(1L);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getUserId());
        assertEquals("Jane Smith", result.get(0).getUserName());
        assertEquals(BigDecimal.valueOf(500), result.get(0).getAmount());
    }

    @Test
    void getWhoOwesMe_shouldReturnEmptyList_whenNobodyOwes() {

        when(ledgerRepository.findWhoOwesMe(1L))
                .thenReturn(List.of());

        List<UserDebtSummaryResponse> result = ledgerService.getWhoOwesMe(1L);

        assertTrue(result.isEmpty());
    }

    // --- Whom I Owe Tests ---

    @Test
    void getWhomIOwe_shouldReturnCorrectCreditors() {

        List<Object[]> whomIOweRows = new ArrayList<>();
        whomIOweRows.add(new Object[]{3L, "Bob Johnson", BigDecimal.valueOf(800)});
        when(ledgerRepository.findWhomIOwe(1L))
                .thenReturn(whomIOweRows);

        List<UserDebtSummaryResponse> result = ledgerService.getWhomIOwe(1L);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getUserId());
        assertEquals("Bob Johnson", result.get(0).getUserName());
        assertEquals(BigDecimal.valueOf(800), result.get(0).getAmount());
    }

    @Test
    void getWhomIOwe_shouldReturnEmptyList_whenIOwNobody() {

        when(ledgerRepository.findWhomIOwe(1L))
                .thenReturn(List.of());

        List<UserDebtSummaryResponse> result = ledgerService.getWhomIOwe(1L);

        assertTrue(result.isEmpty());
    }

    // --- Group Balance Tests ---

    @Test
    void getGroupBalances_shouldComputeNetCorrectly() {

        com.shrihari.smartpaybackend.group.GroupMember member =
                mock(com.shrihari.smartpaybackend.group.GroupMember.class);

        when(member.getUser()).thenReturn(testUser);
        when(groupMemberRepository.findByGroupId(1L))
                .thenReturn(List.of(member));
        when(ledgerRepository.sumIncoming(1L, 1L))
                .thenReturn(BigDecimal.valueOf(1000));
        when(ledgerRepository.sumOutgoing(1L, 1L))
                .thenReturn(BigDecimal.valueOf(400));

        List<GroupBalanceResponse> balances =
                ledgerService.getGroupBalances(1L, 1L);

        assertEquals(1, balances.size());
        assertEquals(BigDecimal.valueOf(600), balances.get(0).getNetBalance());
        assertEquals("Test User", balances.get(0).getFullName());
    }
}