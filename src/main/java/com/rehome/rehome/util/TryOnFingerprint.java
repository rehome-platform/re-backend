package com.rehome.rehome.util;

import com.rehome.rehome.entity.enums.TryOnModelType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed components avoid ambiguities between different input combinations. */
public final class TryOnFingerprint {
    private TryOnFingerprint() {}

    public static String of(TryOnModelType model, String top, String bottom, String dress,
                            byte[] first, byte[] second, String version) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{version, model.name(), top, bottom, dress}) {
                append(digest, value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
            }
            append(digest, first);
            append(digest, second);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void append(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
