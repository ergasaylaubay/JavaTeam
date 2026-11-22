package kz.halyk.dao;

import java.sql.*;
import java.util.*;
import kz.halyk.model.Client;

public final class ClientDao implements Dao<Client> {
  public Client create(Connection c, String name) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO clients(full_name) VALUES(?)", Statement.RETURN_GENERATED_KEYS)) {
      p.setString(1, name);
      p.executeUpdate();
      try (ResultSet r = p.getGeneratedKeys()) {
        r.next();
        return new Client(r.getLong(1), name);
      }
    }
  }

  public Optional<Client> find(Connection c, long id) throws SQLException {
    try (PreparedStatement p = c.prepareStatement("SELECT * FROM clients WHERE id=?")) {
      p.setLong(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next()
            ? Optional.of(new Client(r.getLong("id"), r.getString("full_name")))
            : Optional.empty();
      }
    }
  }

  public List<Client> all(Connection c) throws SQLException {
    List<Client> out = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM clients ORDER BY id")) {
      while (r.next()) out.add(new Client(r.getLong("id"), r.getString("full_name")));
    }
    return out;
  }
}
