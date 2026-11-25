package com.rehome.rehome.util;
import com.rehome.rehome.entity.enums.TryOnModelType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class TryOnFingerprintTest {
    @Test void changesToModelProductsBytesOrVersionInvalidateReuse() {
        String hash = TryOnFingerprint.of(TryOnModelType.FEMALE,"a","b",null,new byte[]{1},new byte[]{2},"v1");
        assertThat(hash).hasSize(64).isEqualTo(TryOnFingerprint.of(TryOnModelType.FEMALE,"a","b",null,new byte[]{1},new byte[]{2},"v1"));
        assertThat(hash).isNotEqualTo(TryOnFingerprint.of(TryOnModelType.MALE,"a","b",null,new byte[]{1},new byte[]{2},"v1"));
        assertThat(hash).isNotEqualTo(TryOnFingerprint.of(TryOnModelType.FEMALE,"x","b",null,new byte[]{1},new byte[]{2},"v1"));
        assertThat(hash).isNotEqualTo(TryOnFingerprint.of(TryOnModelType.FEMALE,"a","b",null,new byte[]{3},new byte[]{2},"v1"));
        assertThat(hash).isNotEqualTo(TryOnFingerprint.of(TryOnModelType.FEMALE,"a","b",null,new byte[]{1},new byte[]{2},"v2"));
        assertThat(TryOnFingerprint.of(TryOnModelType.FEMALE,"ab","c",null,new byte[]{1},new byte[]{2},"v1"))
            .isNotEqualTo(TryOnFingerprint.of(TryOnModelType.FEMALE,"a","bc",null,new byte[]{1},new byte[]{2},"v1"));
    }
}
