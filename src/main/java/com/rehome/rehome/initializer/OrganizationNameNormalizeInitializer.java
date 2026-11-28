package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.util.VietnameseText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Order(102)
@RequiredArgsConstructor
public class OrganizationNameNormalizeInitializer implements ApplicationRunner {

    private final OrganizationProfileRepository organizationProfileRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<OrganizationProfile> pending = organizationProfileRepository.findAllByDisplayNameNormalizedIsNull();

        if (pending.isEmpty()) {
            log.info("[OrganizationNameNormalizeInitializer] Mọi hồ sơ tổ chức đã có tên không dấu, bỏ qua.");
            return;
        }

        for (OrganizationProfile profile : pending) {
            profile.setDisplayNameNormalized(VietnameseText.normalize(profile.getDisplayName()));
            profile.setLegalNameNormalized(VietnameseText.normalize(profile.getLegalName()));
        }
        organizationProfileRepository.saveAll(pending);

        log.info("[OrganizationNameNormalizeInitializer] Đã chuẩn hóa tên cho {} hồ sơ tổ chức.", pending.size());
    }
}
