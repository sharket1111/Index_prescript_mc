package io.github.sharket1111.indexprescript.paper;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.ChunkGenerator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The Mirror Dungeon's own world: empty void, always night, no natural mobs,
 * inventories kept on death. Each run gets its own plot, far from the others;
 * the plugin builds a room there for each node (battle arena, event room,
 * shop, boss hall) in the Theme Pack's colours, clearing the last one first.
 */
final class MdWorld {
	static final String NAME = "index_prescript_mirror";
	private static final int PLOT = 1024;
	private static final int Y = 100;
	/** The room's half width (blocks) and wall height; the cleared box is a bit larger. */
	private static final int HALF = 31;
	private static final int CLEAR_UP = 20;
	private static World world;

	enum Room {
		BATTLE(13), BOSS(16), EVENT(8), SHOP(8), START(8);

		final int half;

		Room(int half) {
			this.half = half;
		}
	}

	private MdWorld() {
	}

	private static final class Void extends ChunkGenerator {
		@Override
		public boolean shouldGenerateNoise() {
			return false;
		}

		@Override
		public boolean shouldGenerateSurface() {
			return false;
		}

		@Override
		public boolean shouldGenerateCaves() {
			return false;
		}

		@Override
		public boolean shouldGenerateDecorations() {
			return false;
		}

		@Override
		public boolean shouldGenerateMobs() {
			return false;
		}

		@Override
		public boolean shouldGenerateStructures() {
			return false;
		}

		@Override
		public Location getFixedSpawnLocation(World w, Random random) {
			return new Location(w, 0.5, Y + 1, 0.5);
		}
	}

	static World world() {
		if (world == null) {
			world = Bukkit.getWorld(NAME);
		}
		if (world == null) {
			world = new WorldCreator(NAME).generator(new Void()).generateStructures(false).createWorld();
		}
		if (world != null) {
			world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
			world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
			world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
			world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
			world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
			world.setGameRule(GameRule.DO_WARDEN_SPAWNING, false);
			world.setGameRule(GameRule.KEEP_INVENTORY, true);
			world.setGameRule(GameRule.MOB_GRIEFING, false);
			world.setGameRule(GameRule.DO_FIRE_TICK, false);
			world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
			world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
			world.setTime(18000);
			world.setStorm(false);
		}
		return world;
	}

	static boolean is(World w) {
		return w != null && w.getName().equals(NAME);
	}

	static boolean in(Entity entity) {
		return is(entity.getWorld());
	}

	/** The centre of run {@code slot}'s plot, on the floor. */
	static Location centre(int slot) {
		return new Location(world(), slot * PLOT + 0.5, Y + 1, 0.5);
	}

	/** Where players stand when they arrive in a room: the near side, facing in. */
	static Location entrance(int slot, Room room) {
		Location at = centre(slot).add(0, 0, -(room.half - 3));
		at.setYaw(0);
		return at;
	}

	/** A random spot inside the room for an enemy (the far half). */
	static Location spawnSpot(int slot, Room room, Random random) {
		int r = room.half - 3;
		return centre(slot).add(random.nextInt(2 * r + 1) - r, 0, random.nextInt(r) + 2);
	}

	/**
	 * Clears the plot and builds {@code room} with the Theme Pack colour
	 * {@code rgb}: a dark tiled floor with a coloured inlay, low walls, lit
	 * pillars, and the room's name floating at the far end.
	 */
	static void build(int slot, Room room, int rgb, String title) {
		build(slot, room, rgb, title, null);
	}

	/**
	 * Clears the plot and builds {@code room} as the Theme Pack's stage
	 * ({@code scene}, MdScenes): its floor, props and backdrop, an invisible
	 * fence round the fighting ground, and the room's name at the far end.
	 * Without a scene, the plain tiled room.
	 */
	static void build(int slot, Room room, int rgb, String title, MdScenes.Scene scene) {
		build(slot, room, rgb, title, scene, new int[] {rgb, rgb});
	}

	static void build(int slot, Room room, int rgb, String title, MdScenes.Scene scene, int[] palette) {
		if (scene != null) {
			clear(slot);
			int h = room.half;
			MdScenes.build(slot, scene, h, palette, (long) slot * 31 + title.hashCode() + room.ordinal());
			World w = world();
			Location c = centre(slot);
			for (int x = -h - 1; x <= h + 1; x++) {
				for (int z = -h - 1; z <= h + 1; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) == h + 1) {
						for (int dy = 0; dy <= 5; dy++) {
							Block b = w.getBlockAt(c.getBlockX() + x, Y + 1 + dy, c.getBlockZ() + z);
							if (b.getType().isAir() || !b.getType().isSolid()) {
								b.setType(Material.BARRIER, false);
							}
						}
					}
				}
			}
			if (room == Room.SHOP) {
				w.getBlockAt(c.getBlockX(), Y + 1, c.getBlockZ() + 3).setType(Material.LECTERN, false);
				w.getBlockAt(c.getBlockX() - 2, Y + 1, c.getBlockZ() + 3).setType(Material.CHEST, false);
				w.getBlockAt(c.getBlockX() + 2, Y + 1, c.getBlockZ() + 3).setType(Material.SMITHING_TABLE, false);
			} else if (room == Room.EVENT) {
				w.getBlockAt(c.getBlockX(), Y + 1, c.getBlockZ() + 3).setType(Material.LECTERN, false);
			}
			label(c, h, rgb, title);
			return;
		}
		MdScenes.forget(slot);
		buildPlain(slot, room, rgb, title);
	}

	/** Empties run {@code slot}'s plot: blocks and everything but players. */
	private static void clear(int slot) {
		World w = world();
		Location c = centre(slot);
		int cx = c.getBlockX();
		int cz = c.getBlockZ();
		for (Entity e : w.getNearbyEntities(c, HALF + 4, CLEAR_UP + 4, HALF + 4)) {
			if (!(e instanceof Player)) {
				e.remove();
			}
		}
		for (int x = -HALF - 2; x <= HALF + 2; x++) {
			for (int z = -HALF - 2; z <= HALF + 2; z++) {
				for (int dy = -2; dy <= CLEAR_UP; dy++) {
					Block b = w.getBlockAt(cx + x, Y + dy, cz + z);
					if (!b.getType().isAir()) {
						b.setType(Material.AIR, false);
					}
				}
			}
		}
	}

	private static void label(Location c, int h, int rgb, String title) {
		Location at = c.clone().add(0, 5.5, h - 1.5);
		world().spawn(at, TextDisplay.class, t -> {
			t.text(Component.text(title, TextColor.color(rgb)).decorate(TextDecoration.BOLD));
			t.setBillboard(Display.Billboard.CENTER);
			t.setPersistent(false);
			t.setShadowed(true);
			t.setBackgroundColor(Color.fromARGB(160, 10, 8, 12));
			t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
					new org.joml.Vector3f(2.2F, 2.2F, 2.2F), new org.joml.Quaternionf()));
		});
	}

	private static void buildPlain(int slot, Room room, int rgb, String title) {
		World w = world();
		Location c = centre(slot);
		int cx = c.getBlockX();
		int cz = c.getBlockZ();
		int y = Y;
		for (Entity e : w.getNearbyEntities(c, HALF + 4, CLEAR_UP + 4, HALF + 4)) {
			if (!(e instanceof Player)) {
				e.remove();
			}
		}
		Material accent = concrete(rgb);
		Material glass = glass(rgb);
		for (int x = -HALF - 2; x <= HALF + 2; x++) {
			for (int z = -HALF - 2; z <= HALF + 2; z++) {
				for (int dy = -1; dy <= CLEAR_UP; dy++) {
					Block b = w.getBlockAt(cx + x, y + dy, cz + z);
					if (!b.getType().isAir()) {
						b.setType(Material.AIR, false);
					}
				}
			}
		}
		int h = room.half;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				boolean edge = Math.abs(x) == h || Math.abs(z) == h;
				boolean ring = Math.max(Math.abs(x), Math.abs(z)) == h - 3 || (x == 0 || z == 0) && Math.max(Math.abs(x), Math.abs(z)) < 3;
				Material floor = edge ? Material.POLISHED_BLACKSTONE_BRICKS
						: ring ? accent : (x + z & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
				w.getBlockAt(cx + x, y, cz + z).setType(floor, false);
				w.getBlockAt(cx + x, y - 1, cz + z).setType(Material.BLACKSTONE, false);
				if (edge) {
					for (int dy = 1; dy <= 3; dy++) {
						w.getBlockAt(cx + x, y + dy, cz + z).setType(dy == 2 ? glass : Material.POLISHED_BLACKSTONE_BRICK_WALL, false);
					}
				}
			}
		}
		// Lit pillars in the corners and halfway along the walls.
		for (int[] p : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}, {0, -h}, {0, h}, {-h, 0}, {h, 0}}) {
			for (int dy = 1; dy <= 5; dy++) {
				w.getBlockAt(cx + p[0], y + dy, cz + p[1]).setType(dy == 5 ? Material.SEA_LANTERN : Material.CHISELED_POLISHED_BLACKSTONE, false);
			}
		}
		// A light floor glow under the ring.
		for (int[] p : new int[][] {{0, 0}, {h / 2, h / 2}, {-h / 2, h / 2}, {h / 2, -h / 2}, {-h / 2, -h / 2}}) {
			w.getBlockAt(cx + p[0], y - 1, cz + p[1]).setType(Material.SHROOMLIGHT, false);
			w.getBlockAt(cx + p[0], y, cz + p[1]).setType(Material.TINTED_GLASS, false);
		}
		if (room == Room.SHOP) {
			w.getBlockAt(cx, y + 1, cz + 3).setType(Material.LECTERN, false);
			w.getBlockAt(cx - 2, y + 1, cz + 3).setType(Material.CHEST, false);
			w.getBlockAt(cx + 2, y + 1, cz + 3).setType(Material.SMITHING_TABLE, false);
		} else if (room == Room.EVENT) {
			w.getBlockAt(cx, y + 1, cz + 3).setType(Material.LECTERN, false);
			w.getBlockAt(cx, y + 2, cz + 5).setType(Material.SOUL_LANTERN, false);
		}
		Location label = c.clone().add(0, 4.5, h - 1.5);
		w.spawn(label, TextDisplay.class, t -> {
			t.text(Component.text(title, TextColor.color(rgb)).decorate(TextDecoration.BOLD));
			t.setBillboard(Display.Billboard.CENTER);
			t.setPersistent(false);
			t.setShadowed(true);
			t.setBackgroundColor(Color.fromARGB(160, 10, 8, 12));
			t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
					new org.joml.Vector3f(2.2F, 2.2F, 2.2F), new org.joml.Quaternionf()));
		});
	}

	private static final int[][] CONCRETE = {
		{0xCFD5D6, 0}, {0xE06101, 1}, {0xA9309F, 2}, {0x2389C7, 3}, {0xF1AF15, 4}, {0x5EA918, 5}, {0xD5658F, 6}, {0x373A3E, 7},
		{0x7D7D73, 8}, {0x157788, 9}, {0x64209C, 10}, {0x2D2F8F, 11}, {0x603C20, 12}, {0x495B24, 13}, {0x8E2121, 14}, {0x080A0F, 15}};
	private static final Material[] CONCRETES = {Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE,
		Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE,
		Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE, Material.BROWN_CONCRETE,
		Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE};
	private static final Material[] GLASSES = {Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
		Material.LIGHT_BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.PINK_STAINED_GLASS,
		Material.GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
		Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.RED_STAINED_GLASS,
		Material.BLACK_STAINED_GLASS};

	private static int nearest(int rgb) {
		int best = 14;
		double bestD = Double.MAX_VALUE;
		for (int[] c : CONCRETE) {
			int dr = (c[0] >> 16 & 255) - (rgb >> 16 & 255);
			int dg = (c[0] >> 8 & 255) - (rgb >> 8 & 255);
			int db = (c[0] & 255) - (rgb & 255);
			double d = dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
			if (d < bestD) {
				bestD = d;
				best = c[1];
			}
		}
		return best;
	}

	static Material concrete(int rgb) {
		return CONCRETES[nearest(rgb)];
	}

	static Material glass(int rgb) {
		return GLASSES[nearest(rgb)];
	}

	/** True if {@code at} is still on the room's floor (not fallen into the void). */
	static boolean fell(Location at) {
		return at.getY() < Y - 6;
	}
}
