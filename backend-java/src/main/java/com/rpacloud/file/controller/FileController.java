package com.rpacloud.file.controller;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.common.infra.StorageService;
import com.rpacloud.file.service.FileService;
import com.rpacloud.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final RpaProperties rpaProperties;

    @PostMapping("/upload")
    public Map<String, Object> upload(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "uploads") String category) {
        Long userId = currentUser.getId();
        var result = fileService.upload(userId, category, file);
        return Map.of(
                "url", result.url(),
                "filename", result.filename(),
                "size", result.size()
        );
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("userId") Long userId,
            @RequestParam("category") String category,
            @RequestParam("filename") String filename,
            @RequestParam("expires") long expires,
            @RequestParam("sig") String signature) {

        long now = System.currentTimeMillis();
        if (now > expires) {
            throw new BizException(ErrorCode.INVALID_SIGNATURE, "Download link expired");
        }

        String payload = userId + ":" + category + ":" + filename + ":" + expires;
        String expectedSig = hmacSha256(payload, rpaProperties.getAuth().getSecretKey());
        if (!MessageDigest.isEqual(expectedSig.getBytes(), signature.getBytes())) {
            throw new BizException(ErrorCode.INVALID_SIGNATURE, "Invalid signature");
        }

        InputStream is = fileService.downloadWithVerification(userId, category, filename);
        String contentType = guessContentType(filename);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(is));
    }

    private static String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private static String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
