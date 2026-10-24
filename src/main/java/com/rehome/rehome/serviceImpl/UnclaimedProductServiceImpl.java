package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.dto.request.ConfirmTransferRequest;
import com.rehome.rehome.dto.response.ProductStatusResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.UnclaimedProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnclaimedProductServiceImpl implements UnclaimedProductService {

    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProductStatusResponse confirmTransfer(String productId, ConfirmTransferRequest req, Account moderator) {
        String partner = req.getPartnerOrganizationName() == null ? "" : req.getPartnerOrganizationName().trim();
        if (partner.isEmpty()) {
            throw new AppException(ErrorCode.PARTNER_ORGANIZATION_NAME_REQUIRED);
        }

        Warehouse warehouse = moderator.getWarehouse();
        if (warehouse == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }

        // Khóa dòng sản phẩm để không xác nhận chuyển giao 2 lần
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getWarehouse().getId().equals(warehouse.getId())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE);
        }
        if (product.getStatus() != ProductStatus.UNCLAIMED) {
            throw new AppException(ErrorCode.PRODUCT_NOT_UNCLAIMED);
        }

        ProductStatus previous = product.getStatus();
        product.setStatus(ProductStatus.DONATED);
        productRepository.save(product);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("partnerOrganizationName", partner);
        detail.put("previousStatus", previous.name());
        detail.put("newStatus", ProductStatus.DONATED.name());
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(moderator)
                .actionType("CONFIRM_UNCLAIMED_TRANSFER")
                .targetType("PRODUCT")
                .targetId(product.getId())
                .detail(detail)
                .build());

        return ProductStatusResponse.builder()
                .id(product.getId())
                .status(product.getStatus())
                .build();
    }
}