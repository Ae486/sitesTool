package com.rpacloud.proxy.controller;

import java.util.List;

import com.rpacloud.proxy.dto.TunnelProxyConfigResponse;
import com.rpacloud.proxy.service.TunnelProxyConfigService;
import com.rpacloud.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proxy/tunnel-configs")
@RequiredArgsConstructor
public class TunnelProxyConfigController {

    private final TunnelProxyConfigService service;

    @GetMapping
    public List<TunnelProxyConfigResponse> list(@AuthenticationPrincipal User user) {
        return service.list(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TunnelProxyConfigResponse create(@AuthenticationPrincipal User user,
                                             @Valid @RequestBody TunnelConfigRequest req) {
        return service.create(user.getId(), req.name(), req.protocol(),
                req.host(), req.port(), req.username(), req.password());
    }

    @PutMapping("/{id}")
    public TunnelProxyConfigResponse update(@AuthenticationPrincipal User user,
                                             @PathVariable Long id,
                                             @Valid @RequestBody TunnelConfigRequest req) {
        return service.update(id, user.getId(), req.name(), req.protocol(),
                req.host(), req.port(), req.username(), req.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(id, user.getId());
    }

    record TunnelConfigRequest(
            @NotBlank String name,
            @NotBlank String protocol,
            @NotBlank String host,
            @Min(1) @Max(65535) int port,
            String username,
            String password
    ) {}
}
