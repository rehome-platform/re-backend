package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.config.CodeGenerator;
import com.rehome.rehome.dto.request.CreateWarehouseRequest;
import com.rehome.rehome.dto.request.UpdateWarehouseRequest;
import com.rehome.rehome.dto.response.WarehouseAdminResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.WarehouseAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseAdminServiceImpl implements WarehouseAdminService {

    // Hàng còn nằm trong kho thì không được ẩn kho (API 144)
    static final List<ProductStatus> STOCK_STATUSES = List.of(
            ProductStatus.ON_SALE, ProductStatus.RESERVED, ProductStatus.UNCLAIMED,
            ProductStatus.PLATFORM_OWNED, ProductStatus.RETURNED, ProductStatus.WAREHOUSE);
    // Hàng SOLD nhưng đơn chưa giao đi: vẫn ở kho chờ đóng gói
    static final List<OrderStatus> OPEN_ORDER_STATUSES = List.of(OrderStatus.PAID, OrderStatus.PACKING);
    // Phiếu ký gửi chưa xong tại kho
    static final List<ConsignmentFormStatus> OPEN_FORM_STATUSES = List.of(
            ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED, ConsignmentFormStatus.INSPECTING);

    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseAdminResponse> list(CatalogStatus status) {
        List<Warehouse> warehouses = status == null
                ? warehouseRepository.findAllByOrderByNameAsc()
                : warehouseRepository.findByStatusOrderByNameAsc(status);
        return warehouses.stream().map(WarehouseAdminResponse::from).toList();
    }

    @Override
    @Transactional
    public WarehouseAdminResponse create(Account admin, CreateWarehouseRequest request) {
        String name = request.getName().trim();
        if (warehouseRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS);
        }

        String code = CodeGenerator.makeUnique(
                CodeGenerator.fromName(name, "kho"), warehouseRepository::existsByCode);

        Warehouse saved = warehouseRepository.save(Warehouse.builder()
                .name(name)
                .code(code)
                .address(request.getAddress().trim())
                .wardName(request.getWardName().trim())
                .provinceName(request.getProvinceName().trim())
                .provinceId(request.getProvinceId())
                .wardCode(request.getWardCode().trim())
                // Kho luôn khai theo hành chính 2 cấp (sau 01/07/2025, không còn quận/huyện)
                .isNewAddress(true)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .openingHours(request.getOpeningHours().trim())
                .status(CatalogStatus.ACTIVE)
                .build());

        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("after", snapshot(saved));
        auditLogger.log(admin, "CREATE_WAREHOUSE", "WAREHOUSE", saved.getId().toString(), detail);

        return WarehouseAdminResponse.from(saved);
    }

    @Override
    @Transactional
    public WarehouseAdminResponse update(Account admin, UUID warehouseId, UpdateWarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        String name = request.getName().trim();
        if (warehouseRepository.existsByNameIgnoreCaseAndIdNot(name, warehouseId)) {
            throw new AppException(ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS);
        }

        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("before", snapshot(warehouse));

        // Mã kho (code) giữ nguyên khi đổi tên: code đã in/đi vào dữ liệu cũ
        warehouse.setName(name);
        warehouse.setAddress(request.getAddress().trim());
        warehouse.setWardName(request.getWardName().trim());
        warehouse.setProvinceName(request.getProvinceName().trim());
        warehouse.setProvinceId(request.getProvinceId());
        warehouse.setWardCode(request.getWardCode().trim());
        // Hệ 2 cấp không có quận/huyện: xóa giá trị cũ của hệ 3 cấp để khỏi lệch với isNewAddress
        warehouse.setDistrictName(null);
        warehouse.setIsNewAddress(true);
        warehouse.setLatitude(request.getLatitude());
        warehouse.setLongitude(request.getLongitude());
        warehouse.setOpeningHours(request.getOpeningHours().trim());
        Warehouse saved = warehouseRepository.save(warehouse);

        detail.set("after", snapshot(saved));
        auditLogger.log(admin, "UPDATE_WAREHOUSE", "WAREHOUSE", saved.getId().toString(), detail);

        return WarehouseAdminResponse.from(saved);
    }

    @Override
    @Transactional
    public WarehouseAdminResponse hide(Account admin, UUID warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
        if (warehouse.getStatus() == CatalogStatus.HIDDEN) {
            throw new AppException(ErrorCode.WAREHOUSE_ALREADY_HIDDEN);
        }

        boolean hasStock = productRepository.countByWarehouseAndStatuses(warehouseId, STOCK_STATUSES) > 0
                || orderItemRepository.countSoldItemsInOpenOrders(warehouseId, OPEN_ORDER_STATUSES) > 0
                || consignmentFormRepository.existsByWarehouse_IdAndStatusIn(warehouseId, OPEN_FORM_STATUSES);
        if (hasStock) {
            throw new AppException(ErrorCode.WAREHOUSE_HAS_STOCK);
        }

        warehouse.setStatus(CatalogStatus.HIDDEN);
        Warehouse saved = warehouseRepository.save(warehouse);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("name", saved.getName());
        auditLogger.log(admin, "HIDE_WAREHOUSE", "WAREHOUSE", saved.getId().toString(), detail);

        return WarehouseAdminResponse.from(saved);
    }

    private ObjectNode snapshot(Warehouse w) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", w.getName());
        node.put("address", w.getAddress());
        node.put("wardName", w.getWardName());
        node.put("provinceName", w.getProvinceName());
        node.put("provinceId", w.getProvinceId());
        node.put("wardCode", w.getWardCode());
        node.put("isNewAddress", w.getIsNewAddress());
        node.put("latitude", w.getLatitude());
        node.put("longitude", w.getLongitude());
        node.put("openingHours", w.getOpeningHours());
        return node;
    }
}