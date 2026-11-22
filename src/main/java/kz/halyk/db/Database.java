package kz.halyk.db;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;

public final class Database {
  private final String url;

  public Database(Path file) throws Exception {
    Path absolute = file.toAbsolutePath();
    Files.createDirectories(absolute.getParent());
    url = "jdbc:sqlite:" + absolute;
    try (Connection c = open();
        InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
      if (in == null) throw new IOException("schema.sql табылмады");
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      try (Statement s = c.createStatement()) {
        for (String part : sql.split(";")) if (!part.isBlank()) s.execute(part);
      }
    }
  }

  public Connection open() throws SQLException {
    Connection c = DriverManager.getConnection(url);
    try (Statement s = c.createStatement()) {
      s.execute("PRAGMA foreign_keys=ON");
      s.execute("PRAGMA busy_timeout=10000");
    } catch (SQLException ex) {
      c.close();
      throw ex;
    }
    return c;
  }

  @FunctionalInterface
  public interface Work<T> {
    T run(Connection c) throws SQLException;
  }

  // BEGIN IMMEDIATE takes SQLite's write lock BEFORE reading balances.
  // This prevents lost updates even with several JDBC connections.
  public <T> T transaction(Work<T> work) throws SQLException {
    try (Connection c = open();
        Statement s = c.createStatement()) {
      s.execute("BEGIN IMMEDIATE");
      try {
        T result = work.run(c);
        s.execute("COMMIT");
        return result;
      } catch (SQLException | RuntimeException ex) {
        try {
          s.execute("ROLLBACK");
        } catch (SQLException rollback) {
          ex.addSuppressed(rollback);
        }
        throw ex;
      }
    }
  }
}
