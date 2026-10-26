package dev.donutpay;

import dev.donutpay.PaymentTracker.Payment;
import dev.donutpay.PaymentTracker.State;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the panel and handles clicks on its buttons. */
public final class HudRenderer {

	// Panel position on screen (top-left). Change these to move it.
	public static int originX = 6;
	public static int originY = 6;

	private static final int WIDTH = 196;
	private static final int PAD = 5;
	private static final int LINE = 11;
	private static final int BTN_H = 16;
	private static final int GAP = 3;

	private static final int BG = 0xC0101018;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int LIGHT = 0xFFCCCCCC;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int GOLD = 0xFFFFAA00;
	private static final int AQUA = 0xFF55FFFF;

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final Rect EMPTY = new Rect(0, 0, 0, 0);
	private static Rect panel = EMPTY;
	private static Rect winBtn = EMPTY;
	private static Rect loseBtn = EMPTY;
	private static Rect skipBtn = EMPTY;
	private static Rect undoBtn = EMPTY;

	private HudRenderer() {}

	// ---------- small drawing helpers ----------

	private static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
		g.text(font, s, x, y, color, true);
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	// ---------- main render ----------

	public static void render(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean interactive) {
		Font font = Minecraft.getInstance().font;
		List<Payment> list = PaymentTracker.entries();
		int size = list.size();
		int start = PaymentTracker.windowStart();
		int end = PaymentTracker.windowEnd();
		Payment sel = PaymentTracker.selected();

		boolean hiddenAbove = start > 0;
		boolean hiddenBelow = end < size;
		int rows = Math.max(1, end - start);

		int height = PAD + LINE + LINE
			+ (hiddenAbove ? LINE : 0)
			+ rows * LINE
			+ (hiddenBelow ? LINE : 0)
			+ (sel != null ? LINE + 2 : 0)
			+ 4 + BTN_H + GAP + BTN_H + GAP + BTN_H + PAD;

		int x = originX;
		int y = originY;
		panel = new Rect(x, y, WIDTH, height);
		g.fill(x, y, x + WIDTH, y + height, BG);
		outline(g, x, y, WIDTH, height, 0x40FFFFFF);

		int cy = y + PAD;

		// header
		text(g, font, "»", x + PAD, cy, GOLD);
		text(g, font, "Gambling HUD", x + PAD + 10, cy, WHITE);
		cy += LINE;
		text(g, font, "(press T/Enter to interact)", x + PAD, cy, GRAY);
		cy += LINE;

		if (hiddenAbove) {
			text(g, font, "▲ scroll up for more (" + start + ")", x + PAD, cy, GRAY);
			cy += LINE;
		}

		// rows
		if (size == 0) {
			text(g, font, "waiting for payments...", x + PAD, cy, GRAY);
			cy += LINE;
		} else {
			for (int i = start; i < end; i++) {
				Payment p = list.get(i);
				boolean isSel = p == sel;
				if (isSel) {
					g.fill(x + 2, cy - 1, x + WIDTH - 2, cy + LINE - 1, 0x66FFAA00);
				}

				String icon;
				int iconColor;
				switch (p.state) {
					case WIN -> { icon = "✔"; iconColor = GREEN; }
					case LOSE -> { icon = "✘"; iconColor = RED; }
					case SKIP -> { icon = "–"; iconColor = GRAY; }
					default -> {
						icon = isSel ? "▶" : "•";
						iconColor = isSel ? YELLOW : GRAY;
					}
				}
				text(g, font, icon, x + PAD, cy, iconColor);
				text(g, font, p.name, x + PAD + 12, cy, AQUA);

				String amount = PaymentTracker.format(p.amount);
				int right = x + WIDTH - PAD;
				if (isSel) {
					String arrow = " → ";
					String payout = PaymentTracker.format(PaymentTracker.payout(p));
					int total = font.width(amount) + font.width(arrow) + font.width(payout);
					int px = right - total;
					text(g, font, amount, px, cy, LIGHT);
					px += font.width(amount);
					text(g, font, arrow, px, cy, WHITE);
					px += font.width(arrow);
					text(g, font, payout, px, cy, GREEN);
				} else {
					text(g, font, amount, right - font.width(amount), cy, LIGHT);
				}
				cy += LINE;
			}
		}

		if (hiddenBelow) {
			text(g, font, "▼ scroll down (" + (size - end) + ")", x + PAD, cy, GRAY);
			cy += LINE;
		}

		// "x = 50.2M"
		if (sel != null) {
			cy += 2;
			int px = x + PAD;
			text(g, font, "x", px, cy, GREEN);
			px += font.width("x");
			text(g, font, " = ", px, cy, GRAY);
			px += font.width(" = ");
			text(g, font, PaymentTracker.format(PaymentTracker.payout(sel)), px, cy, AQUA);
			cy += LINE;
		}

		cy += 4;
		int full = WIDTH - 2 * PAD;
		int half = (full - GAP) / 2;
		boolean hasPending = sel != null;

		winBtn = new Rect(x + PAD, cy, full, BTN_H);
		cy += BTN_H + GAP;
		loseBtn = new Rect(x + PAD, cy, half, BTN_H);
		skipBtn = new Rect(x + PAD + half + GAP, cy, full - half - GAP, BTN_H);
		cy += BTN_H + GAP;
		undoBtn = new Rect(x + PAD, cy, full, BTN_H);

		button(g, font, winBtn, "✔ WIN", 0xFF1F6F1F, 0xFF2B8F2B, GREEN, hasPending, interactive, mouseX, mouseY);
		button(g, font, loseBtn, "✘ LOSE", 0xFF6F1F1F, 0xFF8F2B2B, RED, hasPending, interactive, mouseX, mouseY);
		button(g, font, skipBtn, "→ SKIP", 0xFF3A3A3A, 0xFF505050, LIGHT, hasPending, interactive, mouseX, mouseY);
		button(g, font, undoBtn, "↶ Undo last", 0xFF4A4010, 0xFF645617, YELLOW, PaymentTracker.canUndo(), interactive, mouseX, mouseY);
	}

	private static void button(GuiGraphicsExtractor g, Font font, Rect r, String label, int bg, int hoverBg,
			int textColor, boolean enabled, boolean interactive, int mouseX, int mouseY) {
		boolean hover = interactive && enabled && r.contains(mouseX, mouseY);
		int fill = !enabled ? 0xFF262626 : hover ? hoverBg : bg;
		g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), fill);
		outline(g, r.x(), r.y(), r.w(), r.h(), 0x80000000);
		int tx = r.x() + (r.w() - font.width(label)) / 2;
		int ty = r.y() + (r.h() - 8) / 2;
		text(g, font, label, tx, ty, enabled ? textColor : 0xFF666666);
	}

	/** Returns true if the click landed on the panel (so the chat screen should ignore it). */
	public static boolean click(double mx, double my, int mouseButton) {
		if (!panel.contains(mx, my)) return false;
		if (mouseButton != 0) return true;

		boolean hasPending = PaymentTracker.selected() != null;
		if (hasPending && winBtn.contains(mx, my)) PaymentTracker.mark(State.WIN);
		else if (hasPending && loseBtn.contains(mx, my)) PaymentTracker.mark(State.LOSE);
		else if (hasPending && skipBtn.contains(mx, my)) PaymentTracker.mark(State.SKIP);
		else if (undoBtn.contains(mx, my)) PaymentTracker.undo();
		return true;
	}

	/** Returns true if the scroll happened over the panel. */
	public static boolean scroll(double mx, double my, double vertical) {
		if (!panel.contains(mx, my)) return false;
		PaymentTracker.scrollBy(vertical > 0 ? 1 : -1);
		return true;
	}
}
