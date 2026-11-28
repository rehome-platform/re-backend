package com.rehome.rehome.entity.auth;

import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRepresentative {

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "position", nullable = false, length = 255)
    private String position;

    @Column(name = "cccd_number", nullable = false, length = 255)
    private String cccdNumber;

    @Column(name = "cccd_front_url", nullable = true, length = 2048)
    private String cccdFrontUrl;

    @Column(name = "cccd_back_url", nullable = true, length = 2048)
    private String cccdBackUrl;
}
