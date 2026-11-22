package kz.halyk.dao;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import kz.halyk.model.*;
import kz.halyk.model.Currency;

public final class JournalDao {
  public Optional<TransferRequest> request(Connection c, String id) throws SQLException {
    try (PreparedStatement p = c.prepareStatement("SELECT * FROM transfer_requests WHERE id=?")) {
      p.setString(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? Optional.of(readRequest(r)) : Optional.empty();
      }
    }
  }

  private TransferRequest readRequest(ResultSet r) throws SQLException {
    return new TransferRequest(
        r.getString("id"),
        r.getLong("source_id"),
        r.getLong("target_id"),
        r.getLong("amount_minor"),
        r.getString("type"),
        r.getString("status"),
        r.getString("reason"),
        Instant.ofEpochMilli(r.getLong("created_at_ms")),
        Instant.ofEpochMilli(r.getLong("expires_at_ms")));
  }

  public List<TransferRequest> requests(Connection c) throws SQLException {
    List<TransferRequest> out = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM transfer_requests ORDER BY created_at_ms,id")) {
      while (r.next()) out.add(readRequest(r));
    }
    return out;
  }

  public void insertRequest(Connection c, TransferRequest r) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("INSERT INTO transfer_requests VALUES(?,?,?,?,?,?,?,?,?)")) {
      p.setString(1, r.id());
      p.setLong(2, r.sourceId());
      p.setLong(3, r.targetId());
      p.setLong(4, r.amountMinor());
      p.setString(5, r.type());
      p.setString(6, r.status());
      p.setString(7, r.reason());
      p.setLong(8, r.createdAt().toEpochMilli());
      p.setLong(9, r.expiresAt().toEpochMilli());
      p.executeUpdate();
    }
  }

  public void finish(Connection c, String id, String status, String reason) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("UPDATE transfer_requests SET status=?,reason=? WHERE id=?")) {
      p.setString(1, status);
      p.setString(2, reason);
      p.setString(3, id);
      p.executeUpdate();
    }
  }

  public void operation(Connection c, Operation o) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("INSERT INTO operations VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
      p.setString(1, o.id());
      if (o.clientId() == 0) p.setNull(2, Types.INTEGER);
      else p.setLong(2, o.clientId());
      p.setLong(3, o.sourceId());
      p.setLong(4, o.targetId());
      p.setString(5, o.type());
      p.setLong(6, o.sourceMinor());
      p.setLong(7, o.targetMinor());
      p.setString(8, o.sourceCurrency() == null ? null : o.sourceCurrency().name());
      p.setString(9, o.targetCurrency() == null ? null : o.targetCurrency().name());
      p.setString(10, o.rate());
      p.setLong(11, o.time().toEpochMilli());
      p.setString(12, o.status());
      p.setString(13, o.reason());
      p.executeUpdate();
    }
  }

  public List<Operation> operations(Connection c) throws SQLException {
    List<Operation> out = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM operations ORDER BY occurred_at_ms,id")) {
      while (r.next())
        out.add(
            new Operation(
                r.getString("id"),
                r.getLong("client_id"),
                r.getLong("source_id"),
                r.getLong("target_id"),
                r.getString("type"),
                r.getLong("source_minor"),
                r.getLong("target_minor"),
                currency(r.getString("source_currency")),
                currency(r.getString("target_currency")),
                r.getString("rate"),
                Instant.ofEpochMilli(r.getLong("occurred_at_ms")),
                r.getString("status"),
                r.getString("reason")));
    }
    return out;
  }

  private Currency currency(String value) {
    return value == null ? null : Currency.valueOf(value);
  }

  public boolean knownRecipient(Connection c, long client, long target) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT 1 FROM operations WHERE client_id=? AND target_id=? AND type='TRANSFER' AND"
                + " status='SUCCESS' LIMIT 1")) {
      p.setLong(1, client);
      p.setLong(2, target);
      try (ResultSet r = p.executeQuery()) {
        return r.next();
      }
    }
  }
}
