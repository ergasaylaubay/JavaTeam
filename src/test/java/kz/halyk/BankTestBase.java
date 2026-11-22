package kz.halyk;

import java.nio.file.Path;
import java.time.*;
import kz.halyk.dao.AccountDao;
import kz.halyk.db.Database;
import kz.halyk.member1.*;
import kz.halyk.member2.*;
import kz.halyk.member4.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class BankTestBase {
  @TempDir Path dir;
  Database db;
  ClientService clients;
  ExchangeService exchange;
  FraudService fraud;
  TransferService transfers;
  final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void prepare() throws Exception {
    db = new Database(dir.resolve("bank.db"));
    clients = new ClientService(db);
    clients.seedDemo();
    exchange = new ExchangeService();
    fraud = new FraudService(exchange);
    transfers = new TransferService(db, exchange, fraud, clock);
  }

  void balance(long id, long value) throws Exception {
    db.transaction(
        c -> {
          new AccountDao().balance(c, id, value);
          return null;
        });
  }
}
