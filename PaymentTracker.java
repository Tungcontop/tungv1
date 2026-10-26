package dev.donutpay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Holds the list of incoming payments and what you've marked them as. */
public final class PaymentTracker {

	public enum State { PENDING, WIN, LOSE, SKIP }

	public static final class Payment {
		public final String name;
		public final long amount;
		public State state = State.PENDING;

		Payment(String name, long amount) {
			this.name = name;
			this.amount = amount;
		}
	}

	/** Number of rows shown at once. */
	public static final int VISIBLE_ROWS = 6;
	/** Oldest entries are dropped past this many. */
	private static final int MAX_ENTRIES = 200;
	/** The "x" value: what a win is worth relative to the payment. Change to taste. */
	public static final double WIN_MULTIPLIER = 2.0;

	private static final List<Payment> ENTRIES = new ArrayList<>();
	private static final Deque<Payment> HISTORY = new ArrayDeque<>();
	private static int scroll = 0; // how many rows we've scrolled up from the bottom

	// "Name paid you $25.1M" (anchored to the start of the line so other players
	// typing that sentence in public chat doesn't count)
	private static final Pattern PAID_YOU = Pattern.compile(
		"^(?:\\[[^\\]]*]\\s*)?(\\.?[A-Za-z0-9_]{3,17}) paid you \\$([\\d,]+(?:\\.\\d+)?)\\s*([kKmMbBtT]?)(?![A-Za-z0-9])");
	private static final Pattern AMOUNT = Pattern.compile(
		"^\\$?([\\d,]+(?:\\.\\d+)?)\\s*([kKmMbBtT]?)$");

	private PaymentTracker() {}

	// ---------- parsing / formatting ----------

	/** Returns a Payment if the chat line is an incoming payment, else null. Does not add it. */
	public static Payment parseChat(String line) {
		Matcher m = PAID_YOU.matcher(line);
		if (!m.find()) return null;
		long amount = toLong(m.group(2), m.group(3));
		if (amount <= 0) return null;
		return new Payment(m.group(1), amount);
	}

	/** Parses things like "25.1M", "$500k", "1,000,000". Returns -1 if invalid. */
	public static long parseAmount(String text) {
		Matcher m = AMOUNT.matcher(text.trim());
		if (!m.matches()) return -1;
		return toLong(m.group(1), m.group(2));
	}

	private static long toLong(String number, String suffix) {
		try {
			double v = Double.parseDouble(number.replace(",", ""));
			switch (suffix.toLowerCase(Locale.ROOT)) {
				case "k" -> v *= 1e3;
				case "m" -> v *= 1e6;
				case "b" -> v *= 1e9;
				case "t" -> v *= 1e12;
				default -> { }
			}
			return Math.round(v);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static String format(long value) {
		double d = value;
		String suffix = "";
		if (value >= 1_000_000_000_000L) { d /= 1e12; suffix = "T"; }
		else if (value >= 1_000_000_000L) { d /= 1e9; suffix = "B"; }
		else if (value >= 1_000_000L) { d /= 1e6; suffix = "M"; }
		else if (value >= 1_000L) { d /= 1e3; suffix = "K"; }
		String s = String.format(Locale.ROOT, "%.1f", d);
		if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
		return s + suffix;
	}

	public static long payout(Payment p) {
		return Math.round(p.amount * WIN_MULTIPLIER);
	}

	// ---------- list operations ----------

	public static void add(String name, long amount) {
		ENTRIES.add(new Payment(name, amount));
		while (ENTRIES.size() > MAX_ENTRIES) {
			Payment old = ENTRIES.remove(0);
			HISTORY.remove(old);
		}
		scroll = 0;
	}

	public static List<Payment> entries() {
		return ENTRIES;
	}

	/** The payment currently up for a decision: the oldest one not yet marked. */
	public static Payment selected() {
		for (Payment p : ENTRIES) {
			if (p.state == State.PENDING) return p;
		}
		return null;
	}

	public static void mark(State state) {
		Payment p = selected();
		if (p == null) return;
		p.state = state;
		HISTORY.push(p);
		scroll = 0;
	}

	public static void undo() {
		while (!HISTORY.isEmpty()) {
			Payment p = HISTORY.pop();
			if (ENTRIES.contains(p)) {
				p.state = State.PENDING;
				scroll = 0;
				return;
			}
		}
	}

	public static boolean canUndo() {
		for (Payment p : HISTORY) {
			if (ENTRIES.contains(p)) return true;
		}
		return false;
	}

	public static void clear() {
		ENTRIES.clear();
		HISTORY.clear();
		scroll = 0;
	}

	// ---------- scrolling window ----------

	public static int windowEnd() {
		return ENTRIES.size() - scroll;
	}

	public static int windowStart() {
		return Math.max(0, windowEnd() - VISIBLE_ROWS);
	}

	public static void scrollBy(int rows) {
		int max = Math.max(0, ENTRIES.size() - VISIBLE_ROWS);
		scroll = Math.max(0, Math.min(max, scroll + rows));
	}
}
