package dev.tung.client.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * What modules fill in during render extraction. Colors are ARGB ints.
 * A box with outline 0 is drawn without edges; fill 0 means no fill.
 */
public final class RenderCollector {
	public record Box(double x0, double y0, double z0, double x1, double y1, double z1, int fill, int outline) {}

	/** from == null means "just in front of the camera" (for tracers). */
	public record Line(Vec3 from, Vec3 to, int color) {}

	/** Fraction of the way through the current tick, for smooth entity movement. */
	public final float partialTick;

	final List<Box> boxes = new ArrayList<>();
	final List<Line> lines = new ArrayList<>();

	RenderCollector(float partialTick) {
		this.partialTick = partialTick;
	}

	public void box(double x0, double y0, double z0, double x1, double y1, double z1, int fill, int outline) {
		boxes.add(new Box(x0, y0, z0, x1, y1, z1, fill, outline));
	}

	public void tracer(Vec3 to, int color) {
		lines.add(new Line(null, to, color));
	}

	public void line(Vec3 from, Vec3 to, int color) {
		lines.add(new Line(from, to, color));
	}
}
