package dev.donutprofithud.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

	@ParameterizedTest
	@CsvSource({
		"166, 166",
		"6.7, 6.7",
		"100K, 100000",
		"99.9K, 99900",
		"7.4M, 7400000",
		"1.5B, 1500000000",
		"2t, 2000000000000",
		"'112,348,451', 112348451",
	})
	void parsesServerAmounts(String token, String expected) {
		assertEquals(0, new BigDecimal(expected).compareTo(Money.parse(token)), token);
	}

	@Test
	void rejectsGarbage() {
		assertNull(Money.parse("abc"));
		assertNull(Money.parse("5MM"));
		assertNull(Money.parse("1,23"));
		assertNull(Money.parse("-5M"));
		assertNull(Money.parse(""));
	}

	@ParameterizedTest
	@CsvSource({
		"0, $0",
		"6.7, $6.7",
		"166, $166",
		"999, $999",
		"1000, $1K",
		"1500, $1.5K",
		"99900, $99.9K",
		"999999, $1M",
		"3000000, $3M",
		"1250000, $1.25M",
		"7400000, $7.4M",
		"1000000000, $1B",
		"2500000000, $2.5B",
		"1500000000000, $1500B",
		"-2000000, -$2M",
		"-1500, -$1.5K",
		"-6.7, -$6.7",
		"-999999999, -$1B",
	})
	void formatsCompact(String value, String expected) {
		assertEquals(expected, Money.format(new BigDecimal(value)));
	}

	@Test
	void formatsSigned() {
		assertEquals("+$3M", Money.formatSigned(new BigDecimal("3000000")));
		assertEquals("-$3M", Money.formatSigned(new BigDecimal("-3000000")));
		assertEquals("-$6.12M", Money.formatSigned(new BigDecimal("-6120000")));
		assertEquals("$0", Money.formatSigned(BigDecimal.ZERO));
	}
}
