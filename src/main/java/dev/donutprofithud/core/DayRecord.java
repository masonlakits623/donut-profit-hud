package dev.donutprofithud.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Totals for one day, plus the running net after every payment (for the graph). */
public final class DayRecord {
	/** Most points kept per day; beyond this, neighbouring points are merged. */
	static final int MAX_POINTS = 2000;

	/** One step of the graph: the day's net right after a payment at {@code t} (epoch millis). */
	public static final class Point {
		public long t;
		public BigDecimal net;

		public Point() {}

		public Point(long t, BigDecimal net) {
			this.t = t;
			this.net = net;
		}
	}

	/** ISO date, e.g. "2026-11-23". */
	public String date;
	public BigDecimal received = BigDecimal.ZERO;
	public BigDecimal paid = BigDecimal.ZERO;
	public int count;
	/** First and latest balance seen this day (from /bal), or null if /bal was not used that day. */
	public BigDecimal startBalance;
	public BigDecimal endBalance;
	public long startBalanceAt;
	public long endBalanceAt;
	public List<Point> points = new ArrayList<>();

	public DayRecord() {}

	public DayRecord(String date) {
		this.date = date;
	}

	public BigDecimal net() {
		return received().subtract(paid());
	}

	public BigDecimal received() {
		return received == null ? BigDecimal.ZERO : received;
	}

	public BigDecimal paid() {
		return paid == null ? BigDecimal.ZERO : paid;
	}

	public List<Point> points() {
		if (points == null) points = new ArrayList<>();
		return points;
	}

	/** Balance change between the first and latest /bal of the day, or null with fewer than two readings. */
	public BigDecimal balanceChange() {
		if (startBalance == null || endBalance == null || startBalanceAt == endBalanceAt) return null;
		return endBalance.subtract(startBalance);
	}

	/** Net at time {@code t}: the last point at or before t, or 0 before the first payment. */
	public BigDecimal netAt(long t) {
		BigDecimal v = BigDecimal.ZERO;
		for (Point p : points()) {
			if (p.t > t) break;
			v = p.net;
		}
		return v;
	}

	void apply(BigDecimal delta, long t) {
		if (delta.signum() >= 0) {
			received = received().add(delta);
		} else {
			paid = paid().add(delta.negate());
		}
		count++;
		List<Point> pts = points();
		pts.add(new Point(t, net()));
		if (pts.size() > MAX_POINTS) pts.remove(pts.size() / 2); // keeps first, last and overall shape
	}

	void balance(BigDecimal value, long t) {
		if (startBalance == null) {
			startBalance = value;
			startBalanceAt = t;
		}
		endBalance = value;
		endBalanceAt = t;
	}

	void clearPayments() {
		received = BigDecimal.ZERO;
		paid = BigDecimal.ZERO;
		count = 0;
		points().clear();
	}
}
