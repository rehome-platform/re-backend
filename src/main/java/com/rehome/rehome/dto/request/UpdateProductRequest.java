package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductRequest(@NotBlank @Size(max = 255) String name,
        @NotNull List<@NotBlank @Size(max = 2048) String> photoUrls) {}
