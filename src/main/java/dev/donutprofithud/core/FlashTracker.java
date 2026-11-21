package dev.donutprofithud.core;

import java.math.BigDecimal;

/** Remembers the most recent payment so the HUD can flash its signed amount for a short time. */
public final class FlashTracker {
	private BigDecimal delta;
	private long startedAtMs;

	public synchronized void trigger(BigDecimal signedDelta, long nowMs) {
		this.delta = signedDelta;
		this.startedAtMs = nowMs;
	}

	/** The amount to flash right now, or null when no flash is active. */
	public synchronized BigDecimal active(long nowMs, long durationMs) {
		if (delta == null || durationMs <= 0) return null;
		if (nowMs - startedAtMs >= durationMs) {
			delta = null;
			return null;
		}
		return delta;
	}

	/** 1.0 while fresh, fading to 0.0 over the last 30% of the duration. */
	public synchronized float alpha(long nowMs, long durationMs) {
		if (delta == null || durationMs <= 0) return 0f;
		long elapsed = nowMs - startedAtMs;
		if (elapsed >= durationMs) return 0f;
		long fadeStart = (long) (durationMs * 0.7);
		if (elapsed <= fadeStart) return 1f;
		return 1f - (float) (elapsed - fadeStart) / (float) (durationMs - fadeStart);
	}

	public synchronized void clear() {
		delta = null;
	}
}
