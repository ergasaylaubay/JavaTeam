package kz.halyk.member2;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import kz.halyk.common.Money;
import kz.halyk.dao.*;
import kz.halyk.db.Database;
import kz.halyk.member1.ExchangeService;
import kz.halyk.member4.FraudService;
import kz.halyk.model.*;

public class TransferService {
  private final Database db;
  private final Clock clock;
  private final AccountDao accounts = new AccountDao();
  private final JournalDao journal = new JournalDao();
  private final ExchangeService exchange;
  private final FraudService fraud;

  public TransferService(Database db, ExchangeService exchange, FraudService fraud, Clock clock) {
    this.db = db;
    this.exchange = exchange;
    this.fraud = fraud;
    this.clock = clock;
  }

  public TransferRequest transfer(String id, long source, long target, BigDecimal amount)
      throws SQLException {
    return submit(id, source, target, amount, "TRANSFER");
  }

  public TransferRequest exchange(String id, long source, long target, BigDecimal amount)
      throws SQLException {
    return submit(id, source, target, amount, "EXCHANGE");
  }

  private TransferRequest submit(
      String id, long source, long target, BigDecimal amount, String type) throws SQLException {
    if (id == null || id.isBlank())
      throw new IllegalArgumentException("Operation ID бос болмауы керек");
    long minor = Money.minor(amount);
    Instant now = clock.instant();
    TransferRequest request =
        new TransferRequest(
            id, source, target, minor, type, "PENDING_CONFIRMATION", "", now, now.plusSeconds(300));
    try {
      return db.transaction(
          c -> {
            Optional<TransferRequest> existing = journal.request(c, id);
            if (existing.isPresent()) {
              TransferRequest old = existing.get();
              if (old.sourceId() != source
                  || old.targetId() != target
                  || old.amountMinor() != minor
                  || !old.type().equals(type))
                throw new IllegalArgumentException("Бұл ID басқа сұрауға тиесілі");
              return expireIfNeeded(c, old);
            }
            journal.insertRequest(c, request);
            Validated valid;
            try {
              valid = validate(c, request);
            } catch (IllegalArgumentException ex) {
              return fail(c, request, ex.getMessage());
            }
            String reason =
                type.equals("TRANSFER") ? fraud.assess(c, valid.source(), target, minor) : "";
            if (!reason.isEmpty()) {
              journal.finish(c, id, "PENDING_CONFIRMATION", reason);
              return journal.request(c, id).orElseThrow();
            }
            return execute(c, request, valid);
          });
    } catch (SQLException ex) {
      return saveDatabaseFailure(request, ex);
    }
  }

  private record Validated(
      Account source, Account target, long received, long newTargetBalance, String rate) {}

  private Validated validate(Connection c, TransferRequest r) throws SQLException {
    Account a =
        accounts
            .find(c, r.sourceId())
            .orElseThrow(() -> new IllegalArgumentException("Жіберуші шот табылмады"));
    Account b =
        accounts
            .find(c, r.targetId())
            .orElseThrow(() -> new IllegalArgumentException("Алушы шот табылмады"));
    if (a.id() == b.id()) throw new IllegalArgumentException("Өз шотына аударуға болмайды");
    if (a.type() != AccountType.CARD || b.type() != AccountType.CARD)
      throw new IllegalArgumentException("Тек CARD шоттары");
    if (a.status() != AccountStatus.ACTIVE || b.status() != AccountStatus.ACTIVE)
      throw new IllegalArgumentException("Шот бұғатталған");
    if (a.balanceMinor() < r.amountMinor())
      throw new IllegalArgumentException("Қаражат жеткіліксіз");
    long received = r.amountMinor();
    String rate = "1";
    if (r.type().equals("TRANSFER")) {
      if (a.currency() != b.currency())
        throw new IllegalArgumentException("Аударым валюталары бірдей болуы керек");
    } else {
      if (a.clientId() != b.clientId())
        throw new IllegalArgumentException("Айырбастау тек бір клиенттің шоттары арасында");
      received = exchange.quote(r.amountMinor(), a.currency(), b.currency());
      rate = exchange.rate(a.currency(), b.currency()).toPlainString();
    }
    long newTarget;
    try {
      newTarget = Math.addExact(b.balanceMinor(), received);
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Алушы балансы тым үлкен");
    }
    return new Validated(a, b, received, newTarget, rate);
  }

  private TransferRequest execute(Connection c, TransferRequest r, Validated v)
      throws SQLException {
    accounts.balance(c, r.sourceId(), v.source().balanceMinor() - r.amountMinor());
    afterDebit(); // Тестте осы жерде қате енгізіп, ROLLBACK тексеріледі.
    accounts.balance(c, r.targetId(), v.newTargetBalance());
    journal.operation(
        c,
        new Operation(
            r.id(),
            v.source().clientId(),
            r.sourceId(),
            r.targetId(),
            r.type(),
            r.amountMinor(),
            v.received(),
            v.source().currency(),
            v.target().currency(),
            v.rate(),
            clock.instant(),
            "SUCCESS",
            ""));
    journal.finish(c, r.id(), "SUCCESS", r.reason());
    return journal.request(c, r.id()).orElseThrow();
  }

  protected void afterDebit() throws SQLException {}

  private TransferRequest fail(Connection c, TransferRequest r, String reason) throws SQLException {
    Account a = accounts.find(c, r.sourceId()).orElse(null),
        b = accounts.find(c, r.targetId()).orElse(null);
    journal.operation(
        c,
        new Operation(
            r.id(),
            a == null ? 0 : a.clientId(),
            r.sourceId(),
            r.targetId(),
            r.type(),
            r.amountMinor(),
            0,
            a == null ? null : a.currency(),
            b == null ? null : b.currency(),
            "",
            clock.instant(),
            "FAILED",
            reason));
    journal.finish(c, r.id(), "FAILED", reason);
    return journal.request(c, r.id()).orElseThrow();
  }

  private TransferRequest saveDatabaseFailure(TransferRequest request, SQLException original)
      throws SQLException {
    try {
      return db.transaction(
          c -> {
            TransferRequest current = journal.request(c, request.id()).orElse(null);
            // Another completed attempt may already have committed. Never debit twice.
            if (current != null && !current.status().equals("PENDING_CONFIRMATION")) return current;
            if (current == null) journal.insertRequest(c, request);
            return fail(c, current == null ? request : current, "ДҚ қатесі: операция қайтарылды");
          });
    } catch (SQLException logging) {
      original.addSuppressed(logging);
      throw new SQLException(
          "ДҚ қатесі: нәтиже журналын сақтау мүмкін болмады. ID арқылы тексеріңіз.", original);
    }
  }

  public TransferRequest decide(String id, boolean approve) throws SQLException {
    TransferRequest original;
    try (Connection c = db.open()) {
      original =
          journal.request(c, id).orElseThrow(() -> new IllegalArgumentException("Сұрау табылмады"));
    }
    try {
      return db.transaction(
          c -> {
            TransferRequest r = expireIfNeeded(c, journal.request(c, id).orElseThrow());
            if (!r.status().equals("PENDING_CONFIRMATION")) return r;
            if (!approve) {
              journal.finish(c, id, "CANCELLED", "Оператор демо растаудан бас тартты");
              return journal.request(c, id).orElseThrow();
            }
            Validated valid;
            try {
              valid = validate(c, r);
            } catch (IllegalArgumentException ex) {
              return fail(c, r, ex.getMessage());
            }
            return execute(c, r, valid);
          });
    } catch (SQLException ex) {
      return saveDatabaseFailure(original, ex);
    }
  }

  private TransferRequest expireIfNeeded(Connection c, TransferRequest r) throws SQLException {
    if (r.status().equals("PENDING_CONFIRMATION") && !clock.instant().isBefore(r.expiresAt())) {
      journal.finish(c, r.id(), "EXPIRED", "5 минут ішінде расталмады");
      return journal.request(c, r.id()).orElseThrow();
    }
    return r;
  }

  public List<TransferRequest> requests() throws SQLException {
    return db.transaction(
        c -> {
          List<TransferRequest> out = new ArrayList<>();
          for (TransferRequest r : journal.requests(c)) out.add(expireIfNeeded(c, r));
          return List.copyOf(out);
        });
  }

  public List<Operation> history() throws SQLException {
    try (Connection c = db.open()) {
      return List.copyOf(journal.operations(c));
    }
  }

  public List<Operation> history(long client, Instant from, Instant to) throws SQLException {
    if (!from.isBefore(to)) throw new IllegalArgumentException("Уақыт аралығы қате");
    return history().stream()
        .filter(o -> o.clientId() == client && !o.time().isBefore(from) && o.time().isBefore(to))
        .toList();
  }

  public record BatchItem(String id, long source, long target, BigDecimal amount) {}

  public record BatchResult(String id, String status, String reason) {}

  public List<BatchResult> batch(List<BatchItem> items) throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<BatchResult>> tasks =
          items.stream()
              .<Callable<BatchResult>>map(
                  i ->
                      () -> {
                        try {
                          TransferRequest r = transfer(i.id(), i.source(), i.target(), i.amount());
                          return new BatchResult(i.id(), r.status(), r.reason());
                        } catch (Exception ex) {
                          return new BatchResult(i.id(), "ERROR", ex.getMessage());
                        }
                      })
              .toList();
      List<BatchResult> out = new ArrayList<>();
      for (Future<BatchResult> f : pool.invokeAll(tasks)) {
        try {
          out.add(f.get());
        } catch (ExecutionException ex) {
          throw new IllegalStateException(ex.getCause());
        }
      }
      return List.copyOf(out);
    } finally {
      pool.shutdownNow();
    }
  }
}
