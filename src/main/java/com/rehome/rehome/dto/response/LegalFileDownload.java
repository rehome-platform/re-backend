package com.rehome.rehome.dto.response;

public record LegalFileDownload(String fileName, String contentType, byte[] content) {
}
