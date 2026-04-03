package com.shrihari.smartpaybackend.group;

import com.shrihari.smartpaybackend.exception.ApiException;
import com.shrihari.smartpaybackend.expense.dto.ExpenseListResponse;
import com.shrihari.smartpaybackend.group.dto.GroupMemberResponse;
import com.shrihari.smartpaybackend.group.dto.GroupResponse;
import com.shrihari.smartpaybackend.ledger.dto.GroupBalanceResponse;
import com.shrihari.smartpaybackend.ledger.dto.TransactionResponse;
import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.shrihari.smartpaybackend.expense.ExpenseService;
import com.shrihari.smartpaybackend.friend.FriendshipRepository;
import com.shrihari.smartpaybackend.friend.FriendshipStatus;
import com.shrihari.smartpaybackend.group.dto.GroupDetailResponse;
import com.shrihari.smartpaybackend.ledger.LedgerService;
import com.shrihari.smartpaybackend.user.AuthorizationService;
import com.shrihari.smartpaybackend.user.UserProfileResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final ExpenseService expenseService;
    private final FriendshipRepository friendshipRepository;
    private final AuthorizationService authorizationService;

    public GroupResponse createGroup(String name, String description) {

        String identifier = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User creator = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        Group group = Group.builder()
                .name(name)
                .description(description)
                .createdBy(creator)
                .groupCode(generateUniqueGroupCode())
                .createdAt(LocalDateTime.now())
                .build();

        groupRepository.save(group);

        GroupMember adminMembership = GroupMember.builder()
                .group(group)
                .user(creator)
                .role(GroupRole.ADMIN)
                .joinedAt(java.time.LocalDateTime.now())
                .build();

        groupMemberRepository.save(adminMembership);

        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .groupCode(group.getGroupCode())  // ADD THIS
                .createdByName(creator.getFullName())
                .createdByIdentifier(
                        creator.getEmail() != null
                                ? creator.getEmail()
                                : creator.getPhoneNumber()
                )
                .build();
    }

    public void addMember(Long groupId, String identifier) {

        String currentIdentifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User currentUser = userRepository
                .findByEmailOrPhoneNumber(currentIdentifier, currentIdentifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException("Group not found", HttpStatus.FORBIDDEN));

        GroupMember adminCheck = groupMemberRepository
                .findByGroup_IdAndUser_Id(groupId, currentUser.getId())
                .orElseThrow(() -> new ApiException("Not a group member", HttpStatus.FORBIDDEN));

        if (adminCheck.getRole() != GroupRole.ADMIN) {
            throw new ApiException("Only admin can add members", HttpStatus.FORBIDDEN);
        }

        User newUser = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("Target user not found", HttpStatus.FORBIDDEN));

        if (groupMemberRepository
                .findByGroup_IdAndUser_Id(groupId, newUser.getId())
                .isPresent()) {
            throw new ApiException("User already in group", HttpStatus.FORBIDDEN);
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(newUser)
                .role(GroupRole.MEMBER)
                .joinedAt(java.time.LocalDateTime.now())
                .build();

        groupMemberRepository.save(member);
    }
    public List<GroupResponse> getMyGroups() {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        var memberships =
                groupMemberRepository.findAllWithGroupAndCreator(user.getId());

        return memberships.stream()
                .map(member -> {

                    Group group = member.getGroup();

                    boolean isAdmin =
                            member.getRole() == GroupRole.ADMIN;

                    return GroupResponse.builder()
                            .id(group.getId())
                            .name(group.getName())
                            .description(group.getDescription())
                            .groupCode(isAdmin ? group.getGroupCode() : null)
                            .createdByName(group.getCreatedBy().getFullName())
                            .createdByIdentifier(
                                    group.getCreatedBy().getEmail() != null
                                            ? group.getCreatedBy().getEmail()
                                            : group.getCreatedBy().getPhoneNumber()
                            )
                            .build();
                })
                .toList();
    }


    public java.util.List<GroupMemberResponse> getGroupMembers(Long groupId) {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        groupMemberRepository
                .findByGroup_IdAndUser_Id(groupId, user.getId())
                .orElseThrow(() -> new ApiException("Not a member of this group", HttpStatus.FORBIDDEN));

        var members = groupMemberRepository.findByGroupId(groupId);

        return members.stream()
                .map(member -> GroupMemberResponse.builder()
                        .userId(member.getUser().getId())
                        .fullName(member.getUser().getFullName())
                        .identifier(
                                member.getUser().getEmail() != null
                                        ? member.getUser().getEmail()
                                        : member.getUser().getPhoneNumber()
                        )
                        .role(member.getRole().name())
                        .build()
                )
                .toList();
    }

    private String generateUniqueGroupCode() {

        String code;
        do {
            code = java.util.UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase();
        } while (groupRepository.existsByGroupCode(code));

        return code;
    }

    public void joinGroup(String code) {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        Group group = groupRepository.findByGroupCode(code)
                .orElseThrow(() -> new ApiException("Invalid group code", HttpStatus.FORBIDDEN));

        if (groupMemberRepository
                .findByGroup_IdAndUser_Id(group.getId(), user.getId())
                .isPresent()) {
            throw new ApiException("Already a member", HttpStatus.FORBIDDEN);
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        groupMemberRepository.save(member);
    }

    @Cacheable(value = "groupDetail", key = "#groupId + '-' + #root.target.getCurrentUserIdForCache()")
    public GroupDetailResponse getGroupDetail(Long groupId) {

        User currentUser = authorizationService.getCurrentUser();
        Long userId = currentUser.getId();

        // Validate membership + get member role first (sequential — needed before parallel)
        GroupMember currentMember = groupMemberRepository
                .findByGroup_IdAndUser_Id(groupId, userId)
                .orElseThrow(() -> new ApiException("Not a member of this group", HttpStatus.FORBIDDEN));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException("Group not found", HttpStatus.FORBIDDEN));

        boolean isAdmin = currentMember.getRole() == GroupRole.ADMIN;

        // --- Fire all heavy queries in parallel ---
        CompletableFuture<List<GroupMember>> membersFuture = CompletableFuture
                .supplyAsync(() -> groupMemberRepository.findByGroupId(groupId));

        CompletableFuture<List<TransactionResponse>> transactionsFuture = CompletableFuture
                .supplyAsync(() -> ledgerService.getGroupTransactions(groupId, userId));

        CompletableFuture<List<GroupBalanceResponse>> balancesFuture = CompletableFuture
                .supplyAsync(() -> ledgerService.getGroupBalances(groupId, userId));

        CompletableFuture<BigDecimal> myBalanceFuture = CompletableFuture
                .supplyAsync(() -> ledgerService.getUserBalance(groupId, userId));

        CompletableFuture<List<ExpenseListResponse>> expensesFuture = CompletableFuture
                .supplyAsync(() -> expenseService.getGroupExpenses(groupId, userId));


        // --- Wait for all ---
        CompletableFuture.allOf(
                membersFuture, transactionsFuture,
                balancesFuture, expensesFuture, myBalanceFuture
        ).join();

        // --- Unwrap ---
        List<GroupMember> members = membersFuture.join();
        List<TransactionResponse> transactions = transactionsFuture.join();
        List<GroupBalanceResponse> balances = balancesFuture.join();
        List<ExpenseListResponse> expenses = expensesFuture.join();
        BigDecimal myBalance = myBalanceFuture.join();

        // --- Build group response ---
        GroupResponse groupResponse = GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .groupCode(isAdmin ? group.getGroupCode() : null)
                .createdByName(group.getCreatedBy().getFullName())
                .createdByIdentifier(
                        group.getCreatedBy().getEmail() != null
                                ? group.getCreatedBy().getEmail()
                                : group.getCreatedBy().getPhoneNumber()
                )
                .build();

        // --- Build current user profile ---
        UserProfileResponse currentUserProfile = UserProfileResponse.builder()
                .id(userId)
                .fullName(currentUser.getFullName())
                .email(currentUser.getEmail())
                .phoneNumber(currentUser.getPhoneNumber())
                .role(currentUser.getRole().name())
                .build();

        // --- Build members with isFriend (parallel per member) ---
        List<GroupMemberResponse> memberResponses = members.stream()
                .map(member -> {
                    User memberUser = member.getUser();

                    boolean isFriend = memberUser.getId().equals(userId)
                            ? false
                            : friendshipRepository
                            .findByRequesterAndReceiver(currentUser, memberUser)
                            .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                            .orElseGet(() ->
                                    friendshipRepository
                                            .findByRequesterAndReceiver(memberUser, currentUser)
                                            .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                                            .orElse(false)
                            );

                    return GroupMemberResponse.builder()
                            .userId(memberUser.getId())
                            .fullName(memberUser.getFullName())
                            .identifier(
                                    memberUser.getEmail() != null
                                            ? memberUser.getEmail()
                                            : memberUser.getPhoneNumber()
                            )
                            .role(member.getRole().name())
                            .isFriend(isFriend)
                            .build();
                })
                .toList();

        return GroupDetailResponse.builder()
                .group(groupResponse)
                .currentUser(currentUserProfile)
                .members(memberResponses)
                .transactions(transactions)
                .balances(balances)
                .myBalance(myBalance)
                .expenses(expenses)
                .build();
    }

    public Long getCurrentUserIdForCache() {
        return authorizationService.getCurrentUser().getId();
    }
}
