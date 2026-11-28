package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ViolationWord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ViolationWordRepository
        extends JpaRepository<ViolationWord, UUID>, JpaSpecificationExecutor<ViolationWord> {

    java.util.List<ViolationWord> findAllByIsActiveTrue();

    boolean existsByWord(String word);

    boolean existsByWordAndIdNot(String word, UUID id);
}
