package kz.halyk;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import kz.halyk.member3.LoanCalculator;
import org.junit.jupiter.api.Test;

class Member3Test {
  final LoanCalculator calculator = new LoanCalculator();

  @Test
  void annuityReference() {
    var s = calculator.calculate(new BigDecimal("100000"), new BigDecimal("12"), 12);
    assertEquals(new BigDecimal("8884.88"), s.monthlyPayment());
    assertEquals(12, s.rows().size());
  }

  @Test
  void zeroInterest() {
    var s = calculator.calculate(new BigDecimal("120000"), BigDecimal.ZERO, 12);
    assertEquals(new BigDecimal("10000.00"), s.monthlyPayment());
    assertEquals(new BigDecimal("0.00"), s.totalInterest());
  }

  @Test
  void finalRoundingAndTotals() {
    var s = calculator.calculate(new BigDecimal("100000"), new BigDecimal("12"), 12);
    assertEquals(new BigDecimal("0.00"), s.rows().get(11).remainingDebt());
    assertEquals(
        new BigDecimal("100000.00"),
        s.rows().stream()
            .map(LoanCalculator.Payment::principal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertEquals(s.totalPayment(), new BigDecimal("100000").add(s.totalInterest()));
    s.rows().forEach(r -> assertEquals(r.payment(), r.interest().add(r.principal())));
  }

  @Test
  void invalidParametersRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(BigDecimal.ZERO, BigDecimal.TEN, 12));
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(BigDecimal.TEN, new BigDecimal("-1"), 12));
    assertThrows(
        IllegalArgumentException.class,
        () -> calculator.calculate(BigDecimal.TEN, BigDecimal.ZERO, 0));
  }

  @Test
  void tinyLoanAndOneMonthStillClose() {
    var tiny = calculator.calculate(new BigDecimal("0.01"), BigDecimal.ZERO, 12);
    assertEquals(new BigDecimal("0.01"), tiny.totalPayment());
    assertEquals(new BigDecimal("0.00"), tiny.rows().get(11).remainingDebt());
    var one = calculator.calculate(new BigDecimal("100"), new BigDecimal("12"), 1);
    assertEquals(new BigDecimal("101.00"), one.totalPayment());
  }
}
