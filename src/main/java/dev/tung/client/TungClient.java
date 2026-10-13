package dev.tung.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tung.client.gui.TungScreen;
import dev.tung.client.module.ModuleManager;
import dev.tung.client.render.TungRender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class TungClient implements ClientModInitializer {
	public static final String MOD_ID = "tung";

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping openGui;

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		TungRender.init();

		openGui = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.tung.open_gui",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_RSHIFT,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openGui.consumeClick()) {
				if (client.player == null) continue;
				client.gui.setScreen(new TungScreen());
			}
			if (client.player != null) {
				ModuleManager.tick();
			}
		});
	}
}
