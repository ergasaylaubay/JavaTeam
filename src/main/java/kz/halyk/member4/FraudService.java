package kz.halyk.member4;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import kz.halyk.dao.JournalDao;
import kz.halyk.member1.ExchangeService;
import kz.halyk.model.*;

public final class FraudService {
  public static final BigDecimal THRESHOLD_KZT = new BigDecimal("100000");
  private final ExchangeService exchange;

  public FraudService(ExchangeService exchange) {
    this.exchange = exchange;
  }

  // 4-адам: аударымға ДЕЙІН тексеру. Қауіп белгісі алаяқтықтың дәлелі емес.
  public String assess(Connection c, Account source, long target, long minor) throws SQLException {
    boolean newRecipient = !new JournalDao().knownRecipient(c, source.clientId(), target);
    boolean large = exchange.kztValue(minor, source.currency()).compareTo(THRESHOLD_KZT) > 0;
    return newRecipient && large
        ? "Жаңа алушы + 100 000 KZT-ден жоғары сома; демо растау қажет"
        : "";
  }

  public record Alert(String operationId, long clientId, Instant time, int count, String reason) {}

  // Бұрынғы research ережесі: аяқталған операциялардың қосымша мониторингі.
  public List<Alert> frequencyAlerts(List<Operation> snapshot) {
    List<Operation> successful =
        snapshot.stream()
            .filter(o -> o.type().equals("TRANSFER") && o.status().equals("SUCCESS"))
            .sorted(Comparator.comparing(Operation::time).thenComparing(Operation::id))
            .toList();
    Map<Long, Deque<Operation>> windows = new HashMap<>();
    Set<String> seen = new HashSet<>();
    List<Alert> alerts = new ArrayList<>();
    for (Operation o : successful) {
      if (!seen.add(o.id())) continue;
      Deque<Operation> window = windows.computeIfAbsent(o.clientId(), id -> new ArrayDeque<>());
      Instant boundary = o.time().minusSeconds(600);
      while (!window.isEmpty() && !window.peekFirst().time().isAfter(boundary))
        window.removeFirst();
      window.addLast(o);
      if (window.size() > 5)
        alerts.add(
            new Alert(
                o.id(), o.clientId(), o.time(), window.size(), "10 минутта 5-тен көп аударым"));
    }
    return List.copyOf(alerts);
  }
}
