package kz.halyk.model;

public final class DepositAccount extends Account {
  public DepositAccount(
      long id, long clientId, Currency currency, long balance, long debt, AccountStatus status) {
    super(id, clientId, currency, balance, debt, status);
  }

  @Override
  public AccountType type() {
    return AccountType.DEPOSIT;
  }

  @Override
  public String description() {
    return "Депозит";
  }
}
