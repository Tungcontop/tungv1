package dev.tung.client.module;

import dev.tung.client.render.RenderCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every Tung module. Subclass it, then register an instance
 * in {@link ModuleManager#init()}.
 */
public abstract class Module {
	protected static final Minecraft mc = Minecraft.getInstance();

	public final String name;
	public final Category category;
	private final List<Setting> settings = new ArrayList<>();
	private boolean enabled;

	protected Module(String name, Category category) {
		this.name = name;
		this.category = category;
	}

	protected <T extends Setting> T addSetting(T setting) {
		settings.add(setting);
		return setting;
	}

	public List<Setting> settings() {
		return settings;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		if (value == enabled) return;
		enabled = value;
		if (value) onEnable(); else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {}

	protected void onDisable() {}

	/** Called every client tick while the module is enabled and a world is loaded. */
	public void onTick() {}

	/** Called every frame while enabled; queue boxes and lines to draw through walls. */
	public void extractRender(RenderCollector collector) {}

	/** Prints a line to the local chat, prefixed with the client name. */
	protected void info(String message) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Tung] " + message));
		}
	}
}
