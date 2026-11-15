package io.github.sharket1111.indexprescript.paper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.bukkit.Bukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.sun.net.httpserver.HttpServer;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;

/**
 * Sends the Java resource pack (models and textures) on join, either from an
 * external URL or from a small built-in web server serving the pack bundled in
 * the plugin jar. Bedrock players joining through Floodgate are skipped.
 */
final class ResourcePack implements Listener {
	private static final String BUNDLED = "/index_prescript_pack.zip";
	private static final UUID PACK_ID = UUID.nameUUIDFromBytes("index_prescript:pack".getBytes());
	// Floodgate gives Bedrock players UUIDs with this prefix.
	private static final String FLOODGATE_PREFIX = "00000000-0000-0000-";

	private final IndexPrescriptPlugin plugin;
	private URI uri;
	private String sha1 = "";
	private boolean required;
	private HttpServer server;
	/** The second pack: gift and item pictures, enemy skins, players' own skins (built-in web server only). */
	private SkinPack skins;
	/** Whether players' own skins go on the rig (the second pack is sent either way, for the pictures). */
	private boolean playerSkins = true;

	ResourcePack(IndexPrescriptPlugin plugin) {
		this.plugin = plugin;
	}

	void start() {
		ConfigurationSection config = plugin.getConfig().getConfigurationSection("resource-pack");
		if (config == null || !config.getBoolean("enabled", true)) {
			return;
		}
		required = config.getBoolean("required", false);
		String url = config.getString("url", "").trim();
		boolean external = !url.isEmpty();
		if (external) {
			uri = URI.create(url);
			sha1 = config.getString("sha1", "").trim();
		}
		playerSkins = config.getBoolean("player-skins", true);

		// The built-in web server serves the second pack (gift and item pictures, enemy and player skins) and,
		// without "url", the main pack too.
		String address = config.getString("host-address", "").trim();
		int port = config.getInt("host-port", 8164);
		if (address.isEmpty()) {
			plugin.getLogger().warning(external
					? "resource-pack.host-address is empty: E.G.O gift, item and enemy pictures (the second pack) are not sent. Set it and open host-port."
					: "resource-pack.host-address is empty; set it (or resource-pack.url) so Java players get the pack.");
			return;
		}
		try (InputStream in = getClass().getResourceAsStream(BUNDLED)) {
			if (in == null) {
				plugin.getLogger().warning("Bundled resource pack missing from the plugin jar.");
				return;
			}
			byte[] pack = in.readAllBytes();
			server = HttpServer.create(new InetSocketAddress(port), 0);
			if (!external) {
				sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
				server.createContext(BUNDLED, exchange -> {
					exchange.getResponseHeaders().add("Content-Type", "application/zip");
					exchange.sendResponseHeaders(200, pack.length);
					try (OutputStream out = exchange.getResponseBody()) {
						out.write(pack);
					}
				});
			}
			server.setExecutor(Executors.newFixedThreadPool(2));
			server.start();
			if (!external) {
				uri = URI.create("http://" + address + ":" + port + BUNDLED);
				plugin.getLogger().info("Serving the resource pack at " + uri);
			}
			byte[] meta = packMeta(pack);
			if (meta != null) {
				skins = SkinPack.start(plugin, server, "http://" + address + ":" + port, meta);
				plugin.getLogger().info("Serving the picture pack (gifts, items, skins) at http://" + address + ":" + port);
			}
		} catch (IOException | NoSuchAlgorithmException e) {
			plugin.getLogger().warning("Could not host the resource pack: " + e.getMessage());
		}
	}

	/** The main pack's pack.mcmeta, reused by the skin pack. */
	private static byte[] packMeta(byte[] pack) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(pack))) {
			for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
				if (entry.getName().equals("pack.mcmeta")) {
					return zip.readAllBytes();
				}
			}
		}
		return null;
	}

	void stop() {
		SkinPack.stop();
		if (server != null) {
			server.stop(0);
		}
	}

	void send(Player player) {
		if (uri == null || player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			return;
		}
		ResourcePackInfo main = ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1);
		player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
				.packs(skins != null ? java.util.List.of(main, skins.info()) : java.util.List.of(main))
				.required(required)
				.prompt(Component.text(Messages.get(player, "resource_pack.prompt"))));
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (skins == null || uri == null || !playerSkins || player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			send(player);
			return;
		}
		// Their own skin goes into the skin pack first (fetched once per skin), so the packs they get have it.
		skins.ensure(player).completeOnTimeout(null, 5, TimeUnit.SECONDS).exceptionally(e -> null)
				.thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
					if (player.isOnline()) {
						send(player);
					}
				}));
	}
}
