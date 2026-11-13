package com.rehome.rehome.repository;

import com.rehome.rehome.entity.membership.VoucherTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherTemplateRepository extends JpaRepository<VoucherTemplate, UUID> {

    @EntityGraph(attributePaths = {"tierRules"})
    List<VoucherTemplate> findAllByOrderByCreatedAtAsc();

    @EntityGraph(attributePaths = {"tierRules"})
    Optional<VoucherTemplate> findWithTierRulesById(UUID id);

    // Job phát voucher: chỉ mẫu đang bật mới phát mã mới
    @EntityGraph(attributePaths = {"tierRules"})
    List<VoucherTemplate> findAllByActiveTrue();

    boolean existsByName(String name);
}
