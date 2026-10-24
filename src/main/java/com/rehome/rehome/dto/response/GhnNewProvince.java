package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Tỉnh/thành theo mô hình hành chính 2 cấp (mới, sau sáp nhập 01/07/2025) - xem docs/ghnapi.md "Lấy Tỉnh/Thành (Mới)"
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnNewProvince {

    @JsonProperty("_id")
    private Integer id;

    // Tên chuẩn - dùng nguyên văn cho to_province_name/from_province_name khi tạo đơn với is_new_*_address: true
    @JsonProperty("name")
    private String name;

    // Biến thể tên (không dấu, viết tắt, có tiền tố "tp.") - dùng để so khớp tên người dùng gõ tự do
    @JsonProperty("extension_names")
    private List<String> extensionNames;
}
