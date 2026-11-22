package kz.halyk.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
  private Money() {}

  public static long minor(BigDecimal value) {
    if (value == null || value.signum() <= 0)
      throw new IllegalArgumentException("Сома оң болуы керек");
    try {
      return value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Сома тым үлкен немесе 2 таңбадан артық", ex);
    }
  }

  public static BigDecimal amount(long minor) {
    return BigDecimal.valueOf(minor, 2);
  }
}
