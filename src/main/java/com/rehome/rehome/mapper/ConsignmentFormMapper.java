package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ConsignmentFormDetailResponse;
import com.rehome.rehome.dto.response.ConsignmentFormListItemResponse;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Component
public class ConsignmentFormMapper {


    public ConsignmentFormResponse toResponse(ConsignmentForm form) {
        if (form == null) {
            return null;
        }

        ConsignmentFormResponse response = new ConsignmentFormResponse();
        response.setId(form.getId());
        response.setStatus(form.getStatus());
        response.setBringInDeadline(form.getBringInDeadline());
        response.setCreatedAt(form.getCreatedAt());
        return response;
    }

    /**
     * Danh sach photo entity -> danh sach URL (dung cho API chi tiet neu can).
     */
    public List<String> toPhotoUrls(List<ConsignmentFormPhoto> photos) {
        List<String> urls = new ArrayList<>();
        if (photos == null) {
            return urls;
        }

        for (ConsignmentFormPhoto photo : photos) {
            urls.add(photo.getImageUrl());
        }
        return urls;
    }

    public ConsignmentForm toEntity(ConsignmentFormRequest request) {
        if (request == null) return null;
        ConsignmentForm form = new ConsignmentForm();
        form.setProductName(request.getProductName());
        form.setDescription(request.getDescription());
        form.setDesiredPrice(request.getDesiredPrice());
        form.setSize(blankToNull(request.getSize()));
        form.setCondition(request.getCondition());
        form.setStatus(ConsignmentFormStatus.PENDING);
        form.setBringInDeadline(null);
        return form;   // createdAt do @PrePersist lo
    }

    // PUT: chỉ đổi các field Member được sửa, không đụng status/createdAt/warehouse
    public void applyUpdate(ConsignmentForm form, ConsignmentFormRequest request) {
        form.setProductName(request.getProductName());
        form.setDescription(request.getDescription());
        form.setDesiredPrice(request.getDesiredPrice());
        form.setSize(blankToNull(request.getSize()));
        form.setCondition(request.getCondition());
    }

    public List<ConsignmentFormPhoto> toPhotoEntities(ConsignmentForm form, List<String> photoUrls) {
        List<ConsignmentFormPhoto> photos = new ArrayList<>();
        if (form == null || photoUrls == null) return photos;
        for (int i = 0; i < photoUrls.size(); i++) {
            ConsignmentFormPhoto photo = new ConsignmentFormPhoto();
            photo.setForm(form);
            photo.setImageUrl(photoUrls.get(i));
            photo.setDisplayOrder(i);          // ảnh đầu tiên là ảnh đại diện
            photos.add(photo);
        }
        return photos;
    }

    public ConsignmentFormListItemResponse toListItem(ConsignmentForm form, String thumbnailUrl) {
        if (form == null) return null;
        return ConsignmentFormListItemResponse.builder()
                .id(form.getId())
                .status(form.getStatus())
                .productName(form.getProductName())
                .desiredPrice(form.getDesiredPrice())
                .thumbnailUrl(thumbnailUrl)
                .warehouseName(form.getWarehouse() != null ? form.getWarehouse().getName() : null)
                .bringInDeadline(form.getBringInDeadline())
                .rejectReason(form.getRejectReason())
                .createdAt(form.getCreatedAt())
                .build();
    }

    public ConsignmentFormDetailResponse toDetail(ConsignmentForm form, List<String> photoUrls) {
        Warehouse w = form.getWarehouse();
        return ConsignmentFormDetailResponse.builder()
                .id(form.getId())
                .formNo(form.getFormNo())
                .status(form.getStatus())
                .productName(form.getProductName())
                .description(form.getDescription())
                .desiredPrice(form.getDesiredPrice())
                .size(form.getSize())
                .condition(form.getCondition())
                .photoUrls(photoUrls)
                .categoryId(form.getCategory().getId())
                .categoryName(form.getCategory().getName())
                .brandId(form.getBrand().getId())
                .brandName(form.getBrand().getName())
                // null khi form còn là DRAFT
                .warehouse(w == null ? null : ConsignmentFormDetailResponse.WarehouseInfo.builder()
                        .id(w.getId()).name(w.getName())
                        .address(w.getAddress()).openingHours(w.getOpeningHours())
                        .build())
                .rejectReason(form.getRejectReason())
                .bringInDeadline(form.getBringInDeadline())
                .createdAt(form.getCreatedAt())
                .build();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

}
