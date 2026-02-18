package com.rpacloud.llm.service;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.entity.Account;
import com.rpacloud.llm.entity.Transaction;
import com.rpacloud.llm.repository.AccountRepository;
import com.rpacloud.llm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txRepo;

    @Transactional
    public void freeze(Long userId, String executionId, long estimatedTokens) {
        Account account = accountRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        String idemKey = executionId + ":freeze";
        if (txRepo.existsByIdempotencyKey(idemKey)) {
            log.info("Freeze already applied: {}", idemKey);
            return;
        }

        long available = account.getBalance() - account.getFrozen();
        if (available < estimatedTokens) {
            throw new BizException(ErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient balance: available=" + available + ", required=" + estimatedTokens);
        }

        account.setFrozen(account.getFrozen() + estimatedTokens);
        accountRepo.save(account);
        saveTxSafe(userId, executionId, "freeze", -estimatedTokens, account.getBalance(), idemKey);
        log.info("Frozen {} tokens for user {}, execution {}", estimatedTokens, userId, executionId);
    }

    @Transactional
    public void settle(Long userId, String executionId, long actualTokens, long estimatedTokens) {
        Account account = accountRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        String idemKey = executionId + ":settle";
        if (txRepo.existsByIdempotencyKey(idemKey)) {
            log.info("Settle already applied: {}", idemKey);
            return;
        }

        long frozenAfter = account.getFrozen() - estimatedTokens;
        if (frozenAfter < 0) frozenAfter = 0;
        account.setFrozen(frozenAfter);

        long balanceAfter = account.getBalance() - actualTokens;
        if (balanceAfter < 0) {
            log.warn("Balance went negative for user {}: {} - {} = {}", userId, account.getBalance(), actualTokens, balanceAfter);
        }
        account.setBalance(balanceAfter);
        accountRepo.save(account);

        saveTxSafe(userId, executionId, "settle", -actualTokens, account.getBalance(), idemKey);
        log.info("Settled {} tokens for user {}, execution {}", actualTokens, userId, executionId);
    }

    @Transactional
    public void refund(Long userId, String executionId, long frozenAmount) {
        Account account = accountRepo.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found"));

        String idemKey = executionId + ":refund";
        if (txRepo.existsByIdempotencyKey(idemKey)) {
            log.info("Refund already applied: {}", idemKey);
            return;
        }

        long frozenAfter = account.getFrozen() - frozenAmount;
        if (frozenAfter < 0) frozenAfter = 0;
        account.setFrozen(frozenAfter);
        accountRepo.save(account);

        saveTxSafe(userId, executionId, "refund", frozenAmount, account.getBalance(), idemKey);
        log.info("Refunded {} tokens for user {}, execution {}", frozenAmount, userId, executionId);
    }

    @Transactional
    public void charge(Long userId, long amount) {
        Account account = accountRepo.findByUserIdForUpdate(userId).orElseGet(() -> {
            Account a = new Account();
            a.setUserId(userId);
            a.setBalance(0L);
            a.setFrozen(0L);
            return a;
        });

        account.setBalance(account.getBalance() + amount);
        accountRepo.save(account);

        saveTxSafe(userId, null, "charge", amount, account.getBalance(), null);
        log.info("Charged {} tokens for user {}", amount, userId);
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long userId) {
        return accountRepo.findByUserId(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getTransactions(Long userId, int skip, int limit) {
        int safeLimit = Math.max(limit, 1);
        int safeSkip = Math.max(skip, 0);
        int page = safeSkip / safeLimit;
        return txRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, safeLimit));
    }

    private void saveTxSafe(Long userId, String executionId, String type, long amount, long balanceAfter, String idemKey) {
        try {
            Transaction tx = new Transaction();
            tx.setUserId(userId);
            tx.setExecutionId(executionId);
            tx.setType(type);
            tx.setAmount(amount);
            tx.setBalanceAfter(balanceAfter);
            tx.setIdempotencyKey(idemKey);
            txRepo.save(tx);
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotent transaction already exists: {}", idemKey);
        }
    }
}
