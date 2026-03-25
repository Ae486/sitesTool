package com.rpacloud.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.infra.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private StorageService storageService;
    private RpaProperties rpaProperties;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        rpaProperties = new RpaProperties();
        fileService = new FileService(storageService, rpaProperties);
    }

    @Test
    void upload_success() {
        when(storageService.getUsedBytes(1L)).thenReturn(0L);
        when(storageService.store(eq(1L), eq("uploads"), eq("test.csv"), any()))
                .thenReturn("/data/storage/users/1/uploads/test.csv");
        when(storageService.generateSignedUrl(eq(1L), eq("uploads"), eq("test.csv"), any()))
                .thenReturn("/api/files/download?sig=abc");

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());
        var result = fileService.upload(1L, "uploads", file);

        assertThat(result.filename()).isEqualTo("test.csv");
        assertThat(result.size()).isEqualTo(4);
        assertThat(result.url()).contains("sig=abc");
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> fileService.upload(1L, "uploads", file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void upload_rejectsTooLargeFile() {
        rpaProperties.getStorage().setMaxFileSizeBytes(10);
        MockMultipartFile file = new MockMultipartFile("file", "big.csv", "text/csv", new byte[100]);
        assertThatThrownBy(() -> fileService.upload(1L, "uploads", file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("max size");
    }

    @Test
    void upload_rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "hack.exe", "application/octet-stream", "data".getBytes());
        assertThatThrownBy(() -> fileService.upload(1L, "uploads", file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void upload_rejectsQuotaExceeded() {
        when(storageService.getUsedBytes(1L)).thenReturn(rpaProperties.getStorage().getUserQuotaBytes());
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());
        assertThatThrownBy(() -> fileService.upload(1L, "uploads", file))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("quota");
    }

    @Test
    void extractExtension_variousCases() {
        assertThat(FileService.extractExtension("test.csv")).isEqualTo("csv");
        assertThat(FileService.extractExtension("photo.JPG")).isEqualTo("jpg");
        assertThat(FileService.extractExtension("noext")).isEmpty();
        assertThat(FileService.extractExtension("file.tar.gz")).isEqualTo("gz");
    }
}
