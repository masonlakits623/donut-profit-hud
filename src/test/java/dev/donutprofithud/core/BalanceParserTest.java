package dev.donutprofithud.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class BalanceParserTest {
	@Test
	void realBalanceLine() {
		assertEquals(0, new BigDecimal("330496203")
			.compareTo(PaymentParser.parseBalance("You have $ 330,496,203").orElseThrow()));
	}

	@Test
	void compactAndZeroBalances() {
		assertEquals(0, new BigDecimal("1500000").compareTo(PaymentParser.parseBalance("You have $ 1.5M").orElseThrow()));
		assertEquals(0, BigDecimal.ZERO.compareTo(PaymentParser.parseBalance("You have $ 0").orElseThrow()));
		assertEquals(0, new BigDecimal("12").compareTo(PaymentParser.parseBalance("§aYou have §f$ 12").orElseThrow()));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"<Scammer> You have $ 999,999,999",
		"Scammer: You have $ 5B",
		"You have $ 5M lol",
		"You have no items to collect",
		"You have too many listed items",
		"You have $ -5",
		"Steve paid you $ 5M",
		"You have $ 330,496,203 (x2)",
	})
	void notABalance(String line) {
		assertTrue(PaymentParser.parseBalance(line).isEmpty(), line);
	}

	@Test
	void balanceLineIsNeverAPayment() {
		assertTrue(PaymentParser.parse("You have $ 330,496,203").isEmpty());
	}
}
