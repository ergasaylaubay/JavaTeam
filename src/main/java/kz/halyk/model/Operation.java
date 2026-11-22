package kz.halyk.model;

import java.time.Instant;

public record Operation(
    String id,
    long clientId,
    long sourceId,
    long targetId,
    String type,
    long sourceMinor,
    long targetMinor,
    Currency sourceCurrency,
    Currency targetCurrency,
    String rate,
    Instant time,
    String status,
    String reason) {}
