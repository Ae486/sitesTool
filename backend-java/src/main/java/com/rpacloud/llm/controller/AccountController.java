package com.rpacloud.llm.controller;

import java.util.Map;

import com.rpacloud.common.dto.PageResponse;
import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.entity.Account;
import com.rpacloud.llm.entity.Transaction;
import com.rpacloud.llm.service.AccountService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public Map<String, Object> getBalance() {
        // TODO: get userId from SecurityContext (enterprise); hardcode 1L for open-source
        Long userId = 1L;
        Account account = accountService.getAccount(userId);
        if (account == null) {
            return Map.of("user_id", userId, "balance", 0, "frozen", 0, "available", 0);
        }
        return Map.of(
                "user_id", account.getUserId(),
                "balance", account.getBalance(),
                "frozen", account.getFrozen(),
                "available", account.getBalance() - account.getFrozen());
    }

    @GetMapping("/transactions")
    public PageResponse<Transaction> getTransactions(
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = 1L;
        Page<Transaction> page = accountService.getTransactions(userId, skip, limit);
        return new PageResponse<>(page.getTotalElements(), page.getContent());
    }

    @PostMapping("/charge")
    public Map<String, Object> charge(@RequestBody ChargeRequest req) {
        // Open-source single-user: always charge current user
        // TODO: admin role check in enterprise mode
        Long userId = 1L;
        if (req.getAmount() == null || req.getAmount() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "amount must be positive");
        }
        accountService.charge(userId, req.getAmount());
        Account account = accountService.getAccount(userId);
        return Map.of(
                "user_id", userId,
                "balance", account != null ? account.getBalance() : req.getAmount(),
                "message", "Charged " + req.getAmount() + " tokens");
    }

    @Data
    public static class ChargeRequest {
        @NotNull
        private Long userId;
        @NotNull @Min(1)
        private Long amount;
    }
}
