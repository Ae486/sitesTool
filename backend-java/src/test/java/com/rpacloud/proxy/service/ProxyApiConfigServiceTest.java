package com.rpacloud.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.proxy.dto.ProxyApiConfigResponse;
import com.rpacloud.proxy.entity.ProxyApiConfig;
import com.rpacloud.proxy.repository.ProxyApiConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyApiConfigServiceTest {

    @Mock private ProxyApiConfigRepository repository;
    @InjectMocks private ProxyApiConfigService service;

    @Test
    void list_returnsOnlyCurrentUserConfigs() {
        ProxyApiConfig c1 = ProxyApiConfig.builder().id(1L).userId(10L).name("A").baseUrl("http://a.com").build();
        ProxyApiConfig c2 = ProxyApiConfig.builder().id(2L).userId(10L).name("B").baseUrl("http://b.com").build();
        when(repository.findAllByUserId(10L)).thenReturn(List.of(c1, c2));

        List<ProxyApiConfigResponse> result = service.list(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("A");
        assertThat(result.get(1).name()).isEqualTo("B");
    }

    @Test
    void list_differentUser_returnsEmpty() {
        when(repository.findAllByUserId(99L)).thenReturn(List.of());
        assertThat(service.list(99L)).isEmpty();
    }

    @Test
    void create_savesConfigWithCorrectUserId() {
        ProxyApiConfig saved = ProxyApiConfig.builder()
                .id(1L).userId(5L).name("Test").baseUrl("http://t.com").paramsJson("[{}]").build();
        when(repository.save(any())).thenReturn(saved);

        ProxyApiConfigResponse resp = service.create(5L, "Test", "http://t.com", "[{}]");

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.name()).isEqualTo("Test");
        assertThat(resp.baseUrl()).isEqualTo("http://t.com");
        assertThat(resp.paramsJson()).isEqualTo("[{}]");
    }

    @Test
    void update_ownershipViolation_throwsBizException() {
        when(repository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 99L, "X", "http://x.com", null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void update_ownerMutatesFields() {
        ProxyApiConfig config = ProxyApiConfig.builder()
                .id(1L).userId(5L).name("Old").baseUrl("http://old.com").build();
        when(repository.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, 5L, "New", "http://new.com", "[{\"key\":\"k\"}]");

        assertThat(config.getName()).isEqualTo("New");
        assertThat(config.getBaseUrl()).isEqualTo("http://new.com");
        assertThat(config.getParamsJson()).isEqualTo("[{\"key\":\"k\"}]");
    }

    @Test
    void delete_ownershipViolation_throwsBizException() {
        when(repository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void delete_owner_callsRepositoryDelete() {
        ProxyApiConfig config = ProxyApiConfig.builder().id(1L).userId(5L).build();
        when(repository.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(config));

        service.delete(1L, 5L);

        verify(repository).delete(config);
    }
}
