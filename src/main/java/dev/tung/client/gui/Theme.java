package dev.tung.client.gui;

/** Shaded brown palette (ARGB). */
public final class Theme {
	private Theme() {}

	public static final int PANEL_TOP      = 0xFF33231A;
	public static final int PANEL_BOTTOM   = 0xFF1F140E;
	public static final int HEADER_TOP     = 0xFF2A1B13;
	public static final int HEADER_BOTTOM  = 0xFF160E09;
	public static final int ROW_ACTIVE     = 0xFF45301F;
	public static final int ROW_HOVER      = 0xFF3B2A1D;
	public static final int SECTION_BG     = 0xFF1A110B;
	public static final int SHADOW         = 0x66000000;
	public static final int BORDER         = 0xFF4A3322;

	public static final int ACCENT         = 0xFFD39A5E; // caramel
	public static final int ACCENT_DIM     = 0xFF8C6239;
	public static final int TEXT           = 0xFFC9B8A6;
	public static final int TEXT_ACTIVE    = 0xFFE8B77D;
	public static final int TEXT_MUTED     = 0xFF7D6A58;

	/** Linear blend of two ARGB colors. t in [0,1]. */
	public static int lerp(int a, int b, float t) {
		int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		int ra = Math.round(aa + (ba - aa) * t);
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}
}
