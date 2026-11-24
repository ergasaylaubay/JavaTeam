package kz.halyk.member3;

import java.math.*;
import java.util.*;
import kz.halyk.common.Money;

public final class LoanCalculator {
  private static final MathContext MC = MathContext.DECIMAL128;

  public record Payment(
      int month,
      BigDecimal payment,
      BigDecimal interest,
      BigDecimal principal,
      BigDecimal remainingDebt) {}

  public record Schedule(
      BigDecimal monthlyPayment,
      List<Payment> rows,
      BigDecimal totalPayment,
      BigDecimal totalInterest) {}

  public Schedule calculate(BigDecimal principal, BigDecimal annualRate, int months) {
    Money.minor(principal);
    if (annualRate == null
        || annualRate.signum() < 0
        || annualRate.compareTo(new BigDecimal("1000")) > 0)
      throw new IllegalArgumentException("Жылдық пайыз: 0–1000 аралығы");
    if (months < 1 || months > 1200) throw new IllegalArgumentException("Ай саны: 1–1200 аралығы");
    BigDecimal rate = annualRate.divide(new BigDecimal("1200"), MC);
    BigDecimal payment;
    if (rate.signum() == 0) payment = principal.divide(BigDecimal.valueOf(months), MC);
    else {
      BigDecimal power = BigDecimal.ONE.add(rate).pow(months, MC);
      payment =
          principal
              .multiply(rate, MC)
              .multiply(power, MC)
              .divide(power.subtract(BigDecimal.ONE), MC);
    }
    payment = round(payment);
    BigDecimal remaining = round(principal), total = round(BigDecimal.ZERO), interestTotal = total;
    List<Payment> rows = new ArrayList<>();
    for (int month = 1; month <= months; month++) {
      BigDecimal interest = round(remaining.multiply(rate, MC));
      BigDecimal paidPrincipal =
          month == months ? remaining : payment.subtract(interest).min(remaining);
      if (paidPrincipal.signum() < 0)
        throw new IllegalArgumentException(
            "Дөңгелектеуден теріс төлем шықты: параметрлерді өзгертіңіз");
      BigDecimal actualPayment = paidPrincipal.add(interest);
      remaining = remaining.subtract(paidPrincipal);
      rows.add(new Payment(month, actualPayment, interest, paidPrincipal, remaining));
      total = total.add(actualPayment);
      interestTotal = interestTotal.add(interest);
    }
    return new Schedule(payment, List.copyOf(rows), total, interestTotal);
  }

  private BigDecimal round(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }
}

