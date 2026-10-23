package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DecideEndOfTermRequest {

    @NotNull(message = "decision không được để trống")
    private EndOfTermChoice decision;
}
