package com.shrihari.smartpaybackend.friend;

import com.shrihari.smartpaybackend.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
@Tag(name = "Friends", description = "Friend management")
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    public ApiResponse<?> sendRequest(
            @RequestBody FriendRequestDto request) {

        friendService.sendFriendRequest(request);

        return new ApiResponse<>(
                true,
                "Friend request sent",
                null
        );
    }
    @PostMapping("/{id}/accept")
    public ApiResponse<?> accept(@PathVariable Long id) {

        friendService.acceptRequest(id);

        return new ApiResponse<>(
                true,
                "Friend request accepted",
                null
        );
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<?> reject(@PathVariable Long id) {

        friendService.rejectRequest(id);

        return new ApiResponse<>(
                true,
                "Friend request rejected",
                null
        );
    }
    @GetMapping("/pending")
    public ApiResponse<?> getPending() {
        return new ApiResponse<>(true, "Pending requests", friendService.getPendingRequests());
    }

    @GetMapping
    public ApiResponse<?> getFriends() {
        return new ApiResponse<>(true, "Friends list", friendService.getFriends());
    }

}
