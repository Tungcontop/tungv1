package dev.tung.client.module;

import dev.tung.client.render.RenderCollector;
import dev.tung.client.render.TungRender;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a box around living entities through walls. Every mob type (villagers,
 * zombies, players, ...) is selectable from the Mobs picker; all start selected.
 * Colors: red hostile, green passive, blue water, gold players.
 */
public class Esp extends Module {
	public final ListSetting mobs = addSetting(new ListSetting("Mobs", ListSetting.Kind.ENTITY));
	public final NumberSetting range = addSetting(new NumberSetting("Range", 8, 256, 8, 96, " blocks"));
	public final NumberSetting opacity = addSetting(new NumberSetting("Fill Opacity", 0, 100, 5, 25, "%"));

	public Esp() {
		super("ESP", Category.RENDER);
		mobs.selectAll();
	}

	@Override
	public void extractRender(RenderCollector c) {
		if (mc.level == null || mc.player == null) return;

		double rangeSq = range.get() * range.get();
		int fillPercent = opacity.getInt();

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
			if (e.distanceToSqr(mc.player) > rangeSq) continue;

			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
			if (!mobs.isSelected(id)) continue;

			Vec3 offset = e.getPosition(c.partialTick).subtract(e.position());
			AABB bb = e.getBoundingBox();
			int rgb = TungRender.entityColor(e);
			c.box(bb.minX + offset.x, bb.minY + offset.y, bb.minZ + offset.z,
					bb.maxX + offset.x, bb.maxY + offset.y, bb.maxZ + offset.z,
					fillPercent == 0 ? 0 : TungRender.withPercent(rgb, fillPercent),
					TungRender.withAlpha(rgb, 255));
		}
	}
}
