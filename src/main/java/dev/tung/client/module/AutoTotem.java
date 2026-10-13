package dev.tung.client.module;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/**
 * Keeps a Totem of Undying in your offhand. When the offhand is empty (or holds
 * something else) it swaps a totem in from your inventory or hotbar.
 */
public class AutoTotem extends Module {
	public final NumberSetting delay = addSetting(new NumberSetting("Delay", 0, 10, 1, 1, " ticks"));

	private int cooldown;

	public AutoTotem() {
		super("AutoTotem", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		cooldown = 0;
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) return;

		// Only act in the world or the player inventory; other containers use a different menu
		var screen = mc.gui.screen();
		if (screen != null && !(screen instanceof InventoryScreen)) return;

		if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			cooldown = delay.getInt();
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		int menuSlot = findTotemMenuSlot(player);
		if (menuSlot < 0) return;

		// Button 40 on a SWAP click means "swap with the offhand"
		mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, menuSlot, 40, ClickType.SWAP, player);
		cooldown = delay.getInt() + 1;
	}

	/** Slot number in the player inventory menu: 9-35 main inventory, 36-44 hotbar. */
	private int findTotemMenuSlot(LocalPlayer player) {
		for (int i = 9; i < 36; i++) {
			if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
		}
		for (int i = 0; i < 9; i++) {
			if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return 36 + i;
		}
		return -1;
	}
}
