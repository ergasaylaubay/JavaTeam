package kz.halyk.member4;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import kz.halyk.common.*;
import kz.halyk.member3.LoanCalculator;
import kz.halyk.model.*;
import kz.halyk.model.Currency;

public final class ReportService {
  public record Group(long clientId, Currency currency) {}

  public record Summary(long clientId, Currency currency, long count, BigDecimal amount) {}

  public List<Summary> summary(List<Operation> operations) {
    Snapshot<Operation> snapshot = new Snapshot<>(operations);
    Map<Group, List<Operation>> groups =
        snapshot.items().stream()
            .filter(o -> o.status().equals("SUCCESS") && o.type().equals("TRANSFER"))
            .collect(Collectors.groupingBy(o -> new Group(o.clientId(), o.sourceCurrency())));
    return groups.entrySet().stream()
        .map(
            e ->
                new Summary(
                    e.getKey().clientId(),
                    e.getKey().currency(),
                    e.getValue().size(),
                    e.getValue().stream()
                        .map(o -> Money.amount(o.sourceMinor()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))
        .sorted(Comparator.comparingLong(Summary::clientId).thenComparing(Summary::currency))
        .toList();
  }

  public void summaryCsv(Path path, List<Summary> rows) throws IOException {
    csv(
        path,
        List.of("client_id", "currency", "count", "amount"),
        rows.stream()
            .map(
                r ->
                    List.of(
                        "" + r.clientId(),
                        r.currency().name(),
                        "" + r.count(),
                        r.amount().toPlainString()))
            .toList());
  }

  public void fraudCsv(Path path, List<FraudService.Alert> rows) throws IOException {
    csv(
        path,
        List.of("operation_id", "client_id", "time_utc", "count", "reason"),
        rows.stream()
            .map(
                r ->
                    List.of(
                        r.operationId(),
                        "" + r.clientId(),
                        r.time().toString(),
                        "" + r.count(),
                        r.reason()))
            .toList());
  }

  public void requestsCsv(Path path, List<TransferRequest> rows) throws IOException {
    csv(
        path,
        List.of("id", "source_id", "target_id", "amount", "status", "reason", "expires_utc"),
        rows.stream()
            .filter(r -> !r.reason().isEmpty())
            .map(
                r ->
                    List.of(
                        r.id(),
                        "" + r.sourceId(),
                        "" + r.targetId(),
                        Money.amount(r.amountMinor()).toPlainString(),
                        r.status(),
                        r.reason(),
                        r.expiresAt().toString()))
            .toList());
  }

  public void loanCsv(Path path, LoanCalculator.Schedule schedule) throws IOException {
    csv(
        path,
        List.of("month", "payment", "interest", "principal", "remaining_debt"),
        schedule.rows().stream()
            .map(
                r ->
                    List.of(
                        "" + r.month(),
                        r.payment().toPlainString(),
                        r.interest().toPlainString(),
                        r.principal().toPlainString(),
                        r.remainingDebt().toPlainString()))
            .toList());
  }

  public void csv(Path path, List<String> header, List<List<String>> rows) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      writer.write(line(header));
      writer.newLine();
      for (List<String> row : rows) {
        writer.write(line(row));
        writer.newLine();
      }
    }
  }

  private String line(List<String> values) {
    return values.stream()
        .map(v -> "\"" + v.replace("\"", "\"\"") + "\"")
        .collect(Collectors.joining(","));
  }
}
