package dev.tung.client.module;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {}

	public static void init() {
		register(new AutoTotem());
		register(new Esp());
		register(new Tracers());
		register(new BlockEsp());
		register(new SusChunks());
	}

	public static void register(Module module) {
		MODULES.add(module);
	}

	public static List<Module> all() {
		return MODULES;
	}

	public static List<Module> in(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module m : MODULES) {
			if (m.category == category) result.add(m);
		}
		return result;
	}

	public static void tick() {
		for (Module m : MODULES) {
			if (m.isEnabled()) m.onTick();
		}
	}
}
