package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * What the server pays moves with how much is sold to it (prices.yml), so a
 * farm cannot print money. Items that turn into each other (a diamond and a
 * diamond block, iron ore, raw iron, ingots and nuggets, wheat and bread...)
 * share one price group, found from the server's recipes that use a single
 * kind of ingredient, so crafting cannot dodge a fallen price. Each group
 * keeps how much (in full-price money) was sold to the server lately, W, and
 * pays e^(-W/drop-worth) of the table value (never under the floor). W fades
 * by half every recover-hours, so prices climb back by themselves, and
 * players buying from each other (market, buy board, auctions) takes some of
 * it back at once. Selling many at once pays the falling price along the
 * way, so splitting a sale gains nothing.
 */
final class ServerPrices {
	private static final int HISTORY = 120;
	private static final long MERGE = 60_000;
	private static final Gson GSON = new Gson();

	private static Plugin plugin;
	private static File file;
	private static boolean enabled = true;
	private static double dropWorth = 3000;
	private static double floor = 0.1;
	private static double recoverHours = 3;
	private static double marketRecover = 0.5;
	/** Per price group (its first item): {money's worth sold lately, when that was worked out}. */
	private static final Map<Material, double[]> STATE = new EnumMap<>(Material.class);
	/** Per price group: {time, worth sold lately afterwards} after each sale or purchase, oldest first. */
	private static final Map<Material, Deque<double[]>> EVENTS = new EnumMap<>(Material.class);
	/** Each item's price group (absent: a group of its own). */
	private static final Map<Material, Material> GROUP = new EnumMap<>(Material.class);
	private static final Map<Material, List<Material>> MEMBERS = new EnumMap<>(Material.class);
	/** Items an admin keeps at their full price: they never fall and selling them pushes nothing down. */
	private static final java.util.Set<Material> FIXED = java.util.EnumSet.noneOf(Material.class);
	private static boolean dirty;

	private ServerPrices() {
	}

	static synchronized void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "prices.yml");
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection settings = yaml.getConfigurationSection("settings");
		if (settings != null) {
			enabled = settings.getBoolean("enabled", true);
			dropWorth = Math.max(1, settings.getDouble("drop-worth", 3000));
			floor = Math.max(0.01, Math.min(1, settings.getDouble("floor", 0.1)));
			recoverHours = Math.max(0.05, settings.getDouble("recover-hours", 3));
			marketRecover = Math.max(0, settings.getDouble("market-recover", 0.5));
		}
		FIXED.clear();
		for (String name : yaml.getStringList("fixed-items")) {
			Material material = Material.matchMaterial(name);
			if (material != null) {
				FIXED.add(material);
			} else {
				owner.getLogger().warning("prices.yml: unknown item in fixed-items: " + name);
			}
		}
		groups();
		STATE.clear();
		EVENTS.clear();
		ConfigurationSection items = yaml.getConfigurationSection("items");
		if (items != null) {
			for (String key : items.getKeys(false)) {
				Material found = Material.matchMaterial(key);
				if (found == null) {
					continue;
				}
				Material material = group(found);
				STATE.put(material, new double[] {items.getDouble(key + ".sold"), items.getDouble(key + ".time")});
				Deque<double[]> events = new ArrayDeque<>();
				for (String point : items.getStringList(key + ".history")) {
					String[] parts = point.split(":");
					try {
						events.add(new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1])});
					} catch (RuntimeException e) {
						// Skipped.
					}
				}
				EVENTS.put(material, events);
			}
		}
		save();
		Bukkit.getScheduler().runTaskTimer(owner, () -> {
			if (dirty) {
				save();
			}
		}, 20L * 60, 20L * 60);
	}

	static synchronized void save() {
		if (file == null) {
			return;
		}
		dirty = false;
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.options().setHeader(List.of(
				"PlayerTrade: server sell prices that fall when an item is sold a lot and climb back over time.",
				"settings.enabled: false = always the full values.yml price.",
				"settings.drop-worth: selling this much (in full-price money) of one item, or of items made from each other",
				"  (diamonds and diamond blocks, ingots and nuggets...), brings their price to about a third (37%).",
				"settings.floor: the lowest a price goes, as a share of the full price (0.1 = 10%).",
				"settings.recover-hours: half of a fall comes back every this many hours.",
				"settings.market-recover: each item bought from another player (market, buy board, auction) takes back this many sold ones.",
				"fixed-items: items that always sell at their full price (also set with /trade 시세고정 or the web admin page).",
				"items: how much (in money) of each price group was sold lately (kept by the plugin)."));
		yaml.set("settings.enabled", enabled);
		yaml.set("settings.drop-worth", dropWorth);
		yaml.set("settings.floor", floor);
		yaml.set("settings.recover-hours", recoverHours);
		yaml.set("settings.market-recover", marketRecover);
		yaml.set("fixed-items", FIXED.stream().map(Material::name).sorted().toList());
		long now = System.currentTimeMillis();
		for (Map.Entry<Material, double[]> entry : STATE.entrySet()) {
			double sold = decayed(entry.getValue(), now);
			Deque<double[]> events = EVENTS.get(entry.getKey());
			boolean recent = events != null && !events.isEmpty() && now - events.getLast()[0] < 3 * 24 * 3600_000L;
			if (sold < 0.01 && !recent) {
				continue;
			}
			String path = "items." + entry.getKey().name();
			yaml.set(path + ".sold", Math.round(sold * 1000) / 1000.0);
			yaml.set(path + ".time", now);
			List<String> history = new ArrayList<>();
			if (events != null) {
				events.forEach(e -> history.add((long) e[0] + ":" + Math.round(e[1] * 1000) / 1000.0));
			}
			yaml.set(path + ".history", history);
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.WARNING, "Could not save prices.yml", e);
		}
	}

	// --- the curve -------------------------------------------------------------------------

	private static double halfLife() {
		return recoverHours * 3600_000.0;
	}

	private static double decayed(double[] state, long now) {
		return state[0] * Math.pow(0.5, Math.max(0, now - state[1]) / halfLife());
	}

	// --- price groups ---------------------------------------------------------------------------

	/** The price group of an item: the first item (by order) of the items that turn into each other. */
	static Material group(Material material) {
		Material root = GROUP.get(material);
		return root == null ? material : root;
	}

	private static Material find(Map<Material, Material> parent, Material m) {
		Material p = parent.getOrDefault(m, m);
		if (p == m) {
			return m;
		}
		Material root = find(parent, p);
		parent.put(m, root);
		return root;
	}

	/** Links items made from a single kind of ingredient (and smelted ones) with what they are made of. */
	private static void groups() {
		Map<Material, Material> parent = new EnumMap<>(Material.class);
		for (java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator(); it.hasNext();) {
			org.bukkit.inventory.Recipe recipe;
			try {
				recipe = it.next();
			} catch (RuntimeException e) {
				continue;
			}
			ItemStack result = recipe.getResult();
			Material input = single(recipe);
			if (input == null || result == null || result.isEmpty() || input == result.getType()
					|| ItemValues.value(input) == null || ItemValues.value(result.getType()) == null) {
				continue;
			}
			Material a = find(parent, input);
			Material b = find(parent, result.getType());
			if (a != b) {
				if (a.ordinal() < b.ordinal()) {
					parent.put(b, a);
				} else {
					parent.put(a, b);
				}
			}
		}
		GROUP.clear();
		MEMBERS.clear();
		for (Material m : new ArrayList<>(parent.keySet())) {
			Material root = find(parent, m);
			GROUP.put(m, root);
			GROUP.put(root, root);
		}
		GROUP.forEach((m, root) -> MEMBERS.computeIfAbsent(root, r -> new ArrayList<>()).add(m));
		if (Boolean.getBoolean("playertrade.groups")) {
			MEMBERS.values().stream().sorted((a, b) -> b.size() - a.size()).limit(12)
					.forEach(list -> plugin.getLogger().info("price group (" + list.size() + "): " + list));
		}
	}

	/** The one kind of ingredient of a recipe, or null if it takes several kinds (or a choice of them). */
	private static Material single(org.bukkit.inventory.Recipe recipe) {
		List<org.bukkit.inventory.RecipeChoice> choices = new ArrayList<>();
		if (recipe instanceof org.bukkit.inventory.ShapedRecipe shaped) {
			for (String row : shaped.getShape()) {
				for (char c : row.toCharArray()) {
					if (shaped.getChoiceMap().get(c) != null) {
						choices.add(shaped.getChoiceMap().get(c));
					}
				}
			}
		} else if (recipe instanceof org.bukkit.inventory.ShapelessRecipe shapeless) {
			choices.addAll(shapeless.getChoiceList());
		} else if (recipe instanceof org.bukkit.inventory.CookingRecipe<?> cooking) {
			choices.add(cooking.getInputChoice());
		} else if (recipe instanceof org.bukkit.inventory.StonecuttingRecipe cutting) {
			choices.add(cutting.getInputChoice());
		} else {
			return null;
		}
		Material only = null;
		for (org.bukkit.inventory.RecipeChoice choice : choices) {
			Material m;
			if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc && mc.getChoices().size() == 1) {
				m = mc.getChoices().get(0);
			} else if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice ec && ec.getChoices().size() == 1) {
				m = ec.getChoices().get(0).getType();
			} else {
				return null;
			}
			if (only != null && only != m) {
				return null;
			}
			only = m;
		}
		return only;
	}

	/** Items sharing a price with {@code material} (itself included). */
	static List<Material> members(Material material) {
		List<Material> list = MEMBERS.get(group(material));
		return list == null ? List.of(material) : list;
	}

	// --- the curve -------------------------------------------------------------------------------

	/** Money's worth sold that brings a price to about a third. */
	static double scale() {
		return dropWorth;
	}

	private static double value(Material material) {
		Double value = ItemValues.value(material);
		return value == null ? 0 : value;
	}

	private static synchronized double soldLately(Material material) {
		double[] state = STATE.get(group(material));
		return state == null ? 0 : decayed(state, System.currentTimeMillis());
	}

	/** The share of the full price paid now for one more (1 = full). */
	static double multiplier(Material material) {
		if (!enabled || fixed(material)) {
			return 1;
		}
		return Math.max(floor, Math.exp(-soldLately(material) / dropWorth));
	}

	/**
	 * The average share of the full price for selling {@code amount} of
	 * {@code material} now, after {@code alreadyWorth} of its price group was
	 * sold earlier in the same sale: the price falls along the way.
	 */
	static double average(Material material, double alreadyWorth, int amount) {
		double each = value(material);
		if (!enabled || amount <= 0 || each <= 0 || fixed(material)) {
			return 1;
		}
		double k = dropWorth;
		double a = soldLately(material) + alreadyWorth;
		double b = a + each * amount;
		double knee = k * Math.log(1 / floor);
		double total = 0;
		double end = Math.min(b, knee);
		if (a < end) {
			total += k * (Math.exp(-a / k) - Math.exp(-end / k));
		}
		total += Math.max(0, b - Math.max(a, knee)) * floor;
		return total / (b - a);
	}

	/**
	 * What {@code stack} sells for now (unit value from {@link ItemValues}).
	 * {@code pending} carries what earlier stacks of the same sale add to
	 * their price groups (pass the same map for every stack of one sale).
	 */
	static double worth(ItemStack stack, double unit, Map<Material, Double> pending) {
		if (TradeItems.isTradeDiamond(stack) || fixed(stack.getType())) {
			return unit * stack.getAmount();
		}
		Material group = group(stack.getType());
		double already = pending.getOrDefault(group, 0.0);
		pending.merge(group, value(stack.getType()) * stack.getAmount(), Double::sum);
		return unit * stack.getAmount() * average(stack.getType(), already, stack.getAmount());
	}

	/** What {@code stack} alone sells for now. */
	static double worth(ItemStack stack, double unit) {
		return worth(stack, unit, new java.util.HashMap<>());
	}

	/** {@code stack} was sold to the server. */
	static void sold(ItemStack stack) {
		if (enabled && !TradeItems.isTradeDiamond(stack) && !fixed(stack.getType())) {
			change(stack.getType(), value(stack.getType()) * stack.getAmount());
		}
	}

	/** {@code amount} of {@code type} were bought from another player: the price climbs back a little. */
	static void bought(Material type, int amount) {
		if (enabled && amount > 0 && marketRecover > 0 && ItemValues.value(type) != null) {
			change(type, -value(type) * amount * marketRecover);
		}
	}

	private static synchronized void change(Material item, double delta) {
		Material type = group(item);
		long now = System.currentTimeMillis();
		double[] state = STATE.computeIfAbsent(type, t -> new double[] {0, now});
		double sold = Math.max(0, decayed(state, now) + delta);
		if (sold == 0 && delta < 0 && state[0] == 0) {
			return;
		}
		state[0] = sold;
		state[1] = now;
		Deque<double[]> events = EVENTS.computeIfAbsent(type, t -> new ArrayDeque<>());
		if (!events.isEmpty() && now - events.getLast()[0] < MERGE) {
			events.getLast()[1] = sold;
		} else {
			events.add(new double[] {now, sold});
		}
		while (events.size() > HISTORY) {
			events.removeFirst();
		}
		dirty = true;
	}

	// --- for the web page --------------------------------------------------------------------

	/** Items not at full price now: id -> share of the full price. */
	static synchronized JsonObject drops() {
		JsonObject out = new JsonObject();
		if (!enabled) {
			return out;
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<Material, double[]> entry : STATE.entrySet()) {
			double m = Math.max(floor, Math.exp(-decayed(entry.getValue(), now) / dropWorth));
			if (m < 0.995) {
				for (Material member : members(entry.getKey())) {
					if (!fixed(member)) {
						out.addProperty(member.getKey().getKey(), Math.round(m * 1000) / 1000.0);
					}
				}
			}
		}
		return out;
	}

	/** One item's curve for the graph: its settings and what was sold lately after each sale or purchase. */
	static synchronized String history(String id) {
		Material material = Material.matchMaterial(id);
		JsonObject out = new JsonObject();
		if (material == null || ItemValues.value(material) == null) {
			out.addProperty("error", "없는 아이템입니다.");
			return GSON.toJson(out);
		}
		out.addProperty("enabled", enabled);
		out.addProperty("scale", dropWorth);
		out.addProperty("group", members(material).size());
		out.addProperty("fixed", fixed(material));
		out.addProperty("floor", floor);
		out.addProperty("halfLife", halfLife());
		out.addProperty("now", System.currentTimeMillis());
		out.addProperty("sold", soldLately(material));
		out.addProperty("multiplier", multiplier(material));
		JsonArray events = new JsonArray();
		Deque<double[]> list = EVENTS.get(group(material));
		if (list != null) {
			for (double[] e : list) {
				JsonArray point = new JsonArray();
				point.add((long) e[0]);
				point.add(Math.round(e[1] * 1000) / 1000.0);
				events.add(point);
			}
		}
		out.add("events", events);
		return GSON.toJson(out);
	}

	static synchronized boolean fixed(Material material) {
		return FIXED.contains(material);
	}

	/** Keeps an item at its full price (or lets it move again); saved at once. */
	static void setFixed(Material material, boolean fixed) {
		synchronized (ServerPrices.class) {
			if (fixed) {
				FIXED.add(material);
			} else {
				FIXED.remove(material);
			}
		}
		save();
	}

	static synchronized List<Material> fixedItems() {
		return FIXED.stream().sorted(java.util.Comparator.comparing(Material::name)).toList();
	}

	static synchronized JsonArray fixedJson() {
		JsonArray out = new JsonArray();
		FIXED.stream().map(m -> m.getKey().getKey()).sorted().forEach(out::add);
		return out;
	}

	static boolean enabled() {
		return enabled;
	}

	static double recoverHours() {
		return recoverHours;
	}
}
