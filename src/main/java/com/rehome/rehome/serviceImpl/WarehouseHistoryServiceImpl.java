package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WarehouseHistoryResponse;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.service.WarehouseHistoryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseHistoryServiceImpl implements WarehouseHistoryService {

    static final int MAX_PAGE_SIZE = 50;

    private final AuditLogRepository auditLogRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;
    private final MemberProfileRepository memberProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WarehouseHistoryResponse> list(Account moderator, String type, int page, int size) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        WarehouseAuditGroup group = parseGroup(type);
        if (page < 0) {
            throw new BadRequestException("page phải >= 0.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size phải từ 1 đến " + MAX_PAGE_SIZE + ".");
        }

        // Cách ly kho tuyệt đối: warehouseId luôn lấy từ tài khoản đang đăng nhập
        UUID warehouseId = moderator.getWarehouse().getId();
        var result = auditLogRepository.findWarehouseHistory(
                warehouseId, group.name(), PageRequest.of(page, size));

        List<UUID> actorIds = result.getContent().stream()
                .map(l -> l.getActorAccount().getId()).distinct().toList();
        Map<UUID, String> nameByAccountId = new HashMap<>();
        if (!actorIds.isEmpty()) {
            for (ModeratorProfile profile : moderatorProfileRepository.findAllById(actorIds)) {
                nameByAccountId.put(profile.getAccountId(), profile.getFullName());
            }
            // Xác nhận biên nhận / quyết định hết kỳ là thao tác của Member nên actor có thể là Member
            for (MemberProfile profile : memberProfileRepository.findAllByAccount_IdIn(actorIds)) {
                nameByAccountId.putIfAbsent(profile.getAccountId(), profile.getFullName());
            }
        }

        return PageResponse.from(result.map(l -> toResponse(l, nameByAccountId)));
    }

    private static WarehouseAuditGroup parseGroup(String type) {
        if (type == null || type.isBlank()) {
            throw new BadRequestException("type là bắt buộc (PACKING, INSPECTION, INVENTORY).");
        }
        try {
            return WarehouseAuditGroup.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("type phải là PACKING, INSPECTION hoặc INVENTORY.");
        }
    }

    private static WarehouseHistoryResponse toResponse(AuditLog log, Map<UUID, String> nameByAccountId) {
        Account actor = log.getActorAccount();
        String name = nameByAccountId.get(actor.getId());
        return WarehouseHistoryResponse.builder()
                .target(log.getTargetType() + " " + targetLabel(log))
                .action(log.getActionType())
                .actor(name != null ? name : actor.getEmail())
                .timestamp(log.getCreatedAt())
                .result(log.getResult())
                .build();
    }

    // Chỉ lấy đúng một trường "label" (mã đơn / SKU / số form) từ detail để hiển thị; còn lại không lộ ra
    private static String targetLabel(AuditLog log) {
        JsonNode detail = log.getDetail();
        if (detail != null && detail.hasNonNull("label") && detail.get("label").isTextual()) {
            return detail.get("label").asText();
        }
        return log.getTargetId();
    }
}
