package com.rpacloud.common.infra;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    String store(Long userId, String category, String filename, InputStream data);

    InputStream load(Long userId, String category, String filename);

    void delete(Long userId, String category, String filename);

    String generateSignedUrl(Long userId, String category, String filename, Duration expiry);

    long getUsedBytes(Long userId);
}
