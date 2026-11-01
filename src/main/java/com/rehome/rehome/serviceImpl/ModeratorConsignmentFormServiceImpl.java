package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse;
import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse.MemberInfo;
import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse.ModeratorInfo;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ModeratorFormStage;
import com.rehome.rehome.entity.enums.ModeratorResumeStep;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormPhotoRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.service.ModeratorConsignmentFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModeratorConsignmentFormServiceImpl implements ModeratorConsignmentFormService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    // Cùng một quy tắc "biên nhận mới nhất" cho danh sách và chi tiết: createdAt lớn nhất, hòa thì id lớn hơn
    private static final Comparator<ConsignmentReceipt> NEWER_RECEIPT = Comparator
            .comparing(ConsignmentReceipt::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ConsignmentReceipt::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private final ConsignmentFormRepository consignmentFormRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ProductRepository productRepository;
    private final QueueItemRepository queueItemRepository;
    private final ConsignmentFormPhotoRepository photoRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;

    // ------------------------------------------------------------------ list

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ModeratorConsignmentFormResponse> list(
            Account moderator, String stage, int page, int size) {

        Warehouse warehouse = requireWarehouse(moderator);
        ModeratorFormStage parsedStage = parseStage(stage);

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        Page<ConsignmentForm> result = queryForms(warehouse, parsedStage, pageable);

        List<ModeratorConsignmentFormResponse> items = buildResponses(result.getContent(), false);
        return PageResponse.of(new PageImpl<>(items, pageable, result.getTotalElements()));
    }

    private Page<ConsignmentForm> queryForms(Warehouse warehouse, ModeratorFormStage stage, Pageable pageable) {
        UUID warehouseId = warehouse.getId();

        if (stage == null) {
            return consignmentFormRepository.findForModeratorExcludingStatus(
                    warehouseId, ConsignmentFormStatus.DRAFT, pageable);
        }
        return switch (stage) {
            case PENDING -> consignmentFormRepository.findForModeratorByStatus(
                    warehouseId, ConsignmentFormStatus.PENDING, pageable);
            case APPROVED -> consignmentFormRepository.findForModeratorByStatus(
                    warehouseId, ConsignmentFormStatus.APPROVED, pageable);
            // Dựa vào status + biên nhận + sản phẩm, không dựa vào hàng đợi kiểm định
            case INSPECTING -> consignmentFormRepository.findInspectionTabForModerator(
                    warehouseId, ConsignmentFormStatus.INSPECTING, pageable);
            case STOCKED_TODAY -> {
                // "Hôm nay" theo giờ Việt Nam (UTC+7): [00:00 hôm nay, 00:00 ngày mai)
                LocalDate today = LocalDate.now(VN_ZONE);
                Instant startAt = today.atStartOfDay(VN_ZONE).toInstant();
                Instant endAt = today.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
                yield consignmentFormRepository.findStockedBetweenForModerator(
                        warehouseId, ConsignmentFormStatus.DRAFT, startAt, endAt, pageable);
            }
        };
    }

    // ---------------------------------------------------------------- detail

    @Override
    @Transactional(readOnly = true)
    public ModeratorConsignmentFormResponse getDetail(Account moderator, UUID id) {
        ConsignmentForm form = consignmentFormRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));      // 404

        // Không đọc nháp của Member, không đọc form kho khác
        Warehouse warehouse = requireWarehouse(moderator);
        if (form.getStatus() == ConsignmentFormStatus.DRAFT
                || form.getWarehouse() == null
                || !form.getWarehouse().getId().equals(warehouse.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);                         // 403
        }

        // Đi chung đường build với danh sách để dùng cùng quy tắc chọn biên nhận
        return buildResponses(List.of(form), true).get(0);
    }

    // --------------------------------------------------------------- mapping

    // Gom dữ liệu liên quan của cả trang bằng vài query theo lô, tránh N+1
    private List<ModeratorConsignmentFormResponse> buildResponses(List<ConsignmentForm> forms, boolean detail) {
        if (forms.isEmpty()) {
            return List.of();
        }

        List<UUID> formIds = forms.stream().map(ConsignmentForm::getId).toList();

        // Biên nhận mới nhất của từng form (form có nhiều lần ký gửi lại có nhiều biên nhận)
        Map<UUID, ConsignmentReceipt> latestReceipts = consignmentReceiptRepository.findLatestByFormIds(formIds)
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getConsignmentForm().getId(),
                        Function.identity(),
                        (a, b) -> NEWER_RECEIPT.compare(a, b) >= 0 ? a : b));

        // Sản phẩm của đúng các biên nhận mới nhất đó
        List<UUID> receiptIds = latestReceipts.values().stream().map(ConsignmentReceipt::getId).toList();
        Map<UUID, Product> productsByReceipt = receiptIds.isEmpty()
                ? Map.of()
                : productRepository.findByConsignmentReceipt_IdIn(receiptIds).stream()
                .collect(Collectors.toMap(
                        p -> p.getConsignmentReceipt().getId(),
                        Function.identity(),
                        (a, b) -> a));

        // Chỉ form chưa có biên nhận mới cần tra claim của hàng đợi
        Map<String, Account> claimers = loadQueueClaimers(forms, latestReceipts);

        Map<UUID, MemberProfile> memberProfiles = loadMemberProfiles(forms);
        Map<UUID, ModeratorProfile> moderatorProfiles = loadModeratorProfiles(latestReceipts, claimers);

        Map<UUID, List<String>> photos = photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(formIds)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getForm().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(ConsignmentFormPhoto::getImageUrl, Collectors.toList())));

        List<ModeratorConsignmentFormResponse> result = new ArrayList<>(forms.size());
        for (ConsignmentForm form : forms) {
            ConsignmentReceipt receipt = latestReceipts.get(form.getId());
            Product product = receipt == null ? null : productsByReceipt.get(receipt.getId());
            List<String> formPhotos = photos.getOrDefault(form.getId(), List.of());

            // Giai đoạn trước biên nhận: người nhận việc trong hàng đợi. Sau biên nhận: moderator lưu trên biên nhận
            Account responsible = receipt != null
                    ? receipt.getModerator()
                    : claimers.get(form.getId().toString());

            ModeratorConsignmentFormResponse response = ModeratorConsignmentFormResponse.builder()
                    .id(form.getId())
                    .formNo(form.getFormNo())
                    .status(form.getStatus())
                    .productName(form.getProductName())
                    .desiredPrice(form.getDesiredPrice())
                    .thumbnailUrl(formPhotos.isEmpty() ? null : formPhotos.get(0))
                    .rejectReason(form.getRejectReason())
                    .bringInDeadline(form.getBringInDeadline())
                    .createdAt(form.getCreatedAt())
                    .warehouseName(form.getWarehouse() != null ? form.getWarehouse().getName() : null)
                    .member(toMemberInfo(form.getMember(), memberProfiles))
                    .moderator(toModeratorInfo(responsible, moderatorProfiles))
                    .receiptId(receipt != null ? receipt.getId() : null)
                    .confirmedAt(receipt != null ? receipt.getConfirmedAt() : null)
                    .productSku(product != null ? product.getId() : null)
                    .resumeStep(resolveResumeStep(form, receipt, product != null))
                    .build();

            if (detail) {
                fillDetail(response, form, receipt, product, formPhotos);
            }
            result.add(response);
        }
        return result;
    }

    private void fillDetail(ModeratorConsignmentFormResponse response, ConsignmentForm form,
                            ConsignmentReceipt receipt, Product product, List<String> photoUrls) {
        response.setDescription(form.getDescription());
        response.setSize(form.getSize());
        response.setCondition(form.getCondition());
        response.setPhotoUrls(photoUrls);
        response.setCategoryId(form.getCategory().getId());
        response.setCategoryName(form.getCategory().getName());
        response.setBrandId(form.getBrand().getId());
        response.setBrandName(form.getBrand().getName());

        Warehouse w = form.getWarehouse();
        response.setWarehouse(ModeratorConsignmentFormResponse.WarehouseInfo.builder()
                .id(w.getId())
                .name(w.getName())
                .address(w.getAddress())
                .openingHours(w.getOpeningHours())
                .build());

        if (receipt != null) {
            response.setReceipt(ModeratorConsignmentFormResponse.ReceiptInfo.builder()
                    .id(receipt.getId())
                    .receiptNo(receipt.getReceiptNo())
                    .agreedPrice(receipt.getAgreedPrice())
                    .floorPrice(receipt.getFloorPrice())
                    .termLengthDays(receipt.getTermLengthDays())
                    .conditionNote(receipt.getConditionNote())
                    .moderatorNote(receipt.getModeratorNote())
                    .confirmedAt(receipt.getConfirmedAt())
                    .createdAt(receipt.getCreatedAt())
                    .build());
        }
        if (product != null) {
            response.setProduct(ModeratorConsignmentFormResponse.ProductInfo.builder()
                    .sku(product.getId())
                    .name(product.getName())
                    .status(product.getStatus())
                    .shelfLocation(product.getShelfLocation())
                    .createdAt(product.getCreatedAt())
                    .build());
        }
    }

    // Dẫn xuất bước nghiệp vụ từ status form + biên nhận mới nhất + sản phẩm; không thêm status mới vào DB
    static ModeratorResumeStep resolveResumeStep(ConsignmentForm form, ConsignmentReceipt receipt, boolean hasProduct) {
        ConsignmentFormStatus status = form.getStatus();
        if (status == ConsignmentFormStatus.REJECTED
                || status == ConsignmentFormStatus.EXPIRED
                || status == ConsignmentFormStatus.CANCELLED
                || status == ConsignmentFormStatus.DRAFT) {
            return ModeratorResumeStep.NONE;
        }
        if (receipt != null) {
            if (receipt.getConfirmedAt() == null) {
                return ModeratorResumeStep.AWAIT_MEMBER_CONFIRM;
            }
            return hasProduct ? ModeratorResumeStep.COMPLETED : ModeratorResumeStep.STOCK_IN;
        }
        return switch (status) {
            case PENDING -> ModeratorResumeStep.REVIEW_FORM;
            case APPROVED -> ModeratorResumeStep.START_INSPECTION;
            case INSPECTING -> ModeratorResumeStep.INSPECTION;
            default -> ModeratorResumeStep.NONE;
        };
    }

    // ------------------------------------------------------ batch loaders

    // Claim của hàng đợi cho các form chưa có biên nhận. Ưu tiên claim ở hàng đợi kiểm định
    private Map<String, Account> loadQueueClaimers(List<ConsignmentForm> forms,
                                                   Map<UUID, ConsignmentReceipt> latestReceipts) {
        List<String> refIds = forms.stream()
                .map(ConsignmentForm::getId)
                .filter(id -> !latestReceipts.containsKey(id))
                .map(UUID::toString)
                .toList();
        if (refIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Account> claimers = new HashMap<>();
        for (QueueItem q : queueItemRepository.findByTypesAndRefIds(
                List.of(QueueType.INSPECTION, QueueType.FORM_REVIEW), refIds)) {
            if (q.getClaimedBy() == null) {
                continue;
            }
            if (q.getType() == QueueType.INSPECTION || !claimers.containsKey(q.getRefId())) {
                claimers.put(q.getRefId(), q.getClaimedBy());
            }
        }
        return claimers;
    }

    private Map<UUID, MemberProfile> loadMemberProfiles(List<ConsignmentForm> forms) {
        Set<UUID> ids = new HashSet<>();
        for (ConsignmentForm f : forms) {
            ids.add(f.getMember().getId());
        }
        return memberProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MemberProfile::getAccountId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, ModeratorProfile> loadModeratorProfiles(Map<UUID, ConsignmentReceipt> latestReceipts,
                                                              Map<String, Account> claimers) {
        Set<UUID> ids = new HashSet<>();
        latestReceipts.values().forEach(r -> ids.add(r.getModerator().getId()));
        claimers.values().forEach(a -> ids.add(a.getId()));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return moderatorProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ModeratorProfile::getAccountId, Function.identity(), (a, b) -> a));
    }

    private MemberInfo toMemberInfo(Account member, Map<UUID, MemberProfile> profiles) {
        if (member == null) {
            return null;
        }
        MemberProfile profile = profiles.get(member.getId());
        return MemberInfo.builder()
                .id(member.getId())
                .fullName(profile != null ? profile.getFullName() : null)
                .memberCode(profile != null ? profile.getMemberCode() : null)
                .phone(member.getPhone())
                .build();
    }

    private ModeratorInfo toModeratorInfo(Account moderator, Map<UUID, ModeratorProfile> profiles) {
        if (moderator == null) {
            return null;
        }
        ModeratorProfile profile = profiles.get(moderator.getId());
        return ModeratorInfo.builder()
                .id(moderator.getId())
                .fullName(profile != null ? profile.getFullName() : null)
                .moderatorCode(profile != null ? profile.getModeratorCode() : null)
                .build();
    }

    // --------------------------------------------------------------- helpers

    private Warehouse requireWarehouse(Account moderator) {
        if (moderator == null || moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);                             // 403
        }
        return moderator.getWarehouse();
    }

    private ModeratorFormStage parseStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return null;
        }
        try {
            return ModeratorFormStage.valueOf(stage.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_FORM_STAGE);                                 // 400
        }
    }
}