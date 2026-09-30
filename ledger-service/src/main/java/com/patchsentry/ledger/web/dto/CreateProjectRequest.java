package com.patchsentry.ledger.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}",
                message = "must look like owner/name") String repository,
        @NotBlank @Size(max = 120) String name) {
}
