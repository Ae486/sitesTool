package com.rpacloud.site.service;

import java.util.List;

import com.rpacloud.catalog.entity.Category;
import com.rpacloud.catalog.entity.Tag;
import com.rpacloud.catalog.repository.CategoryRepository;
import com.rpacloud.catalog.repository.TagRepository;
import com.rpacloud.common.dto.OffsetBasedPageRequest;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.site.dto.SiteCreateRequest;
import com.rpacloud.site.dto.SiteResponse;
import com.rpacloud.site.dto.SiteUpdateRequest;
import com.rpacloud.site.entity.Site;
import com.rpacloud.site.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public PageResponse<SiteResponse> list(int skip, int limit) {
        var pageable = new OffsetBasedPageRequest(skip, limit);
        Page<Long> idPage = siteRepository.findAllIds(pageable);
        List<Site> sites = idPage.getContent().isEmpty()
                ? List.of()
                : siteRepository.findAllWithTagsByIdIn(idPage.getContent());
        List<SiteResponse> items = sites.stream().map(SiteResponse::from).toList();
        return PageResponse.of(idPage.getTotalElements(), items);
    }

    @Transactional(readOnly = true)
    public SiteResponse getById(Long id) {
        Site site = siteRepository.findByIdWithTags(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Site not found"));
        return SiteResponse.from(site);
    }

    @Transactional
    public SiteResponse create(SiteCreateRequest request) {
        Site site = Site.builder()
                .name(request.getName())
                .url(request.getUrl())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElse(null);
            site.setCategory(cat);
        }
        site = siteRepository.save(site);
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            site.setTags(tags);
            site = siteRepository.save(site);
        }
        return getById(site.getId());
    }

    @Transactional
    public SiteResponse update(Long id, SiteUpdateRequest request) {
        Site site = siteRepository.findByIdWithTags(id)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Site not found"));
        if (request.getName() != null) site.setName(request.getName());
        if (request.getUrl() != null) site.setUrl(request.getUrl());
        if (request.getDescription() != null) site.setDescription(request.getDescription());
        if (request.getSortOrder() != null) site.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) site.setIsActive(request.getIsActive());
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElse(null);
            site.setCategory(cat);
        }
        if (request.getTagIds() != null) {
            List<Tag> tags = tagRepository.findAllById(request.getTagIds());
            site.setTags(tags);
        }
        siteRepository.save(site);
        return getById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (!siteRepository.existsById(id)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Site not found");
        }
        siteRepository.deleteById(id);
    }
}
