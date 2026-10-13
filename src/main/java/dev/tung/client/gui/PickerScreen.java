package dev.tung.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tung.client.module.ListSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Searchable checklist for a {@link ListSetting}: type to filter, click a row to
 * tick or untick it, scroll with the mouse wheel. "Select all" and "Clear" apply to
 * whatever the search currently shows.
 */
public class PickerScreen extends Screen {
	private static final int W = 280;
	private static final int HEADER_H = 36;
	private static final int SEARCH_H = 22;
	private static final int ROW_H = 14;
	private static final int FOOTER_H = 34;
	private static final int PAD = 12;

	private final Screen parent;
	private final ListSetting setting;

	private String query = "";
	private List<Identifier> filtered = new ArrayList<>();
	private int scroll;

	// geometry, computed in init()
	private int px, py, ph, searchY, listTop, listBottom;

	public PickerScreen(Screen parent, ListSetting setting) {
		super(Component.literal("Select " + setting.name));
		this.parent = parent;
		this.setting = setting;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		ph = Math.max(200, Math.min(this.height - 40, 380));
		px = (this.width - W) / 2;
		py = (this.height - ph) / 2;
		searchY = py + HEADER_H + 8;
		listTop = searchY + SEARCH_H + 8;
		listBottom = py + ph - FOOTER_H;
		refilter();
	}

	private int visibleRows() {
		return Math.max(1, (listBottom - listTop) / ROW_H);
	}

	private void refilter() {
		String q = query.toLowerCase().trim().replace(' ', '_');
		List<Identifier> out = new ArrayList<>();
		for (Identifier id : setting.all()) {
			if (q.isEmpty() || id.getPath().contains(q)) out.add(id);
		}
		filtered = out;
		scroll = 0;
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(scroll, Math.max(0, filtered.size() - visibleRows())));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ---- layout of the three footer buttons ------------------------------------

	private int buttonW() {
		return (W - 2 * PAD - 2 * 6) / 3;
	}

	private int buttonX(int index) {
		return px + PAD + index * (buttonW() + 6);
	}

	private int buttonY() {
		return py + ph - FOOTER_H + 7;
	}

	// ---- drawing ------------------------------------------------------------

	private static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
		for (int i = 0; i < h; i++) {
			float t = h <= 1 ? 0f : (float) i / (h - 1);
			g.fill(x, y + i, x + w, y + i + 1, Theme.lerp(top, bottom, t));
		}
	}

	private void button(GuiGraphicsExtractor g, int index, String label, boolean accent, int mx, int my) {
		int x = buttonX(index), y = buttonY(), w = buttonW(), h = 20;
		boolean hover = inside(mx, my, x, y, w, h);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
		g.fill(x, y, x + w, y + h, accent ? (hover ? Theme.ACCENT : Theme.ACCENT_DIM) : (hover ? Theme.ROW_HOVER : Theme.SECTION_BG));
		int color = accent ? 0xFF1A110B : Theme.TEXT;
		g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + 6, color, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, this.width, this.height, 0x80000000);

		// panel
		g.fill(px - 2, py - 1, px + W + 2, py + ph + 3, Theme.SHADOW);
		g.fill(px - 1, py - 1, px + W + 1, py + ph + 1, Theme.BORDER);
		gradient(g, px, py, W, ph, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);

		// header
		gradient(g, px, py, W, HEADER_H, Theme.HEADER_TOP, Theme.HEADER_BOTTOM);
		g.fill(px + PAD, py + 14, px + PAD + 8, py + 22, Theme.ACCENT);
		g.text(this.font, "Select " + setting.name, px + PAD + 16, py + 14, Theme.TEXT_ACTIVE, false);
		String count = setting.count() + " selected";
		g.text(this.font, count, px + W - PAD - this.font.width(count), py + 14, Theme.TEXT_MUTED, false);

		// search box
		g.fill(px + PAD - 1, searchY - 1, px + W - PAD + 1, searchY + SEARCH_H + 1, Theme.BORDER);
		g.fill(px + PAD, searchY, px + W - PAD, searchY + SEARCH_H, Theme.SECTION_BG);
		boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
		if (query.isEmpty()) {
			g.text(this.font, "Type to search...", px + PAD + 6, searchY + 7, Theme.TEXT_MUTED, false);
		}
		g.text(this.font, query + (blink ? "_" : ""), px + PAD + 6, searchY + 7, Theme.TEXT, false);

		// rows
		clampScroll();
		int rows = visibleRows();
		for (int i = 0; i < rows; i++) {
			int idx = scroll + i;
			if (idx >= filtered.size()) break;
			Identifier id = filtered.get(idx);
			int ry = listTop + i * ROW_H;
			boolean on = setting.isSelected(id);
			boolean hover = inside(mouseX, mouseY, px + PAD, ry, W - 2 * PAD, ROW_H);
			if (on) {
				g.fill(px + PAD, ry, px + W - PAD, ry + ROW_H, Theme.ROW_ACTIVE);
			} else if (hover) {
				g.fill(px + PAD, ry, px + W - PAD, ry + ROW_H, Theme.ROW_HOVER);
			}
			// checkbox
			g.fill(px + PAD + 4, ry + 3, px + PAD + 12, ry + 11, Theme.BORDER);
			g.fill(px + PAD + 5, ry + 4, px + PAD + 11, ry + 10, on ? Theme.ACCENT : Theme.SECTION_BG);
			String label = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
			g.text(this.font, label, px + PAD + 18, ry + 3, on ? Theme.TEXT_ACTIVE : Theme.TEXT, false);
		}
		if (filtered.isEmpty()) {
			g.text(this.font, "Nothing matches", px + PAD + 6, listTop + 4, Theme.TEXT_MUTED, false);
		}

		// scrollbar
		if (filtered.size() > rows) {
			int trackH = rows * ROW_H;
			int thumbH = Math.max(12, trackH * rows / filtered.size());
			int maxScroll = filtered.size() - rows;
			int thumbY = listTop + (trackH - thumbH) * scroll / Math.max(1, maxScroll);
			g.fill(px + W - PAD + 2, listTop, px + W - PAD + 4, listTop + trackH, Theme.BORDER);
			g.fill(px + W - PAD + 2, thumbY, px + W - PAD + 4, thumbY + thumbH, Theme.ACCENT);
		}

		// footer
		g.fill(px, py + ph - FOOTER_H, px + W, py + ph - FOOTER_H + 1, Theme.BORDER);
		button(g, 0, "Select all", false, mouseX, mouseY);
		button(g, 1, "Clear", false, mouseX, mouseY);
		button(g, 2, "Done", true, mouseX, mouseY);
	}

	// ---- input ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (event.button() != 0) return true;

		int by = buttonY();
		if (inside(mx, my, buttonX(0), by, buttonW(), 20)) {
			setting.select(filtered);
			return true;
		}
		if (inside(mx, my, buttonX(1), by, buttonW(), 20)) {
			setting.deselect(filtered);
			return true;
		}
		if (inside(mx, my, buttonX(2), by, buttonW(), 20)) {
			this.onClose();
			return true;
		}

		if (inside(mx, my, px + PAD, listTop, W - 2 * PAD, visibleRows() * ROW_H)) {
			int idx = scroll + (int) ((my - listTop) / ROW_H);
			if (idx >= 0 && idx < filtered.size()) setting.toggle(filtered.get(idx));
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll -= (int) Math.signum(scrollY) * 3;
		clampScroll();
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		int cp = event.codepoint();
		if (cp >= 32 && cp != 127 && query.length() < 40) {
			query += new String(Character.toChars(cp));
			refilter();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (!query.isEmpty()) {
				query = query.substring(0, query.length() - 1);
				refilter();
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_RSHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
