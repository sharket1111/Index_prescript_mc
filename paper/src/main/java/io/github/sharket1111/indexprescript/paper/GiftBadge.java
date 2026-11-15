package io.github.sharket1111.indexprescript.paper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Marks a gift's picture as the original's gift icons are: its tier in Roman numerals at the bottom right
 * (I-IV, EX) and its keyword's icon at the top right (gift/keywords.png: the seven keywords from the pack's
 * status icons, then Slash, Pierce, Blunt). Done when the skin pack is built; the files in gift_icons stay as
 * they are.
 */
final class GiftBadge {
	private static final List<String> KEYWORDS = List.of("burn", "bleed", "tremor", "rupture", "sinking", "poise", "charge", "slash", "pierce", "blunt");
	/** 3x5 pixel letters for the tier. */
	private static final Map<Character, String[]> GLYPHS = Map.of(
			'I', new String[] {"###", ".#.", ".#.", ".#.", "###"},
			'V', new String[] {"#.#", "#.#", "#.#", ".#.", ".#."},
			'X', new String[] {"#.#", "#.#", ".#.", "#.#", "#.#"},
			'E', new String[] {"###", "#..", "##.", "#..", "###"});
	private static BufferedImage strip;

	private GiftBadge() {
	}

	private static BufferedImage strip() {
		if (strip == null) {
			try (InputStream in = IndexPrescriptPlugin.instance().getResource("gift/keywords.png")) {
				strip = in == null ? null : ImageIO.read(in);
			} catch (IOException e) {
				strip = null;
			}
		}
		return strip;
	}

	/** The picture with the badges, or as it was if the gift is unknown or the picture cannot be read. */
	/** id -> {tier, keyword}, read from md/gifts.json (the pack is built before the dungeon data loads). */
	private static Map<String, String[]> info;

	private static String[] info(String id) {
		if (info == null) {
			info = new java.util.HashMap<>();
			try (InputStream in = IndexPrescriptPlugin.instance().getResource("md/gifts.json")) {
				com.google.gson.JsonObject root = new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8),
						com.google.gson.JsonObject.class);
				for (com.google.gson.JsonElement e : root.getAsJsonArray("gifts")) {
					com.google.gson.JsonObject o = e.getAsJsonObject();
					info.put(o.get("id").getAsString(), new String[] {o.get("tier").getAsString(), o.get("keyword").getAsString()});
				}
			} catch (IOException | RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().warning("Gift badges: could not read md/gifts.json");
			}
		}
		return info.get(id);
	}

	/** A picture padded to a square (item textures must be square), or as it was. */
	static byte[] square(byte[] png) {
		try {
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
			if (src == null || src.getWidth() == src.getHeight()) {
				return png;
			}
			int n = Math.max(src.getWidth(), src.getHeight());
			BufferedImage out = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.drawImage(src, (n - src.getWidth()) / 2, (n - src.getHeight()) / 2, null);
			g.dispose();
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			ImageIO.write(out, "png", buf);
			return buf.toByteArray();
		} catch (IOException | RuntimeException e) {
			return png;
		}
	}

	/**
	 * The picture with the badges, or as it was if the gift is unknown or the picture cannot be read. The
	 * keyword's picture is the server's item_icons/keyword_<keyword>.png when there is one ({@code icons}),
	 * else the plugin's own.
	 */
	static byte[] decorate(String id, byte[] png, java.util.function.Function<String, byte[]> icons) {
		String[] gift = info(id);
		if (gift == null) {
			return png;
		}
		try {
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
			if (src == null) {
				return png;
			}
			int w = src.getWidth();
			int h = src.getHeight();
			BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(src, 0, 0, null);
			int unit = Math.max(1, w / 32);
			// The keyword, top right.
			int k = KEYWORDS.indexOf(gift[1]);
			byte[] own = k >= 0 && icons != null ? icons.apply("keyword_" + gift[1]) : null;
			BufferedImage picture = own == null ? null : ImageIO.read(new ByteArrayInputStream(own));
			BufferedImage strip = strip();
			if (picture != null) {
				// The original's icon: smooth scaling, a little larger.
				int size = 11 * unit;
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(picture, w - size, 0, size, size, null);
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			} else if (k >= 0 && strip != null) {
				int size = 9 * unit;
				g.drawImage(strip.getSubimage(k * 12, 0, 12, 12), w - size - unit, unit, size, size, null);
			}
			// The tier, bottom right.
			String tier = gift[0].toUpperCase(java.util.Locale.ROOT);
			if (!tier.isEmpty() && tier.chars().allMatch(c -> GLYPHS.containsKey((char) c))) {
				int px = Math.max(1, unit * 3 / 2);
				int edge = Math.max(1, px / 2);
				int width = tier.length() * 4 * px - px;
				int x = w - width - unit * 2;
				int y = h - 5 * px - unit * 2;
				// A dark outline under white letters.
				for (int pass = 0; pass < 2; pass++) {
					g.setColor(pass == 0 ? new Color(15, 12, 10, 235) : new Color(245, 232, 200));
					for (int c = 0; c < tier.length(); c++) {
						String[] glyph = GLYPHS.get(tier.charAt(c));
						for (int row = 0; row < 5; row++) {
							for (int col = 0; col < 3; col++) {
								if (glyph[row].charAt(col) != '#') {
									continue;
								}
								int gx = x + (c * 4 + col) * px;
								int gy = y + row * px;
								if (pass == 0) {
									g.fillRect(gx - edge, gy - edge, px + 2 * edge, px + 2 * edge);
								} else {
									g.fillRect(gx, gy, px, px);
								}
							}
						}
					}
				}
			}
			g.dispose();
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			ImageIO.write(out, "png", buf);
			return buf.toByteArray();
		} catch (IOException | RuntimeException e) {
			return png;
		}
	}
}
