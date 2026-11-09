package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Logins for the web page (web-accounts.yml).
 *
 * A player sets a password in game ({@link WebSite}); it is kept only as a
 * salted PBKDF2-SHA256 hash. Logging in on the page with the Minecraft name
 * and that password gives a session key the page keeps; only a SHA-256 of
 * each key is stored, so the file cannot be used to log in. A personal link
 * from /trade 웹 is a session too. Wrong passwords lock the account for a
 * few minutes after {@link #MAX_FAILS} tries, and an address that keeps
 * failing is slowed down the same way.
 *
 * Every method is synchronized: the web threads and the main thread both use it.
 */
final class WebAccounts {
	static final int MIN_PASSWORD = 6;
	static final int MAX_PASSWORD = 64;
	private static final int ITERATIONS = 120_000;
	private static final int MAX_FAILS = 5;
	private static final long LOCK_MS = 5 * 60_000L;
	private static final int MAX_FAILS_PER_ADDRESS = 20;
	private static final long ADDRESS_WINDOW_MS = 10 * 60_000L;
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final class Account {
		String name;
		String salt;
		String hash;
		int fails;
		long lockedUntil;
	}

	private record Session(UUID player, long expires) {
	}

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, Account> ACCOUNTS = new LinkedHashMap<>();
	/** Sessions by the SHA-256 of their key. */
	private static final Map<String, Session> SESSIONS = new LinkedHashMap<>();
	private static final Map<String, long[]> ADDRESS_FAILS = new LinkedHashMap<>();

	private WebAccounts() {
	}

	static synchronized void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "web-accounts.yml");
		ACCOUNTS.clear();
		SESSIONS.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
		if (accounts != null) {
			for (String key : accounts.getKeys(false)) {
				Account account = new Account();
				account.name = accounts.getString(key + ".name", "?");
				account.salt = accounts.getString(key + ".salt", "");
				account.hash = accounts.getString(key + ".hash", "");
				ACCOUNTS.put(UUID.fromString(key), account);
			}
		}
		ConfigurationSection sessions = yaml.getConfigurationSection("sessions");
		long now = System.currentTimeMillis();
		if (sessions != null) {
			for (String key : sessions.getKeys(false)) {
				long expires = sessions.getLong(key + ".expires");
				if (expires > now) {
					SESSIONS.put(key, new Session(UUID.fromString(sessions.getString(key + ".player")), expires));
				}
			}
		}
	}

	private static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.options().setHeader(List.of(
				"PlayerTrade web logins. Passwords and session keys are stored only as hashes.",
				"Delete a player's entry under 'accounts' to remove their password."));
		ACCOUNTS.forEach((id, account) -> {
			yaml.set("accounts." + id + ".name", account.name);
			yaml.set("accounts." + id + ".salt", account.salt);
			yaml.set("accounts." + id + ".hash", account.hash);
		});
		SESSIONS.forEach((key, session) -> {
			yaml.set("sessions." + key + ".player", session.player.toString());
			yaml.set("sessions." + key + ".expires", session.expires);
		});
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save web-accounts.yml", e);
		}
	}

	// --- passwords -------------------------------------------------------------------

	/** Why {@code password} cannot be used, or null. */
	static String badPassword(String name, String password) {
		if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
			return "비밀번호는 " + MIN_PASSWORD + "~" + MAX_PASSWORD + "글자로 정하세요.";
		}
		if (password.equalsIgnoreCase(name)) {
			return "닉네임과 같은 비밀번호는 쓸 수 없습니다.";
		}
		if (password.isBlank() || password.chars().distinct().count() < 3) {
			return "너무 쉬운 비밀번호입니다.";
		}
		return null;
	}

	static synchronized boolean hasPassword(UUID player) {
		return ACCOUNTS.containsKey(player);
	}

	/** Sets (or changes) the password; every existing session of the player ends. */
	static synchronized void setPassword(UUID player, String name, String password) {
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		Account account = ACCOUNTS.computeIfAbsent(player, id -> new Account());
		account.name = name;
		account.salt = HexFormat.of().formatHex(salt);
		account.hash = hash(password, salt);
		account.fails = 0;
		account.lockedUntil = 0;
		SESSIONS.values().removeIf(session -> session.player.equals(player));
		save();
	}

	/** Keeps the stored name in step with the player's current one. */
	static synchronized void renamed(UUID player, String name) {
		Account account = ACCOUNTS.get(player);
		if (account != null && !account.name.equals(name)) {
			account.name = name;
			save();
		}
	}

	static synchronized void removePassword(UUID player) {
		if (ACCOUNTS.remove(player) != null) {
			SESSIONS.values().removeIf(session -> session.player.equals(player));
			save();
		}
	}

	private static String hash(String password, byte[] salt) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
			byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			spec.clearPassword();
			return HexFormat.of().formatHex(out);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The outcome of a login: the player, or why not. */
	record Login(UUID player, String error) {
	}

	/** Checks a name and password, counting failures against the account and the address. */
	static synchronized Login login(String name, String password, String address) {
		long now = System.currentTimeMillis();
		long[] fails = ADDRESS_FAILS.get(address);
		if (fails != null && now - fails[1] > ADDRESS_WINDOW_MS) {
			ADDRESS_FAILS.remove(address);
			fails = null;
		}
		if (fails != null && fails[0] >= MAX_FAILS_PER_ADDRESS) {
			return new Login(null, "로그인 시도가 너무 많습니다. 10분 뒤에 다시 해 주세요.");
		}
		UUID player = null;
		Account account = null;
		for (Map.Entry<UUID, Account> entry : ACCOUNTS.entrySet()) {
			if (entry.getValue().name.toLowerCase(Locale.ROOT).equals(name.trim().toLowerCase(Locale.ROOT))) {
				player = entry.getKey();
				account = entry.getValue();
				break;
			}
		}
		if (account != null && now < account.lockedUntil) {
			return new Login(null, "비밀번호를 여러 번 틀려 잠겼습니다. " + ((account.lockedUntil - now) / 60_000 + 1) + "분 뒤에 다시 해 주세요.");
		}
		boolean ok;
		if (account == null) {
			// Same work as a real check, so the answer's timing does not tell whether the name exists.
			hash(password, new byte[16]);
			ok = false;
		} else {
			ok = matches(account, password);
		}
		if (!ok) {
			long[] count = ADDRESS_FAILS.computeIfAbsent(address, key -> new long[] {0, now});
			count[0]++;
			if (account != null && ++account.fails >= MAX_FAILS) {
				account.fails = 0;
				account.lockedUntil = now + LOCK_MS;
			}
			return new Login(null, "닉네임이나 비밀번호가 맞지 않습니다. (게임에서 비밀번호를 정했는지 확인하세요)");
		}
		account.fails = 0;
		return new Login(player, null);
	}

	private static boolean matches(Account account, String password) {
		return MessageDigest.isEqual(hash(password, HexFormat.of().parseHex(account.salt)).getBytes(StandardCharsets.US_ASCII),
				account.hash.getBytes(StandardCharsets.US_ASCII));
	}

	/** Whether {@code password} is the player's current one (for changing it on the page). */
	static synchronized boolean verify(UUID player, String password) {
		Account account = ACCOUNTS.get(player);
		return account != null && matches(account, password);
	}

	static synchronized String nameOf(UUID player) {
		Account account = ACCOUNTS.get(player);
		return account == null ? null : account.name;
	}

	// --- sessions --------------------------------------------------------------------

	private static String digest(String key) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A new session for {@code player}, valid for {@code millis}; returns its key (shown once). */
	static synchronized String newSession(UUID player, long millis) {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		String key = HexFormat.of().formatHex(bytes);
		long now = System.currentTimeMillis();
		SESSIONS.values().removeIf(session -> session.expires < now);
		SESSIONS.put(digest(key), new Session(player, now + millis));
		save();
		return key;
	}

	/** The player a session key belongs to, or null if it is unknown or expired. */
	static synchronized UUID session(String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		String id = digest(key.trim());
		Session session = SESSIONS.get(id);
		if (session == null) {
			return null;
		}
		if (session.expires < System.currentTimeMillis()) {
			SESSIONS.remove(id);
			save();
			return null;
		}
		return session.player;
	}

	static synchronized long expires(String key) {
		Session session = key == null ? null : SESSIONS.get(digest(key.trim()));
		return session == null ? 0 : session.expires;
	}

	static synchronized void endSession(String key) {
		if (key != null && SESSIONS.remove(digest(key.trim())) != null) {
			save();
		}
	}

	/** Logs the player out everywhere. */
	static synchronized void endAll(UUID player) {
		if (SESSIONS.values().removeIf(session -> session.player.equals(player))) {
			save();
		}
	}
}
