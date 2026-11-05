package com.rehome.rehome.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void normalize_stripsSeparatorsAndConvertsCountryCode() {
        assertThat(PhoneNumbers.normalize("0912345678")).isEqualTo("0912345678");
        assertThat(PhoneNumbers.normalize(" 0912 345-678 ")).isEqualTo("0912345678");
        assertThat(PhoneNumbers.normalize("+84912345678")).isEqualTo("0912345678");
        assertThat(PhoneNumbers.normalize("+84 912.345.678")).isEqualTo("0912345678");
        assertThat(PhoneNumbers.normalize("84912345678")).isEqualTo("0912345678");
    }

    @Test
    void normalize_blankIsNull() {
        assertThat(PhoneNumbers.normalize(null)).isNull();
        assertThat(PhoneNumbers.normalize("   ")).isNull();
    }

    @Test
    void isValid_onlyTenDigitsStartingWithZero() {
        assertThat(PhoneNumbers.isValid("0912345678")).isTrue();
        assertThat(PhoneNumbers.isValid("091234567")).isFalse();
        assertThat(PhoneNumbers.isValid("09123456789")).isFalse();
        assertThat(PhoneNumbers.isValid("1912345678")).isFalse();
        assertThat(PhoneNumbers.isValid("09123abc78")).isFalse();
        assertThat(PhoneNumbers.isValid(null)).isFalse();
    }

    @Test
    void toSearchKeyword_normalizesOnlyPhoneLikeKeywords() {
        assertThat(PhoneNumbers.toSearchKeyword("+84 912 345")).isEqualTo("0912345");
        assertThat(PhoneNumbers.toSearchKeyword("0912-345")).isEqualTo("0912345");
        assertThat(PhoneNumbers.toSearchKeyword("nguyen van a")).isEqualTo("nguyen van a");
        assertThat(PhoneNumbers.toSearchKeyword("a@b.com")).isEqualTo("a@b.com");
        assertThat(PhoneNumbers.toSearchKeyword(null)).isEmpty();
        assertThat(PhoneNumbers.toSearchKeyword("  ")).isEmpty();
    }
}
