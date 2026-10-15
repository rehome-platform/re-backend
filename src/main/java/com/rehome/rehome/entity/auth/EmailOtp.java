package com.rehome.rehome.entity.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "email_otps")
@Getter @Setter @NoArgsConstructor
public class EmailOtp {
    @Id
    private String id;
    @Column(nullable = false)
    private String codeHash;
    private Instant expiresAt;
    private Instant sentAt;
    private int attempts;
    private boolean consumed;
    private String resetTokenHash;
    private Instant resetExpiresAt;
}
