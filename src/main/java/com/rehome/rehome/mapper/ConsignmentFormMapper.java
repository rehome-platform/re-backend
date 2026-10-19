package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Component
public class ConsignmentFormMapper {

    /**
     * Request -> ConsignmentForm entity.
     * Status mac dinh PENDING, bringInDeadline de null (Moderator duyet moi set).
     * Photos duoc map rieng bang toPhotoEntities().
     */
    public ConsignmentForm toEntity(ConsignmentFormRequest request) {
        if (request == null) {
            return null;
        }

        ConsignmentForm form = new ConsignmentForm();
        form.setProductName(request.getProductName());
        form.setDescription(request.getDescription());
        form.setDesiredPrice(request.getDesiredPrice());
        form.setStatus(ConsignmentFormStatus.PENDING);
        form.setBringInDeadline(null);
        form.setCreatedAt(Instant.now());
        return form;
    }

    /**
     * List<String> photoUrls -> List<ConsignmentFormPhoto>.
     * displayOrder lay theo thu tu trong mang (anh dau tien la anh dai dien).
     */
    public List<ConsignmentFormPhoto> toPhotoEntities(ConsignmentForm form, List<String> photoUrls) {
        List<ConsignmentFormPhoto> photos = new ArrayList<>();
        if (form == null || photoUrls == null) {
            return photos;
        }

        for (String url : photoUrls) {
            ConsignmentFormPhoto photo = new ConsignmentFormPhoto();
            photo.setForm(form);
            photo.setImageUrl(url);
            photos.add(photo);
        }
        return photos;
    }

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
}
