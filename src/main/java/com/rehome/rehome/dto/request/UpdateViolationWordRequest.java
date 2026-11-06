package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PUT: thay toàn bộ thông tin từ khóa; riêng isActive không gửi thì giữ nguyên trạng thái hiện tại. */
@Data
public class UpdateViolationWordRequest {

    @NotBlank(message = "word không được để trống")
    @Size(max = 255, message = "word tối đa 255 ký tự")
    private String word;

    @NotNull(message = "category không được để trống")
    private ViolationWordCategory category;

    @NotNull(message = "severity không được để trống")
    private ViolationWordSeverity severity;

    @NotNull(message = "action không được để trống")
    private ViolationWordAction action;

    private Boolean isActive;

    @Size(max = 1000, message = "note tối đa 1000 ký tự")
    private String note;
}
