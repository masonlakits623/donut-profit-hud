package dev.donutprofithud.core;

import java.math.BigDecimal;

/** Running session totals. Every confirmed payment is applied exactly once; nothing is de-duplicated. */
public final class Ledger {
	private BigDecimal received = BigDecimal.ZERO;
	private BigDecimal paid = BigDecimal.ZERO;
	private int count;

	public Ledger() {}

	public Ledger(BigDecimal received, BigDecimal paid, int count) {
		this.received = received == null ? BigDecimal.ZERO : received;
		this.paid = paid == null ? BigDecimal.ZERO : paid;
		this.count = Math.max(0, count);
	}

	/** Applies one payment and returns its signed effect on the net. */
	public synchronized BigDecimal apply(Payment p, boolean includeTax) {
		BigDecimal delta = p.signedDelta(includeTax);
		if (delta.signum() >= 0) {
			received = received.add(delta);
		} else {
			paid = paid.add(delta.negate());
		}
		count++;
		return delta;
	}

	public synchronized BigDecimal received() { return received; }

	public synchronized BigDecimal paid() { return paid; }

	public synchronized BigDecimal net() { return received.subtract(paid); }

	public synchronized int count() { return count; }

	public synchronized void reset() {
		received = BigDecimal.ZERO;
		paid = BigDecimal.ZERO;
		count = 0;
	}
}
