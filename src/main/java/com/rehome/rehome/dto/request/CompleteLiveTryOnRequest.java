package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CompleteLiveTryOnRequest {

    @NotNull(message = "status phải là COMPLETED hoặc FAILED.")
    private LiveTryOnStatus status;

    private Integer actualDurationSeconds;
}
