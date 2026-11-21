package dev.donutprofithud.core;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises DonutSMP payment confirmations. Formats were taken from the user's own game logs and
 * screenshots (Nov 2026):
 *
 * <pre>
 *   Steve paid you $ 7.4M
 *   You paid maple_trader $ 100K (+$ 2K tax)
 *   You were paid $ 4.8M while you were away
 * </pre>
 *
 * <p>Safety rules:
 * <ul>
 *   <li>The whole message must match, anchored at both ends. DonutSMP delivers player chat as
 *       system messages too ("&lt;name&gt; text", "[Rank] name: text"), so a player typing
 *       "x paid you $ 5M" arrives with their own prefix and can never match.</li>
 *   <li>Only the exact confirmation shapes count. "X was paid $ ... by Y", shop/auction lines,
 *       "/pay" error messages ("This user is not online", ...) and the user's own typed
 *       commands never match, so failed payments are never counted.</li>
 *   <li>The caller must only pass system (game) messages that are not action-bar overlays.</li>
 * </ul>
 */
public final class PaymentParser {
	private PaymentParser() {}

	/** Java usernames are 3-16 of [A-Za-z0-9_]; Bedrock (Floodgate) players get a leading '.'. */
	private static final String NAME = "(?<name>\\.?[A-Za-z0-9_]{1,16})";
	private static final String AMT = "\\$ ?(?<amt>" + Money.AMOUNT_REGEX + ")";

	private static final Pattern INCOMING = Pattern.compile("^" + NAME + " paid you " + AMT + "$");
	private static final Pattern OUTGOING = Pattern.compile(
		"^You paid " + NAME + " " + AMT + "(?: \\(\\+\\$ ?(?<tax>" + Money.AMOUNT_REGEX + ") tax\\))?$");

	/** Join summary of payments received while offline: "You were paid $ 4.8M while you were away". */
	private static final Pattern AWAY = Pattern.compile("^You were paid " + AMT + " while you were away$");

	/** Legacy formatting codes such as "§a" that some servers embed in literal text. */
	private static final Pattern SECTION_CODES = Pattern.compile("§.");

	public static Optional<Payment> parse(String rawPlainText) {
		if (rawPlainText == null) return Optional.empty();
		String text = normalize(rawPlainText);
		if (text.isEmpty() || text.length() > 120) return Optional.empty();

		Matcher in = INCOMING.matcher(text);
		if (in.matches()) {
			String name = in.group("name");
			if (name.equals("You")) return Optional.empty();
			BigDecimal amount = Money.parse(in.group("amt"));
			if (amount == null || amount.signum() <= 0) return Optional.empty();
			return Optional.of(new Payment(Payment.Direction.INCOMING, name, amount, BigDecimal.ZERO));
		}

		Matcher out = OUTGOING.matcher(text);
		if (out.matches()) {
			if (out.group("name").equalsIgnoreCase("you")) return Optional.empty();
			BigDecimal amount = Money.parse(out.group("amt"));
			if (amount == null || amount.signum() <= 0) return Optional.empty();
			BigDecimal tax = out.group("tax") == null ? BigDecimal.ZERO : Money.parse(out.group("tax"));
			if (tax == null) return Optional.empty();
			return Optional.of(new Payment(Payment.Direction.OUTGOING, out.group("name"), amount, tax));
		}

		Matcher away = AWAY.matcher(text);
		if (away.matches()) {
			BigDecimal amount = Money.parse(away.group("amt"));
			if (amount == null || amount.signum() <= 0) return Optional.empty();
			return Optional.of(new Payment(Payment.Direction.INCOMING, "(while away)", amount, BigDecimal.ZERO, true));
		}
		return Optional.empty();
	}

	/**
	 * Removes decoration that is not part of the message text: legacy colour codes, icon glyphs
	 * (private-use characters from resource packs, emoji, invisible format characters) and extra
	 * whitespace. Leading symbols such as '&lt;' or '[' that start player chat are NOT removed.
	 */
	static String normalize(String s) {
		s = SECTION_CODES.matcher(s).replaceAll("");
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().forEach(cp -> {
			if (isIconOrInvisible(cp)) return;
			if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
				sb.append(' ');
			} else {
				sb.appendCodePoint(cp);
			}
		});
		return sb.toString().replaceAll(" {2,}", " ").trim();
	}

	private static boolean isIconOrInvisible(int cp) {
		int type = Character.getType(cp);
		return type == Character.PRIVATE_USE
			|| type == Character.FORMAT
			|| type == Character.SURROGATE
			|| type == Character.UNASSIGNED
			|| type == Character.OTHER_SYMBOL
			|| type == Character.CONTROL && !Character.isWhitespace(cp);
	}
}
