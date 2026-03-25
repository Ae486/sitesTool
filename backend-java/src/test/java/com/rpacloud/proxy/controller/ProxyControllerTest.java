package com.rpacloud.proxy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.security.JwtTokenProvider;
import com.rpacloud.proxy.dto.ProxyResponse;
import com.rpacloud.proxy.service.ProxyPoolService;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProxyController.class)
@ActiveProfiles("enterprise")
@AutoConfigureMockMvc(addFilters = false)
class ProxyControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProxyPoolService proxyPoolService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserRepository userRepository;

    @Test
    void listProxies_returnsPageResponse() throws Exception {
        ProxyResponse resp = new ProxyResponse(
                1L, "1.2.3.4", 8080, "HTTP", "US", "ScdnProxyProvider",
                true, 10, 2, 150, true, LocalDateTime.now()
        );
        when(proxyPoolService.getAllProxies(0, 20, null, null, null, null))
                .thenReturn(PageResponse.of(1L, List.of(resp)));

        mockMvc.perform(get("/api/proxy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].ip").value("1.2.3.4"))
                .andExpect(jsonPath("$.items[0].last_check_success").value(true));
    }

    @Test
    void listProxies_withPagination() throws Exception {
        when(proxyPoolService.getAllProxies(10, 5, null, null, null, null))
                .thenReturn(PageResponse.of(0L, List.of()));

        mockMvc.perform(get("/api/proxy").param("skip", "10").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void listProxies_withFilters() throws Exception {
        when(proxyPoolService.getAllProxies(0, 20, "HTTP", "manual", "available", null))
                .thenReturn(PageResponse.of(0L, List.of()));

        mockMvc.perform(get("/api/proxy")
                        .param("protocol", "HTTP")
                        .param("provider", "manual")
                        .param("status", "available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void refresh_returnsOk() throws Exception {
        doNothing().when(proxyPoolService).refreshPool("HTTPS", 5);

        mockMvc.perform(post("/api/proxy/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void stats_returnsMap() throws Exception {
        when(proxyPoolService.getStats())
                .thenReturn(Map.of("total", 10L, "active", 7L, "inactive", 3L, "pool_size", 5L));

        mockMvc.perform(get("/api/proxy/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.active").value(7));
    }

    @Test
    void fetchPreview_ssrf_localhost_blocked() throws Exception {
        mockMvc.perform(post("/api/proxy/fetch-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base_url\":\"http://localhost:8000/api/proxy/stats\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECURITY_VIOLATION"));
    }

    @Test
    void fetchPreview_ssrf_privateIp_blocked() throws Exception {
        mockMvc.perform(post("/api/proxy/fetch-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base_url\":\"http://192.168.1.1/admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SECURITY_VIOLATION"));
    }

    @Test
    void listProxies_withSearch() throws Exception {
        when(proxyPoolService.getAllProxies(0, 20, null, null, null, "192.168"))
                .thenReturn(PageResponse.of(0L, List.of()));

        mockMvc.perform(get("/api/proxy").param("search", "192.168"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void logs_returnsPageResponse() throws Exception {
        when(proxyPoolService.getProxyHealthLogs(1L, 0, 20))
                .thenReturn(PageResponse.of(0L, List.of()));

        mockMvc.perform(get("/api/proxy/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
