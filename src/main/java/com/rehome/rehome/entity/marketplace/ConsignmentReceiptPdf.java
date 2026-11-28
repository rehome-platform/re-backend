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

@Entity
@Table(name = "consignment_receipt_pdfs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsignmentReceiptPdf {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "receipt_id")
    private ConsignmentReceipt receipt;

    @Column(name = "content", nullable = false, length = 16 * 1024 * 1024)
    private byte[] content;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
}
