package kz.halyk.model;

public final class CardAccount extends Account {
  public CardAccount(
      long id, long clientId, Currency currency, long balance, long debt, AccountStatus status) {
    super(id, clientId, currency, balance, debt, status);
  }

  @Override
  public AccountType type() {
    return AccountType.CARD;
  }

  @Override
  public String description() {
    return "Карта";
  }
}
