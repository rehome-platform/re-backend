package com.rehome.rehome.service;

import java.util.Optional;

public interface FileStorageService {

    String store(String path, byte[] content, String contentType);

    boolean delete(String path, String contentType);

    boolean deleteByUrl(String url, String contentType);

    Optional<byte[]> loadByUrl(String url);
}