package com.rpacloud.common.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.rpacloud.common.config.RpaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnMissingBean(name = "cloudStorageService")
public class LocalStorageService implements StorageService {

    private final Path basePath;
    private final String hmacSecret;

    public LocalStorageService(RpaProperties props) {
        this.basePath = Path.of(props.getStorage().getBasePath());
        this.hmacSecret = props.getAuth().getSecretKey();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory", e);
        }
    }

    @Override
    public String store(Long userId, String category, String filename, InputStream data) {
        String sanitized = sanitizeFilename(filename);
        Path dir = resolvePath(userId, category);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(sanitized);
            Files.copy(data, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file: " + sanitized, e);
        }
    }

    @Override
    public InputStream load(Long userId, String category, String filename) {
        String sanitized = sanitizeFilename(filename);
        Path file = resolvePath(userId, category).resolve(sanitized);
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException("File not found: " + sanitized, e);
        }
    }

    @Override
    public void delete(Long userId, String category, String filename) {
        String sanitized = sanitizeFilename(filename);
        Path file = resolvePath(userId, category).resolve(sanitized);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", file, e);
        }
    }

    @Override
    public String generateSignedUrl(Long userId, String category, String filename, Duration expiry) {
        long expiresAt = System.currentTimeMillis() + expiry.toMillis();
        String payload = userId + ":" + category + ":" + filename + ":" + expiresAt;
        String signature = hmacSha256(payload);
        return "/api/files/download?userId=" + userId
                + "&category=" + category
                + "&filename=" + sanitizeFilename(filename)
                + "&expires=" + expiresAt
                + "&sig=" + signature;
    }

    @Override
    public long getUsedBytes(Long userId) {
        Path userDir = basePath.resolve("users").resolve(String.valueOf(userId));
        if (!Files.isDirectory(userDir)) return 0;
        try (Stream<Path> walk = Files.walk(userDir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private Path resolvePath(Long userId, String category) {
        String cleanCategory = sanitizeCategory(category);
        Path userRoot = basePath.resolve("users").resolve(String.valueOf(userId));
        Path resolved = userRoot.resolve(cleanCategory).normalize();
        if (!resolved.startsWith(userRoot)) {
            throw new SecurityException("Path traversal in category: " + category);
        }
        return resolved;
    }

    static String sanitizeCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category cannot be blank");
        }
        if (!category.matches("[A-Za-z0-9_-]+")) {
            throw new SecurityException("Invalid category: " + category);
        }
        return category;
    }

    static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be blank");
        }
        // Block path traversal
        String clean = filename.replace("\\", "/");
        if (clean.contains("..") || clean.contains("\0") || clean.startsWith("/")) {
            throw new SecurityException("Invalid filename: " + filename);
        }
        // Keep only the last segment
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0) clean = clean.substring(lastSlash + 1);
        if (clean.isBlank()) {
            throw new IllegalArgumentException("Filename resolved to blank");
        }
        return clean;
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }
}
