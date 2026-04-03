package com.shrihari.smartpaybackend.friend;

import com.shrihari.smartpaybackend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository
        extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByRequesterAndReceiver(User requester, User receiver);

    List<Friendship> findByRequesterOrReceiverAndStatus(
            User requester,
            User receiver,
            FriendshipStatus status
    );
    List<Friendship> findByReceiverAndStatus(
            User receiver,
            FriendshipStatus status
    );


}
