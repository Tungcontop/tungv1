package dev.tung.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tung.client.module.Module;
import dev.tung.client.module.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the boxes and lines modules ask for, on top of everything (through walls).
 * Follows the Fabric 26.2 "rendering in the world" example: modules fill a
 * {@link RenderCollector} during extraction, and everything is drawn in one
 * batch after translucent terrain.
 *
 * Boxes are translucent fills plus thin outlines; lines (tracers and box edges)
 * are camera-facing flat strips, since this pipeline only draws quads.
 */
public final class TungRender {
	private static final int BUFFER_BYTES = 4 * 1024 * 1024;
	private static final int BYTES_PER_VERTEX = 16; // position + color
	private static final int MAX_VERTICES = BUFFER_BYTES / BYTES_PER_VERTEX - 2048;

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("tung", "pipeline/esp_through_walls"))
					.withDepthStencilState(Optional.empty())
					.build());

	private static final StagedVertexBuffer BUFFER = new StagedVertexBuffer(() -> "Tung ESP", BUFFER_BYTES);

	private record Snapshot(List<RenderCollector.Box> boxes, List<RenderCollector.Line> lines, Vec3 look) {}

	private static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), Vec3.ZERO);
	private static Snapshot snapshot = EMPTY;

	private TungRender() {}

	public static void init() {
		LevelExtractionEvents.END_EXTRACTION.register(context -> extract());
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> draw(context));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BUFFER.close());
	}

	// ---- extraction: ask every enabled module what to draw ----------------

	private static void extract() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			snapshot = EMPTY;
			return;
		}
		RenderCollector collector = new RenderCollector(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		for (Module m : ModuleManager.all()) {
			if (m.isEnabled()) m.extractRender(collector);
		}
		snapshot = new Snapshot(List.copyOf(collector.boxes), List.copyOf(collector.lines), mc.player.getLookAngle());
	}

	// ---- drawing ------------------------------------------------------------

	private static void draw(LevelRenderContext context) {
		Snapshot s = snapshot;
		if (s.boxes().isEmpty() && s.lines().isEmpty()) return;

		var cam = context.levelState().cameraRenderState.pos;
		var poseStack = context.poseStack();

		var topology = PIPELINE.getPrimitiveTopology();
		var draw = BUFFER.appendDraw(
				PIPELINE.getVertexFormatBinding(0),
				topology,
				topology == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = poseStack.last().pose();
		VertexConsumer vc = BUFFER.getVertexBuilder(draw);

		int vertices = 0;

		for (RenderCollector.Box b : s.boxes()) {
			int need = (b.fill() != 0 ? 24 : 0) + (b.outline() != 0 ? 48 : 0);
			if (vertices + need > MAX_VERTICES) break;
			vertices += need;
			if (b.fill() != 0) {
				fillBox(vc, m, (float) b.x0(), (float) b.y0(), (float) b.z0(), (float) b.x1(), (float) b.y1(), (float) b.z1(), b.fill());
			}
			if (b.outline() != 0) {
				outlineBox(vc, m, cam, b);
			}
		}

		Vec3 look = s.look();
		for (RenderCollector.Line l : s.lines()) {
			if (vertices + 4 > MAX_VERTICES) break;
			vertices += 4;
			Vec3 from = l.from() != null ? l.from()
					: new Vec3(cam.x + look.x * 0.5, cam.y + look.y * 0.5, cam.z + look.z * 0.5);
			lineQuad(vc, m, cam, from.x, from.y, from.z, l.to().x, l.to().y, l.to().z, l.color());
		}

		poseStack.popPose();

		BUFFER.upload();
		var info = BUFFER.getExecuteInfo(draw);
		if (info != null) {
			var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
					RenderSystem.getModelViewMatrixCopy(),
					new Vector4f(1f, 1f, 1f, 1f),
					new Vector3f(),
					new Matrix4f());
			var target = Minecraft.getInstance().mainRenderTarget();
			try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
					() -> "Tung ESP rendering",
					target.getColorTextureView(),
					Optional.empty(),
					target.getDepthTextureView(),
					OptionalDouble.empty())) {
				pass.setPipeline(PIPELINE);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", dynamicTransforms);
				pass.setVertexBuffer(0, info.vertexBuffer().slice());
				pass.setIndexBuffer(info.indexBuffer(), info.indexType());
				pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
			}
		}
		BUFFER.endFrame();
	}

	// ---- geometry -----------------------------------------------------------

	private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int argb) {
		vc.addVertex(m, x, y, z).setColor(argb);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, int c,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz) {
		v(vc, m, ax, ay, az, c);
		v(vc, m, bx, by, bz, c);
		v(vc, m, cx, cy, cz, c);
		v(vc, m, dx, dy, dz, c);
	}

	private static void fillBox(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		quad(vc, m, c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1); // bottom
		quad(vc, m, c, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0); // top
		quad(vc, m, c, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north
		quad(vc, m, c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1); // south
		quad(vc, m, c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0); // west
		quad(vc, m, c, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east
	}

	private static void outlineBox(VertexConsumer vc, Matrix4f m, Vec3 cam, RenderCollector.Box b) {
		double[] xs = {b.x0(), b.x1()};
		double[] ys = {b.y0(), b.y1()};
		double[] zs = {b.z0(), b.z1()};
		int c = b.outline();
		// four edges along each axis
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				lineQuad(vc, m, cam, xs[0], ys[i], zs[j], xs[1], ys[i], zs[j], c); // along X
				lineQuad(vc, m, cam, xs[i], ys[0], zs[j], xs[i], ys[1], zs[j], c); // along Y
				lineQuad(vc, m, cam, xs[i], ys[j], zs[0], xs[i], ys[j], zs[1], c); // along Z
			}
		}
	}

	/** A flat strip from a to b that always faces the camera, about 2 pixels wide on screen. */
	private static void lineQuad(VertexConsumer vc, Matrix4f m, Vec3 cam,
								 double ax, double ay, double az, double bx, double by, double bz, int argb) {
		double mx = (ax + bx) / 2 - cam.x, my = (ay + by) / 2 - cam.y, mz = (az + bz) / 2 - cam.z;
		double dx = bx - ax, dy = by - ay, dz = bz - az;
		double sx = dy * mz - dz * my;
		double sy = dz * mx - dx * mz;
		double sz = dx * my - dy * mx;
		double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (len < 1.0e-9) return;
		double dist = Math.sqrt(mx * mx + my * my + mz * mz);
		double scale = Math.max(0.004, dist * 0.0016) / len;
		sx *= scale;
		sy *= scale;
		sz *= scale;
		v(vc, m, (float) (ax - sx), (float) (ay - sy), (float) (az - sz), argb);
		v(vc, m, (float) (ax + sx), (float) (ay + sy), (float) (az + sz), argb);
		v(vc, m, (float) (bx + sx), (float) (by + sy), (float) (bz + sz), argb);
		v(vc, m, (float) (bx - sx), (float) (by - sy), (float) (bz - sz), argb);
	}

	// ---- color helpers shared by modules --------------------------------------

	public static int withAlpha(int rgb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	/** Alpha from a 0-100 percentage. */
	public static int withPercent(int rgb, int percent) {
		return withAlpha(rgb, Math.round(percent * 2.55f));
	}

	public static int hsv(float h, float s, float v) {
		float r = 0, g = 0, b = 0;
		int i = (int) Math.floor(h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (((i % 6) + 6) % 6) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
	}

	/** RGB color for an entity: red hostile, green passive, blue water, gold players, caramel other. */
	public static int entityColor(Entity e) {
		if (e instanceof Player) return 0xE8B77D;
		MobCategory c = e.getType().getCategory();
		if (c == MobCategory.MONSTER) return 0xE05A47;
		if (c == MobCategory.CREATURE || c == MobCategory.AMBIENT) return 0x7CCB5C;
		if (c == MobCategory.WATER_CREATURE || c == MobCategory.WATER_AMBIENT
				|| c == MobCategory.UNDERGROUND_WATER_CREATURE) return 0x4FA3D9;
		if (c == MobCategory.AXOLOTLS) return 0xE58FB5;
		return 0xD39A5E;
	}
}
