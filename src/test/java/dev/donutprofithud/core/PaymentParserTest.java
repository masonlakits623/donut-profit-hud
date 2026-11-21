package dev.donutprofithud.core;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PaymentParserTest {

	private static BigDecimal bd(String s) {
		return new BigDecimal(s);
	}

	private static void assertSame(BigDecimal expected, BigDecimal actual) {
		assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
	}

	// --- incoming: real lines from the user's logs/screenshots ---

	@Test
	void incomingRealLines() {
		Payment p = PaymentParser.parse("Steve paid you $ 7.4M").orElseThrow();
		assertEquals(Payment.Direction.INCOMING, p.direction());
		assertEquals("Steve", p.counterparty());
		assertSame(bd("7400000"), p.amount());

		assertSame(bd("18000000"), PaymentParser.parse("CoinCollector42 paid you $ 18M").orElseThrow().amount());
		assertSame(bd("5000000"), PaymentParser.parse("maple_trader paid you $ 5M").orElseThrow().amount());
		assertSame(bd("99900"), PaymentParser.parse("Steve paid you $ 99.9K").orElseThrow().amount());
		assertSame(bd("6.7"), PaymentParser.parse("w7_shop_TEST_01 paid you $ 6.7").orElseThrow().amount());
		assertSame(bd("101000000"), PaymentParser.parse("alex9 paid you $ 101M").orElseThrow().amount());
	}

	@Test
	void incomingFromBedrockPlayer() {
		Payment p = PaymentParser.parse(".BedrockPlayer paid you $ 2M").orElseThrow();
		assertEquals(".BedrockPlayer", p.counterparty());
		assertSame(bd("2000000"), p.amount());
	}

	@Test
	void incomingWithHeadIconOrColourCodes() {
		// Screenshot shows a player-head icon before some lines; resource packs draw such icons
		// with private-use glyphs. Legacy colour codes may also be embedded.
		assertTrue(PaymentParser.parse(" CoinCollector42 paid you $ 18M").isPresent());
		assertTrue(PaymentParser.parse("💰 CoinCollector42 paid you $ 18M").isPresent());
		assertTrue(PaymentParser.parse("§aCoinCollector42§f paid you §a$ §f18M").isPresent());
		assertTrue(PaymentParser.parse("CoinCollector42 paid you $ 18M").isPresent());
	}

	@Test
	void awaySummaryCountsAsIncoming() {
		// real line from the user's logs
		Payment p = PaymentParser.parse("You were paid $ 4.8M while you were away").orElseThrow();
		assertEquals(Payment.Direction.INCOMING, p.direction());
		assertTrue(p.away());
		assertSame(bd("4800000"), p.amount());
		assertSame(bd("4800000"), p.signedDelta(true));
		// normal payments are not flagged as away
		assertFalse(PaymentParser.parse("maple_trader paid you $ 5M").orElseThrow().away());
	}

	// --- outgoing ---

	@Test
	void outgoingRealLines() {
		Payment p = PaymentParser.parse("You paid maple_trader $ 100K (+$ 2K tax)").orElseThrow();
		assertEquals(Payment.Direction.OUTGOING, p.direction());
		assertEquals("maple_trader", p.counterparty());
		assertSame(bd("100000"), p.amount());
		assertSame(bd("2000"), p.tax());

		Payment q = PaymentParser.parse("You paid CoinCollector42 $ 6M (+$ 120K tax)").orElseThrow();
		assertSame(bd("6000000"), q.amount());
		assertSame(bd("120000"), q.tax());

		Payment r = PaymentParser.parse("You paid DiamondDan $ 166 (+$ 4 tax)").orElseThrow();
		assertSame(bd("166"), r.amount());
		assertSame(bd("4"), r.tax());
	}

	@Test
	void outgoingTaxIsOptionalInSignedDelta() {
		Payment p = PaymentParser.parse("You paid CoinCollector42 $ 6M (+$ 120K tax)").orElseThrow();
		assertSame(bd("-6120000"), p.signedDelta(true));
		assertSame(bd("-6000000"), p.signedDelta(false));
	}

	// --- failed payments and typed commands must not count ---

	@ParameterizedTest
	@ValueSource(strings = {
		// real DonutSMP failure lines from the user's logs
		"That player does not have payments enabled in /settings",
		"This user is not online",
		"You don't have enough money",
		"You do not have enough money to pay maple_trader $ 5M",
		"You cannot pay yourself",
		"Invalid amount",
		"You cannot do this in combat",
		"Usage: /pay <player> <amount>",
		"/pay maple_trader 5m",
		"pay maple_trader 5m",
		"Payment to maple_trader failed: $ 5M",
		"Are you sure you want to pay maple_trader $ 5M?",
		"Click to confirm: You paid maple_trader $ 5M (+$ 100K tax)",
	})
	void failedPaymentsAndCommandsDoNotCount(String line) {
		assertEquals(Optional.empty(), PaymentParser.parse(line), line);
	}

	// --- player chat imitating a payment must not count ---

	@ParameterizedTest
	@ValueSource(strings = {
		// DonutSMP delivers player chat as system messages with a "<name>" prefix (seen in logs)
		"§f<§f§f§fchatter_mc§f> maple_trader paid you $ 50M",
		"<Scammer> You paid maple_trader $ 5M (+$ 100K tax)",
		"<Scammer> Scammer paid you $ 1B",
		"randomplayer: CoinCollector42 paid you $ 18M",
		"[VIP] Scammer » maple_trader paid you $ 5M",
		"[Scammer -> me] maple_trader paid you $ 5M",
		"Scammer whispers: maple_trader paid you $ 5M",
		// other players' transactions and non-payment money lines
		"CoinCollector42 was paid $ 200K by Notch2",
		"CoinCollector42 paid maple_trader $ 250K",
		// player chat imitating the "while away" join summary
		"<Scammer> You were paid $ 4.8M while you were away",
		"Scammer: You were paid $ 4.8M while you were away",
		"You were paid $ 4.8M while you were away lol",
		"You were paid $ 0 while you were away",
		"KixoxMinecraft bought your Diamond Pickaxe for $ 70K",
		"You bought 1 Ender Chest for $ 7.4K",
		"You listed 64 End Crystal for $ 1M (+$ 20K tax)",
		"yoshiboat67 sold multiple items for $ 427",
		"You have $ 112,348,451",
		"$ 1.5K",
		// near misses
		"maple_trader paid you $ 5M lol",
		"maple_trader paid you $ 5M (x2)",
		"two words paid you $ 5M",
		"You paid you $ 5M",
		"maple_trader paid you $ 0",
		"maple_trader paid you $ -5M",
		"maple_trader paid you 5M",
		"You paid maple_trader $ 5M (+$ 100K tax) extra",
		"a_name_that_is_way_too_long_for_mc paid you $ 5M",
	})
	void chatOrOtherLinesDoNotCount(String line) {
		assertEquals(Optional.empty(), PaymentParser.parse(line), line);
	}

	@Test
	void nullAndBlank() {
		assertTrue(PaymentParser.parse(null).isEmpty());
		assertTrue(PaymentParser.parse("").isEmpty());
		assertTrue(PaymentParser.parse("   ").isEmpty());
	}
}
