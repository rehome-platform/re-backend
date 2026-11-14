package com.rehome.rehome.initializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.repository.AccountRepository;
/**
 * Seed dữ liệu mẫu cho bảng warehouses (phục vụ test API F-015 - kho gần nhất).
 * Chỉ seed khi bảng đang trống, nên khởi động lại app không bị trùng dữ liệu. Nếu bảng đã có dữ liệu
 * thì chỉ bổ sung khối địa chỉ GHN (phường/xã, quận/huyện, tỉnh/thành) còn thiếu theo định nghĩa seed,
 * không ghi đè giá trị đã có (Admin có thể đã sửa kho qua API 143).
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.initializer.seed-enabled", havingValue = "true")
public class WarehouseInitializer implements CommandLineRunner {

    // Số nhận hàng trả về kho (to_phone của vận đơn GHN chiều trả) - tạm dùng chung một hotline
    private static final String WAREHOUSE_HOTLINE = "0901234567";

    private final WarehouseRepository warehouseRepository;

    private static final String DEFAULT_MODERATOR_WAREHOUSE = "Kho Quận 7";

    // email moderator -> tên kho phụ trách
    private static final Map<String, String> MODERATOR_WAREHOUSES = Map.of(
            "moderator1@rehome.com", "Kho Quận 7",
            "moderator2@rehome.com", "Kho Quận 1",
            "moderator3@rehome.com", "Kho Quận 3"
    );

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        List<Warehouse> seeds = seedDefinitions();

        if (warehouseRepository.count() == 0) {
            warehouseRepository.saveAll(seeds);
            log.info("Đã seed {} kho mẫu", seeds.size());
        } else {
            syncAddressUnits(seeds);
        }

        assignWarehousesToModerators();
    }

    // Gán kho cho moderator chưa có kho (warehouse_id đang null). Chạy lặp lại không ghi đè
// những moderator đã được gán kho.
    private void assignWarehousesToModerators() {
        Map<String, Warehouse> warehouseByName = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getName, w -> w, (a, b) -> a));

        int assigned = 0;
        for (Account moderator : accountRepository.findByRole(Role.MODERATOR)) {
            if (moderator.getWarehouse() != null) {
                continue;
            }

            String warehouseName = MODERATOR_WAREHOUSES
                    .getOrDefault(moderator.getEmail(), DEFAULT_MODERATOR_WAREHOUSE);
            Warehouse warehouse = warehouseByName.get(warehouseName);
            if (warehouse == null) {
                log.warn("Không tìm thấy kho '{}' để gán cho {}", warehouseName, moderator.getEmail());
                continue;
            }

            moderator.setWarehouse(warehouse);
            accountRepository.save(moderator);
            assigned++;
        }

        if (assigned > 0) {
            log.info("Đã gán kho cho {} moderator", assigned);
        }
    }

    // Điền khối địa chỉ GHN (phường/xã, quận/huyện, tỉnh/thành) cho kho seed còn thiếu. Từ khi Admin có API sửa kho
    // (API 143), seed không còn là nguồn chuẩn nữa nên chỉ lấp chỗ trống, không ghi đè giá trị đã có.
    private void syncAddressUnits(List<Warehouse> seeds) {
        Map<String, Warehouse> seedByName = seeds.stream()
                .collect(Collectors.toMap(Warehouse::getName, w -> w));

        int fixed = 0;
        for (Warehouse existing : warehouseRepository.findAll()) {
            Warehouse seed = seedByName.get(existing.getName());
            if (seed == null || !isMissingAddressUnit(existing)) {
                continue;
            }

            existing.setWardName(seed.getWardName());
            existing.setDistrictName(seed.getDistrictName());
            existing.setProvinceName(seed.getProvinceName());
            existing.setIsNewAddress(seed.getIsNewAddress());
            warehouseRepository.save(existing);
            fixed++;
        }

        if (fixed > 0) {
            log.info("Đã bổ sung khối địa chỉ GHN cho {} kho", fixed);
        }
    }

    private boolean isMissingAddressUnit(Warehouse w) {
        return isBlank(w.getWardName()) || isBlank(w.getDistrictName()) || isBlank(w.getProvinceName());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Tên tỉnh/thành phải đúng như danh mục GHN trả về: "Hồ Chí Minh", không phải "TP. Hồ Chí Minh"
    // (xem docs/ghnapi.md:1598 hệ cũ và :1832 hệ 2 cấp). Kho vẫn khai theo hệ cũ (isNewAddress=false,
    // có quận/huyện) nên phường/xã + quận/huyện tra ở danh mục cũ; GHN cho phép mỗi khối địa chỉ
    // của một vận đơn dùng hệ riêng qua cờ is_new_from_address/is_new_to_address.
    private List<Warehouse> seedDefinitions() {
        return List.of(
                build("Kho Quận 1", "12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM",
                        "10.7710000", "106.6900000", "08:00 - 20:00", CatalogStatus.ACTIVE,
                        "Phường Bến Thành", "Quận 1", "Hồ Chí Minh"),
                build("Kho Quận 3", "245 Võ Văn Tần, P.5, Q.3, TP.HCM",
                        "10.7843000", "106.6844000", "08:00 - 20:00", CatalogStatus.ACTIVE,
                        "Phường 5", "Quận 3", "Hồ Chí Minh"),
                build("Kho Quận 7", "105 Nguyễn Thị Thập, P. Tân Phú, Q.7, TP.HCM",
                        "10.7340000", "106.7220000", "08:00 - 21:00", CatalogStatus.ACTIVE,
                        "Phường Tân Phú", "Quận 7", "Hồ Chí Minh"),
                build("Kho Quận 10", "320 Ba Tháng Hai, P.12, Q.10, TP.HCM",
                        "10.7679000", "106.6670000", "08:00 - 20:00", CatalogStatus.ACTIVE,
                        "Phường 12", "Quận 10", "Hồ Chí Minh"),
                build("Kho Bình Thạnh", "88 Xô Viết Nghệ Tĩnh, P.21, Q. Bình Thạnh, TP.HCM",
                        "10.8106000", "106.7091000", "08:00 - 20:00", CatalogStatus.ACTIVE,
                        "Phường 21", "Quận Bình Thạnh", "Hồ Chí Minh"),
                build("Kho Phú Nhuận", "150 Phan Xích Long, P.7, Q. Phú Nhuận, TP.HCM",
                        "10.7992000", "106.6803000", "08:30 - 19:30", CatalogStatus.ACTIVE,
                        "Phường 7", "Quận Phú Nhuận", "Hồ Chí Minh"),
                build("Kho Tân Bình", "56 Cộng Hòa, P.4, Q. Tân Bình, TP.HCM",
                        "10.8015000", "106.6527000", "08:00 - 20:00", CatalogStatus.ACTIVE,
                        "Phường 4", "Quận Tân Bình", "Hồ Chí Minh"),
                build("Kho Gò Vấp", "72 Quang Trung, P.10, Q. Gò Vấp, TP.HCM",
                        "10.8386000", "106.6652000", "08:00 - 20:00", CatalogStatus.ACTIVE,
                        "Phường 10", "Quận Gò Vấp", "Hồ Chí Minh"),
                build("Kho Thủ Đức", "88 Võ Văn Ngân, P. Linh Chiểu, TP. Thủ Đức, TP.HCM",
                        "10.8494000", "106.7537000", "07:30 - 20:30", CatalogStatus.ACTIVE,
                        "Phường Linh Chiểu", "Thành phố Thủ Đức", "Hồ Chí Minh"),
                // Kho HIDDEN để test: API nearest không được trả về kho này
                build("Kho Bình Tân (tạm đóng)", "18 Kinh Dương Vương, P. An Lạc, Q. Bình Tân, TP.HCM",
                        "10.7654000", "106.6038000", "08:00 - 20:00", CatalogStatus.HIDDEN,
                        "Phường An Lạc", "Quận Bình Tân", "Hồ Chí Minh")
        );
    }

    private Warehouse build(String name, String address, String lat, String lng,
                            String openingHours, CatalogStatus status,
                            String wardName, String districtName, String provinceName) {
        return Warehouse.builder()
                .name(name)
                .address(address)
                .phone(WAREHOUSE_HOTLINE)
                .latitude(new BigDecimal(lat))
                .longitude(new BigDecimal(lng))
                .openingHours(openingHours)
                .status(status)
                .wardName(wardName)
                .districtName(districtName)
                .provinceName(provinceName)
                .isNewAddress(false)
                .build();
    }
}
