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
 * Puts the bundled Bedrock pack (voice lines) into Geyser's packs folder so
 * Geyser sends it to Bedrock players. Runs while plugins load, before Geyser
 * reads its packs.
 */
final class BedrockPack {
	private static final String BUNDLED = "/index_prescript_bedrock.mcpack";
	private static final List<String> GEYSER_FOLDERS = List.of("Geyser-Spigot", "Geyser-Paper", "Geyser");

	private BedrockPack() {
	}

	static void install(IndexPrescriptPlugin plugin) {
		File plugins = plugin.getDataFolder().getParentFile();
		File geyser = GEYSER_FOLDERS.stream().map(name -> new File(plugins, name)).filter(File::isDirectory).findFirst().orElse(null);
		if (geyser == null) {
			return;
		}
		try (InputStream in = BedrockPack.class.getResourceAsStream(BUNDLED)) {
			if (in == null) {
				return;
			}
			byte[] pack = in.readAllBytes();
			Path target = geyser.toPath().resolve("packs").resolve(BUNDLED.substring(1));
			if (Files.exists(target) && Arrays.equals(Files.readAllBytes(target), pack)) {
				return;
			}
			Files.createDirectories(target.getParent());
			Files.write(target, pack);
			plugin.getLogger().info("Installed the Bedrock voice pack into " + target
					+ " (restart the server if Geyser was already running).");
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not install the Bedrock voice pack for Geyser", e);
		}
	}
}
