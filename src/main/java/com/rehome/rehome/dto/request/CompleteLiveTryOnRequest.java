package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// PATCH /tryon/live/sessions/{id}/complete - mobile tự báo lại sau khi quay xong (hoặc lỗi), chỉ để audit
@Getter
@Setter
@NoArgsConstructor
public class CompleteLiveTryOnRequest {

    @NotNull(message = "status phải là COMPLETED hoặc FAILED.")
    private LiveTryOnStatus status;

    // Thời lượng thực tế đã stream, không bắt buộc (vd. status=FAILED trước khi kết nối được)
    private Integer actualDurationSeconds;
}
