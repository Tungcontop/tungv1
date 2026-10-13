package dev.tung.client.module;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A "pick any number of these" setting backed by a game registry (every block, or
 * every entity type). Opens a searchable picker screen from the GUI.
 */
public final class ListSetting extends Setting {
	public enum Kind { BLOCK, ENTITY }

	public final Kind kind;
	private final Set<Identifier> selected = new HashSet<>();
	private List<Identifier> all;
	private int version;

	public ListSetting(String name, Kind kind) {
		super(name);
		this.kind = kind;
	}

	/** Every selectable id, sorted by name. Built once, on first use. */
	public List<Identifier> all() {
		if (all == null) {
			Set<Identifier> keys = (kind == Kind.BLOCK)
					? BuiltInRegistries.BLOCK.keySet()
					: BuiltInRegistries.ENTITY_TYPE.keySet();
			List<Identifier> list = new ArrayList<>();
			for (Identifier id : keys) {
				String p = id.getPath();
				if (kind == Kind.BLOCK && (p.equals("air") || p.equals("cave_air") || p.equals("void_air"))) continue;
				list.add(id);
			}
			list.sort(Comparator.comparing(Identifier::toString));
			all = list;
		}
		return all;
	}

	public boolean isSelected(Identifier id) {
		return selected.contains(id);
	}

	public void toggle(Identifier id) {
		if (!selected.remove(id)) selected.add(id);
		version++;
	}

	public void select(Collection<Identifier> ids) {
		selected.addAll(ids);
		version++;
	}

	public void deselect(Collection<Identifier> ids) {
		selected.removeAll(ids);
		version++;
	}

	public void selectAll() {
		select(all());
	}

	/** Selects the given minecraft: names (for defaults), ignoring any that do not exist. */
	public void selectNames(String... paths) {
		Set<Identifier> known = new HashSet<>(all());
		for (String p : paths) {
			Identifier id = Identifier.fromNamespaceAndPath("minecraft", p);
			if (known.contains(id)) selected.add(id);
		}
		version++;
	}

	public int count() {
		return selected.size();
	}

	/** Bumps every time the selection changes, so modules can cache derived data. */
	public int version() {
		return version;
	}

	public Set<Identifier> selection() {
		return selected;
	}
}
