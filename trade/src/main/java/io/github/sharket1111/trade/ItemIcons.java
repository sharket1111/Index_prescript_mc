package io.github.sharket1111.trade;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Pictures of every item for the web page, drawn by the plugin itself the
 * way the game's inventory shows them. On first start (and after a game
 * update) it fetches the game's client files for this server's version from
 * Mojang, draws each item (flat items from their textures, blocks as small
 * 3D models from their block models) into one picture sheet, and keeps it in
 * web-icons/. The client file is deleted afterwards. If anything fails, the
 * page simply shows item names without pictures.
 */
final class ItemIcons {
	static final int CELL = 32;
	private static final int RENDER = 64;
	private static final Gson GSON = new Gson();

	private static volatile byte[] sheet;
	private static volatile String index = "{}";

	private ItemIcons() {
	}

	static byte[] sheet() {
		return sheet;
	}

	static String index() {
		return index;
	}

	// --- getting the sheet --------------------------------------------------------------

	static void start(Plugin plugin) {
		String version = Bukkit.getMinecraftVersion();
		File folder = new File(plugin.getDataFolder(), "web-icons");
		File png = new File(folder, "icons-" + version + ".png");
		File json = new File(folder, "icons-" + version + ".json");
		Thread thread = new Thread(() -> {
			try {
				if (!png.exists() || !json.exists()) {
					plugin.getLogger().info("Drawing item pictures for the web page (one time, downloads the " + version + " game files from Mojang)...");
					long started = System.currentTimeMillis();
					folder.mkdirs();
					File client = new File(folder, "client-" + version + ".jar.tmp");
					try {
						download(version, client);
						try (ZipFile zip = new ZipFile(client)) {
							Sheet built = build(zip);
							Files.write(png.toPath(), built.png());
							Files.writeString(json.toPath(), built.index(), StandardCharsets.UTF_8);
							plugin.getLogger().info("Drew " + built.count() + " item pictures in " + (System.currentTimeMillis() - started) / 1000 + "s.");
						}
					} finally {
						Files.deleteIfExists(client.toPath());
					}
					// Older versions' sheets are no longer needed.
					File[] old = folder.listFiles((dir, name) -> name.startsWith("icons-") && !name.startsWith("icons-" + version + "."));
					if (old != null) {
						for (File file : old) {
							Files.deleteIfExists(file.toPath());
						}
					}
				}
				sheet = Files.readAllBytes(png.toPath());
				index = Files.readString(json.toPath(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING, "Could not draw the item pictures; the web page shows item names instead", e);
			}
		}, "PlayerTrade-ItemIcons");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	private static void download(String version, File target) throws IOException, InterruptedException {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
		JsonObject manifest = GSON.fromJson(get(http, "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"), JsonObject.class);
		String url = null;
		for (JsonElement entry : manifest.getAsJsonArray("versions")) {
			if (entry.getAsJsonObject().get("id").getAsString().equals(version)) {
				url = entry.getAsJsonObject().get("url").getAsString();
			}
		}
		if (url == null) {
			throw new IOException("Mojang does not list version " + version);
		}
		JsonObject client = GSON.fromJson(get(http, url), JsonObject.class).getAsJsonObject("downloads").getAsJsonObject("client");
		HttpResponse<java.nio.file.Path> response = http.send(HttpRequest.newBuilder(URI.create(client.get("url").getAsString()))
				.timeout(Duration.ofMinutes(5)).build(), HttpResponse.BodyHandlers.ofFile(target.toPath()));
		if (response.statusCode() != 200) {
			throw new IOException("Client download answered " + response.statusCode());
		}
		try (InputStream in = Files.newInputStream(target.toPath())) {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			byte[] buffer = new byte[1 << 16];
			for (int n; (n = in.read(buffer)) > 0;) {
				sha1.update(buffer, 0, n);
			}
			if (!HexFormat.of().formatHex(sha1.digest()).equalsIgnoreCase(client.get("sha1").getAsString())) {
				throw new IOException("The downloaded client file is damaged");
			}
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static String get(HttpClient http, String url) throws IOException, InterruptedException {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
				HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException(url + " answered " + response.statusCode());
		}
		return response.body();
	}

	/** For trying the drawing outside a server: ItemIcons client.jar out (writes out.png and out.json). */
	public static void main(String[] args) throws IOException {
		try (ZipFile zip = new ZipFile(args[0])) {
			Sheet built = build(zip);
			Files.write(new File(args[1] + ".png").toPath(), built.png());
			Files.writeString(new File(args[1] + ".json").toPath(), built.index());
			System.out.println(built.count() + " icons, missing: " + built.missing());
		}
	}

	// --- drawing --------------------------------------------------------------------------

	record Sheet(byte[] png, String index, int count, List<String> missing) {
	}

	static Sheet build(ZipFile zip) throws IOException {
		Assets assets = new Assets(zip);
		Map<String, BufferedImage> icons = new TreeMap<>();
		List<String> missing = new ArrayList<>();
		Enumeration<? extends ZipEntry> entries = zip.entries();
		List<String> ids = new ArrayList<>();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();
			if (name.startsWith("assets/minecraft/items/") && name.endsWith(".json")) {
				ids.add(name.substring("assets/minecraft/items/".length(), name.length() - 5));
			}
		}
		for (String id : ids) {
			try {
				BufferedImage icon = assets.draw(id);
				if (icon != null && !empty(icon)) {
					icons.put(id, icon);
				} else {
					missing.add(id);
				}
			} catch (RuntimeException e) {
				if (Boolean.getBoolean("icons.debug")) {
					e.printStackTrace();
				}
				missing.add(id);
			}
		}
		int columns = 40;
		int rows = Math.max(1, (icons.size() + columns - 1) / columns);
		BufferedImage out = new BufferedImage(columns * CELL, rows * CELL, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		JsonObject ids2 = new JsonObject();
		int i = 0;
		for (Map.Entry<String, BufferedImage> entry : icons.entrySet()) {
			g.drawImage(entry.getValue(), i % columns * CELL, i / columns * CELL, null);
			ids2.addProperty(entry.getKey(), i);
			i++;
		}
		g.dispose();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(out, "png", bytes);
		JsonObject root = new JsonObject();
		root.addProperty("cell", CELL);
		root.addProperty("columns", columns);
		root.addProperty("version", Integer.toHexString(java.util.Arrays.hashCode(bytes.toByteArray())));
		root.add("ids", ids2);
		return new Sheet(bytes.toByteArray(), GSON.toJson(root), icons.size(), missing);
	}

	private static boolean empty(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) > 16) {
					return false;
				}
			}
		}
		return true;
	}

	/** One face to draw: three corners in model space (top-left, top-right, bottom-left of the texture area), and the area of the texture in pixels. */
	private record Face(double[] tl, double[] tr, double[] bl, BufferedImage texture, double u1, double v1, double u2, double v2, int rotation, int tint) {
	}

	/** A model part placed by a composite (beds): its faces and how far it is moved, in blocks. */
	private record Part(List<Face> faces, double[] offset) {
	}

	private static final class Assets {
		private final ZipFile zip;
		private final Map<String, JsonObject> json = new HashMap<>();
		private final Map<String, BufferedImage> textures = new HashMap<>();

		Assets(ZipFile zip) {
			this.zip = zip;
		}

		private JsonObject json(String path) {
			return json.computeIfAbsent(path, p -> {
				ZipEntry entry = zip.getEntry(p);
				if (entry == null) {
					return null;
				}
				try (InputStream in = zip.getInputStream(entry)) {
					return GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
				} catch (IOException | RuntimeException e) {
					return null;
				}
			});
		}

		/** A texture ("minecraft:block/stone", "block/stone"), first frame only; null if missing. */
		BufferedImage texture(String name) {
			String path = "assets/minecraft/textures/" + strip(name) + ".png";
			return textures.computeIfAbsent(path, p -> {
				ZipEntry entry = zip.getEntry(p);
				if (entry == null) {
					return null;
				}
				try (InputStream in = zip.getInputStream(entry)) {
					BufferedImage image = ImageIO.read(new ByteArrayInputStream(in.readAllBytes()));
					if (image == null) {
						return null;
					}
					// Animated textures are a strip of square frames: the first one.
					boolean strip = (p.contains("/block/") || p.contains("/item/")) && image.getHeight() > image.getWidth()
							&& image.getHeight() % image.getWidth() == 0;
					BufferedImage argb = new BufferedImage(image.getWidth(), strip ? image.getWidth() : image.getHeight(), BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = argb.createGraphics();
					g.drawImage(image, 0, 0, null);
					g.dispose();
					return argb;
				} catch (IOException e) {
					return null;
				}
			});
		}

		private static String strip(String name) {
			return name.startsWith("minecraft:") ? name.substring(10) : name;
		}

		// --- models ---

		/** A model with its parents merged: textures, elements, the GUI view, and whether it is a flat item. */
		final class Model {
			final Map<String, String> textureMap = new LinkedHashMap<>();
			JsonArray elements;
			JsonObject gui;
			boolean generated;
			boolean builtin;
		}

		Model model(String name) {
			Model model = new Model();
			List<JsonObject> chain = new ArrayList<>();
			String current = strip(name);
			for (int depth = 0; current != null && depth < 20; depth++) {
				if (current.equals("builtin/generated")) {
					model.generated = true;
					break;
				}
				if (current.equals("builtin/entity")) {
					model.builtin = true;
					break;
				}
				JsonObject json = json("assets/minecraft/models/" + current + ".json");
				if (json == null) {
					break;
				}
				chain.add(json);
				current = json.has("parent") ? strip(json.get("parent").getAsString()) : null;
			}
			for (int i = chain.size() - 1; i >= 0; i--) {
				JsonObject json = chain.get(i);
				if (json.has("textures")) {
					for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
						// A texture is a name, or an object with its name in "sprite".
						JsonElement v = e.getValue();
						String sprite = v.isJsonObject() ? v.getAsJsonObject().has("sprite") ? v.getAsJsonObject().get("sprite").getAsString() : null
								: v.getAsString();
						if (sprite != null) {
							model.textureMap.put(e.getKey(), sprite);
						}
					}
				}
			}
			for (JsonObject json : chain) {
				if (model.elements == null && json.has("elements")) {
					model.elements = json.getAsJsonArray("elements");
				}
				if (model.gui == null && json.has("display") && json.getAsJsonObject("display").has("gui")) {
					model.gui = json.getAsJsonObject("display").getAsJsonObject("gui");
				}
			}
			return model;
		}

		/** "#side" (or a texture name) to a texture, following references. */
		BufferedImage resolve(Model model, String ref) {
			String value = ref;
			for (int i = 0; i < 10 && value != null && value.startsWith("#"); i++) {
				value = model.textureMap.get(value.substring(1));
			}
			return value == null ? null : texture(value);
		}

		// --- item definitions ---

		private record Use(String model, JsonArray tints, double[] offset, JsonObject special, String base) {
		}

		private void uses(JsonObject m, List<Use> out, double[] offset) {
			if (m == null || !m.has("type")) {
				return;
			}
			double[] here = offset;
			if (m.has("transformation") && m.get("transformation").isJsonObject() && m.getAsJsonObject("transformation").has("translation")) {
				JsonArray t = m.getAsJsonObject("transformation").getAsJsonArray("translation");
				here = new double[] {offset[0] + t.get(0).getAsDouble(), offset[1] + t.get(1).getAsDouble(), offset[2] + t.get(2).getAsDouble()};
			}
			switch (m.get("type").getAsString()) {
				case "minecraft:model" -> out.add(new Use(m.get("model").getAsString(), m.has("tints") ? m.getAsJsonArray("tints") : new JsonArray(), here, null, null));
				case "minecraft:composite" -> {
					for (JsonElement child : m.getAsJsonArray("models")) {
						uses(child.getAsJsonObject(), out, here);
					}
				}
				case "minecraft:condition" -> uses(m.getAsJsonObject("on_false"), out, offset);
				case "minecraft:range_dispatch" -> {
					if (m.has("fallback")) {
						uses(m.getAsJsonObject("fallback"), out, offset);
					} else if (m.getAsJsonArray("entries").size() > 0) {
						uses(m.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("model"), out, offset);
					}
				}
				case "minecraft:select" -> {
					JsonObject chosen = null;
					if ("minecraft:display_context".equals(m.has("property") ? m.get("property").getAsString() : "")) {
						for (JsonElement c : m.getAsJsonArray("cases")) {
							JsonElement when = c.getAsJsonObject().get("when");
							if (when.toString().contains("\"gui\"")) {
								chosen = c.getAsJsonObject().getAsJsonObject("model");
							}
						}
					}
					if (chosen == null && m.has("fallback")) {
						chosen = m.getAsJsonObject("fallback");
					}
					if (chosen == null && m.getAsJsonArray("cases").size() > 0) {
						chosen = m.getAsJsonArray("cases").get(0).getAsJsonObject().getAsJsonObject("model");
					}
					uses(chosen, out, offset);
				}
				case "minecraft:special" -> out.add(new Use(null, null, here, m.getAsJsonObject("model"), m.get("base").getAsString()));
				default -> {
				}
			}
		}

		BufferedImage draw(String id) {
			JsonObject definition = json("assets/minecraft/items/" + id + ".json");
			if (definition == null) {
				return null;
			}
			List<Use> uses = new ArrayList<>();
			uses(definition.getAsJsonObject("model"), uses, new double[3]);
			if (uses.isEmpty()) {
				return null;
			}
			Use first = uses.get(0);
			if (first.special() != null) {
				return special(first.special(), model(first.base()));
			}
			Model main = model(first.model());
			if (main.generated) {
				return flat(main, first.tints());
			}
			if (main.elements == null) {
				// No shape (e.g. a builtin): its particle texture, flat.
				BufferedImage particle = resolve(main, "#particle");
				return particle == null ? null : scaleFlat(particle, -1);
			}
			List<Part> parts = new ArrayList<>();
			for (Use use : uses) {
				if (use.model() != null) {
					Model m = model(use.model());
					if (m.elements != null) {
						parts.add(new Part(faces(m, use.tints()), use.offset()));
					}
				}
			}
			return render(parts, main.gui);
		}

		// --- tints ---

		int tint(JsonArray tints, int index) {
			if (tints == null || index < 0 || index >= tints.size()) {
				return -1;
			}
			JsonObject t = tints.get(index).getAsJsonObject();
			String type = t.get("type").getAsString();
			if (t.has("value")) {
				return t.get("value").getAsInt();
			}
			if (t.has("default")) {
				return t.get("default").getAsInt();
			}
			if (type.equals("minecraft:grass")) {
				BufferedImage map = texture("colormap/grass");
				if (map == null) {
					return 0xFF91BD59;
				}
				double temp = t.get("temperature").getAsDouble();
				double rain = t.get("downfall").getAsDouble() * temp;
				int x = (int) ((1 - temp) * 255);
				int y = (int) ((1 - rain) * 255);
				return map.getRGB(Math.max(0, Math.min(255, x)), Math.max(0, Math.min(255, y)));
			}
			return -1;
		}

		BufferedImage tinted(BufferedImage image, int color) {
			if (color == -1 || image == null) {
				return image;
			}
			BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			int r = color >> 16 & 255;
			int g = color >> 8 & 255;
			int b = color & 255;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int p = image.getRGB(x, y);
					out.setRGB(x, y, p & 0xFF000000 | (p >> 16 & 255) * r / 255 << 16 | (p >> 8 & 255) * g / 255 << 8 | (p & 255) * b / 255);
				}
			}
			return out;
		}

		// --- flat items ---

		BufferedImage flat(Model model, JsonArray tints) {
			BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			for (int layer = 0; layer < 8; layer++) {
				String ref = model.textureMap.get("layer" + layer);
				if (ref == null) {
					continue;
				}
				BufferedImage texture = tinted(resolve(model, ref), tint(tints, layer));
				if (texture != null) {
					g.drawImage(texture, 0, 0, CELL, CELL, null);
				}
			}
			g.dispose();
			return out;
		}

		BufferedImage scaleFlat(BufferedImage texture, int tint) {
			BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(tinted(texture, tint), 0, 0, CELL, CELL, null);
			g.dispose();
			return out;
		}

		// --- block models ---

		List<Face> faces(Model model, JsonArray tints) {
			List<Face> out = new ArrayList<>();
			for (JsonElement e : model.elements) {
				JsonObject element = e.getAsJsonObject();
				double[] from = vec(element.getAsJsonArray("from"));
				double[] to = vec(element.getAsJsonArray("to"));
				JsonObject faces = element.getAsJsonObject("faces");
				if (faces == null) {
					continue;
				}
				JsonObject rotation = element.has("rotation") ? element.getAsJsonObject("rotation") : null;
				for (Map.Entry<String, JsonElement> f : faces.entrySet()) {
					JsonObject face = f.getValue().getAsJsonObject();
					BufferedImage texture = resolve(model, face.get("texture").getAsString());
					if (texture == null) {
						continue;
					}
					double[] uv = face.has("uv") ? vec(face.getAsJsonArray("uv")) : defaultUv(f.getKey(), from, to);
					double k = texture.getWidth() / 16.0;
					double[][] corners = corners(f.getKey(), from, to);
					if (corners == null) {
						continue;
					}
					if (rotation != null) {
						for (double[] c : corners) {
							rotate(c, rotation);
						}
					}
					int tint = face.has("tintindex") ? tint(tints, face.get("tintindex").getAsInt()) : -1;
					out.add(new Face(corners[0], corners[1], corners[2], texture, uv[0] * k, uv[1] * k, uv[2] * k, uv[3] * k,
							face.has("rotation") ? face.get("rotation").getAsInt() : 0, tint));
				}
			}
			return out;
		}

		private static double[] vec(JsonArray array) {
			double[] out = new double[array.size()];
			for (int i = 0; i < out.length; i++) {
				out[i] = array.get(i).getAsDouble();
			}
			return out;
		}

		private static double[] defaultUv(String face, double[] f, double[] t) {
			return switch (face) {
				case "down" -> new double[] {f[0], 16 - t[2], t[0], 16 - f[2]};
				case "up" -> new double[] {f[0], f[2], t[0], t[2]};
				case "north" -> new double[] {16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
				case "south" -> new double[] {f[0], 16 - t[1], t[0], 16 - f[1]};
				case "west" -> new double[] {f[2], 16 - t[1], t[2], 16 - f[1]};
				default -> new double[] {16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
			};
		}

		/** Top-left, top-right and bottom-left corners of a face, as its texture lies on it. */
		private static double[][] corners(String face, double[] f, double[] t) {
			return switch (face) {
				case "north" -> new double[][] {{t[0], t[1], f[2]}, {f[0], t[1], f[2]}, {t[0], f[1], f[2]}};
				case "south" -> new double[][] {{f[0], t[1], t[2]}, {t[0], t[1], t[2]}, {f[0], f[1], t[2]}};
				case "west" -> new double[][] {{f[0], t[1], f[2]}, {f[0], t[1], t[2]}, {f[0], f[1], f[2]}};
				case "east" -> new double[][] {{t[0], t[1], t[2]}, {t[0], t[1], f[2]}, {t[0], f[1], t[2]}};
				case "up" -> new double[][] {{f[0], t[1], f[2]}, {t[0], t[1], f[2]}, {f[0], t[1], t[2]}};
				case "down" -> new double[][] {{f[0], f[1], t[2]}, {t[0], f[1], t[2]}, {f[0], f[1], f[2]}};
				default -> null;
			};
		}

		private static void rotate(double[] p, JsonObject rotation) {
			double[] o = vec(rotation.getAsJsonArray("origin"));
			double a = Math.toRadians(rotation.get("angle").getAsDouble());
			double c = Math.cos(a);
			double s = Math.sin(a);
			double x = p[0] - o[0];
			double y = p[1] - o[1];
			double z = p[2] - o[2];
			switch (rotation.get("axis").getAsString()) {
				case "x" -> {
					p[1] = o[1] + y * c - z * s;
					p[2] = o[2] + y * s + z * c;
				}
				case "y" -> {
					p[0] = o[0] + x * c + z * s;
					p[2] = o[2] - x * s + z * c;
				}
				default -> {
					p[0] = o[0] + x * c - y * s;
					p[1] = o[1] + x * s + y * c;
				}
			}
		}

		// --- special items (drawn by the game in code): chests, shulker boxes, heads, banners, shield ---

		BufferedImage special(JsonObject spec, Model base) {
			String type = spec.get("type").getAsString();
			switch (type) {
				case "minecraft:chest" -> {
					BufferedImage t = texture("entity/chest/" + strip(spec.get("texture").getAsString()));
					if (t == null) {
						return null;
					}
					List<Face> faces = new ArrayList<>();
					box(faces, t, new double[] {1, 0, 1}, new double[] {15, 10, 15}, 0, 19, 14, 10, 14, true);
					box(faces, t, new double[] {1, 9, 1}, new double[] {15, 14, 15}, 0, 0, 14, 5, 14, true);
					box(faces, t, new double[] {7, 7, 15}, new double[] {9, 11, 16}, 0, 0, 2, 4, 1, true);
					return render(List.of(new Part(faces, new double[3])), base.gui != null ? base.gui : blockGui());
				}
				case "minecraft:shulker_box" -> {
					BufferedImage t = texture("entity/shulker/" + strip(spec.get("texture").getAsString()));
					if (t == null) {
						return null;
					}
					List<Face> faces = new ArrayList<>();
					// The lid's notched lower edge lets the base show through, so the base sits a hair inside it.
					box(faces, t, new double[] {0.05, 0, 0.05}, new double[] {15.95, 8, 15.95}, 0, 28, 16, 8, 16, false);
					box(faces, t, new double[] {0, 4, 0}, new double[] {16, 16, 16}, 0, 0, 16, 12, 16, false);
					return render(List.of(new Part(faces, new double[3])), base.gui != null ? base.gui : blockGui());
				}
				case "minecraft:head", "minecraft:player_head" -> {
					String kind = spec.has("kind") ? spec.get("kind").getAsString() : "player";
					String texture = switch (kind) {
						case "skeleton" -> "entity/skeleton/skeleton";
						case "wither_skeleton" -> "entity/skeleton/wither_skeleton";
						case "zombie" -> "entity/zombie/zombie";
						case "creeper" -> "entity/creeper/creeper";
						case "piglin" -> "entity/piglin/piglin";
						case "player" -> "entity/player/wide/steve";
						default -> null;
					};
					BufferedImage t = texture == null ? null : texture(texture);
					if (t == null) {
						return null;
					}
					List<Face> faces = new ArrayList<>();
					if (kind.equals("piglin")) {
						// A wider head, with a snout.
						box(faces, t, new double[] {3, 0, 4}, new double[] {13, 8, 12}, 0, 0, 10, 8, 8, true);
						box(faces, t, new double[] {6, 0, 12}, new double[] {10, 4, 13}, 31, 1, 4, 4, 1, true);
						return render(List.of(new Part(faces, new double[3])), base.gui);
					}
					box(faces, t, new double[] {4, 0, 4}, new double[] {12, 8, 12}, 0, 0, 8, 8, 8, true);
					if (t.getWidth() >= 64 && !texture.contains("creeper")) {
						box(faces, t, new double[] {3.75, -0.25, 3.75}, new double[] {12.25, 8.25, 12.25}, 32, 0, 8, 8, 8, true);
					}
					return render(List.of(new Part(faces, new double[3])), base.gui);
				}
				case "minecraft:banner" -> {
					BufferedImage t = texture("entity/banner/banner_base");
					if (t == null) {
						t = texture("entity/banner_base");
					}
					if (t == null) {
						return null;
					}
					BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = out.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
					int k = t.getWidth() / 64;
					BufferedImage flag = tinted(t.getSubimage(1 * k, 1 * k, 20 * k, 40 * k), dye(spec.get("color").getAsString()));
					g.drawImage(flag, 8, 2, 16, 28, null);
					g.setColor(new java.awt.Color(0x6B4F2C));
					g.fillRect(7, 1, 18, 2);
					g.dispose();
					return out;
				}
				case "minecraft:shield" -> {
					BufferedImage t = texture("entity/shield/shield_base_nopattern");
					if (t == null) {
						t = texture("entity/shield_base_nopattern");
					}
					if (t == null) {
						return null;
					}
					BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = out.createGraphics();
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
					int k = t.getWidth() / 64;
					g.drawImage(t.getSubimage(1 * k, 1 * k, 12 * k, 22 * k), 7, 2, 18, 28, null);
					g.dispose();
					return out;
				}
				default -> {
					BufferedImage particle = resolve(base, "#particle");
					return particle == null ? null : scaleFlat(particle, -1);
				}
			}
		}

		private static int dye(String color) {
			return switch (color) {
				case "white" -> 0xFFF9FFFE;
				case "orange" -> 0xFFF9801D;
				case "magenta" -> 0xFFC74EBD;
				case "light_blue" -> 0xFF3AB3DA;
				case "yellow" -> 0xFFFED83D;
				case "lime" -> 0xFF80C71F;
				case "pink" -> 0xFFF38BAA;
				case "gray" -> 0xFF474F52;
				case "light_gray" -> 0xFF9D9D97;
				case "cyan" -> 0xFF169C9C;
				case "purple" -> 0xFF8932B8;
				case "blue" -> 0xFF3C44AA;
				case "brown" -> 0xFF835432;
				case "green" -> 0xFF5E7C16;
				case "red" -> 0xFFB02E26;
				default -> 0xFF1D1D21;
			};
		}

		private static JsonObject blockGui() {
			return GSON.fromJson("{\"rotation\":[30,225,0],\"translation\":[0,0,0],\"scale\":[0.625,0.625,0.625]}", JsonObject.class);
		}

		/**
		 * An entity-model box: a cuboid from {@code from} to {@code to} (block
		 * pixels) textured from the standard box layout at (u, v) of size
		 * (w, h, d). Entity textures are drawn upside down, so {@code flip}
		 * swaps the top and the bottom.
		 */
		private static void box(List<Face> out, BufferedImage t, double[] from, double[] to, int u, int v, int w, int h, int d, boolean flip) {
			double k = t.getWidth() / 64.0;
			double[][] top = {{u + d, v}, {u + d + w, v + d}};
			double[][] bottom = {{u + d + w, v}, {u + d + 2 * w, v + d}};
			double[][] front = {{u + d, v + d}, {u + d + w, v + d + h}};
			double[][] right = {{u, v + d}, {u + d, v + d + h}};
			double[][] left = {{u + d + w, v + d}, {u + 2 * d + w, v + d + h}};
			double[][] back = {{u + 2 * d + w, v + d}, {u + 2 * d + 2 * w, v + d + h}};
			add(out, t, "up", from, to, flip ? bottom : top, k, flip);
			add(out, t, "down", from, to, flip ? top : bottom, k, flip);
			// Turned upside down, the entity's front (north) ends up facing south.
			add(out, t, "north", from, to, flip ? back : front, k, flip);
			add(out, t, "south", from, to, flip ? front : back, k, flip);
			add(out, t, "east", from, to, right, k, flip);
			add(out, t, "west", from, to, left, k, flip);
		}

		private static void add(List<Face> out, BufferedImage t, String face, double[] from, double[] to, double[][] area, double k, boolean flip) {
			double[][] c = corners(face, from, to);
			double u1 = area[0][0] * k;
			double v1 = area[0][1] * k;
			double u2 = area[1][0] * k;
			double v2 = area[1][1] * k;
			if (flip && !face.equals("up") && !face.equals("down")) {
				// Upside down on the entity texture.
				double swap = v1;
				v1 = v2;
				v2 = swap;
				swap = u1;
				u1 = u2;
				u2 = swap;
			}
			out.add(new Face(c[0], c[1], c[2], t, u1, v1, u2, v2, 0, -1));
		}

		// --- the 3D drawing ---

		private record Drawn(double depth, Path2D shape, AffineTransform transform, BufferedImage texture) {
		}

		BufferedImage render(List<Part> parts, JsonObject gui) {
			JsonObject view = gui != null ? gui : blockGui();
			double[] rot = view.has("rotation") ? vec(view.getAsJsonArray("rotation")) : new double[3];
			double[] tr = view.has("translation") ? vec(view.getAsJsonArray("translation")) : new double[3];
			double[] sc = view.has("scale") ? vec(view.getAsJsonArray("scale")) : new double[] {1, 1, 1};
			double[][] m = multiply(multiply(rotX(rot[0]), rotY(rot[1])), rotZ(rot[2]));
			double s = RENDER / 16.0;
			List<Drawn> drawn = new ArrayList<>();
			for (Part part : parts) {
				for (Face face : part.faces()) {
					double[][] p = new double[3][];
					double[][] world = {face.tl(), face.tr(), face.bl()};
					for (int i = 0; i < 3; i++) {
						double[] v = {(world[i][0] / 16 + part.offset()[0] - 0.5) * sc[0], (world[i][1] / 16 + part.offset()[1] - 0.5) * sc[1],
								(world[i][2] / 16 + part.offset()[2] - 0.5) * sc[2]};
						double[] r = apply(m, v);
						p[i] = new double[] {(8 + (r[0] + tr[0] / 16) * 16) * s, (8 - (r[1] + tr[1] / 16) * 16) * s, r[2]};
					}
					// The fourth corner, and which way the face looks.
					double[] br = {p[1][0] + p[2][0] - p[0][0], p[1][1] + p[2][1] - p[0][1], p[1][2] + p[2][2] - p[0][2]};
					double ax = p[1][0] - p[0][0];
					double ay = p[1][1] - p[0][1];
					double bx = p[2][0] - p[0][0];
					double by = p[2][1] - p[0][1];
					double cross = ax * by - ay * bx;
					if (Math.abs(cross) < 1e-6) {
						continue;
					}
					// Screen y points down: a face seen from the front has its corners clockwise.
					if (cross < 0) {
						continue;
					}
					double[] n = normal(world, m, sc);
					double light = n[1] > 0.5 ? 1.0 : n[0] < 0 ? 0.8 : 0.62;
					if (n[1] < -0.5) {
						light = 0.5;
					}
					AffineTransform transform = mapping(face, p);
					if (transform == null) {
						continue;
					}
					Path2D shape = new Path2D.Double();
					shape.moveTo(p[0][0], p[0][1]);
					shape.lineTo(p[1][0], p[1][1]);
					shape.lineTo(br[0], br[1]);
					shape.lineTo(p[2][0], p[2][1]);
					shape.closePath();
					drawn.add(new Drawn((p[0][2] + p[1][2] + p[2][2] + br[2]) / 4, shape, transform, shade(tinted(face.texture(), face.tint()), light)));
				}
			}
			drawn.sort((a, b) -> Double.compare(a.depth(), b.depth()));
			BufferedImage big = new BufferedImage(RENDER, RENDER, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = big.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			for (Drawn d : drawn) {
				g.setClip(d.shape());
				g.drawImage(d.texture(), d.transform(), null);
			}
			g.dispose();
			BufferedImage out = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
			Graphics2D small = out.createGraphics();
			small.setComposite(AlphaComposite.Src);
			small.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			small.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			small.drawImage(big, 0, 0, CELL, CELL, null);
			small.dispose();
			return out;
		}

		private final Map<BufferedImage, Map<Double, BufferedImage>> shaded = new HashMap<>();

		private BufferedImage shade(BufferedImage image, double light) {
			if (light >= 0.999) {
				return image;
			}
			return shaded.computeIfAbsent(image, i -> new HashMap<>()).computeIfAbsent(light, l -> {
				int c = (int) Math.round(l * 255);
				return tinted(image, 0xFF000000 | c << 16 | c << 8 | c);
			});
		}

		/** The texture-to-screen mapping of a face: its texture area's corners onto the face's corners. */
		private static AffineTransform mapping(Face face, double[][] p) {
			double[][] uv = {{face.u1(), face.v1()}, {face.u2(), face.v1()}, {face.u2(), face.v2()}, {face.u1(), face.v2()}};
			int turn = (face.rotation() / 90) & 3;
			double[] a = uv[(0 + turn) & 3];
			double[] b = uv[(1 + turn) & 3];
			double[] c = uv[(3 + turn) & 3];
			AffineTransform texture = new AffineTransform(b[0] - a[0], b[1] - a[1], c[0] - a[0], c[1] - a[1], a[0], a[1]);
			AffineTransform screen = new AffineTransform(p[1][0] - p[0][0], p[1][1] - p[0][1], p[2][0] - p[0][0], p[2][1] - p[0][1], p[0][0], p[0][1]);
			try {
				screen.concatenate(texture.createInverse());
				return screen;
			} catch (NoninvertibleTransformException e) {
				return null;
			}
		}

		private static double[] normal(double[][] world, double[][] m, double[] sc) {
			double[] a = {world[1][0] - world[0][0], world[1][1] - world[0][1], world[1][2] - world[0][2]};
			double[] b = {world[2][0] - world[0][0], world[2][1] - world[0][1], world[2][2] - world[0][2]};
			double[] n = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
			n = apply(m, new double[] {n[0] * Math.signum(sc[0]), n[1] * Math.signum(sc[1]), n[2] * Math.signum(sc[2])});
			double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
			return len == 0 ? n : new double[] {-n[0] / len, -n[1] / len, -n[2] / len};
		}

		private static double[][] rotX(double deg) {
			double a = Math.toRadians(deg);
			return new double[][] {{1, 0, 0}, {0, Math.cos(a), -Math.sin(a)}, {0, Math.sin(a), Math.cos(a)}};
		}

		private static double[][] rotY(double deg) {
			double a = Math.toRadians(deg);
			return new double[][] {{Math.cos(a), 0, Math.sin(a)}, {0, 1, 0}, {-Math.sin(a), 0, Math.cos(a)}};
		}

		private static double[][] rotZ(double deg) {
			double a = Math.toRadians(deg);
			return new double[][] {{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}};
		}

		private static double[][] multiply(double[][] a, double[][] b) {
			double[][] out = new double[3][3];
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 3; j++) {
					out[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
				}
			}
			return out;
		}

		private static double[] apply(double[][] m, double[] v) {
			return new double[] {m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2], m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
					m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
		}
	}

}
