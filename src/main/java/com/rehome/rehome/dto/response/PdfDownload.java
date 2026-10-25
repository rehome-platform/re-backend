package com.rehome.rehome.dto.response;

/**
 * PDF trả thẳng về client dưới dạng binary, không qua URL.
 * Controller dùng {@code fileName} cho header Content-Disposition.
 */
public record PdfDownload(String fileName, byte[] content) {
}
