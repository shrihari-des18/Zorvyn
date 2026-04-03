package com.shrihari.smartpaybackend.group;

import com.shrihari.smartpaybackend.common.ApiResponse;
import com.shrihari.smartpaybackend.group.dto.GroupResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group management")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ApiResponse<?> createGroup(@RequestBody CreateGroupRequest request) {

        GroupResponse group = groupService.createGroup(
                request.getName(),
                request.getDescription()
        );

        return new ApiResponse<>(true, "Group created successfully", group);
    }

    @PostMapping("/{groupId}/members")
    public ApiResponse<?> addMember(
            @PathVariable Long groupId,
            @RequestParam String identifier) {

        groupService.addMember(groupId, identifier);

        return new ApiResponse<>(true, "Member added successfully", null);
    }

    @GetMapping("/my")
    public ApiResponse<?> myGroups() {

        return new ApiResponse<>(
                true,
                "User groups fetched",
                groupService.getMyGroups()
        );
    }

    @GetMapping("/{groupId}/members")
    public ApiResponse<?> getMembers(@PathVariable Long groupId) {

        return new ApiResponse<>(
                true,
                "Group members fetched",
                groupService.getGroupMembers(groupId)
        );
    }

    @PostMapping("/join")
    public ApiResponse<?> joinGroup(@RequestParam String code) {

        groupService.joinGroup(code);

        return new ApiResponse<>(true, "Joined group successfully", null);
    }

    @GetMapping("/{groupId}/detail")
    public ApiResponse<?> getGroupDetail(@PathVariable Long groupId) {

        return new ApiResponse<>(
                true,
                "OK",
                groupService.getGroupDetail(groupId)
        );
    }


    @Data
    static class CreateGroupRequest {
        private String name;
        private String description;
    }
}
