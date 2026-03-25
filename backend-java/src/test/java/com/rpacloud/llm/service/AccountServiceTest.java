package com.rpacloud.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.llm.entity.Account;
import com.rpacloud.llm.repository.AccountRepository;
import com.rpacloud.llm.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepo;
    @Mock private TransactionRepository txRepo;
    @InjectMocks private AccountService accountService;

    private Account makeAccount(Long userId, long balance, long frozen) {
        Account a = new Account();
        a.setId(1L);
        a.setUserId(userId);
        a.setBalance(balance);
        a.setFrozen(frozen);
        return a;
    }

    @Test
    void freezeSuccess() {
        Account account = makeAccount(1L, 10000L, 0L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-1:freeze")).thenReturn(false);
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.freeze(1L, "exec-1", 5000L);

        assertThat(account.getFrozen()).isEqualTo(5000L);
        verify(txRepo).save(any());
    }

    @Test
    void freezeIdempotent() {
        Account account = makeAccount(1L, 10000L, 0L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-1:freeze")).thenReturn(true);

        accountService.freeze(1L, "exec-1", 5000L);

        verify(accountRepo, never()).save(any());
    }

    @Test
    void freezeInsufficientBalance() {
        Account account = makeAccount(1L, 3000L, 1000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-2:freeze")).thenReturn(false);

        assertThatThrownBy(() -> accountService.freeze(1L, "exec-2", 5000L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void freezeAccountNotFound() {
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.freeze(1L, "exec-3", 1000L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void settleSuccess() {
        Account account = makeAccount(1L, 10000L, 5000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-1:settle")).thenReturn(false);
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.settle(1L, "exec-1", 3000L, 5000L);

        assertThat(account.getFrozen()).isEqualTo(0L);
        assertThat(account.getBalance()).isEqualTo(7000L);
        verify(txRepo).save(any());
    }

    @Test
    void settleFrozenFloorAtZero() {
        Account account = makeAccount(1L, 10000L, 2000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-x:settle")).thenReturn(false);
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.settle(1L, "exec-x", 1000L, 5000L);

        assertThat(account.getFrozen()).isEqualTo(0L);
        assertThat(account.getBalance()).isEqualTo(9000L);
    }

    @Test
    void settleIdempotent() {
        Account account = makeAccount(1L, 10000L, 5000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-1:settle")).thenReturn(true);

        accountService.settle(1L, "exec-1", 3000L, 5000L);

        verify(accountRepo, never()).save(any());
    }

    @Test
    void refundSuccess() {
        Account account = makeAccount(1L, 10000L, 5000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-1:refund")).thenReturn(false);
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.refund(1L, "exec-1", 5000L);

        assertThat(account.getFrozen()).isEqualTo(0L);
        assertThat(account.getBalance()).isEqualTo(10000L);
    }

    @Test
    void refundFrozenFloorAtZero() {
        Account account = makeAccount(1L, 10000L, 2000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-y:refund")).thenReturn(false);
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.refund(1L, "exec-y", 5000L);

        assertThat(account.getFrozen()).isEqualTo(0L);
    }

    @Test
    void refundIdempotent() {
        Account account = makeAccount(1L, 10000L, 5000L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-1:refund")).thenReturn(true);

        accountService.refund(1L, "exec-1", 5000L);

        verify(accountRepo, never()).save(any());
    }

    @Test
    void chargeCreatesNewAccountIfNotExists() {
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.empty());
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.charge(1L, 10000L);

        verify(accountRepo).save(argThat(a -> a.getBalance() == 10000L));
        verify(txRepo).save(any());
    }

    @Test
    void chargeAddsToExistingBalance() {
        Account account = makeAccount(1L, 5000L, 0L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.charge(1L, 3000L);

        assertThat(account.getBalance()).isEqualTo(8000L);
    }

    @Test
    void settleBalanceCappedAtZero() {
        Account account = makeAccount(1L, 100L, 500L);
        when(accountRepo.findByUserIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(txRepo.existsByIdempotencyKey("exec-cap:settle")).thenReturn(false);
        when(accountRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.settle(1L, "exec-cap", 300L, 500L);

        assertThat(account.getBalance()).isEqualTo(0L);
        assertThat(account.getFrozen()).isEqualTo(0L);
    }

    @Test
    void getAccountReturnsNullIfNotFound() {
        when(accountRepo.findByUserId(99L)).thenReturn(Optional.empty());

        assertThat(accountService.getAccount(99L)).isNull();
    }
}
