package com.rpacloud.common.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.rpacloud.common.config.RpaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService storage;

    @BeforeEach
    void setUp() {
        RpaProperties props = new RpaProperties();
        props.getStorage().setBasePath(tempDir.toString());
        storage = new LocalStorageService(props);
    }

    @Test
    void storeAndLoad() throws Exception {
        byte[] content = "hello world".getBytes();
        storage.store(1L, "screenshots", "test.png", new ByteArrayInputStream(content));

        try (InputStream is = storage.load(1L, "screenshots", "test.png")) {
            assertThat(is.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void delete() {
        storage.store(1L, "screenshots", "del.png", new ByteArrayInputStream("x".getBytes()));
        storage.delete(1L, "screenshots", "del.png");

        assertThatThrownBy(() -> storage.load(1L, "screenshots", "del.png"))
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void getUsedBytes() {
        storage.store(1L, "screenshots", "a.png", new ByteArrayInputStream(new byte[100]));
        storage.store(1L, "downloads", "b.txt", new ByteArrayInputStream(new byte[200]));
        assertThat(storage.getUsedBytes(1L)).isEqualTo(300);
    }

    @Test
    void getUsedBytesEmptyUser() {
        assertThat(storage.getUsedBytes(999L)).isEqualTo(0);
    }

    @Test
    void generateSignedUrl() {
        String url = storage.generateSignedUrl(1L, "screenshots", "test.png", Duration.ofMinutes(5));
        assertThat(url).contains("/api/files/download");
        assertThat(url).contains("userId=1");
        assertThat(url).contains("sig=");
    }

    @Test
    void sanitizeBlocksTraversal() {
        assertThatThrownBy(() -> LocalStorageService.sanitizeFilename("../../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void sanitizeBlocksNullByte() {
        assertThatThrownBy(() -> LocalStorageService.sanitizeFilename("file\0.txt"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void sanitizeBlocksBlank() {
        assertThatThrownBy(() -> LocalStorageService.sanitizeFilename(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitizeExtractsFilename() {
        assertThat(LocalStorageService.sanitizeFilename("path/to/file.txt")).isEqualTo("file.txt");
    }

    @Test
    void categoryTraversalBlocked() {
        assertThatThrownBy(() -> LocalStorageService.sanitizeCategory("../../etc"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void categoryBlankBlocked() {
        assertThatThrownBy(() -> LocalStorageService.sanitizeCategory(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryValidAccepted() {
        assertThat(LocalStorageService.sanitizeCategory("screenshots")).isEqualTo("screenshots");
        assertThat(LocalStorageService.sanitizeCategory("my-data_01")).isEqualTo("my-data_01");
    }
}
