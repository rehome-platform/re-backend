package com.rehome.rehome.entity.auth;

import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.generator.GeneratedModeratorCode;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "moderator_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeratorProfile {
    @Id
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @GeneratedModeratorCode
    @Column(name = "moderator_code", nullable = false, unique = true, length = 255)
    private String moderatorCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
}
