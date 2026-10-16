package com.rehome.rehome.entity.auth;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRepresentative {

    // Họ tên, phải khớp tên ghi trên giấy phép
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    // Chức vụ, xác định thẩm quyền đại diện
    @Column(name = "position", nullable = false, length = 255)
    private String position;

    // Số CCCD, đối chiếu với ảnh CCCD
    @Column(name = "cccd_number", nullable = false, length = 255)
    private String cccdNumber;

    // Ảnh CCCD mặt trước, tự xóa sau khi hồ sơ được duyệt
    @Column(name = "cccd_front_url", nullable = true, length = 2048)
    private String cccdFrontUrl;

    // Ảnh CCCD mặt sau, tự xóa sau khi hồ sơ được duyệt
    @Column(name = "cccd_back_url", nullable = true, length = 2048)
    private String cccdBackUrl;
}
