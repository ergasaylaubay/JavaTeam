package kz.halyk.model;

import kz.halyk.common.Money;

// Абстракт класс: әр шот ортақ деректерді пайдаланады.
public abstract class Account {
  private final long id, clientId, balanceMinor, debtMinor;
  private final Currency currency;
  private final AccountStatus status;

  protected Account(
      long id,
      long clientId,
      Currency currency,
      long balanceMinor,
      long debtMinor,
      AccountStatus status) {
    this.id = id;
    this.clientId = clientId;
    this.currency = currency;
    this.balanceMinor = balanceMinor;
    this.debtMinor = debtMinor;
    this.status = status;
  }

  public long id() {
    return id;
  }

  public long clientId() {
    return clientId;
  }

  public Currency currency() {
    return currency;
  }

  public long balanceMinor() {
    return balanceMinor;
  }

  public long debtMinor() {
    return debtMinor;
  }

  public AccountStatus status() {
    return status;
  }

  public abstract AccountType type();

  public abstract String description();

  @Override
  public String toString() {
    return "#"
        + id
        + " client="
        + clientId
        + " "
        + description()
        + " "
        + currency
        + " balance="
        + Money.amount(balanceMinor)
        + " debt="
        + Money.amount(debtMinor)
        + " "
        + status;
  }
}
