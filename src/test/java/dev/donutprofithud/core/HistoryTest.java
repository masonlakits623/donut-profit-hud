package dev.donutprofithud.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoryTest {
	private static final ZoneId ZONE = ZoneOffset.UTC;

	private static long at(String isoLocal) {
		return LocalDateTime.parse(isoLocal).atZone(ZONE).toInstant().toEpochMilli();
	}

	private static Payment pay(String line) {
		return PaymentParser.parse(line).orElseThrow();
	}

	@Test
	void profitResetsEveryDay() {
		History h = new History();
		h.applyPayment(pay("Steve paid you $ 5M"), true, at("2026-11-22T23:59:00"), ZONE, 0);
		h.applyPayment(pay("Steve paid you $ 2M"), true, at("2026-11-23T00:01:00"), ZONE, 0);
		assertEquals(0, new BigDecimal("5000000").compareTo(h.day("2026-11-22").net()));
		assertEquals(0, new BigDecimal("2000000").compareTo(h.day("2026-11-23").net()));
		assertEquals(0, new BigDecimal("2000000").compareTo(h.todayNet(at("2026-11-23T12:00:00"), ZONE, 0)));
		assertEquals(0, BigDecimal.ZERO.compareTo(h.todayNet(at("2026-11-24T00:00:00"), ZONE, 0)));
	}

	@Test
	void dayStartHourMovesTheBoundary() {
		History h = new History();
		// With the day starting at 06:00, 03:00 on the 23rd still belongs to the 22nd.
		h.applyPayment(pay("Steve paid you $ 1M"), true, at("2026-11-23T03:00:00"), ZONE, 6);
		h.applyPayment(pay("Steve paid you $ 1M"), true, at("2026-11-23T07:00:00"), ZONE, 6);
		assertEquals(1, h.day("2026-11-22").count);
		assertEquals(1, h.day("2026-11-23").count);
		assertEquals(at("2026-11-23T06:00:00"), History.dayStartMs(LocalDate.parse("2026-11-23"), ZONE, 6));
		assertEquals(at("2026-11-24T06:00:00"), History.dayEndMs(LocalDate.parse("2026-11-23"), ZONE, 6));
	}

	@Test
	void graphPointsFollowTheRunningNet() {
		History h = new History();
		h.applyPayment(pay("Steve paid you $ 3M"), true, at("2026-11-23T10:00:00"), ZONE, 0);
		h.applyPayment(pay("You paid Steve $ 5M"), true, at("2026-11-23T11:00:00"), ZONE, 0);
		h.applyPayment(pay("Steve paid you $ 3M"), true, at("2026-11-23T12:00:00"), ZONE, 0);
		DayRecord d = h.day("2026-11-23");
		List<DayRecord.Point> pts = d.points();
		assertEquals(3, pts.size());
		assertEquals(0, new BigDecimal("3000000").compareTo(pts.get(0).net));
		assertEquals(0, new BigDecimal("-2000000").compareTo(pts.get(1).net));
		assertEquals(0, new BigDecimal("1000000").compareTo(pts.get(2).net));
		assertEquals(0, BigDecimal.ZERO.compareTo(d.netAt(at("2026-11-23T09:00:00"))));
		assertEquals(0, new BigDecimal("-2000000").compareTo(d.netAt(at("2026-11-23T11:30:00"))));
		assertEquals(3, d.count);
	}

	@Test
	void taxCountsTowardThePaidSideOfTheDay() {
		History h = new History();
		h.applyPayment(pay("You paid Steve $ 6M (+$ 120K tax)"), true, at("2026-11-23T10:00:00"), ZONE, 0);
		assertEquals(0, new BigDecimal("6120000").compareTo(h.day("2026-11-23").paid()));
		h.applyPayment(pay("You paid Steve $ 6M (+$ 120K tax)"), false, at("2026-11-23T10:01:00"), ZONE, 0);
		assertEquals(0, new BigDecimal("12120000").compareTo(h.day("2026-11-23").paid()));
	}

	@Test
	void balanceKeepsFirstAndLatestReadingOfTheDay() {
		History h = new History();
		h.recordBalance(new BigDecimal("330496203"), at("2026-11-23T08:00:00"), ZONE, 0);
		DayRecord one = h.day("2026-11-23");
		assertNull(one.balanceChange(), "one reading is not a change");
		h.recordBalance(new BigDecimal("345538661"), at("2026-11-23T20:00:00"), ZONE, 0);
		DayRecord d = h.day("2026-11-23");
		assertEquals(0, new BigDecimal("330496203").compareTo(d.startBalance));
		assertEquals(0, new BigDecimal("345538661").compareTo(d.endBalance));
		assertEquals(0, new BigDecimal("15042458").compareTo(d.balanceChange()));
		assertEquals(0, new BigDecimal("345538661").compareTo(h.lastBalance()));
	}

	@Test
	void resetDayClearsPaymentsButKeepsBalance() {
		History h = new History();
		h.applyPayment(pay("Steve paid you $ 3M"), true, at("2026-11-23T10:00:00"), ZONE, 0);
		h.recordBalance(new BigDecimal("100"), at("2026-11-23T10:05:00"), ZONE, 0);
		h.resetDayPayments("2026-11-23");
		DayRecord d = h.day("2026-11-23");
		assertEquals(0, d.count);
		assertEquals(0, BigDecimal.ZERO.compareTo(d.net()));
		assertTrue(d.points().isEmpty());
		assertEquals(0, new BigDecimal("100").compareTo(d.endBalance));
	}

	@Test
	void oldDaysArePruned() {
		History h = new History();
		LocalDate start = LocalDate.parse("2025-01-01");
		for (int i = 0; i < History.MAX_DAYS + 10; i++) {
			long t = start.plusDays(i).atTime(12, 0).atZone(ZONE).toInstant().toEpochMilli();
			h.applyPayment(pay("Steve paid you $ 1"), true, t, ZONE, 0);
		}
		List<DayRecord> days = h.daysNewestFirst();
		assertEquals(History.MAX_DAYS, days.size());
		assertEquals(start.plusDays(History.MAX_DAYS + 9).toString(), days.get(0).date);
	}

	@Test
	void saveAndLoadKeepsExactAmountsAndPoints(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("history.json");
		History h = new History();
		h.applyPayment(pay("Steve paid you $ 7.4M"), true, at("2026-11-23T10:00:00"), ZONE, 0);
		h.applyPayment(pay("You paid Steve $ 166 (+$ 4 tax)"), true, at("2026-11-23T10:01:00"), ZONE, 0);
		h.recordBalance(new BigDecimal("330496203"), at("2026-11-23T10:02:00"), ZONE, 0);
		new HistoryStore(f).save(h);

		History back = new HistoryStore(f).load();
		DayRecord d = back.day("2026-11-23");
		assertEquals(0, new BigDecimal("7399830").compareTo(d.net()));
		assertEquals(2, d.points().size());
		assertEquals(2, d.count);
		assertEquals(0, new BigDecimal("330496203").compareTo(back.lastBalance()));
	}

	@Test
	void corruptFileGivesEmptyHistory(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("history.json");
		Files.writeString(f, "{ this is not json", StandardCharsets.UTF_8);
		History h = new HistoryStore(f).load();
		assertTrue(h.daysNewestFirst().isEmpty());
	}
}
