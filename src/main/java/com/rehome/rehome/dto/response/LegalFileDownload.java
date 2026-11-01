package com.rehome.rehome.dto.response;

/**
 * Tệp giấy tờ pháp lý trả thẳng về client dưới dạng binary, không qua URL công khai (F-139).
 */
public record LegalFileDownload(String fileName, String contentType, byte[] content) {
}
