package dev.tung.client.module;

import dev.tung.client.render.RenderCollector;
import dev.tung.client.render.TungRender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Highlights chosen block types through walls. Every block in the game is
 * selectable from the Blocks picker (search by name). Each block type gets its own
 * color. Only chunks your client has loaded can be scanned.
 */
public class BlockEsp extends Module {
	public final ListSetting blocks = addSetting(new ListSetting("Blocks", ListSetting.Kind.BLOCK));
	public final NumberSetting range = addSetting(new NumberSetting("Range", 16, 128, 16, 64, " blocks"));
	public final NumberSetting maxBoxes = addSetting(new NumberSetting("Max Boxes", 50, 1000, 50, 300, ""));
	public final NumberSetting opacity = addSetting(new NumberSetting("Fill Opacity", 0, 100, 5, 20, "%"));

	private static final int PER_CHUNK_CAP = 4096;
	private static final int CHUNKS_PER_TICK = 4;

	private record Shown(long pos, int rgb) {}
	private record Candidate(long pos, double distSq) {}

	private Level lastLevel;
	private int tickCount;
	private int watchVersion = -1;
	private Set<Block> watch = Collections.emptySet();
	private final Map<Block, Integer> colors = new IdentityHashMap<>();
	private final Map<Long, long[]> found = new HashMap<>();
	private final ArrayDeque<long[]> queue = new ArrayDeque<>();
	private List<Shown> shown = List.of();

	public BlockEsp() {
		super("Block ESP", Category.RENDER);
		blocks.selectNames("spawner", "chest", "ender_chest", "ancient_debris", "diamond_ore", "deepslate_diamond_ore");
	}

	@Override
	protected void onEnable() {
		reset();
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		found.clear();
		queue.clear();
		shown = List.of();
		watchVersion = -1;
		tickCount = 0;
		lastLevel = null;
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	private void rebuildWatch() {
		Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<>());
		colors.clear();
		for (Identifier id : blocks.selection()) {
			Block b = BuiltInRegistries.BLOCK.getValue(id);
			if (b == null) continue;
			set.add(b);
			float hue = (id.toString().hashCode() & 0x7FFFFFFF) % 360 / 360f;
			colors.put(b, TungRender.hsv(hue, 0.65f, 1f));
		}
		watch = set;
		watchVersion = blocks.version();
		found.clear();
		queue.clear();
		shown = List.of();
	}

	// ---- scanning (client tick) ---------------------------------------------

	@Override
	public void onTick() {
		if (mc.level == null || mc.player == null) return;

		if (mc.level != lastLevel) {
			reset();
			lastLevel = mc.level;
		}
		if (watchVersion != blocks.version()) rebuildWatch();
		if (watch.isEmpty()) return;

		tickCount++;

		if (queue.isEmpty()) fillQueue();
		for (int i = 0; i < CHUNKS_PER_TICK && !queue.isEmpty(); i++) {
			long[] next = queue.poll();
			scanChunk((int) next[0], (int) next[1]);
		}

		if (tickCount % 10 == 0) updateShown();
	}

	private int chunkRadius() {
		return (int) Math.ceil(range.get() / 16.0);
	}

	/** Queue every chunk in range, nearest first, and forget chunks that fell out of range. */
	private void fillQueue() {
		int px = mc.player.blockPosition().getX() >> 4;
		int pz = mc.player.blockPosition().getZ() >> 4;
		int r = chunkRadius();

		found.keySet().removeIf(k -> {
			int cx = (int) (k >> 32), cz = (int) (long) k;
			return Math.abs(cx - px) > r + 1 || Math.abs(cz - pz) > r + 1;
		});

		List<long[]> list = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				list.add(new long[]{px + dx, pz + dz, (long) dx * dx + (long) dz * dz});
			}
		}
		list.sort(Comparator.comparingLong(a -> a[2]));
		queue.addAll(list);
	}

	private void scanChunk(int cx, int cz) {
		ChunkAccess chunk = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) return;

		List<Long> hits = new ArrayList<>();
		int minY = chunk.getMinY();
		LevelChunkSection[] sections = chunk.getSections();

		for (int i = 0; i < sections.length && hits.size() < PER_CHUNK_CAP; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir()) continue;
			if (!section.maybeHas(state -> watch.contains(state.getBlock()))) continue;

			int baseY = minY + i * 16;
			for (int x = 0; x < 16; x++) {
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						BlockState state = section.getBlockState(x, y, z);
						if (watch.contains(state.getBlock())) {
							hits.add(BlockPos.asLong((cx << 4) + x, baseY + y, (cz << 4) + z));
							if (hits.size() >= PER_CHUNK_CAP) break;
						}
					}
				}
			}
		}

		long k = key(cx, cz);
		if (hits.isEmpty()) {
			found.remove(k);
		} else {
			found.put(k, hits.stream().mapToLong(Long::longValue).toArray());
		}
	}

	/** Pick the nearest blocks (up to Max Boxes) that are still the right block. */
	private void updateShown() {
		double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
		double rangeSq = range.get() * range.get();
		int max = maxBoxes.getInt();

		PriorityQueue<Candidate> heap = new PriorityQueue<>((a, b) -> Double.compare(b.distSq(), a.distSq()));
		for (long[] arr : found.values()) {
			for (long pos : arr) {
				double dx = BlockPos.getX(pos) + 0.5 - px;
				double dy = BlockPos.getY(pos) + 0.5 - py;
				double dz = BlockPos.getZ(pos) + 0.5 - pz;
				double d = dx * dx + dy * dy + dz * dz;
				if (d > rangeSq) continue;
				heap.add(new Candidate(pos, d));
				if (heap.size() > max) heap.poll();
			}
		}

		List<Shown> out = new ArrayList<>(heap.size());
		for (Candidate c : heap) {
			Block b = mc.level.getBlockState(BlockPos.of(c.pos())).getBlock();
			Integer rgb = watch.contains(b) ? colors.get(b) : null;
			if (rgb != null) out.add(new Shown(c.pos(), rgb));
		}
		shown = out;
	}

	// ---- drawing (every frame) ------------------------------------------------

	@Override
	public void extractRender(RenderCollector c) {
		int fillPercent = opacity.getInt();
		double e = 0.002; // tiny inset so boxes sit just outside the block
		for (Shown s : shown) {
			double x = BlockPos.getX(s.pos()), y = BlockPos.getY(s.pos()), z = BlockPos.getZ(s.pos());
			c.box(x - e, y - e, z - e, x + 1 + e, y + 1 + e, z + 1 + e,
					fillPercent == 0 ? 0 : TungRender.withPercent(s.rgb(), fillPercent),
					TungRender.withAlpha(s.rgb(), 255));
		}
	}
}
