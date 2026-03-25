package com.rpacloud.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.flow.entity.AutomationFlow;
import com.rpacloud.flow.entity.FlowStatus;
import com.rpacloud.flow.repository.FlowRepository;
import com.rpacloud.market.dto.MarketFlowResponse;
import com.rpacloud.market.dto.MarketPublishRequest;
import com.rpacloud.market.entity.MarketplaceFlow;
import com.rpacloud.market.repository.MarketplaceFlowRepository;
import com.rpacloud.site.entity.Site;
import com.rpacloud.user.entity.User;
import com.rpacloud.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

    @Mock private MarketplaceFlowRepository marketRepository;
    @Mock private FlowRepository flowRepository;
    @Mock private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private MarketService marketService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        marketService = new MarketService(marketRepository, flowRepository, userRepository, objectMapper);
    }

    @Test
    void publish_success() {
        AutomationFlow flow = AutomationFlow.builder()
                .id(42L).name("Test").dsl("{\"steps\":[]}").build();
        when(flowRepository.findById(42L)).thenReturn(Optional.of(flow));
        when(marketRepository.findByFlowIdAndAuthorIdAndIsActiveTrue(42L, 1L)).thenReturn(Optional.empty());
        when(marketRepository.save(any())).thenAnswer(inv -> {
            MarketplaceFlow saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().fullName("Admin").build()));

        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(42L);
        req.setTitle("My Workflow");
        req.setDescription("Test description");

        MarketFlowResponse result = marketService.publish(req, 1L);
        assertThat(result.getTitle()).isEqualTo("My Workflow");
    }

    @Test
    void publish_duplicateThrows() {
        AutomationFlow flow = AutomationFlow.builder().id(42L).dsl("{}").build();
        when(flowRepository.findById(42L)).thenReturn(Optional.of(flow));
        when(marketRepository.findByFlowIdAndAuthorIdAndIsActiveTrue(42L, 1L))
                .thenReturn(Optional.of(new MarketplaceFlow()));

        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(42L);
        req.setTitle("Test");

        assertThatThrownBy(() -> marketService.publish(req, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already published");
    }

    @Test
    void publish_flowNotFoundThrows() {
        when(flowRepository.findById(99L)).thenReturn(Optional.empty());

        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(99L);
        req.setTitle("Test");

        assertThatThrownBy(() -> marketService.publish(req, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void publish_sanitizesSensitiveFields() {
        String dslWithPassword = "{\"steps\":[{\"type\":\"input\",\"password\":\"secret123\",\"selector\":\"#pw\"}]}";
        AutomationFlow flow = AutomationFlow.builder()
                .id(42L).name("Test").dsl(dslWithPassword).build();
        when(flowRepository.findById(42L)).thenReturn(Optional.of(flow));
        when(marketRepository.findByFlowIdAndAuthorIdAndIsActiveTrue(42L, 1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().fullName("Admin").build()));

        ArgumentCaptor<MarketplaceFlow> captor = ArgumentCaptor.forClass(MarketplaceFlow.class);
        when(marketRepository.save(captor.capture())).thenAnswer(inv -> {
            MarketplaceFlow saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(42L);
        req.setTitle("Test");

        marketService.publish(req, 1L);

        String snapshot = captor.getValue().getDslSnapshot();
        assertThat(snapshot).doesNotContain("secret123");
    }

    @Test
    void install_success() {
        Site site = new Site();
        site.setId(1L);
        AutomationFlow sourceFlow = AutomationFlow.builder()
                .id(42L).site(site).siteId(1L).name("Original").build();

        MarketplaceFlow mf = MarketplaceFlow.builder()
                .id(1L).flowId(42L).title("Marketplace Flow")
                .description("Test").dslSnapshot("{\"steps\":[]}").build();

        when(marketRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(mf));
        when(flowRepository.findById(42L)).thenReturn(Optional.of(sourceFlow));
        when(flowRepository.save(any())).thenAnswer(inv -> {
            AutomationFlow saved = inv.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        var result = marketService.install(1L, 2L);
        assertThat(result.getName()).contains("installed");
        verify(marketRepository).incrementDownloadCount(1L);
    }

    @Test
    void unpublish_authorOnly() {
        MarketplaceFlow mf = MarketplaceFlow.builder()
                .id(1L).authorId(1L).build();
        when(marketRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(mf));

        assertThatThrownBy(() -> marketService.unpublish(1L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("author");
    }

    @Test
    void publish_sanitizesCamelCaseApiKey() {
        String dslWithApiKey = "{\"steps\":[{\"type\":\"http_request\",\"apiKey\":\"sk-secret-123\",\"url\":\"https://api.com\"}]}";
        AutomationFlow flow = AutomationFlow.builder()
                .id(42L).name("Test").dsl(dslWithApiKey).build();
        when(flowRepository.findById(42L)).thenReturn(Optional.of(flow));
        when(marketRepository.findByFlowIdAndAuthorIdAndIsActiveTrue(42L, 1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().fullName("Admin").build()));

        ArgumentCaptor<MarketplaceFlow> captor = ArgumentCaptor.forClass(MarketplaceFlow.class);
        when(marketRepository.save(captor.capture())).thenAnswer(inv -> {
            MarketplaceFlow saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        MarketPublishRequest req = new MarketPublishRequest();
        req.setFlowId(42L);
        req.setTitle("Test");

        marketService.publish(req, 1L);

        String snapshot = captor.getValue().getDslSnapshot();
        assertThat(snapshot).doesNotContain("sk-secret-123");
        assertThat(snapshot).contains("\"apiKey\":\"\"");
    }
}
