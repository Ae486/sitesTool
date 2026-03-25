package com.rpacloud.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.common.infra.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "json", "csv", "txt", "png", "jpg", "jpeg", "gif", "pdf", "xlsx", "zip"
    );
    private static final Duration SIGNED_URL_EXPIRY = Duration.ofHours(1);

    // Magic number signatures for binary file types (extension → header bytes)
    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.of(
            "png",  new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "jpg",  new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "gif",  new byte[]{0x47, 0x49, 0x46},
            "pdf",  new byte[]{0x25, 0x50, 0x44, 0x46},
            "xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "zip",  new byte[]{0x50, 0x4B, 0x03, 0x04}
    );

    private final StorageService storageService;
    private final RpaProperties rpaProperties;

    public FileUploadResult upload(Long userId, String category, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "File is empty");
        }
        if (file.getSize() > rpaProperties.getStorage().getMaxFileSizeBytes()) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE,
                    "File exceeds max size: " + rpaProperties.getStorage().getMaxFileSizeBytes() + " bytes");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "Filename is required");
        }

        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "File type not allowed: " + extension + ". Allowed: " + ALLOWED_EXTENSIONS);
        }

        validateMagicNumber(file, extension);

        long usedBytes = storageService.getUsedBytes(userId);
        if (usedBytes + file.getSize() > rpaProperties.getStorage().getUserQuotaBytes()) {
            throw new BizException(ErrorCode.STORAGE_QUOTA_EXCEEDED, "Storage quota exceeded");
        }

        try (InputStream is = file.getInputStream()) {
            String storedPath = storageService.store(userId, category, originalFilename, is);
            String signedUrl = storageService.generateSignedUrl(userId, category, originalFilename, SIGNED_URL_EXPIRY);
            log.info("File uploaded: userId={}, category={}, filename={}, size={}", userId, category, originalFilename, file.getSize());
            return new FileUploadResult(signedUrl, originalFilename, file.getSize(), storedPath);
        } catch (Exception e) {
            log.error("Failed to store file: userId={}, category={}, filename={}", userId, category, originalFilename, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to store file");
        }
    }

    public InputStream downloadWithVerification(Long userId, String category, String filename) {
        return storageService.load(userId, category, filename);
    }

    public void delete(Long userId, String category, String filename) {
        storageService.delete(userId, category, filename);
    }

    static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    private static void validateMagicNumber(MultipartFile file, String extension) {
        byte[] expected = MAGIC_NUMBERS.get(extension);
        if (expected == null) return; // text-based formats (json/csv/txt) have no magic number
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[expected.length];
            int read = is.read(header);
            if (read < expected.length) {
                throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                        "File too small to be a valid " + extension);
            }
            for (int i = 0; i < expected.length; i++) {
                if (header[i] != expected[i]) {
                    throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                            "File header does not match " + extension + " format");
                }
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Failed to read file header");
        }
    }

    public record FileUploadResult(String url, String filename, long size, String storedPath) {}
}
