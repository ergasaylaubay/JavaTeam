package kz.halyk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import kz.halyk.member2.TransferService;
import org.junit.jupiter.api.Test;

class Member2Test extends BankTestBase {
  @Test
  void transferConservesMoney() throws Exception {
    balance(1, 1_000_000);
    balance(2, 200_000);
    assertEquals("SUCCESS", transfers.transfer("one", 1, 2, new BigDecimal("3000")).status());
    assertEquals(700_000, clients.account(1).balanceMinor());
    assertEquals(500_000, clients.account(2).balanceMinor());
    assertEquals(1, transfers.history().size());
  }

  @Test
  void duplicateDoesNotDebitTwiceAndConflictingIdRejected() throws Exception {
    transfers.transfer("same", 1, 2, new BigDecimal("1000"));
    transfers.transfer("same", 1, 2, new BigDecimal("1000.00"));
    assertEquals(100_000, clients.account(2).balanceMinor());
    assertEquals(1, transfers.history().size());
    assertThrows(
        IllegalArgumentException.class,
        () -> transfers.transfer("same", 1, 2, new BigDecimal("2000")));
  }

  @Test
  void invalidAmountsAndAccountsDoNotMoveMoney() throws Exception {
    for (String invalid : List.of("0", "-1", "0.001", "100000000000000000000"))
      assertThrows(
          IllegalArgumentException.class,
          () -> transfers.transfer("bad", 1, 2, new BigDecimal(invalid)));
    assertEquals("FAILED", transfers.transfer("missing", 1, 999, new BigDecimal("10")).status());
    assertEquals("FAILED", transfers.transfer("self", 1, 1, new BigDecimal("10")).status());
    assertEquals("FAILED", transfers.transfer("currency", 1, 3, new BigDecimal("10")).status());
    assertEquals(100_000_000, clients.account(1).balanceMinor());
  }

  @Test
  void insufficientFundsLogged() throws Exception {
    balance(1, 100);
    assertEquals("FAILED", transfers.transfer("poor", 1, 2, new BigDecimal("2")).status());
    assertEquals(100, clients.account(1).balanceMinor());
    assertEquals("FAILED", transfers.history().get(0).status());
  }

  @Test
  void rollbackAfterDebitForTransferAndExchange() throws Exception {
    var broken =
        new TransferService(db, exchange, fraud, clock) {
          @Override
          protected void afterDebit() throws SQLException {
            throw new SQLException("injected");
          }
        };
    assertEquals("FAILED", broken.transfer("rollback1", 1, 2, new BigDecimal("1000")).status());
    assertEquals("FAILED", broken.exchange("rollback2", 1, 3, new BigDecimal("50000")).status());
    assertEquals(100_000_000, clients.account(1).balanceMinor());
    assertEquals(0, clients.account(2).balanceMinor());
    assertEquals(100_000, clients.account(3).balanceMinor());
    assertTrue(transfers.history().stream().allMatch(o -> o.status().equals("FAILED")));
  }

  @Test
  void exchangeUpdatesBothBalancesOnce() throws Exception {
    assertEquals("SUCCESS", transfers.exchange("fx", 1, 3, new BigDecimal("50000")).status());
    transfers.exchange("fx", 1, 3, new BigDecimal("50000"));
    assertEquals(95_000_000, clients.account(1).balanceMinor());
    assertEquals(110_000, clients.account(3).balanceMinor());
    assertEquals("EXCHANGE", transfers.history().get(0).type());
  }

  @Test
  void parallelBatchHasTenSuccessAndTenFailures() throws Exception {
    balance(1, 1_000_000);
    List<TransferService.BatchItem> items = new ArrayList<>();
    for (int i = 0; i < 20; i++)
      items.add(new TransferService.BatchItem("batch-" + i, 1, 2, new BigDecimal("1000")));
    var results = transfers.batch(items);
    assertEquals(10, results.stream().filter(r -> r.status().equals("SUCCESS")).count());
    assertEquals(10, results.stream().filter(r -> r.status().equals("FAILED")).count());
    assertEquals(0, clients.account(1).balanceMinor());
    assertEquals(1_000_000, clients.account(2).balanceMinor());
    assertEquals(20, transfers.history().size());
  }

  @Test
  void historyTimeIntervalIsHalfOpen() throws Exception {
    transfers.transfer("time", 1, 2, new BigDecimal("1"));
    assertEquals(1, transfers.history(1, clock.instant(), clock.instant().plusSeconds(1)).size());
    assertEquals(0, transfers.history(1, clock.instant().minusSeconds(1), clock.instant()).size());
  }
}
