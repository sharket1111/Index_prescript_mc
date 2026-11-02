package io.github.sharket1111.indexprescript.paper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Puts the bundled Bedrock pack (3D items, icons and voice lines) into
 * Geyser's packs folder, and the custom item mappings into its
 * custom_mappings folder, so Bedrock players see Caduceus, Laevatein and the
 * terminal as they look on Java. Runs while plugins load, before Geyser reads
 * either folder.
 */
final class BedrockPack {
	private static final String BUNDLED = "/index_prescript_bedrock.mcpack";
	private static final String MAPPINGS = "/geyser/index_prescript_mappings.json";
	private static final List<String> GEYSER_FOLDERS = List.of("Geyser-Spigot", "Geyser-Paper", "Geyser");

	private BedrockPack() {
	}

	static void install(IndexPrescriptPlugin plugin) {
		File plugins = plugin.getDataFolder().getParentFile();
		File geyser = GEYSER_FOLDERS.stream().map(name -> new File(plugins, name)).filter(File::isDirectory).findFirst().orElse(null);
		if (geyser == null) {
			return;
		}
		copy(plugin, BUNDLED, geyser.toPath().resolve("packs").resolve("index_prescript_bedrock.mcpack"));
		copy(plugin, MAPPINGS, geyser.toPath().resolve("custom_mappings").resolve("index_prescript.json"));
	}

	/** Copies a bundled file to {@code target} unless an identical copy is already there. */
	private static void copy(IndexPrescriptPlugin plugin, String resource, Path target) {
		try (InputStream in = BedrockPack.class.getResourceAsStream(resource)) {
			if (in == null) {
				plugin.getLogger().warning("Bundled " + resource + " is missing from the plugin jar.");
				return;
			}
			byte[] data = in.readAllBytes();
			if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), data)) {
				return;
			}
			Files.createDirectories(target.getParent());
			Files.write(target, data);
			plugin.getLogger().info("Installed " + target + " for Geyser (restart the server if Geyser was already running).");
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not install " + target + " for Geyser", e);
		}
	}
}
