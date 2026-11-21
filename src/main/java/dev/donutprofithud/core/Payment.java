package dev.donutprofithud.core;

import java.math.BigDecimal;

/**
 * One confirmed payment read from a DonutSMP confirmation message.
 *
 * @param direction   incoming (someone paid you) or outgoing (you paid someone)
 * @param counterparty the other player's name as printed by the server
 * @param amount      the amount the other side received / sent
 * @param tax         tax DonutSMP charged on an outgoing payment (zero for incoming)
 * @param away        true for the join summary "You were paid $ X while you were away"
 */
public record Payment(Direction direction, String counterparty, BigDecimal amount, BigDecimal tax, boolean away) {
	public enum Direction { INCOMING, OUTGOING }

	public Payment(Direction direction, String counterparty, BigDecimal amount, BigDecimal tax) {
		this(direction, counterparty, amount, tax, false);
	}

	/** Signed effect on the net: +amount for incoming, -(amount [+ tax]) for outgoing. */
	public BigDecimal signedDelta(boolean includeTax) {
		if (direction == Direction.INCOMING) return amount;
		BigDecimal out = includeTax ? amount.add(tax) : amount;
		return out.negate();
	}
}
