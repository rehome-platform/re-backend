package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReturnRequestPhotoRepository extends JpaRepository<ReturnRequestPhoto, UUID> {

    List<ReturnRequestPhoto> findAllByReturnRequest_IdOrderByIdAsc(UUID returnRequestId);
}
