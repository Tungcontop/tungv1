package dev.tung.client.module;

/** Sidebar categories, in display order. */
public enum Category {
	COMBAT("Combat"),
	RENDER("Render"),
	UTILITY("Utility"),
	WORLD("World"),
	BASE_FINDING("Base Finding"),
	INVENTORY("Inventory"),
	MINIGAMES("Minigames"),
	OTHER("Other");

	public final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}
}
