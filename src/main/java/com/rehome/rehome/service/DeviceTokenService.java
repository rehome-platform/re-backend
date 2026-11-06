package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.RegisterDeviceTokenRequest;
import java.util.UUID;

public interface DeviceTokenService {

    // Đăng ký/làm mới token FCM của thiết bị cho account đang đăng nhập. Token đã thuộc account
    // khác (đổi người dùng trên cùng thiết bị) thì gán lại cho account này.
    void register(UUID accountId, RegisterDeviceTokenRequest request);

    // Gọi lúc đăng xuất để thiết bị đó không nhận push của account vừa đăng xuất nữa.
    void unregister(UUID accountId, String token);
}
