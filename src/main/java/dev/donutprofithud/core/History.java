package dev.donutprofithud.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * Per-day profit and balance history. A "day" runs from {@code dayStartHour}:00 local time to the
 * same time the next day, so today's profit starts again from 0 every day.
 */
public final class History {
	/** Days older than this are dropped. */
	public static final int MAX_DAYS = 400;

	private final TreeMap<String, DayRecord> days = new TreeMap<>();
	private BigDecimal lastBalance;
	private long lastBalanceAt;

	public History() {}

	// ------------------------------------------------------------------ day keys

	public static LocalDate dayOf(long epochMs, ZoneId zone, int dayStartHour) {
		return Instant.ofEpochMilli(epochMs).atZone(zone).minusHours(dayStartHour).toLocalDate();
	}

	public static String keyOf(long epochMs, ZoneId zone, int dayStartHour) {
		return dayOf(epochMs, zone, dayStartHour).toString();
	}

	/** Start of the given day in epoch millis. */
	public static long dayStartMs(LocalDate day, ZoneId zone, int dayStartHour) {
		return day.atStartOfDay(zone).plusHours(dayStartHour).toInstant().toEpochMilli();
	}

	/** End of the given day (start of the next one) in epoch millis. */
	public static long dayEndMs(LocalDate day, ZoneId zone, int dayStartHour) {
		ZonedDateTime start = day.atStartOfDay(zone).plusHours(dayStartHour);
		return start.plusDays(1).toInstant().toEpochMilli();
	}

	// ------------------------------------------------------------------ updates

	/** Adds a payment to the day it happened on and returns its signed effect on that day's net. */
	public synchronized BigDecimal applyPayment(Payment p, boolean includeTax, long nowMs, ZoneId zone, int dayStartHour) {
		BigDecimal delta = p.signedDelta(includeTax);
		dayForWrite(keyOf(nowMs, zone, dayStartHour)).apply(delta, nowMs);
		prune();
		return delta;
	}

	/** Records a balance reading (from the /bal reply). */
	public synchronized void recordBalance(BigDecimal balance, long nowMs, ZoneId zone, int dayStartHour) {
		dayForWrite(keyOf(nowMs, zone, dayStartHour)).balance(balance, nowMs);
		lastBalance = balance;
		lastBalanceAt = nowMs;
		prune();
	}

	/** Clears the payments of one day (keeps its balance readings). */
	public synchronized void resetDayPayments(String key) {
		DayRecord d = days.get(key);
		if (d != null) d.clearPayments();
	}

	public synchronized void deleteDay(String key) {
		days.remove(key);
	}

	// ------------------------------------------------------------------ reads

	/** The record for a day, or an empty one (not stored) when nothing happened that day. */
	public synchronized DayRecord day(String key) {
		DayRecord d = days.get(key);
		return d != null ? d : new DayRecord(key);
	}

	public synchronized BigDecimal todayNet(long nowMs, ZoneId zone, int dayStartHour) {
		DayRecord d = days.get(keyOf(nowMs, zone, dayStartHour));
		return d == null ? BigDecimal.ZERO : d.net();
	}

	/** All stored days, newest first. */
	public synchronized List<DayRecord> daysNewestFirst() {
		List<DayRecord> list = new ArrayList<>(days.values());
		Collections.reverse(list);
		return list;
	}

	public synchronized BigDecimal lastBalance() {
		return lastBalance;
	}

	public synchronized long lastBalanceAt() {
		return lastBalanceAt;
	}

	// ------------------------------------------------------------------ persistence helpers

	synchronized void restore(List<DayRecord> records, BigDecimal lastBalance, long lastBalanceAt) {
		days.clear();
		if (records != null) {
			for (DayRecord r : records) {
				if (r == null || r.date == null) continue;
				try {
					LocalDate.parse(r.date);
				} catch (RuntimeException e) {
					continue;
				}
				r.points().removeIf(p -> p == null || p.net == null);
				days.put(r.date, r);
			}
		}
		this.lastBalance = lastBalance;
		this.lastBalanceAt = lastBalanceAt;
		prune();
	}

	synchronized List<DayRecord> snapshot() {
		return new ArrayList<>(days.values());
	}

	private DayRecord dayForWrite(String key) {
		return days.computeIfAbsent(key, DayRecord::new);
	}

	private void prune() {
		while (days.size() > MAX_DAYS) days.pollFirstEntry();
	}
}
