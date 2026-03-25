package com.rpacloud.file.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.file.service.FileService;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FileService fileService;
    @MockBean private RpaProperties rpaProperties;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User testUser = User.builder().id(1L).email("test@test.com").fullName("Test")
                .isActive(true).hashedPassword("x").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser, null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void upload_success() throws Exception {
        var result = new FileService.FileUploadResult("/api/files/download?sig=abc", "test.csv", 100L, "/tmp/test.csv");
        when(fileService.upload(eq(1L), eq("uploads"), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("test.csv"))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void upload_withCategory() throws Exception {
        var result = new FileService.FileUploadResult("/api/files/download?sig=abc", "data.json", 50L, "/tmp/data.json");
        when(fileService.upload(eq(1L), eq("screenshots"), any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "data.json", "application/json", "{}".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file).param("category", "screenshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("data.json"));
    }

    @Test
    void download_expiredSignature() throws Exception {
        RpaProperties.Auth auth = new RpaProperties.Auth();
        auth.setSecretKey("test-secret-at-least-32-characters!!");
        when(rpaProperties.getAuth()).thenReturn(auth);

        mockMvc.perform(get("/api/files/download")
                        .param("userId", "1")
                        .param("category", "uploads")
                        .param("filename", "test.csv")
                        .param("expires", "1000")
                        .param("sig", "invalid"))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_invalidSignature() throws Exception {
        RpaProperties.Auth auth = new RpaProperties.Auth();
        auth.setSecretKey("test-secret-at-least-32-characters!!");
        when(rpaProperties.getAuth()).thenReturn(auth);

        long futureExpires = System.currentTimeMillis() + 3600000;
        mockMvc.perform(get("/api/files/download")
                        .param("userId", "1")
                        .param("category", "uploads")
                        .param("filename", "test.csv")
                        .param("expires", String.valueOf(futureExpires))
                        .param("sig", "invalid-sig"))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_validSignature() throws Exception {
        RpaProperties.Auth auth = new RpaProperties.Auth();
        String secret = "test-secret-at-least-32-characters!!";
        auth.setSecretKey(secret);
        when(rpaProperties.getAuth()).thenReturn(auth);

        long futureExpires = System.currentTimeMillis() + 3600000;
        String payload = "1:uploads:test.csv:" + futureExpires;
        String sig = hmacSha256(payload, secret);

        when(fileService.downloadWithVerification(1L, "uploads", "test.csv"))
                .thenReturn(new ByteArrayInputStream("file content".getBytes()));

        mockMvc.perform(get("/api/files/download")
                        .param("userId", "1")
                        .param("category", "uploads")
                        .param("filename", "test.csv")
                        .param("expires", String.valueOf(futureExpires))
                        .param("sig", sig))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.csv\""));
    }

    private static String hmacSha256(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
