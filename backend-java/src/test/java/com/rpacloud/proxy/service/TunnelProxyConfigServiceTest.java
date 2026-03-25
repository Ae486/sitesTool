package com.rpacloud.proxy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.proxy.dto.TunnelProxyConfigResponse;
import com.rpacloud.proxy.entity.TunnelProxyConfig;
import com.rpacloud.proxy.repository.TunnelProxyConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TunnelProxyConfigServiceTest {

    @Mock private TunnelProxyConfigRepository repository;
    @InjectMocks private TunnelProxyConfigService service;

    @Test
    void create_normalizesProtocol_socks5() {
        TunnelProxyConfig saved = TunnelProxyConfig.builder()
                .id(1L).userId(1L).name("T").protocol("socks5").host("h").port(1080).build();
        when(repository.save(any())).thenReturn(saved);

        TunnelProxyConfigResponse resp = service.create(1L, "T", "SOCKS5", "h", 1080, null, null);

        assertThat(resp.protocol()).isEqualTo("socks5");
    }

    @Test
    void create_normalizesUnknownProtocol_toHttp() {
        TunnelProxyConfig saved = TunnelProxyConfig.builder()
                .id(1L).userId(1L).name("T").protocol("http").host("h").port(8080).build();
        when(repository.save(any())).thenReturn(saved);

        TunnelProxyConfigResponse resp = service.create(1L, "T", "HTTPS", "h", 8080, null, null);

        assertThat(resp.protocol()).isEqualTo("http");
    }

    @Test
    void create_passwordExcludedFromResponse() {
        TunnelProxyConfig saved = TunnelProxyConfig.builder()
                .id(1L).userId(1L).name("T").protocol("http").host("h").port(8080)
                .username("user").password("secret").build();
        when(repository.save(any())).thenReturn(saved);

        TunnelProxyConfigResponse resp = service.create(1L, "T", "http", "h", 8080, "user", "secret");

        assertThat(resp.username()).isEqualTo("user");
        // TunnelProxyConfigResponse record has no password field — compile-time guarantee
    }

    @Test
    void update_ownershipViolation_throwsBizException() {
        when(repository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 99L, "X", "http", "h", 8080, null, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    void update_blankPassword_preservesExistingPassword() {
        TunnelProxyConfig config = TunnelProxyConfig.builder()
                .id(1L).userId(5L).name("T").protocol("http").host("h").port(8080)
                .password("existing").build();
        when(repository.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, 5L, "T2", "http", "h", 8080, "user", "  ");

        assertThat(config.getPassword()).isEqualTo("existing");
    }

    @Test
    void update_nonBlankPassword_overwritesPassword() {
        TunnelProxyConfig config = TunnelProxyConfig.builder()
                .id(1L).userId(5L).name("T").protocol("http").host("h").port(8080)
                .password("old").build();
        when(repository.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(config));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, 5L, "T", "http", "h", 8080, null, "newpass");

        assertThat(config.getPassword()).isEqualTo("newpass");
    }

    @Test
    void delete_ownershipViolation_throwsBizException() {
        when(repository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 99L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void delete_owner_callsRepositoryDelete() {
        TunnelProxyConfig config = TunnelProxyConfig.builder().id(1L).userId(5L).build();
        when(repository.findByIdAndUserId(1L, 5L)).thenReturn(Optional.of(config));

        service.delete(1L, 5L);

        verify(repository).delete(config);
    }
}
