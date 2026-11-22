package kz.halyk.model;

public final class LoanAccount extends Account {
  public LoanAccount(
      long id, long clientId, Currency currency, long balance, long debt, AccountStatus status) {
    super(id, clientId, currency, balance, debt, status);
  }

  @Override
  public AccountType type() {
    return AccountType.LOAN;
  }

  @Override
  public String description() {
    return "Несие шоты";
  }
}
