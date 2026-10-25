package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Byte PDF của biên nhận ký gửi (F-022).
 *
 * <p>Nằm trong DB, không phải storage ngoài: biên nhận là hợp đồng giữa nền tảng và
 * người ký gửi, không có chữ ký tay, nên chính file này là bằng chứng Member đã đồng ý
 * với những con số nào. Lưu trong DB để (1) commit cùng {@code confirmedAt} trong một
 * transaction, (2) chỉ ra được qua endpoint có phân quyền chứ không qua URL công khai,
 * (3) nằm cùng bản backup với dòng dữ liệu nó thuộc về.
 *
 * <p>Tách bảng riêng thay vì thêm cột vào {@link ConsignmentReceipt} để byte PDF không bị
 * load kèm mỗi lần đọc biên nhận - chỉ endpoint tải PDF mới query bảng này.
 */
@Entity
@Table(name = "consignment_receipt_pdfs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsignmentReceiptPdf {

    // Dùng chung khóa với biên nhận: 1 biên nhận - 1 PDF
    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "receipt_id")
    private ConsignmentReceipt receipt;

    // length phải khai rõ: để mặc định Hibernate sinh varbinary(255) và PDF ~40KB
    // insert không vào. Postgres map byte[] sang bytea (không có giới hạn length),
    // H2 trong test thì cần con số này để không bị cắt.
    @Column(name = "content", nullable = false, length = 16 * 1024 * 1024)
    private byte[] content;

    // SHA-256 của content, để đối chiếu về sau là file chưa bị sửa
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
