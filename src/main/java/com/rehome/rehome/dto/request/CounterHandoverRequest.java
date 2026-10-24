package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CounterHandoverRequest {

    @NotNull(message = "accountId không được để trống")
    private UUID accountId;

    // ITEM_WITHDRAWAL / END_OF_TERM_TAKE_BACK / INVALID_RETURN_PICKUP — parse thủ công để trả đúng message khi sai
    @NotBlank(message = "context không được để trống")
    private String context;

    @NotBlank(message = "refId không được để trống")
    private String refId;
}
