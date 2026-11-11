package io.github.sharket1111.indexprescript.paper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.bukkit.entity.Player;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;

import net.kyori.adventure.resource.ResourcePackInfo;

/**
 * Players' own skins on the rig's body, arms and legs. The game draws a whole
 * skin only on players, so the skins are put in a second, small resource pack
 * served next to the main one: when a player joins, their skin is fetched
 * from Mojang's texture server (once per skin), cut into the rig's bones by
 * the pack's models (the main pack's rig/base models, which already follow the
 * skin layout) and offered as item model index_prescript:rig_skin with
 * custom model data [s_<skin hash>, bone]. A viewer whose copy of this pack
 * predates someone's skin sees the suit instead, until they next join.
 * Skins are kept in plugins/IndexPrescript/skins/.
 */
final class SkinPack {
	static final String MODEL = "index_prescript:rig_skin";
	private static final String PATH = "/index_prescript_skins.zip";
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("index_prescript:skins".getBytes(StandardCharsets.UTF_8));
	private static final List<String> BONES = Animations.BONES;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static SkinPack instance;

	private final IndexPrescriptPlugin plugin;
	private final File dir;
	private final byte[] packMeta;
	private final String base;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
	/** Player -> skin hash, and skin hash -> slim arms. */
	private final Map<UUID, String> players = new ConcurrentHashMap<>();
	private final Map<String, Boolean> skins = new ConcurrentHashMap<>();
	private volatile byte[] zip;
	private volatile String sha1 = "";
	/** The skins in the pack being served. */
	private volatile java.util.Set<String> served = java.util.Set.of();
	/** Enemy skins (enemy_skins/<name>.png): slug -> slim arms. */
	private final Map<String, Boolean> enemies = new ConcurrentHashMap<>();
	private final File enemyDir;

	private SkinPack(IndexPrescriptPlugin plugin, byte[] packMeta, String base) {
		this.plugin = plugin;
		this.dir = new File(plugin.getDataFolder(), "skins");
		this.enemyDir = new File(plugin.getDataFolder(), "enemy_skins");
		this.packMeta = packMeta;
		this.base = base;
	}

	/** Starts serving the skin pack on the main pack's web server ({@code base} is its http://host:port). */
	static SkinPack start(IndexPrescriptPlugin plugin, HttpServer server, String base, byte[] packMeta) {
		SkinPack pack = new SkinPack(plugin, packMeta, base);
		pack.load();
		pack.loadEnemies();
		pack.rebuild();
		server.createContext(PATH, exchange -> {
			byte[] body = pack.zip;
			exchange.getResponseHeaders().add("Content-Type", "application/zip");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		instance = pack;
		return pack;
	}

	static void stop() {
		instance = null;
	}

	/** The outfit (custom model data) of {@code player}'s own skin, if the pack being served has it. */
	static String outfit(UUID player) {
		SkinPack pack = instance;
		if (pack == null) {
			return null;
		}
		String hash = pack.players.get(player);
		return hash != null && pack.served.contains(hash) ? "s_" + hash : null;
	}

	/**
	 * The outfit of a Mirror Dungeon enemy's own skin, if an admin put one in
	 * plugins/IndexPrescript/enemy_skins/ (named after the enemy, e.g.
	 * "Kurokumo Clan Captain Jun.png"; "_slim" at the end for thin arms).
	 */
	static String enemyOutfit(String enemy) {
		SkinPack pack = instance;
		if (pack == null) {
			return null;
		}
		String slug = slug(enemy);
		return pack.served.contains("e_" + slug) ? "s_e_" + slug : null;
	}

	static String slug(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	/** Reads enemy_skins/ again and rebuilds the pack. Returns how many skins are there. */
	static int reloadEnemySkins() {
		SkinPack pack = instance;
		if (pack == null) {
			return 0;
		}
		pack.loadEnemies();
		pack.rebuild();
		return pack.enemies.size();
	}

	private void loadEnemies() {
		enemies.clear();
		enemyDir.mkdirs();
		File[] files = enemyDir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
		if (files == null) {
			return;
		}
		for (File f : files) {
			String base = f.getName().substring(0, f.getName().length() - 4);
			boolean slim = base.toLowerCase(java.util.Locale.ROOT).endsWith("_slim");
			if (slim) {
				base = base.substring(0, base.length() - 5);
			}
			try {
				byte[] png = normalise(Files.readAllBytes(f.toPath()));
				Files.write(new File(dir, "e_" + slug(base) + ".png").toPath(), png);
				enemies.put(slug(base), slim);
			} catch (IOException e) {
				plugin.getLogger().warning("enemy_skins/" + f.getName() + ": " + e.getMessage() + " (a 64x64 or 64x32 skin is needed)");
			}
		}
	}

	ResourcePackInfo info() {
		return ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(base + PATH + "?v=" + sha1), sha1);
	}

	// --- skins --------------------------------------------------------------------------

	private void load() {
		dir.mkdirs();
		File index = new File(dir, "index.json");
		if (!index.exists()) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			root.getAsJsonObject("players").entrySet().forEach(e -> players.put(UUID.fromString(e.getKey()), e.getValue().getAsString()));
			root.getAsJsonObject("skins").entrySet().forEach(e -> {
				if (new File(dir, e.getKey() + ".png").exists()) {
					skins.put(e.getKey(), e.getValue().getAsBoolean());
				}
			});
		} catch (IOException | RuntimeException e) {
			plugin.getLogger().log(Level.WARNING, "Could not read skins/index.json", e);
		}
	}

	private synchronized void save() {
		JsonObject root = new JsonObject();
		JsonObject p = new JsonObject();
		players.forEach((id, hash) -> p.addProperty(id.toString(), hash));
		JsonObject s = new JsonObject();
		skins.forEach(s::addProperty);
		root.add("players", p);
		root.add("skins", s);
		try {
			Files.writeString(new File(dir, "index.json").toPath(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not write skins/index.json", e);
		}
	}

	/**
	 * Makes sure {@code player}'s current skin is in the pack: fetches it (off the
	 * main thread) if it is new, then rebuilds the pack. Completes when done.
	 */
	CompletableFuture<Void> ensure(Player player) {
		String[] skin = skinOf(player);
		if (skin == null) {
			return CompletableFuture.completedFuture(null);
		}
		UUID id = player.getUniqueId();
		String url = skin[0];
		boolean slim = skin[1] != null;
		String hash = url.substring(url.lastIndexOf('/') + 1).replaceAll("[^0-9a-fA-F]", "").toLowerCase(java.util.Locale.ROOT);
		if (hash.isEmpty()) {
			return CompletableFuture.completedFuture(null);
		}
		hash = hash.substring(0, Math.min(24, hash.length()));
		String key = hash;
		if (skins.containsKey(key)) {
			if (!key.equals(players.put(id, key))) {
				save();
			}
			return CompletableFuture.completedFuture(null);
		}
		return CompletableFuture.runAsync(() -> {
			try {
				HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url.replace("http://", "https://")))
						.timeout(Duration.ofSeconds(6)).build(), HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() != 200) {
					throw new IOException("HTTP " + response.statusCode());
				}
				byte[] png = normalise(response.body());
				Files.write(new File(dir, key + ".png").toPath(), png);
				skins.put(key, slim);
				players.put(id, key);
				save();
				rebuild();
			} catch (IOException | RuntimeException e) {
				plugin.getLogger().warning("Could not fetch the skin of " + player.getName() + ": " + e.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	/** The skin URL and (non-null when slim) model of a player, from their profile's textures. */
	private static String[] skinOf(Player player) {
		for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
			if (!property.getName().equals("textures")) {
				continue;
			}
			try {
				JsonObject json = GSON.fromJson(new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8), JsonObject.class);
				JsonObject skin = json.getAsJsonObject("textures").getAsJsonObject("SKIN");
				if (skin == null || !skin.has("url")) {
					return null;
				}
				boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
				return new String[] {skin.get("url").getAsString(), slim ? "slim" : null};
			} catch (RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	/** A 64x64 skin: old 64x32 skins get their right arm and leg copied to the left. */
	private static byte[] normalise(byte[] png) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
			throw new IOException("not a skin");
		}
		BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(image, 0, 0, null);
		if (image.getHeight() == 32) {
			out.getGraphics().drawImage(image.getSubimage(0, 16, 16, 16), 16, 48, null);
			out.getGraphics().drawImage(image.getSubimage(40, 16, 16, 16), 32, 48, null);
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(out, "png", bytes);
		return bytes.toByteArray();
	}

	// --- the pack -----------------------------------------------------------------------

	/** Writes the pack: one texture and six bone models per skin, and the rig_skin item definition. */
	private synchronized void rebuild() {
		Map<String, Boolean> now = new LinkedHashMap<>(skins);
		enemies.forEach((slug, slim) -> now.put("e_" + slug, slim));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream out = new ZipOutputStream(bytes)) {
			put(out, "pack.mcmeta", packMeta);
			JsonArray cases = new JsonArray();
			for (Map.Entry<String, Boolean> skin : now.entrySet()) {
				String name = "s_" + skin.getKey();
				put(out, "assets/index_prescript/textures/item/rig/p/" + skin.getKey() + ".png", Files.readAllBytes(new File(dir, skin.getKey() + ".png").toPath()));
				JsonArray boneCases = new JsonArray();
				for (String bone : BONES) {
					JsonObject model = new JsonObject();
					model.addProperty("parent", "index_prescript:item/rig/base/" + bone + (skin.getValue() ? "_slim" : "_wide"));
					JsonObject textures = new JsonObject();
					textures.addProperty("skin", "index_prescript:item/rig/p/" + skin.getKey());
					model.add("textures", textures);
					put(out, "assets/index_prescript/models/item/rig/p/" + skin.getKey() + "/" + bone + ".json", GSON.toJson(model).getBytes(StandardCharsets.UTF_8));
					boneCases.add(when(bone, plain("index_prescript:item/rig/p/" + skin.getKey() + "/" + bone)));
				}
				cases.add(when(name, select(1, boneCases, plain("index_prescript:item/rig/p/" + skin.getKey() + "/body"))));
			}
			// Skins this copy of the pack does not have yet: the suit.
			JsonArray suit = new JsonArray();
			for (String bone : BONES) {
				suit.add(when(bone, plain("index_prescript:item/rig/outfit/suit/" + bone)));
			}
			JsonObject definition = new JsonObject();
			definition.add("model", select(0, cases, select(1, suit, plain("index_prescript:item/rig/outfit/suit/body"))));
			put(out, "assets/index_prescript/items/rig_skin.json", GSON.toJson(definition).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not build the skin pack", e);
			return;
		}
		byte[] built = bytes.toByteArray();
		try {
			sha1 = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(built));
		} catch (NoSuchAlgorithmException e) {
			sha1 = "";
		}
		zip = built;
		served = java.util.Set.copyOf(now.keySet());
	}

	private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0);
		out.putNextEntry(entry);
		out.write(data);
		out.closeEntry();
	}

	private static JsonObject plain(String model) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:model");
		o.addProperty("model", model);
		return o;
	}

	private static JsonObject when(String value, JsonObject model) {
		JsonObject o = new JsonObject();
		o.addProperty("when", value);
		o.add("model", model);
		return o;
	}

	private static JsonObject select(int index, JsonArray cases, JsonObject fallback) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "minecraft:select");
		o.addProperty("property", "minecraft:custom_model_data");
		o.addProperty("index", index);
		if (!cases.isEmpty()) {
			o.add("cases", cases);
		} else {
			// A select needs at least one case.
			JsonArray none = new JsonArray();
			none.add(when("-", fallback));
			o.add("cases", none);
		}
		o.add("fallback", fallback);
		return o;
	}
}
