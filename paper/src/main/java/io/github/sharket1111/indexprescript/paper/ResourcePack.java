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
		if (!url.isEmpty()) {
			uri = URI.create(url);
			sha1 = config.getString("sha1", "").trim();
			return;
		}

		String address = config.getString("host-address", "").trim();
		int port = config.getInt("host-port", 8164);
		if (address.isEmpty()) {
			plugin.getLogger().warning("resource-pack.host-address is empty; set it (or resource-pack.url) so Java players get the pack.");
			return;
		}
		try (InputStream in = getClass().getResourceAsStream(BUNDLED)) {
			if (in == null) {
				plugin.getLogger().warning("Bundled resource pack missing from the plugin jar.");
				return;
			}
			byte[] pack = in.readAllBytes();
			sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
			server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext(BUNDLED, exchange -> {
				exchange.getResponseHeaders().add("Content-Type", "application/zip");
				exchange.sendResponseHeaders(200, pack.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(pack);
				}
			});
			server.setExecutor(Executors.newFixedThreadPool(2));
			server.start();
			uri = URI.create("http://" + address + ":" + port + BUNDLED);
			plugin.getLogger().info("Serving the resource pack at " + uri);
		} catch (IOException | NoSuchAlgorithmException e) {
			plugin.getLogger().warning("Could not host the resource pack: " + e.getMessage());
		}
	}

	void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	void send(Player player) {
		if (uri == null || player.getUniqueId().toString().startsWith(FLOODGATE_PREFIX)) {
			return;
		}
		player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
				.packs(ResourcePackInfo.resourcePackInfo(PACK_ID, uri, sha1))
				.required(required)
				.prompt(Component.text(Messages.get(player, "resource_pack.prompt"))));
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		send(event.getPlayer());
	}
}
