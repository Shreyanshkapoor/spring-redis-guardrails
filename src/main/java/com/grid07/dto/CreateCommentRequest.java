package com.grid07.dto;

import com.grid07.entity.AuthorType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotNull(message = "authorId is required")
    private Long authorId;

    @NotNull(message = "authorType is required (USER or BOT)")
    private AuthorType authorType;

    @NotBlank(message = "content must not be blank")
    private String content;


    @NotNull(message = "depthLevel is required")
    @Min(value = 0, message = "depthLevel must be >= 0")
    private Integer depthLevel;


    private Long targetUserId;
}
