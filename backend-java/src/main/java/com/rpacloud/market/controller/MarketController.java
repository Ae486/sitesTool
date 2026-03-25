package com.rpacloud.market.controller;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.flow.dto.FlowResponse;
import com.rpacloud.market.dto.MarketFlowDetailResponse;
import com.rpacloud.market.dto.MarketFlowResponse;
import com.rpacloud.market.dto.MarketPublishRequest;
import com.rpacloud.market.service.MarketService;
import com.rpacloud.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    @GetMapping("/flows")
    public PageResponse<MarketFlowResponse> list(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        return marketService.list(skip, limit);
    }

    @GetMapping("/flows/{id}")
    public MarketFlowDetailResponse getById(@PathVariable Long id) {
        return marketService.getById(id);
    }

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketFlowResponse publish(@AuthenticationPrincipal User currentUser,
                                       @Valid @RequestBody MarketPublishRequest request) {
        return marketService.publish(request, currentUser.getId());
    }

    @PostMapping("/flows/{id}/install")
    @ResponseStatus(HttpStatus.CREATED)
    public FlowResponse install(@AuthenticationPrincipal User currentUser,
                                @PathVariable Long id) {
        return marketService.install(id, currentUser.getId());
    }

    @DeleteMapping("/flows/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpublish(@AuthenticationPrincipal User currentUser,
                          @PathVariable Long id) {
        marketService.unpublish(id, currentUser.getId());
    }
}
