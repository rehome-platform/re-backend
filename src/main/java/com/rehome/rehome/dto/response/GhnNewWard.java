package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Phường/xã theo mô hình hành chính 2 cấp (mới, thuộc thẳng tỉnh/thành, không còn quận/huyện)
// - xem docs/ghnapi.md "Lấy Phường/Xã (Mới)"
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnNewWard {

    @JsonProperty("_id")
    private Integer id;

    // Tên chuẩn - dùng nguyên văn cho to_ward_name/from_ward_name khi tạo đơn với is_new_*_address: true
    @JsonProperty("name")
    private String name;

    // Biến thể tên (không dấu, viết tắt "p.") - dùng để so khớp tên người dùng gõ tự do
    @JsonProperty("extension_names")
    private List<String> extensionNames;
}
