package com.rpacloud.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.catalog.dto.CategoryResponse;
import com.rpacloud.catalog.dto.TagCreateRequest;
import com.rpacloud.catalog.dto.TagResponse;
import com.rpacloud.catalog.service.CatalogService;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogController.class)
@AutoConfigureMockMvc(addFilters = false)
class CatalogControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CatalogService catalogService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    @Test
    void listCategories() throws Exception {
        when(catalogService.listCategories()).thenReturn(List.of(
                new CategoryResponse(1L, "Category1", null)
        ));

        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Category1"));
    }

    @Test
    void listTags() throws Exception {
        when(catalogService.listTags()).thenReturn(List.of(
                new TagResponse(1L, "tag1", "#ff0000")
        ));

        mockMvc.perform(get("/api/catalog/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("tag1"))
                .andExpect(jsonPath("$[0].color").value("#ff0000"));
    }

    @Test
    void createTag_returns201() throws Exception {
        when(catalogService.upsertTag(any(TagCreateRequest.class)))
                .thenReturn(new TagResponse(1L, "newtag", "#00ff00"));

        TagCreateRequest req = new TagCreateRequest();
        req.setName("newtag");
        req.setColor("#00ff00");

        mockMvc.perform(post("/api/catalog/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("newtag"));
    }

    @Test
    void deleteTag_returns204() throws Exception {
        mockMvc.perform(delete("/api/catalog/tags/1"))
                .andExpect(status().isNoContent());
    }
}
