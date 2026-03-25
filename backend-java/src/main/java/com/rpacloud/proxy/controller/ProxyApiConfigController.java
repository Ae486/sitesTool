package com.rpacloud.proxy.controller;

import java.util.List;

import com.rpacloud.proxy.dto.ProxyApiConfigResponse;
import com.rpacloud.proxy.service.ProxyApiConfigService;
import com.rpacloud.user.entity.User;
import jakarta.validation.Valid;
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
@RequestMapping("/api/proxy/api-configs")
@RequiredArgsConstructor
public class ProxyApiConfigController {

    private final ProxyApiConfigService service;

    @GetMapping
    public List<ProxyApiConfigResponse> list(@AuthenticationPrincipal User user) {
        return service.list(user.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProxyApiConfigResponse create(@AuthenticationPrincipal User user,
                                         @Valid @RequestBody ApiConfigRequest req) {
        return service.create(user.getId(), req.name(), req.baseUrl(), req.paramsJson());
    }

    @PutMapping("/{id}")
    public ProxyApiConfigResponse update(@AuthenticationPrincipal User user,
                                          @PathVariable Long id,
                                          @Valid @RequestBody ApiConfigRequest req) {
        return service.update(id, user.getId(), req.name(), req.baseUrl(), req.paramsJson());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(id, user.getId());
    }

    record ApiConfigRequest(
            @NotBlank String name,
            @NotBlank String baseUrl,
            String paramsJson
    ) {}
}
