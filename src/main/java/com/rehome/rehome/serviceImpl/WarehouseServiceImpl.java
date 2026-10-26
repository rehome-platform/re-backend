package com.rehome.rehome.serviceImpl;
import com.rehome.rehome.dto.marketplace.response.WarehouseResponse;
import com.rehome.rehome.dto.response.MyWarehouseResponse;
import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.WarehouseService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {
    private final WarehouseRepository warehouseRepository;
    private final AccountRepository accountRepository;

    private static final double EARTH_RADIUS_KM = 6371.0;

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }

    public List<WarehouseNearestResponse> getNearest(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }

        return warehouseRepository.findByStatus(CatalogStatus.ACTIVE).stream()
                .map(w -> new WarehouseNearestResponse(
                        w.getId(), w.getName(), w.getAddress(),
                        haversine(lat, lng,
                                w.getLatitude().doubleValue(),
                                w.getLongitude().doubleValue())))
                .sorted(Comparator.comparingDouble(WarehouseNearestResponse::distanceKm))
                .limit(20)
                .toList();
    }

    @Override
    @Transactional
    public List<WarehouseResponse> getActiveWarehouses() {
        return warehouseRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private com.rehome.rehome.dto.marketplace.response.WarehouseResponse toResponse(Warehouse w) {
        return WarehouseResponse.builder()
                .id(w.getId())
                .name(w.getName())
                .address(w.getAddress())
                .latitude(w.getLatitude())
                .longitude(w.getLongitude())
                .openingHours(w.getOpeningHours())
                .build();
    }

    @Transactional
    public List<MyWarehouseResponse> getManagedWarehouses(UUID accountId) {
        Account account = accountRepository.findWithWarehouseById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.MODERATOR_NOT_FOUND));

        Warehouse warehouse = account.getWarehouse();
        if (warehouse == null) {
            // account không phải moderator, hoặc chưa được gán kho
            throw new AppException(ErrorCode.MODERATOR_NOT_FOUND);
        }

        return List.of(MyWarehouseResponse.from(warehouse));
    }

//    Code này dùng khi tạo warehouse thủ công
//    // Khi tạo Warehouse
//    String code = CodeGenerator.makeUnique(
//            CodeGenerator.fromName(request.getName(), "kho"),
//            warehouseRepository::existsByCode);
//warehouse.setCode(code);
}
