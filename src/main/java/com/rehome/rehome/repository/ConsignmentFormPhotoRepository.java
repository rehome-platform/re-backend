package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConsignmentFormPhotoRepository extends JpaRepository<ConsignmentFormPhoto, UUID> {
    List<ConsignmentFormPhoto> findByForm_IdInOrderByDisplayOrderAscIdAsc(Collection<UUID> formIds);
    List<ConsignmentFormPhoto> findByForm_IdOrderByDisplayOrderAscIdAsc(UUID formId);
    void deleteByForm_Id(UUID formId);

}
