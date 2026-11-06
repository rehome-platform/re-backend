package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * App gọi lúc đăng nhập (hoặc lúc có token mới do Firebase refresh) để đăng ký nhận push.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceTokenRequest {
    private String token;
    private String platform; // ANDROID, IOS, WEB
}
