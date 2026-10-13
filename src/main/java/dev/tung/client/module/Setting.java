package dev.tung.client.module;

/** Anything shown under a module in the GUI (slider, picker, ...). */
public abstract class Setting {
	public final String name;

	protected Setting(String name) {
		this.name = name;
	}
}
