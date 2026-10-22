package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecideEndOfTermResponse {

    private EndOfTermChoice decision;

    // null nếu TAKE_BACK
    private UUID newReceiptId;
}
