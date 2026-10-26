package dev.donutpay;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class DonutPayClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// 1) Watch chat for "<name> paid you $<amount>"
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return; // action-bar copy of the same payment, ignore to avoid double counting
			PaymentTracker.Payment p = PaymentTracker.parseChat(message.getString());
			if (p != null) PaymentTracker.add(p.name, p.amount);
		});

		// 2) Passive HUD (only once there's something to show; the chat screen draws its own interactive copy)
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("donutpaytracker", "pay_hud"),
			(graphics, deltaTracker) -> {
				if (Minecraft.getInstance().gui.screen() instanceof ChatScreen) return;
				if (PaymentTracker.entries().isEmpty()) return;
				HudRenderer.render(graphics, 0, 0, false);
			});

		// 3) Interactive while the chat box is open (T / Enter)
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) return;
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
				HudRenderer.render(graphics, mouseX, mouseY, true));
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
				!HudRenderer.click(event.x(), event.y(), event.button()));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, horiz, vert) ->
				!HudRenderer.scroll(mx, my, vert));
		});

		// 4) /donutpay clear   and   /donutpay test <amount>   (test adds a fake payment)
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
			dispatcher.register(ClientCommands.literal("donutpay")
				.then(ClientCommands.literal("clear").executes(c -> {
					PaymentTracker.clear();
					c.getSource().sendFeedback(Component.literal("Payment list cleared."));
					return 1;
				}))
				.then(ClientCommands.literal("test")
					.then(ClientCommands.argument("amount", StringArgumentType.word())
						.executes(c -> {
							long amount = PaymentTracker.parseAmount(StringArgumentType.getString(c, "amount"));
							if (amount <= 0) {
								c.getSource().sendError(Component.literal("Couldn't read that amount. Try 25.1M or 500k."));
								return 0;
							}
							PaymentTracker.add("TestPlayer", amount);
							return 1;
						})))));
	}
}
