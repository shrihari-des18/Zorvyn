package com.shrihari.smartpaybackend.friend;

import com.shrihari.smartpaybackend.user.User;
import com.shrihari.smartpaybackend.user.UserRepository;
import com.shrihari.smartpaybackend.exception.ApiException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public void sendFriendRequest(FriendRequestDto request) {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User requester = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        User receiver;
        if (request.getIdentifier() != null && !request.getIdentifier().isBlank()) {
            String id = request.getIdentifier();
            receiver = userRepository
                    .findByEmailOrPhoneNumber(id, id)
                    .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));
        } else if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            receiver = userRepository
                    .findByPhoneNumber(request.getPhoneNumber())
                    .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));
        } else {
            throw new ApiException("Identifier or phone number is required", HttpStatus.FORBIDDEN);
        }

        if (requester.getId().equals(receiver.getId())) {
            throw new ApiException("Cannot send friend request to yourself", HttpStatus.FORBIDDEN);
        }

        // Check existing relationship in either direction
        boolean alreadyExists =
                friendshipRepository
                        .findByRequesterAndReceiver(requester, receiver)
                        .isPresent()
                        ||
                        friendshipRepository
                                .findByRequesterAndReceiver(receiver, requester)
                                .isPresent();

        if (alreadyExists) {
            throw new ApiException("Friend request already exists or already friends", HttpStatus.FORBIDDEN);
        }

        Friendship friendship = Friendship.builder()
                .requester(requester)
                .receiver(receiver)
                .status(FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        friendshipRepository.save(friendship);
    }
    @Transactional
    public void acceptRequest(Long requestId) {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User currentUser = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        Friendship friendship = friendshipRepository
                .findById(requestId)
                .orElseThrow(() -> new ApiException("Request not found", HttpStatus.FORBIDDEN));

        if (!friendship.getReceiver().getId().equals(currentUser.getId())) {
            throw new ApiException("Only receiver can accept request", HttpStatus.FORBIDDEN);
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ApiException("Request already processed", HttpStatus.FORBIDDEN);
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }
    @Transactional
    public void rejectRequest(Long requestId) {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User currentUser = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.FORBIDDEN));

        Friendship friendship = friendshipRepository
                .findById(requestId)
                .orElseThrow(() -> new ApiException("Request not found", HttpStatus.FORBIDDEN));

        if (!friendship.getReceiver().getId().equals(currentUser.getId())) {
            throw new ApiException("Only receiver can reject request", HttpStatus.FORBIDDEN);
        }

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new ApiException("Request already processed", HttpStatus.FORBIDDEN);
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }
    public List<PendingRequestResponse> getPendingRequests() {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User currentUser = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow();

        return friendshipRepository
                .findByReceiverAndStatus(currentUser, FriendshipStatus.PENDING)
                .stream()
                .map(f -> PendingRequestResponse.builder()
                        .requestId(f.getId())
                        .requesterId(f.getRequester().getId())
                        .requesterName(f.getRequester().getFullName())
                        .requesterPhone(f.getRequester().getPhoneNumber())
                        .createdAt(f.getCreatedAt())
                        .build())
                .toList();
    }
    public List<FriendResponse> getFriends() {

        String identifier = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User currentUser = userRepository
                .findByEmailOrPhoneNumber(identifier, identifier)
                .orElseThrow();

        return friendshipRepository
                .findByRequesterOrReceiverAndStatus(
                        currentUser,
                        currentUser,
                        FriendshipStatus.ACCEPTED
                )
                .stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(currentUser.getId())
                            ? f.getReceiver()
                            : f.getRequester();
                    return FriendResponse.builder()
                            .id(friend.getId())
                            .fullName(friend.getFullName())
                            .email(friend.getEmail())
                            .phoneNumber(friend.getPhoneNumber())
                            .build();
                })
                .toList();
    }



}
