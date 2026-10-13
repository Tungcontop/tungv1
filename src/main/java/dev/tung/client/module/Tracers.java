package dev.tung.client.module;

import dev.tung.client.render.RenderCollector;
import dev.tung.client.render.TungRender;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a line from your crosshair to each selected mob, through walls.
 * Starts with players only; open the Mobs picker to add any other mob.
 */
public class Tracers extends Module {
	public final ListSetting mobs = addSetting(new ListSetting("Mobs", ListSetting.Kind.ENTITY));
	public final NumberSetting range = addSetting(new NumberSetting("Range", 16, 512, 16, 128, " blocks"));

	public Tracers() {
		super("Tracers", Category.RENDER);
		mobs.selectNames("player");
	}

	@Override
	public void extractRender(RenderCollector c) {
		if (mc.level == null || mc.player == null) return;

		double rangeSq = range.get() * range.get();

		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
			if (e.distanceToSqr(mc.player) > rangeSq) continue;

			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
			if (!mobs.isSelected(id)) continue;

			Vec3 target = e.getPosition(c.partialTick).add(0, e.getBbHeight() / 2.0, 0);
			c.tracer(target, TungRender.withAlpha(TungRender.entityColor(e), 230));
		}
	}
}
