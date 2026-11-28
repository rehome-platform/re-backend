package com.rehome.rehome.dto.response;

public record PdfDownload(String fileName, byte[] content) {
}
