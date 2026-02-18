package com.rpacloud.site.controller;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.site.dto.SiteCreateRequest;
import com.rpacloud.site.dto.SiteResponse;
import com.rpacloud.site.dto.SiteUpdateRequest;
import com.rpacloud.site.service.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    public PageResponse<SiteResponse> list(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return siteService.list(skip, limit);
    }

    @GetMapping("/{id}")
    public SiteResponse getById(@PathVariable Long id) {
        return siteService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SiteResponse create(@Valid @RequestBody SiteCreateRequest request) {
        return siteService.create(request);
    }

    @PutMapping("/{id}")
    public SiteResponse update(@PathVariable Long id, @RequestBody SiteUpdateRequest request) {
        return siteService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        siteService.delete(id);
    }
}
