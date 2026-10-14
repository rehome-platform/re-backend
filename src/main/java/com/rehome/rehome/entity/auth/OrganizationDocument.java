package com.rehome.rehome.entity.auth;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "organization_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationDocument {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tổ chức đã nộp giấy tờ này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    // Loại giấy tờ, ví dụ giấy phép thành lập, điều lệ
    @Column(name = "doc_type", nullable = false, length = 255)
    private String docType;

    // Số giấy tờ; quyết định nội bộ thường không có số
    @Column(name = "doc_number", nullable = true, length = 255)
    private String docNumber;

    // Ngày cấp ghi trên giấy
    @Column(name = "issue_date", nullable = true)
    private LocalDate issueDate;

    // Cơ quan đã cấp giấy
    @Column(name = "issuing_authority", nullable = true, length = 255)
    private String issuingAuthority;

    // Tệp ảnh hoặc PDF, chỉ Moderator xem được
    @Column(name = "file_url", nullable = false, length = 2048)
    private String fileUrl;
}
