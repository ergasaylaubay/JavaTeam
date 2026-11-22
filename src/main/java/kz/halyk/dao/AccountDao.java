package kz.halyk.dao;

import java.sql.*;
import java.util.*;
import kz.halyk.model.*;
import kz.halyk.model.Currency;

public final class AccountDao implements Dao<Account> {
  private Account read(ResultSet r) throws SQLException {
    long id = r.getLong("id"),
        client = r.getLong("client_id"),
        balance = r.getLong("balance_minor"),
        debt = r.getLong("debt_minor");
    Currency currency = Currency.valueOf(r.getString("currency"));
    AccountStatus status = AccountStatus.valueOf(r.getString("status"));
    return switch (AccountType.valueOf(r.getString("type"))) {
      case CARD -> new CardAccount(id, client, currency, balance, debt, status);
      case DEPOSIT -> new DepositAccount(id, client, currency, balance, debt, status);
      case LOAN -> new LoanAccount(id, client, currency, balance, debt, status);
    };
  }

  public Account create(Connection c, long client, AccountType type, Currency currency)
      throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO accounts(client_id,type,currency,status) VALUES(?,?,?,'ACTIVE')",
            Statement.RETURN_GENERATED_KEYS)) {
      p.setLong(1, client);
      p.setString(2, type.name());
      p.setString(3, currency.name());
      p.executeUpdate();
      try (ResultSet r = p.getGeneratedKeys()) {
        r.next();
        return find(c, r.getLong(1)).orElseThrow();
      }
    }
  }

  public Optional<Account> find(Connection c, long id) throws SQLException {
    try (PreparedStatement p = c.prepareStatement("SELECT * FROM accounts WHERE id=?")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? Optional.of(read(r)) : Optional.empty();
      }
    }
  }

  public List<Account> all(Connection c) throws SQLException {
    List<Account> out = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM accounts ORDER BY id")) {
      while (r.next()) out.add(read(r));
    }
    return out;
  }

  public void balance(Connection c, long id, long minor) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("UPDATE accounts SET balance_minor=? WHERE id=?")) {
      p.setLong(1, minor);
      p.setLong(2, id);
      if (p.executeUpdate() != 1) throw new SQLException("Шот табылмады");
    }
  }

  public void status(Connection c, long id, AccountStatus status) throws SQLException {
    try (PreparedStatement p = c.prepareStatement("UPDATE accounts SET status=? WHERE id=?")) {
      p.setString(1, status.name());
      p.setLong(2, id);
      if (p.executeUpdate() != 1) throw new IllegalArgumentException("Шот табылмады");
    }
  }
}
