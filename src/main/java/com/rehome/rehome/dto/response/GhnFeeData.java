package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Chỉ map field cần dùng (total); các phụ phí khác GHN trả về (insurance_fee, cod_fee, ...)
// bị bỏ qua vì chưa có nhu cầu hiển thị riêng.
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnFeeData {
    private Integer total;
}
