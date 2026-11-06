package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.RegisterDeviceTokenRequest;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.DeviceToken;
import com.rehome.rehome.entity.enums.DevicePlatform;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.DeviceTokenRepository;
import com.rehome.rehome.service.DeviceTokenService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void register(UUID accountId, RegisterDeviceTokenRequest request) {
        if (!StringUtils.hasText(request.getToken())) {
            throw new BadRequestException("token là bắt buộc.");
        }
        DevicePlatform platform = parsePlatform(request.getPlatform());

        DeviceToken deviceToken = deviceTokenRepository.findByToken(request.getToken())
                .orElseGet(DeviceToken::new);
        // Token đã tồn tại nhưng thuộc account khác (đổi người dùng trên cùng thiết bị) -> gán lại,
        // không tạo bản ghi mới, vì cột token là unique.
        if (deviceToken.getAccount() == null || !deviceToken.getAccount().getId().equals(accountId)) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
            deviceToken.setAccount(account);
        }
        deviceToken.setToken(request.getToken());
        deviceToken.setPlatform(platform);
        deviceTokenRepository.save(deviceToken);
    }

    @Override
    @Transactional
    public void unregister(UUID accountId, String token) {
        if (!StringUtils.hasText(token)) {
            throw new BadRequestException("token là bắt buộc.");
        }
        deviceTokenRepository.deleteByAccount_IdAndToken(accountId, token);
    }

    private DevicePlatform parsePlatform(String rawPlatform) {
        if (!StringUtils.hasText(rawPlatform)) {
            throw new BadRequestException("platform là bắt buộc.");
        }
        try {
            return DevicePlatform.valueOf(rawPlatform.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("platform phải là ANDROID, IOS hoặc WEB.");
        }
    }
}
