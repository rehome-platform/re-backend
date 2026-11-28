package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.RegisterDeviceTokenRequest;
import java.util.UUID;

public interface DeviceTokenService {

    void register(UUID accountId, RegisterDeviceTokenRequest request);

    void unregister(UUID accountId, String token);
}
