package com.rpacloud.integration.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rpacloud.common.exception.BizException;
import com.rpacloud.common.exception.ErrorCode;
import com.rpacloud.integration.BaseIntegrationTest;
import com.rpacloud.llm.entity.Account;
import com.rpacloud.llm.entity.Transaction;
import com.rpacloud.llm.repository.AccountRepository;
import com.rpacloud.llm.repository.TransactionRepository;
import com.rpacloud.llm.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

class AccountServiceIT extends BaseIntegrationTest {

    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Account createAccount(Long userId, long balance) {
        Account a = new Account();
        a.setUserId(userId);
        a.setBalance(balance);
        a.setFrozen(0L);
        return accountRepository.save(a);
    }

    @Test
    void chargeCreatesAccountAndTransaction() {
        accountService.charge(1L, 10000L);

        Account account = accountService.getAccount(1L);
        assertThat(account).isNotNull();
        assertThat(account.getBalance()).isEqualTo(10000L);

        Page<Transaction> txs = accountService.getTransactions(1L, 0, 10);
        assertThat(txs.getContent()).hasSize(1);
        assertThat(txs.getContent().get(0).getType()).isEqualTo("charge");
        assertThat(txs.getContent().get(0).getAmount()).isEqualTo(10000L);
    }

    @Test
    void freezeSettleCycle() {
        createAccount(1L, 10000L);

        accountService.freeze(1L, "exec-1", 5000L);
        Account afterFreeze = accountService.getAccount(1L);
        assertThat(afterFreeze.getBalance()).isEqualTo(10000L);
        assertThat(afterFreeze.getFrozen()).isEqualTo(5000L);

        accountService.settle(1L, "exec-1", 3000L, 5000L);
        Account afterSettle = accountService.getAccount(1L);
        assertThat(afterSettle.getBalance()).isEqualTo(7000L);
        assertThat(afterSettle.getFrozen()).isEqualTo(0L);
    }

    @Test
    void freezeRefundCycle() {
        createAccount(1L, 10000L);

        accountService.freeze(1L, "exec-2", 4000L);
        accountService.refund(1L, "exec-2", 4000L);

        Account afterRefund = accountService.getAccount(1L);
        assertThat(afterRefund.getBalance()).isEqualTo(10000L);
        assertThat(afterRefund.getFrozen()).isEqualTo(0L);
    }

    @Test
    void freezeInsufficientBalance() {
        createAccount(1L, 3000L);

        assertThatThrownBy(() -> accountService.freeze(1L, "exec-3", 5000L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void freezeIdempotent() {
        createAccount(1L, 10000L);

        accountService.freeze(1L, "exec-4", 3000L);
        accountService.freeze(1L, "exec-4", 3000L);

        Account account = accountService.getAccount(1L);
        assertThat(account.getFrozen()).isEqualTo(3000L);
    }

    @Test
    void settleIdempotent() {
        createAccount(1L, 10000L);
        accountService.freeze(1L, "exec-5", 5000L);

        accountService.settle(1L, "exec-5", 3000L, 5000L);
        accountService.settle(1L, "exec-5", 3000L, 5000L);

        Account account = accountService.getAccount(1L);
        assertThat(account.getBalance()).isEqualTo(7000L);
    }

    @Test
    void transactionHistoryPagination() {
        createAccount(1L, 50000L);
        for (int i = 0; i < 5; i++) {
            accountService.charge(1L, 1000L);
        }

        Page<Transaction> page1 = accountService.getTransactions(1L, 0, 3);
        assertThat(page1.getContent()).hasSize(3);
        assertThat(page1.getTotalElements()).isEqualTo(5);

        Page<Transaction> page2 = accountService.getTransactions(1L, 3, 3);
        assertThat(page2.getContent()).hasSize(2);
    }

    @Test
    void settleFrozenFloorAtZero() {
        createAccount(1L, 10000L);
        accountService.freeze(1L, "exec-6", 2000L);

        accountService.settle(1L, "exec-6", 1000L, 5000L);

        Account account = accountService.getAccount(1L);
        assertThat(account.getFrozen()).isEqualTo(0L);
        assertThat(account.getBalance()).isEqualTo(9000L);
    }
}
