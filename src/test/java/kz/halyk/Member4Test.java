package kz.halyk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import kz.halyk.member2.TransferService;
import kz.halyk.member4.ReportService;
import kz.halyk.model.Currency;
import kz.halyk.model.Operation;
import org.junit.jupiter.api.Test;

class Member4Test extends BankTestBase {
  @Test
  void riskyTransferWaitsThenApprovesOnlyOnce() throws Exception {
    var r = transfers.transfer("risk", 1, 2, new BigDecimal("150000"));
    assertEquals("PENDING_CONFIRMATION", r.status());
    assertEquals(100_000_000, clients.account(1).balanceMinor());
    assertTrue(transfers.history().isEmpty());
    assertEquals("SUCCESS", transfers.decide("risk", true).status());
    transfers.decide("risk", true);
    transfers.transfer("risk", 1, 2, new BigDecimal("150000"));
    assertEquals(85_000_000, clients.account(1).balanceMinor());
    assertEquals(15_000_000, clients.account(2).balanceMinor());
    assertEquals(1, transfers.history().size());
  }

  @Test
  void rejectionAndExpiryNeverDebit() throws Exception {
    transfers.transfer("reject", 1, 2, new BigDecimal("150000"));
    assertEquals("CANCELLED", transfers.decide("reject", false).status());
    assertEquals("CANCELLED", transfers.decide("reject", true).status());
    transfers.transfer("expire", 1, 2, new BigDecimal("150000"));
    var later =
        new TransferService(db, exchange, fraud, Clock.offset(clock, Duration.ofMinutes(5)));
    assertEquals("EXPIRED", later.decide("expire", true).status());
    assertEquals(100_000_000, clients.account(1).balanceMinor());
  }

  @Test
  void confirmationRechecksFunds() throws Exception {
    transfers.transfer("pending", 1, 2, new BigDecimal("150000"));
    balance(1, 100);
    assertEquals("FAILED", transfers.decide("pending", true).status());
    assertEquals(100, clients.account(1).balanceMinor());
    assertEquals(0, clients.account(2).balanceMinor());
  }

  @Test
  void exactThresholdAndKnownRecipientAreNotFlagged() throws Exception {
    assertEquals(
        "SUCCESS", transfers.transfer("boundary", 1, 2, new BigDecimal("100000")).status());
    assertEquals("SUCCESS", transfers.transfer("known", 1, 2, new BigDecimal("150000")).status());
  }

  private Operation op(
      String id, long client, int seconds, String type, String status, Currency currency) {
    return new Operation(
        id,
        client,
        1,
        2,
        type,
        100,
        100,
        currency,
        currency,
        "1",
        clock.instant().plusSeconds(seconds),
        status,
        "");
  }

  @Test
  void sixthTransferFlaggedAcrossAccountsButNotOtherClients() {
    List<Operation> ops = new ArrayList<>();
    for (int i = 0; i < 5; i++) ops.add(op("o" + i, 1, i, "TRANSFER", "SUCCESS", Currency.KZT));
    assertTrue(fraud.frequencyAlerts(ops).isEmpty());
    ops.add(op("other", 2, 4, "TRANSFER", "SUCCESS", Currency.KZT));
    ops.add(
        new Operation(
            "o5",
            1,
            3,
            2,
            "TRANSFER",
            100,
            100,
            Currency.KZT,
            Currency.KZT,
            "1",
            clock.instant().plusSeconds(5),
            "SUCCESS",
            ""));
    var alerts = fraud.frequencyAlerts(ops);
    assertEquals(1, alerts.size());
    assertEquals("o5", alerts.get(0).operationId());
    assertEquals(6, alerts.get(0).count());
  }

  @Test
  void frequencyWindowExcludesExactTenMinutesAndIgnoresFailures() {
    List<Operation> ops = new ArrayList<>();
    ops.add(op("old", 1, 0, "TRANSFER", "SUCCESS", Currency.KZT));
    for (int i = 1; i <= 5; i++) ops.add(op("n" + i, 1, 600, "TRANSFER", "SUCCESS", Currency.KZT));
    ops.add(op("fx", 1, 600, "EXCHANGE", "SUCCESS", Currency.KZT));
    ops.add(op("fail", 1, 600, "TRANSFER", "FAILED", Currency.KZT));
    assertTrue(fraud.frequencyAlerts(ops).isEmpty());
    ops.add(op("n6", 1, 600, "TRANSFER", "SUCCESS", Currency.KZT));
    assertEquals("n6", fraud.frequencyAlerts(ops).get(0).operationId());
    Collections.reverse(ops);
    assertEquals("n6", fraud.frequencyAlerts(ops).get(0).operationId());
  }

  @Test
  void currenciesRemainSeparate() {
    var summaries =
        new ReportService()
            .summary(
                List.of(
                    op("kzt", 1, 0, "TRANSFER", "SUCCESS", Currency.KZT),
                    op("usd", 1, 1, "TRANSFER", "SUCCESS", Currency.USD)));
    assertEquals(2, summaries.size());
    summaries.forEach(s -> assertEquals(new BigDecimal("1.00"), s.amount()));
  }

  @Test
  void csvEscapesQuotesCommasNewlinesAndEmptyReport() throws Exception {
    var report = new ReportService();
    Path path = dir.resolve("test.csv");
    report.csv(path, List.of("name"), List.of(List.of("a,b\"c\nd")));
    String text = Files.readString(path);
    assertTrue(text.contains("\"a,b\"\"c\nd\""));
    report.summaryCsv(path, List.of());
    assertEquals(1, Files.readAllLines(path).size());
    assertThrows(java.io.IOException.class, () -> report.summaryCsv(dir, List.of()));
  }

  @Test
  void pendingSurvivesRestart() throws Exception {
    transfers.transfer("restart", 1, 2, new BigDecimal("150000"));
    var db2 = new kz.halyk.db.Database(dir.resolve("bank.db"));
    var again = new TransferService(db2, exchange, fraud, clock);
    assertEquals("PENDING_CONFIRMATION", again.requests().get(0).status());
    assertEquals("SUCCESS", again.decide("restart", true).status());
  }
}
