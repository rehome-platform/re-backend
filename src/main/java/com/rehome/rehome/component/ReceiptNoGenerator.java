package com.rehome.rehome.component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

// Sinh số biên nhận dạng KG-2026-000123. Số thứ tự lấy từ sequence receipt_no_seq (PostgreSQL),
// nên không bị trùng khi nhiều Moderator tạo phiếu cùng lúc. Phải chạy SQL tạo sequence trước.
@Component
public class ReceiptNoGenerator {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @PersistenceContext private EntityManager entityManager;

    public String next() {
        Number seq = (Number) entityManager
                .createNativeQuery("select nextval('receipt_no_seq')")
                .getSingleResult();
        int year = LocalDate.now(ZONE).getYear();
        return String.format("KG-%d-%06d", year, seq.longValue());
    }
}