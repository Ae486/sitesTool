package com.rpacloud.llm.controller;

import java.util.UUID;

import com.rpacloud.common.config.RpaProperties;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.dto.InternalLlmRequest;
import com.rpacloud.llm.dto.LlmChatRequest;
import com.rpacloud.llm.dto.LlmChatResponse;
import com.rpacloud.llm.service.AccountService;
import com.rpacloud.llm.service.InternalTokenProvider;
import com.rpacloud.llm.service.LlmGatewayService;
import com.rpacloud.llm.service.RateLimiter;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/llm")
@RequiredArgsConstructor
public class InternalLlmController {

    private final InternalTokenProvider internalTokenProvider;
    private final LlmGatewayService llmGatewayService;
    private final AccountService accountService;
    private final RateLimiter rateLimiter;
    private final RpaProperties rpaProperties;

    @PostMapping("/chat")
    public LlmChatResponse chat(@RequestBody InternalLlmRequest req) {
        Claims claims = internalTokenProvider.validateAndParse(req.getInternalToken());
        if (claims == null) {
            throw new BizException(ErrorCode.INVALID_INTERNAL_TOKEN, "Invalid or expired internal token");
        }

        Long userId = claims.get("user_id", Long.class);
        String executionId = claims.getSubject();
        if (userId == null || executionId == null) {
            throw new BizException(ErrorCode.INVALID_INTERNAL_TOKEN, "Malformed internal token");
        }
        log.info("Internal LLM chat: userId={}, executionId={}", userId, executionId);

        String rateLimitKey = "user:" + userId;
        if (!rateLimiter.tryAcquire(rateLimitKey, rpaProperties.getLlm().getDefaultRpm())) {
            throw new BizException(ErrorCode.LLM_QUOTA_EXCEEDED, "Rate limit exceeded");
        }

        // Billing: freeze estimated tokens (skip if no billing account)
        String billingId = executionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        long estimatedTokens = req.getMaxTokens() != null
                ? req.getMaxTokens()
                : rpaProperties.getLlm().getPreFreezePadding();
        boolean billed = false;
        try {
            accountService.freeze(userId, billingId, estimatedTokens);
            billed = true;
        } catch (BizException e) {
            if (e.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) throw e;
            log.debug("No billing account for user {}, proceeding without billing", userId);
        }

        LlmChatRequest chatReq = new LlmChatRequest();
        chatReq.setPrompt(req.getPrompt());
        chatReq.setModel(req.getModel());
        chatReq.setMaxTokens(req.getMaxTokens());
        chatReq.setTemperature(req.getTemperature());

        try {
            LlmChatResponse response = llmGatewayService.chat(chatReq);
            if (billed) {
                accountService.settle(userId, billingId, response.getTokensUsed(), estimatedTokens);
            }
            return response;
        } catch (Exception e) {
            if (billed) {
                accountService.refund(userId, billingId, estimatedTokens);
            }
            throw e;
        }
    }
}
