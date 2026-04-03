package com.shrihari.smartpaybackend.group.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupResponse {

    private Long id;
    private String name;
    private String description;
    private String groupCode;
    private String createdByName;
    private String createdByIdentifier;
}
