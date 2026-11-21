package dev.donutprofithud.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exact money handling. Amounts are kept as {@link BigDecimal}; only the display is compact.
 *
 * <p>Note: DonutSMP itself prints amounts in compact form ("$ 7.4M"), so the most precise value
 * the client ever receives is what the server printed. Parsing "7.4M" gives exactly 7,400,000.
 */
public final class Money {
	private Money() {}

	/** One amount token as DonutSMP prints it: "166", "6.7", "100K", "7.4M", "1.5B", "112,348,451". */
	public static final String AMOUNT_REGEX = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?[KkMmBbTt]?";

	private static final Pattern AMOUNT = Pattern.compile("^(" + "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?" + ")([KkMmBbTt]?)$");

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
	private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);
	private static final BigDecimal TRILLION = BigDecimal.valueOf(1_000_000_000_000L);

	/** Parses one amount token; returns null if it is not a valid amount. */
	public static BigDecimal parse(String token) {
		if (token == null) return null;
		Matcher m = AMOUNT.matcher(token.trim());
		if (!m.matches()) return null;
		BigDecimal base = new BigDecimal(m.group(1).replace(",", ""));
		String suffix = m.group(2).toUpperCase(Locale.ROOT);
		BigDecimal mult = switch (suffix) {
			case "K" -> THOUSAND;
			case "M" -> MILLION;
			case "B" -> BILLION;
			case "T" -> TRILLION;
			default -> BigDecimal.ONE;
		};
		return base.multiply(mult).stripTrailingZeros();
	}

	/** Compact display without a sign for positives: 3000000 -> "$3M", -2000000 -> "-$2M", 6.7 -> "$6.7". */
	public static String format(BigDecimal value) {
		return format(value, false);
	}

	/** Compact display with an explicit sign: "+$3M", "-$3M", "$0". */
	public static String formatSigned(BigDecimal value) {
		return format(value, true);
	}

	private static String format(BigDecimal value, boolean forceSign) {
		if (value == null) value = BigDecimal.ZERO;
		int sign = value.signum();
		BigDecimal abs = value.abs();
		String body = compact(abs);
		if (body.equals("0")) return "$0";
		String prefix = sign < 0 ? "-" : (forceSign ? "+" : "");
		return prefix + "$" + body;
	}

	/** Compact form of a non-negative amount with at most two decimals, e.g. 1250000 -> "1.25M". */
	static String compact(BigDecimal abs) {
		String[] suffixes = {"", "K", "M", "B"};
		BigDecimal[] divisors = {BigDecimal.ONE, THOUSAND, MILLION, BILLION};
		int idx = 0;
		for (int i = divisors.length - 1; i > 0; i--) {
			if (abs.compareTo(divisors[i]) >= 0) {
				idx = i;
				break;
			}
		}
		BigDecimal scaled = abs.divide(divisors[idx]).setScale(2, RoundingMode.HALF_UP);
		// 999,999 would round to "1000K": promote to the next unit instead.
		if (idx < divisors.length - 1 && scaled.compareTo(THOUSAND) >= 0) {
			idx++;
			scaled = abs.divide(divisors[idx]).setScale(2, RoundingMode.HALF_UP);
		}
		String num = scaled.stripTrailingZeros().toPlainString();
		return num + suffixes[idx];
	}
}
