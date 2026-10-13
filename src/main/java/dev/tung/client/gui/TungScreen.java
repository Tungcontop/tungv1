package dev.tung.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tung.client.module.Category;
import dev.tung.client.module.ListSetting;
import dev.tung.client.module.Module;
import dev.tung.client.module.ModuleManager;
import dev.tung.client.module.NumberSetting;
import dev.tung.client.module.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tung v.1 click GUI: category sidebar on the left, module list on the right.
 * Drag either panel by its header to move it. Left click a module to toggle it.
 * Right click it (or click the three dots) to open its settings, then drag the sliders.
 */
public class TungScreen extends Screen {
	private static final int PANEL_W = 220;
	private static final int HEADER_H = 36;
	private static final int ROW_H = 26;
	private static final int SETTING_H = 32;
	private static final int SECTION_H = 18;
	private static final int FOOTER_H = 30;
	private static final int RIGHT_W = 220;
	private static final int RIGHT_HEADER_H = HEADER_H - 6;

	private static final String[] MISC = {"Profiles", "Macros"};

	// Remembered between openings, including where you left the panels
	private static Category selected = Category.COMBAT;
	private static final Set<Module> EXPANDED = new HashSet<>();
	private static int leftX = 40, leftY = 24;
	private static int rightX = 40 + PANEL_W + 16, rightY = 24;

	private enum Drag { NONE, LEFT_PANEL, RIGHT_PANEL, SLIDER }

	private Drag dragMode = Drag.NONE;
	private int dragOffsetX, dragOffsetY;
	private NumberSetting draggingSlider;

	/** One visible line in the right panel: either a module or one of its settings. */
	private record Row(Module module, Setting setting, int y, int h) {}

	public TungScreen() {
		super(Component.literal("Tung v.1"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- layout helpers ------------------------------------------------

	private int leftHeight() {
		return HEADER_H + Category.values().length * ROW_H + SECTION_H + MISC.length * ROW_H + FOOTER_H;
	}

	private int rightHeight() {
		List<Row> rows = buildRows();
		int bodyH = ROW_H;
		if (!rows.isEmpty()) {
			Row last = rows.get(rows.size() - 1);
			bodyH = (last.y() + last.h()) - (rightY + RIGHT_HEADER_H);
		}
		return RIGHT_HEADER_H + bodyH + 8;
	}

	private int categoryRowY(int index) {
		return leftY + HEADER_H + index * ROW_H;
	}

	private int miscRowY(int index) {
		return leftY + HEADER_H + Category.values().length * ROW_H + SECTION_H + index * ROW_H;
	}

	private List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		int y = rightY + RIGHT_HEADER_H;
		for (Module m : ModuleManager.in(selected)) {
			rows.add(new Row(m, null, y, ROW_H));
			y += ROW_H;
			if (EXPANDED.contains(m)) {
				for (Setting s : m.settings()) {
					int h = (s instanceof NumberSetting) ? SETTING_H : ROW_H;
					rows.add(new Row(m, s, y, h));
					y += h;
				}
			}
		}
		return rows;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// slider geometry shared by drawing and mouse handling
	private int trackX() { return rightX + 14; }
	private int trackW() { return RIGHT_W - 28; }

	/** Keeps a panel fully on screen (at least its header stays reachable). */
	private int clampX(int x, int w) {
		return Math.max(0, Math.min(this.width - w, x));
	}

	private int clampY(int y, int h) {
		return Math.max(0, Math.min(this.height - Math.min(h, 40), y));
	}

	// ---- drawing helpers -----------------------------------------------

	/** Vertical gradient built from 1px strips, so it only needs fill(). */
	private static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
		for (int i = 0; i < h; i++) {
			float t = h <= 1 ? 0f : (float) i / (h - 1);
			g.fill(x, y + i, x + w, y + i + 1, Theme.lerp(top, bottom, t));
		}
	}

	private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		// soft drop shadow
		g.fill(x - 2, y - 1, x + w + 2, y + h + 3, Theme.SHADOW);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, Theme.BORDER);
		gradient(g, x, y, w, h, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
	}

	private void chevron(GuiGraphicsExtractor g, int x, int y, int color) {
		g.text(this.font, ">", x, y, color, false);
	}

	// ---- rendering -----------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Slight dim over the world; no blur so the game stays readable.
		g.fill(0, 0, this.width, this.height, 0x40000000);

		drawLeftPanel(g, mouseX, mouseY);
		drawRightPanel(g, mouseX, mouseY);
	}

	private void drawLeftPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int x = leftX, y = leftY, w = PANEL_W, h = leftHeight();
		panel(g, x, y, w, h);

		// Header
		gradient(g, x, y, w, HEADER_H, Theme.HEADER_TOP, Theme.HEADER_BOTTOM);
		g.text(this.font, "TUNG", x + 14, y + 14, Theme.ACCENT, true);
		int nameW = this.font.width("TUNG");
		int tagX = x + 14 + nameW + 5;
		g.fill(tagX - 3, y + 11, tagX + this.font.width("v.1") + 3, y + 24, Theme.ACCENT_DIM);
		g.text(this.font, "v.1", tagX, y + 14, 0xFF1A110B, false);
		g.text(this.font, "*", x + w - 20, y + 14, Theme.TEXT_MUTED, false); // settings

		// Categories
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			int ry = categoryRowY(i);
			boolean active = cats[i] == selected;
			boolean hover = inside(mouseX, mouseY, x, ry, w, ROW_H);
			if (active) {
				g.fill(x, ry, x + w, ry + ROW_H, Theme.ROW_ACTIVE);
				g.fill(x, ry, x + 2, ry + ROW_H, Theme.ACCENT);
			} else if (hover) {
				g.fill(x, ry, x + w, ry + ROW_H, Theme.ROW_HOVER);
			}
			int color = active ? Theme.TEXT_ACTIVE : Theme.TEXT;
			// small square stands in for the category icon
			g.fill(x + 14, ry + 9, x + 22, ry + 17, active ? Theme.ACCENT : Theme.TEXT_MUTED);
			g.text(this.font, cats[i].displayName, x + 30, ry + 9, color, false);
			chevron(g, x + w - 16, ry + 9, active ? Theme.ACCENT : Theme.TEXT_MUTED);
		}

		// MISC section
		int sy = leftY + HEADER_H + cats.length * ROW_H;
		g.fill(x, sy, x + w, sy + SECTION_H, Theme.SECTION_BG);
		g.text(this.font, "MISC", x + 14, sy + 5, Theme.TEXT_MUTED, false);

		for (int i = 0; i < MISC.length; i++) {
			int ry = miscRowY(i);
			if (inside(mouseX, mouseY, x, ry, w, ROW_H)) {
				g.fill(x, ry, x + w, ry + ROW_H, Theme.ROW_HOVER);
			}
			g.text(this.font, MISC[i], x + 14, ry + 9, Theme.TEXT, false);
			if (MISC[i].equals("Profiles")) {
				int tw = this.font.width("default");
				int bx = x + w - 34 - tw - 10;
				g.fill(bx, ry + 5, bx + tw + 12, ry + ROW_H - 5, Theme.SECTION_BG);
				g.text(this.font, "default", bx + 6, ry + 9, Theme.TEXT_MUTED, false);
			}
			chevron(g, x + w - 16, ry + 9, Theme.TEXT_MUTED);
		}

		// Footer
		int fy = y + h - FOOTER_H;
		g.fill(x, fy, x + w, fy + 1, Theme.BORDER);
		g.fill(x + 14, fy + 11, x + 22, fy + 19, Theme.TEXT_MUTED);
	}

	private void drawRightPanel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		List<Row> rows = buildRows();
		int x = rightX, y = rightY, w = RIGHT_W;
		panel(g, x, y, w, rightHeight());

		// Header
		gradient(g, x, y, w, RIGHT_HEADER_H, Theme.HEADER_TOP, Theme.HEADER_BOTTOM);
		g.fill(x + 14, y + 11, x + 22, y + 19, Theme.ACCENT);
		g.text(this.font, selected.displayName, x + 30, y + 11, Theme.TEXT_ACTIVE, false);
		g.text(this.font, "^", x + w - 20, y + 11, Theme.TEXT_MUTED, false);

		if (rows.isEmpty()) {
			g.text(this.font, "No modules yet", x + 14, y + RIGHT_HEADER_H + 10, Theme.TEXT_MUTED, false);
			return;
		}

		for (Row row : rows) {
			if (row.setting() == null) {
				drawModuleRow(g, row, mouseX, mouseY);
			} else {
				drawSettingRow(g, row, mouseX, mouseY);
			}
		}
	}

	private void drawModuleRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
		Module m = row.module();
		int x = rightX, w = RIGHT_W, ry = row.y();
		boolean hover = inside(mouseX, mouseY, x, ry, w, ROW_H);

		if (m.isEnabled()) {
			g.fill(x, ry, x + w, ry + ROW_H, Theme.ROW_ACTIVE);
			g.fill(x, ry, x + 2, ry + ROW_H, Theme.ACCENT);
		} else if (hover) {
			g.fill(x, ry, x + w, ry + ROW_H, Theme.ROW_HOVER);
		}
		g.text(this.font, m.name, x + 14, ry + 9, m.isEnabled() ? Theme.TEXT_ACTIVE : Theme.TEXT, false);

		// three-dot menu marker, lit up while the settings are open
		int dotColor = EXPANDED.contains(m) ? Theme.ACCENT : Theme.TEXT_MUTED;
		int dx = x + w - 16;
		for (int d = 0; d < 3; d++) {
			g.fill(dx, ry + 6 + d * 5, dx + 2, ry + 8 + d * 5, dotColor);
		}
	}

	private void drawSettingRow(GuiGraphicsExtractor g, Row row, int mouseX, int mouseY) {
		int x = rightX, w = RIGHT_W, ry = row.y();

		// list pickers (mobs, blocks): one button-style row that opens the picker
		if (row.setting() instanceof ListSetting list) {
			boolean hover = inside(mouseX, mouseY, x, ry, w, ROW_H);
			g.fill(x, ry, x + w, ry + ROW_H, hover ? Theme.ROW_HOVER : Theme.SECTION_BG);
			g.fill(x, ry, x + 2, ry + ROW_H, Theme.ACCENT_DIM);
			g.text(this.font, list.name, x + 14, ry + 9, Theme.TEXT, false);
			String value = list.count() + " selected  >";
			g.text(this.font, value, x + w - 14 - this.font.width(value), ry + 9, Theme.TEXT_ACTIVE, false);
			return;
		}

		NumberSetting s = (NumberSetting) row.setting();

		g.fill(x, ry, x + w, ry + SETTING_H, Theme.SECTION_BG);
		g.fill(x, ry, x + 2, ry + SETTING_H, Theme.ACCENT_DIM);

		g.text(this.font, s.name, x + 14, ry + 6, Theme.TEXT, false);
		String value = s.display();
		g.text(this.font, value, x + w - 14 - this.font.width(value), ry + 6, Theme.TEXT_ACTIVE, false);

		// slider track + fill + knob
		int tx = trackX(), tw = trackW(), ty = ry + 21;
		g.fill(tx, ty, tx + tw, ty + 4, Theme.BORDER);
		int filled = Math.round(tw * s.fraction());
		g.fill(tx, ty, tx + filled, ty + 4, Theme.ACCENT);
		int kx = tx + filled;
		g.fill(kx - 2, ty - 3, kx + 3, ty + 7, Theme.TEXT_ACTIVE);
	}

	// ---- input ---------------------------------------------------------

	private void sliderTo(double mouseX) {
		if (draggingSlider == null) return;
		draggingSlider.setFraction((mouseX - trackX()) / trackW());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();

		// Panel headers start a drag (left button only)
		if (button == 0) {
			if (inside(mx, my, leftX, leftY, PANEL_W, HEADER_H)) {
				dragMode = Drag.LEFT_PANEL;
				dragOffsetX = (int) mx - leftX;
				dragOffsetY = (int) my - leftY;
				return true;
			}
			if (inside(mx, my, rightX, rightY, RIGHT_W, RIGHT_HEADER_H)) {
				dragMode = Drag.RIGHT_PANEL;
				dragOffsetX = (int) mx - rightX;
				dragOffsetY = (int) my - rightY;
				return true;
			}
		}

		// Category selection
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			if (inside(mx, my, leftX, categoryRowY(i), PANEL_W, ROW_H)) {
				selected = cats[i];
				return true;
			}
		}

		// Module rows and setting sliders
		for (Row row : buildRows()) {
			if (!inside(mx, my, rightX, row.y(), RIGHT_W, row.h())) continue;

			if (row.setting() instanceof ListSetting list) {
				if (button == 0) this.minecraft.gui.setScreen(new PickerScreen(this, list));
				return true;
			}
			if (row.setting() instanceof NumberSetting number) {
				if (button == 0) {
					dragMode = Drag.SLIDER;
					draggingSlider = number;
					sliderTo(mx);
				}
				return true;
			}

			Module m = row.module();
			boolean onDots = mx >= rightX + RIGHT_W - 30;
			if (button == 1 || onDots) {
				if (!EXPANDED.remove(m)) EXPANDED.add(m);
			} else if (button == 0) {
				m.toggle();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		switch (dragMode) {
			case LEFT_PANEL -> {
				leftX = clampX((int) event.x() - dragOffsetX, PANEL_W);
				leftY = clampY((int) event.y() - dragOffsetY, leftHeight());
				return true;
			}
			case RIGHT_PANEL -> {
				rightX = clampX((int) event.x() - dragOffsetX, RIGHT_W);
				rightY = clampY((int) event.y() - dragOffsetY, rightHeight());
				return true;
			}
			case SLIDER -> {
				sliderTo(event.x());
				return true;
			}
			default -> {
				return super.mouseDragged(event, dragX, dragY);
			}
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragMode != Drag.NONE) {
			dragMode = Drag.NONE;
			draggingSlider = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Right Shift closes the GUI too (Escape is handled by Screen)
		if (event.key() == InputConstants.KEY_RSHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
