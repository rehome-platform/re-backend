package com.rehome.rehome.util;


public final class MemberQrPayload {

    public static final String PREFIX = "REHOME:MEMBER:v1:";

    private MemberQrPayload() {}

    public static String encode(String memberCode) {
        return PREFIX + memberCode;
    }

}
