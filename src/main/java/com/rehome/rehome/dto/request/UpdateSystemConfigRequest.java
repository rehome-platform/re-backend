package com.rehome.rehome.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** API 146 - F-157. Không đặt @NotBlank: value rỗng/sai kiểu trả 422 từ service theo đúng docs. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateSystemConfigRequest {
    private String value;
}
