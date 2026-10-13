package dev.tung.client.module;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans the chunks the server has sent to you for signs of player activity:
 * amethyst geodes that have had their budding amethyst removed, and blocks that
 * rarely or never generate naturally (hoppers, pistons, redstone parts, and so on).
 *
 * Each chunk gets a score. Chunks scoring at or above the threshold (set by the
 * Sensitivity slider) are reported in chat with their coordinates.
 *
 * You can only scan chunks your client has loaded, so the real reach is capped by
 * your render distance, whatever Simulation Distance is set to.
 */
public class SusChunks extends Module {
	public final NumberSetting sensitivity = addSetting(
			new NumberSetting("Sensitivity", 1, 10, 1, 6, ""));
	public final NumberSetting distance = addSetting(
			new NumberSetting("Simulation Distance", 2, 32, 1, 12, " chunks"));

	// ---- what counts as suspicious ----------------------------------------

	/** Block -> weight. Strong = almost never natural, medium = natural in some structures. */
	private static final Map<Block, Integer> WATCH = new HashMap<>();
	private static final Map<Block, String> LABEL = new HashMap<>();

	private static void strong(Block block, String label) {
		WATCH.put(block, 2);
		LABEL.put(block, label);
	}

	private static void medium(Block block, String label) {
		WATCH.put(block, 1);
		LABEL.put(block, label);
	}

	static {
		strong(Blocks.HOPPER, "hopper");
		strong(Blocks.PISTON, "piston");
		strong(Blocks.STICKY_PISTON, "sticky piston");
		strong(Blocks.OBSERVER, "observer");
		strong(Blocks.DISPENSER, "dispenser");
		strong(Blocks.DROPPER, "dropper");
		strong(Blocks.REPEATER, "repeater");
		strong(Blocks.COMPARATOR, "comparator");
		strong(Blocks.REDSTONE_LAMP, "redstone lamp");
		strong(Blocks.TNT, "TNT");
		strong(Blocks.BEACON, "beacon");
		strong(Blocks.ENCHANTING_TABLE, "enchanting table");
		strong(Blocks.ANVIL, "anvil");
		strong(Blocks.GLASS, "glass");
		strong(Blocks.IRON_BLOCK, "iron block");
		strong(Blocks.DIAMOND_BLOCK, "diamond block");
		strong(Blocks.EMERALD_BLOCK, "emerald block");
		strong(Blocks.NETHERITE_BLOCK, "netherite block");

		medium(Blocks.CRAFTING_TABLE, "crafting table");
		medium(Blocks.FURNACE, "furnace");
		medium(Blocks.BLAST_FURNACE, "blast furnace");
		medium(Blocks.SMOKER, "smoker");
		medium(Blocks.CHEST, "chest");
		medium(Blocks.TRAPPED_CHEST, "trapped chest");
		medium(Blocks.ENDER_CHEST, "ender chest");
		medium(Blocks.TORCH, "torch");
		medium(Blocks.WALL_TORCH, "wall torch");
		medium(Blocks.LANTERN, "lantern");
		medium(Blocks.LADDER, "ladder");
		medium(Blocks.SCAFFOLDING, "scaffolding");
	}

	private static final int GEODE_MINED_SCORE = 3;

	// ---- state -------------------------------------------------------------

	private record Hit(int score, String reasons) {}

	private Level lastLevel;
	private int tickCount;
	private final Set<Long> scanned = new HashSet<>();
	private final List<long[]> queue = new ArrayList<>();
	private final Map<Long, Hit> results = new HashMap<>();
	private final Map<Long, Integer> announced = new HashMap<>();

	public SusChunks() {
		super("Sus Chunks", Category.BASE_FINDING);
	}

	@Override
	protected void onEnable() {
		reset();
		info("Sus Chunks on. Scanning loaded chunks, results will appear here.");
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		scanned.clear();
		queue.clear();
		results.clear();
		announced.clear();
		tickCount = 0;
		lastLevel = null;
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private int threshold() {
		return 12 - sensitivity.getInt();
	}

	private int effectiveRadius() {
		int renderCap = mc.options.getEffectiveRenderDistance();
		return Math.max(1, Math.min(distance.getInt(), renderCap));
	}

	// ---- tick --------------------------------------------------------------

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;

		if (mc.level != lastLevel) {
			reset();
			lastLevel = mc.level;
		}

		tickCount++;

		// Rescan everything every five minutes to catch new building
		if (tickCount % 6000 == 0) scanned.clear();

		if (queue.isEmpty() && tickCount % 20 == 0) {
			fillQueue();
		}

		// A couple of chunks per tick keeps the game smooth
		for (int i = 0; i < 2 && !queue.isEmpty(); i++) {
			long[] next = queue.remove(queue.size() - 1);
			scanChunk((int) next[0], (int) next[1]);
		}

		if (tickCount % 20 == 5) announceNew();
	}

	/** Queue unscanned loaded chunks in the radius, nearest ones processed first. */
	private void fillQueue() {
		int px = mc.player.blockPosition().getX() >> 4;
		int pz = mc.player.blockPosition().getZ() >> 4;
		int radius = effectiveRadius();
		List<long[]> found = new ArrayList<>();

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int distSq = dx * dx + dz * dz;
				if (distSq > radius * radius) continue;
				int cx = px + dx, cz = pz + dz;
				if (scanned.contains(key(cx, cz))) continue;
				found.add(new long[]{cx, cz, distSq});
			}
		}
		// queue is consumed from the end, so sort farthest first
		found.sort((a, b) -> Long.compare(b[2], a[2]));
		queue.addAll(found);
	}

	// ---- scanning ----------------------------------------------------------

	private void scanChunk(int cx, int cz) {
		ChunkAccess chunk = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) return; // not received from the server yet, try again next fill

		long k = key(cx, cz);
		scanned.add(k);

		Set<Block> present = new LinkedHashSet<>();
		boolean hasAmethyst = false;
		boolean hasBudding = false;

		for (LevelChunkSection section : chunk.getSections()) {
			if (section == null || section.hasOnlyAir()) continue;

			// Cheap palette check first, so most sections are skipped quickly
			boolean interesting = section.maybeHas(state -> {
				Block b = state.getBlock();
				return b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST || WATCH.containsKey(b);
			});
			if (!interesting) continue;

			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						Block b = section.getBlockState(x, y, z).getBlock();
						if (b == Blocks.AMETHYST_BLOCK) {
							hasAmethyst = true;
						} else if (b == Blocks.BUDDING_AMETHYST) {
							hasBudding = true;
						} else if (WATCH.containsKey(b)) {
							present.add(b);
						}
					}
				}
			}
		}

		int score = 0;
		List<String> reasons = new ArrayList<>();

		if (hasAmethyst && !hasBudding) {
			score += GEODE_MINED_SCORE;
			reasons.add("geode missing budding amethyst");
		}
		for (Block b : present) {
			score += WATCH.get(b);
			reasons.add(LABEL.get(b));
		}

		if (score > 0) {
			results.put(k, new Hit(score, String.join(", ", reasons)));
		} else {
			results.remove(k);
		}
	}

	// ---- reporting ---------------------------------------------------------

	private void announceNew() {
		int min = threshold();
		for (Map.Entry<Long, Hit> e : results.entrySet()) {
			Hit hit = e.getValue();
			if (hit.score() < min) continue;

			Integer last = announced.get(e.getKey());
			if (last != null && last >= hit.score()) continue;
			announced.put(e.getKey(), hit.score());

			long k = e.getKey();
			int cx = (int) (k >> 32);
			int cz = (int) k;
			int bx = (cx << 4) + 8;
			int bz = (cz << 4) + 8;
			info("Sus chunk at X " + bx + ", Z " + bz + " (score " + hit.score() + "): " + hit.reasons());
		}
	}
}
