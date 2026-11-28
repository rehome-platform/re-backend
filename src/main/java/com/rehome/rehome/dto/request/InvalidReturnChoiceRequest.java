package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidReturnChoiceRequest {
    private InvalidReturnChoice choice;

    private UUID addressId;

    public InvalidReturnChoiceRequest(InvalidReturnChoice choice) {
        this.choice = choice;
    }
}
