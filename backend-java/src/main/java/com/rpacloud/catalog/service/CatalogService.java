package com.rpacloud.catalog.service;

import java.util.List;

import com.rpacloud.catalog.dto.CategoryResponse;
import com.rpacloud.catalog.dto.TagCreateRequest;
import com.rpacloud.catalog.dto.TagResponse;
import com.rpacloud.catalog.entity.Tag;
import com.rpacloud.catalog.repository.CategoryRepository;
import com.rpacloud.catalog.repository.TagRepository;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public List<TagResponse> listTags() {
        return tagRepository.findAllByOrderByNameAsc().stream()
                .map(TagResponse::from)
                .toList();
    }

    @Transactional
    public TagResponse upsertTag(TagCreateRequest request) {
        Tag tag = tagRepository.findByName(request.getName()).orElse(null);
        if (tag != null) {
            if (request.getColor() != null) {
                tag.setColor(request.getColor());
            }
        } else {
            tag = Tag.builder()
                    .name(request.getName())
                    .color(request.getColor())
                    .build();
        }
        tag = tagRepository.save(tag);
        return TagResponse.from(tag);
    }

    @Transactional
    public void deleteTag(Long tagId) {
        if (!tagRepository.existsById(tagId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Tag not found");
        }
        tagRepository.deleteById(tagId);
    }
}
