package com.rehome.rehome.entity.enums;

public enum EndOfTermChoice {
    TAKE_BACK,
    RE_CONSIGN,
    UNCLAIMED,
    // Member quyên góp sản phẩm đang ở WAREHOUSE thay vì chọn TAKE_BACK/RE_CONSIGN (xem ProductServiceImpl.donate)
    DONATE
}
