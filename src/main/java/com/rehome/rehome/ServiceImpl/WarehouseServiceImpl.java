package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.config.CodeGenerator;
import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {
    private final WarehouseRepository warehouseRepository;

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

//    Code này dùng khi tạo warehouse thủ công
//    // Khi tạo Warehouse
//    String code = CodeGenerator.makeUnique(
//            CodeGenerator.fromName(request.getName(), "kho"),
//            warehouseRepository::existsByCode);
//warehouse.setCode(code);
}
