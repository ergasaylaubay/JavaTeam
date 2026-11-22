package kz.halyk.model;

import java.time.Instant;

public record TransferRequest(
    String id,
    long sourceId,
    long targetId,
    long amountMinor,
    String type,
    String status,
    String reason,
    Instant createdAt,
    Instant expiresAt) {}
