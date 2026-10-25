package com.rehome.rehome.util;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;

// Nội dung QR mà Member đưa cho Moderator tại quầy: chỉ là memberCode có tiền tố.
// Tiền tố để web Moderator biết vừa quét QR Member hay QR sản phẩm (F-124);
// "v1" để sau này đổi format mà QR đã in/đã chụp vẫn đọc được.
// QR không phải chứng thực: bước tin cậy là Moderator đối chiếu người thật
// rồi gọi /counter/members/{accountId}/confirm-identity (API 26).
public final class MemberQrPayload {

    public static final String PREFIX = "REHOME:MEMBER:v1:";

    private MemberQrPayload() {}

    public static String encode(String memberCode) {
        return PREFIX + memberCode;
    }

    public static String decodeMemberCode(String raw) {
        if (raw == null) {
            throw new AppException(ErrorCode.QR_PAYLOAD_INVALID);          // 400
        }
        String payload = raw.trim();
        if (!payload.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            throw new AppException(ErrorCode.QR_PAYLOAD_INVALID);
        }
        String memberCode = payload.substring(PREFIX.length()).trim();
        if (memberCode.isEmpty()) {
            throw new AppException(ErrorCode.QR_PAYLOAD_INVALID);
        }
        return memberCode;
    }
}
