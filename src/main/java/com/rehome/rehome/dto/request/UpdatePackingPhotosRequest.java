package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdatePackingPhotosRequest(
        @NotNull @Size(max = 5) List<@jakarta.validation.constraints.NotBlank
                @Size(max = 2048) @org.hibernate.validator.constraints.URL(protocol = "https") String> photoUrls) {
}
