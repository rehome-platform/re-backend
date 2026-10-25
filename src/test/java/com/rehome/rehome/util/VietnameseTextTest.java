package com.rehome.rehome.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseTextTest {

    @ParameterizedTest
    @CsvSource({
            "Áo khoác,                 ao khoac",
            "ÁO KHOÁC,                 ao khoac",
            "Quần jean nữ,             quan jean nu",
            "Giày thể thao,            giay the thao",
            "Túi xách da,              tui xach da",
            "Đồng hồ đeo tay,          dong ho deo tay",
            "Mũ lưỡi trai,             mu luoi trai",
            "Váy đầm dự tiệc,          vay dam du tiec",
            "Áo sơ mi trắng,           ao so mi trang"
    })
    void normalize_stripsVietnameseDiacriticsAndLowercases(String input, String expected) {
        assertThat(VietnameseText.normalize(input)).isEqualTo(expected);
    }

    // đ/Đ không phân rã được bằng NFD nên dễ bị sót, giữ test riêng cho nó
    @Test
    void normalize_mapsDStrokeToPlainD() {
        assertThat(VietnameseText.normalize("Đầm")).isEqualTo("dam");
        assertThat(VietnameseText.normalize("đỏ")).isEqualTo("do");
    }

    // ơ/ư dùng dấu móc, là ký tự kết hợp khác với dấu thanh
    @Test
    void normalize_stripsHornFromOAndU() {
        assertThat(VietnameseText.normalize("Mươn")).isEqualTo("muon");
    }

    @Test
    void normalize_trimsSurroundingWhitespace() {
        assertThat(VietnameseText.normalize("  Áo khoác  ")).isEqualTo("ao khoac");
    }

    @Test
    void normalize_returnsNullForNull() {
        assertThat(VietnameseText.normalize(null)).isNull();
    }

    // Gõ đủ dấu vẫn phải khớp với dữ liệu đã chuẩn hóa, không chỉ gõ không dấu
    @Test
    void normalize_isIdempotent() {
        String once = VietnameseText.normalize("Áo khoác");
        assertThat(VietnameseText.normalize(once)).isEqualTo(once);
    }
}
