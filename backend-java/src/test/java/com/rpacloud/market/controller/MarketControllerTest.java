package com.rpacloud.market.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.market.dto.MarketFlowDetailResponse;
import com.rpacloud.market.dto.MarketFlowResponse;
import com.rpacloud.market.dto.MarketPublishRequest;
import com.rpacloud.market.service.MarketService;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarketController.class)
@AutoConfigureMockMvc(addFilters = false)
class MarketControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private MarketService marketService;
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

    private MarketFlowResponse sampleMarketFlow() {
        return MarketFlowResponse.builder()
                .id(1L).title("Test Workflow").description("A test flow")
                .authorName("admin").version(1).visibility("PUBLIC")
                .downloadCount(10).avgRating(new BigDecimal("4.5"))
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    @Test
    void listFlows_success() throws Exception {
        when(marketService.list(0, 20))
                .thenReturn(PageResponse.of(1L, List.of(sampleMarketFlow())));

        mockMvc.perform(get("/api/market/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Test Workflow"));
    }

    @Test
    void getById_success() throws Exception {
        var detail = MarketFlowDetailResponse.builder()
                .id(1L).title("Test Workflow").authorName("admin")
                .dslSnapshot(Map.of("steps", List.of()))
                .downloadCount(10).avgRating(new BigDecimal("4.5"))
                .visibility("PUBLIC").version(1)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        when(marketService.getById(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/market/flows/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test Workflow"))
                .andExpect(jsonPath("$.dsl_snapshot.steps").isArray());
    }

    @Test
    void publish_success() throws Exception {
        when(marketService.publish(any(), eq(1L))).thenReturn(sampleMarketFlow());

        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(42L);
        req.setTitle("My Workflow");

        mockMvc.perform(post("/api/market/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Workflow"));
    }

    @Test
    void publish_missingTitle() throws Exception {
        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(42L);
        // title is missing (@NotBlank)

        mockMvc.perform(post("/api/market/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void install_success() throws Exception {
        FlowResponse installed = FlowResponse.builder()
                .id(99L).siteId(1L).name("Test Workflow (installed)")
                .dsl(Map.of("steps", List.of())).isActive(true)
                .lastStatus(FlowStatus.idle)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        when(marketService.install(1L, 1L)).thenReturn(installed);

        mockMvc.perform(post("/api/market/flows/1/install"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Workflow (installed)"));
    }

    @Test
    void unpublish_success() throws Exception {
        doNothing().when(marketService).unpublish(1L, 1L);

        mockMvc.perform(delete("/api/market/flows/1"))
                .andExpect(status().isNoContent());
    }
}
