package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.DeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByAccount_Id(UUID accountId);

    void deleteByAccount_IdAndToken(UUID accountId, String token);

    void deleteByTokenIn(Collection<String> tokens);
}
