package dev.donutprofithud.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerTest {

	private static Payment parse(String line) {
		return PaymentParser.parse(line).orElseThrow(() -> new AssertionError("should parse: " + line));
	}

	@Test
	void receiveThreeMillionThenPayFiveMillionLeavesMinusTwoMillion() {
		Ledger l = new Ledger();
		l.apply(parse("maple_trader paid you $ 3M"), true);
		l.apply(parse("You paid maple_trader $ 5M"), true);
		assertEquals(0, new BigDecimal("-2000000").compareTo(l.net()));
		assertEquals("-$2M", Money.format(l.net()));
	}

	@Test
	void bothDirectionsWithTax() {
		Ledger l = new Ledger();
		BigDecimal in = l.apply(parse("CoinCollector42 paid you $ 18M"), true);
		BigDecimal out = l.apply(parse("You paid CoinCollector42 $ 6M (+$ 120K tax)"), true);
		assertEquals("+$18M", Money.formatSigned(in));
		assertEquals("-$6.12M", Money.formatSigned(out));
		assertEquals(0, new BigDecimal("18000000").compareTo(l.received()));
		assertEquals(0, new BigDecimal("6120000").compareTo(l.paid()));
		assertEquals("$11.88M", Money.format(l.net()));
		assertEquals(2, l.count());
	}

	@Test
	void taxCanBeExcluded() {
		Ledger l = new Ledger();
		l.apply(parse("You paid CoinCollector42 $ 6M (+$ 120K tax)"), false);
		assertEquals("-$6M", Money.format(l.net()));
	}

	@Test
	void twoIdenticalPaymentsInARowBothCount() {
		Ledger l = new Ledger();
		String line = "maple_trader paid you $ 5M";
		l.apply(parse(line), true);
		l.apply(parse(line), true);
		assertEquals("$10M", Money.format(l.net()));
		assertEquals(2, l.count());

		String outLine = "You paid maple_trader $ 1M (+$ 20K tax)";
		l.apply(parse(outLine), true);
		l.apply(parse(outLine), true);
		assertEquals("$7.96M", Money.format(l.net()));
		assertEquals(4, l.count());
	}

	@Test
	void exactAmountsAccumulateWithoutDrift() {
		Ledger l = new Ledger();
		for (int i = 0; i < 1000; i++) l.apply(parse("w7_shop_TEST_01 paid you $ 6.7"), true);
		assertEquals(0, new BigDecimal("6700").compareTo(l.net()));
	}

	@Test
	void awaySummaryAddsToReceivedAndResetClearsIt() {
		Ledger l = new Ledger();
		l.apply(parse("You were paid $ 4.8M while you were away"), true);
		l.apply(parse("You paid maple_trader $ 1M (+$ 20K tax)"), true);
		assertEquals("$3.78M", Money.format(l.net()));
		assertEquals(0, new BigDecimal("4800000").compareTo(l.received()));
		l.reset();
		assertEquals("$0", Money.format(l.net()));
		assertEquals(0, l.count());
	}

	@Test
	void resetClearsEverything() {
		Ledger l = new Ledger();
		l.apply(parse("maple_trader paid you $ 3M"), true);
		l.reset();
		assertEquals(0, BigDecimal.ZERO.compareTo(l.net()));
		assertEquals(0, l.count());
	}

	@Test
	void sessionSurvivesSaveAndLoad(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("config").resolve("donutprofithud-session.properties");
		SessionStore store = new SessionStore(file);
		Ledger l = new Ledger();
		l.apply(parse("Steve paid you $ 7.4M"), true);
		l.apply(parse("You paid DiamondDan $ 166 (+$ 4 tax)"), true);
		store.save(l);
		assertTrue(Files.exists(file));

		Ledger loaded = store.load();
		assertEquals(0, l.net().compareTo(loaded.net()));
		assertEquals(0, new BigDecimal("7399830").compareTo(loaded.net()));
		assertEquals(2, loaded.count());
	}

	@Test
	void missingOrCorruptSessionFileStartsAtZero(@TempDir Path dir) throws Exception {
		SessionStore store = new SessionStore(dir.resolve("nope.properties"));
		assertEquals(0, BigDecimal.ZERO.compareTo(store.load().net()));
		Path bad = dir.resolve("bad.properties");
		Files.writeString(bad, "received=not-a-number\n");
		assertEquals(0, BigDecimal.ZERO.compareTo(new SessionStore(bad).load().net()));
	}

	@Test
	void flashShowsSignedAmountThenExpires() {
		FlashTracker f = new FlashTracker();
		f.trigger(new BigDecimal("-3000000"), 1_000);
		assertEquals("-$3M", Money.formatSigned(f.active(1_500, 2_000)));
		assertEquals(1f, f.alpha(1_100, 2_000));
		assertTrue(f.alpha(2_900, 2_000) < 0.2f);
		assertNull(f.active(3_000, 2_000));
		assertNull(f.active(1_100, 0)); // flash disabled / zero duration
	}
}
