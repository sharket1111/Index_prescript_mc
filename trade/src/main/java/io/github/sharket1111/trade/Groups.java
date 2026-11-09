package io.github.sharket1111.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Groups of players, saved to groups.yml. A player may be in several groups
 * (up to {@link #MAX_GROUPS}). A group costs {@link #PRICE} (money) to
 * found, has a name and an optional tag; each player picks which of their
 * groups' tags is shown before their name ([TAG]Name in chat, the player list
 * and over their head), or none. Groups have roles in rank order (changeable):
 * the leader, a deputy and members by default, plus any the group adds. Each
 * role has its own permissions, from running the group (inviting, kicking,
 * giving roles...) to its perks: no fighting between group members, and the
 * group's discount on other members' goods.
 */
final class Groups {
	static final int PRICE = 500;
	static final int MAX_MEMBERS = 36;
	static final int MAX_ROLES = 8;
	static final int MAX_DISCOUNT = 50;
	static final int MAX_INVITES = 18;
	static final int MAX_GROUPS = 9;
	/** What each new waypoint costs: the first, second... up to the last allowed. */
	static final int[] WAYPOINT_PRICES = {1000, 1500, 2500, 3500, 5000};
	static final int TELEPORT_PRICE = 50;
	private static final String NO_TAG = "none";
	static final String LEADER = "leader";
	static final String DEPUTY = "deputy";
	static final String MEMBER = "member";
	private static final String TEAM_PREFIX = "ptg_";

	enum Perm {
		INVITE("초대", "플레이어를 그룹에 초대합니다."),
		KICK("추방", "자기보다 낮은 역할의 그룹원을 내보냅니다."),
		ASSIGN_ROLES("역할 지정", "자기보다 낮은 역할의 그룹원에게 역할을 줍니다."),
		EDIT_ROLES("역할 편집", "자기보다 낮은 역할을 만들고, 이름과 권한을 바꾸고, 지웁니다."),
		RENAME("이름·접두사", "그룹 이름과 이름 앞 접두사를 바꿉니다."),
		SETTINGS("그룹 설정", "그룹 할인율을 정합니다."),
		NO_PVP("그룹원 공격 차단", "이 역할은 그룹원과 서로 공격할 수 없습니다."),
		DISCOUNT("그룹 할인", "그룹원의 물품을 그룹 할인율만큼 싸게 삽니다."),
		WAYPOINT_USE("웨이포인트 사용", "그룹 웨이포인트로 텔레포트합니다. (비용을 냅니다)"),
		WAYPOINT_VIEW("웨이포인트 좌표 보기", "그룹 웨이포인트의 좌표를 봅니다. (없으면 비공개)"),
		WAYPOINT_MANAGE("웨이포인트 관리", "웨이포인트를 사서 만들고, 이름/위치를 바꾸고, 지웁니다.");

		final String label;
		final String help;

		Perm(String label, String help) {
			this.label = label;
			this.help = help;
		}

		/** Perks rather than powers: these the leader's role can turn off too. */
		boolean perk() {
			return this == NO_PVP || this == DISCOUNT;
		}
	}

	static final class Role {
		final String id;
		String name;
		final EnumSet<Perm> perms;

		Role(String id, String name, EnumSet<Perm> perms) {
			this.id = id;
			this.name = name;
			this.perms = perms;
		}
	}

	/** A place the group can teleport to. */
	static final class Waypoint {
		String name;
		String world;
		double x;
		double y;
		double z;
		float yaw;
		float pitch;

		Waypoint(String name, org.bukkit.Location at) {
			this.name = name;
			moveTo(at);
		}

		private Waypoint(String name) {
			this.name = name;
		}

		void moveTo(org.bukkit.Location at) {
			world = at.getWorld().getName();
			x = at.getX();
			y = at.getY();
			z = at.getZ();
			yaw = at.getYaw();
			pitch = at.getPitch();
		}

		/** The spot, or null if its world is not loaded. */
		org.bukkit.Location location() {
			org.bukkit.World w = Bukkit.getWorld(world);
			return w == null ? null : new org.bukkit.Location(w, x, y, z, yaw, pitch);
		}

		String coords() {
			return world + " " + (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", " + (int) Math.floor(z);
		}
	}

	static final class Group {
		final UUID id;
		String name;
		/** The tag shown as [tag] before members' names, with &amp; colour codes; empty for none. */
		String tag = "";
		int discount;
		UUID leader;
		/** In rank order: the leader's role first, the default member role last. */
		final List<Role> roles = new ArrayList<>();
		final Map<UUID, String> memberRoles = new LinkedHashMap<>();
		final Map<UUID, String> memberNames = new LinkedHashMap<>();
		final Set<UUID> invites = new LinkedHashSet<>();
		final List<Waypoint> waypoints = new ArrayList<>();

		Group(UUID id, String name) {
			this.id = id;
			this.name = name;
		}

		Role role(String roleId) {
			for (Role role : roles) {
				if (role.id.equals(roleId)) {
					return role;
				}
			}
			return null;
		}

		Role roleOf(UUID member) {
			Role role = role(memberRoles.getOrDefault(member, MEMBER));
			return role != null ? role : roles.getLast();
		}

		/** 0 for the leader's role, larger further down. */
		int rank(Role role) {
			return roles.indexOf(role);
		}

		int rankOf(UUID member) {
			return rank(roleOf(member));
		}

		boolean has(UUID member, Perm perm) {
			if (member.equals(leader) && !perm.perk()) {
				return true;
			}
			return memberRoles.containsKey(member) && roleOf(member).perms.contains(perm);
		}

		/** Whether {@code actor} outranks {@code other} (the leader outranks everyone). */
		boolean outranks(UUID actor, UUID other) {
			return actor.equals(leader) || rankOf(actor) < rankOf(other);
		}

		boolean outranks(UUID actor, Role role) {
			return actor.equals(leader) ? !role.id.equals(LEADER) : rankOf(actor) < rank(role);
		}

		Component tagComponent() {
			if (tag.isEmpty()) {
				return Component.empty();
			}
			Component inner = LegacyComponentSerializer.legacyAmpersand().deserialize(tag);
			boolean coloured = tag.contains("&");
			return Component.text("[", NamedTextColor.GOLD).append(coloured ? inner : inner.color(NamedTextColor.GOLD))
					.append(Component.text("]", NamedTextColor.GOLD));
		}

		String plainTag() {
			return tag.isEmpty() ? "" : "[" + PlainTextComponentSerializer.plainText()
					.serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(tag)) + "]";
		}

		String nameOf(UUID member) {
			String name = Bukkit.getOfflinePlayer(member).getName();
			return name != null ? name : memberNames.getOrDefault(member, "?");
		}
	}

	private static Plugin plugin;
	private static File file;
	private static final Map<UUID, Group> GROUPS = new LinkedHashMap<>();
	/** The group whose tag each player shows, or {@link #NO_TAG}; absent until they choose (then their first group). */
	private static final Map<UUID, String> SHOWN_TAG = new LinkedHashMap<>();

	private Groups() {
	}

	static Group create(Player founder, String name) {
		Group group = new Group(UUID.randomUUID(), name);
		group.leader = founder.getUniqueId();
		EnumSet<Perm> all = EnumSet.allOf(Perm.class);
		group.roles.add(new Role(LEADER, "그룹장", all));
		group.roles.add(new Role(DEPUTY, "부대표", EnumSet.of(Perm.INVITE, Perm.KICK, Perm.ASSIGN_ROLES, Perm.NO_PVP,
				Perm.DISCOUNT, Perm.WAYPOINT_USE, Perm.WAYPOINT_VIEW)));
		group.roles.add(new Role(MEMBER, "멤버", EnumSet.of(Perm.NO_PVP, Perm.DISCOUNT, Perm.WAYPOINT_USE)));
		GROUPS.put(group.id, group);
		addMember(group, founder.getUniqueId(), founder.getName(), LEADER);
		save();
		return group;
	}

	static void addMember(Group group, UUID member, String name, String roleId) {
		group.memberRoles.put(member, roleId);
		group.memberNames.put(member, name);
		group.invites.remove(member);
		SHOWN_TAG.putIfAbsent(member, group.id.toString());
		Land.groupChanged(member);
		refreshName(member);
	}

	static void removeMember(Group group, UUID member) {
		group.memberRoles.remove(member);
		group.memberNames.remove(member);
		Land.groupChanged(member);
		refreshName(member);
	}

	static void disband(Group group) {
		GROUPS.remove(group.id);
		for (UUID member : List.copyOf(group.memberRoles.keySet())) {
			Land.groupChanged(member);
			refreshName(member);
		}
		Team team = board().getTeam(teamName(group));
		if (team != null) {
			team.unregister();
		}
		save();
	}

	/** The groups {@code member} is in, in the order they were founded. */
	static List<Group> of(UUID member) {
		return GROUPS.values().stream().filter(g -> g.memberRoles.containsKey(member)).toList();
	}

	static Group byId(UUID id) {
		return id == null ? null : GROUPS.get(id);
	}

	/** The group with this id if {@code member} is in it, else null. */
	static Group member(UUID id, UUID member) {
		Group group = byId(id);
		return group != null && group.memberRoles.containsKey(member) ? group : null;
	}

	/** The group whose tag {@code player} shows, or null for none. */
	static Group shownTag(UUID player) {
		String chosen = SHOWN_TAG.get(player);
		if (NO_TAG.equals(chosen)) {
			return null;
		}
		Group group = null;
		try {
			group = chosen == null ? null : member(UUID.fromString(chosen), player);
		} catch (IllegalArgumentException ignored) {
			// a broken entry
		}
		if (group == null) {
			// Never chosen, or the chosen group is gone: the first of their groups.
			group = of(player).stream().filter(g -> !g.tag.isEmpty()).findFirst().orElse(null);
		}
		return group;
	}

	/** Shows {@code group}'s tag before {@code player}'s name, or no tag if null. */
	static void showTag(UUID player, Group group) {
		SHOWN_TAG.put(player, group == null ? NO_TAG : group.id.toString());
		save();
		refreshName(player);
	}

	/** Moves {@code role} one place up ({@code -1}) or down ({@code 1}) in rank; the leader's role stays first. */
	static boolean move(Group group, Role role, int by) {
		int from = group.roles.indexOf(role);
		int to = from + by;
		if (from <= 0 || to <= 0 || to >= group.roles.size()) {
			return false;
		}
		group.roles.remove(from);
		group.roles.add(to, role);
		save();
		return true;
	}

	static Collection<Group> all() {
		return GROUPS.values();
	}

	/** Groups that have invited {@code player}. */
	static List<Group> invitesFor(UUID player) {
		return GROUPS.values().stream().filter(g -> g.invites.contains(player)).toList();
	}

	static boolean nameTaken(String name, Group except) {
		return GROUPS.values().stream().anyMatch(g -> g != except && g.name.equalsIgnoreCase(name));
	}

	/** Why {@code name} cannot be a group name, or null if it can. */
	static String badName(String name, Group except) {
		if (name.length() < 2 || name.length() > 16) {
			return "그룹 이름은 2~16글자로 지어 주세요.";
		}
		if (name.contains("&") || name.contains("§")) {
			return "그룹 이름에는 색 코드를 쓸 수 없습니다.";
		}
		if (nameTaken(name, except)) {
			return "이미 있는 그룹 이름입니다.";
		}
		return null;
	}

	/** Why {@code tag} cannot be a group tag, or null if it can. */
	static String badTag(String tag) {
		String plain = PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(tag));
		if (plain.isBlank() || plain.length() > 8) {
			return "접두사는 1~8글자로 정해 주세요. (색 코드 &a 같은 것은 글자 수에 들지 않습니다)";
		}
		if (plain.contains("[") || plain.contains("]") || tag.contains("§")) {
			return "괄호 [ ]는 자동으로 붙으니 빼고 입력해 주세요.";
		}
		return null;
	}

	/** The groups two players are both in. */
	static List<Group> shared(UUID a, UUID b) {
		return GROUPS.values().stream().filter(g -> g.memberRoles.containsKey(a) && g.memberRoles.containsKey(b)).toList();
	}

	/** The group discount (percent) {@code buyer} gets on {@code seller}'s goods: the best of the groups they share. */
	static int discount(UUID buyer, UUID seller) {
		int best = 0;
		for (Group group : shared(buyer, seller)) {
			if (group.has(buyer, Perm.DISCOUNT)) {
				best = Math.max(best, group.discount);
			}
		}
		return best;
	}

	/** A price after a discount: rounded up, at least one. */
	static int discounted(int price, int percent) {
		if (percent <= 0) {
			return price;
		}
		return Math.max(1, (int) Math.ceil(price * (100 - percent) / 100.0));
	}

	// --- name tags ---------------------------------------------------------

	private static Scoreboard board() {
		return Bukkit.getScoreboardManager().getMainScoreboard();
	}

	private static String teamName(Group group) {
		return TEAM_PREFIX + group.id.toString().substring(0, 8);
	}

	/** Shows {@code member}'s group tag (or none) in chat, the player list and over their head. */
	static void refreshName(UUID member) {
		Player player = Bukkit.getPlayer(member);
		if (player == null) {
			return;
		}
		Scoreboard board = board();
		Group group = shownTag(member);
		for (Team team : board.getTeams()) {
			if (team.getName().startsWith(TEAM_PREFIX) && team.hasEntry(player.getName())
					&& (group == null || !team.getName().equals(teamName(group)))) {
				team.removeEntry(player.getName());
			}
		}
		if (group == null || group.tag.isEmpty()) {
			player.displayName(null);
			player.playerListName(null);
			if (group != null) {
				Team team = board.getTeam(teamName(group));
				if (team != null) {
					team.removeEntry(player.getName());
				}
			}
			return;
		}
		Component tag = group.tagComponent();
		player.displayName(tag.append(Component.text(player.getName(), NamedTextColor.WHITE)));
		player.playerListName(tag.append(Component.text(player.getName(), NamedTextColor.WHITE)));
		Team team = board.getTeam(teamName(group));
		if (team == null) {
			team = board.registerNewTeam(teamName(group));
		}
		team.prefix(tag);
		team.addEntry(player.getName());
	}

	static void refreshGroup(Group group) {
		for (UUID member : group.memberRoles.keySet()) {
			refreshName(member);
		}
	}

	/** Refreshes after leaving or losing a group (which may have been the one whose tag showed). */
	static void refreshAll(java.util.Collection<UUID> members) {
		for (UUID member : members) {
			refreshName(member);
		}
	}

	// --- saving ------------------------------------------------------------

	static void load(Plugin owner) {
		plugin = owner;
		file = new File(owner.getDataFolder(), "groups.yml");
		GROUPS.clear();
		SHOWN_TAG.clear();
		// Tags are put back as members join.
		for (Team team : Set.copyOf(board().getTeams())) {
			if (team.getName().startsWith(TEAM_PREFIX)) {
				team.unregister();
			}
		}
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection shown = yaml.getConfigurationSection("shown-tag");
		if (shown != null) {
			for (String id : shown.getKeys(false)) {
				SHOWN_TAG.put(UUID.fromString(id), shown.getString(id));
			}
		}
		ConfigurationSection root = yaml.getConfigurationSection("groups");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			ConfigurationSection s = root.getConfigurationSection(key);
			try {
				Group group = new Group(UUID.fromString(key), s.getString("name", "?"));
				group.tag = s.getString("tag", "");
				group.discount = Math.max(0, Math.min(MAX_DISCOUNT, s.getInt("discount")));
				group.leader = UUID.fromString(s.getString("leader"));
				for (Map<?, ?> map : s.getMapList("roles")) {
					EnumSet<Perm> perms = EnumSet.noneOf(Perm.class);
					if (map.get("perms") instanceof List<?> names) {
						for (Object name : names) {
							try {
								perms.add(Perm.valueOf(String.valueOf(name).toUpperCase(Locale.ROOT)));
							} catch (IllegalArgumentException ignored) {
								// a permission from another version
							}
						}
					}
					group.roles.add(new Role(String.valueOf(map.get("id")), String.valueOf(map.get("name")), perms));
				}
				if (group.role(LEADER) == null || group.role(MEMBER) == null) {
					throw new IllegalStateException("missing the leader or member role");
				}
				ConfigurationSection members = s.getConfigurationSection("members");
				if (members != null) {
					for (String id : members.getKeys(false)) {
						UUID uuid = UUID.fromString(id);
						group.memberRoles.put(uuid, members.getString(id + ".role", MEMBER));
						group.memberNames.put(uuid, members.getString(id + ".name", "?"));
					}
				}
				for (Map<?, ?> map : s.getMapList("waypoints")) {
					Waypoint waypoint = new Waypoint(String.valueOf(map.get("name")));
					waypoint.world = String.valueOf(map.get("world"));
					waypoint.x = ((Number) map.get("x")).doubleValue();
					waypoint.y = ((Number) map.get("y")).doubleValue();
					waypoint.z = ((Number) map.get("z")).doubleValue();
					waypoint.yaw = map.get("yaw") instanceof Number n ? n.floatValue() : 0F;
					waypoint.pitch = map.get("pitch") instanceof Number n ? n.floatValue() : 0F;
					group.waypoints.add(waypoint);
				}
				if (!s.getBoolean("waypoint-perms", false)) {
					// Groups from before waypoints: members may use them, deputies also see where they are.
					for (Role role : group.roles) {
						role.perms.add(Perm.WAYPOINT_USE);
						if (role.id.equals(DEPUTY)) {
							role.perms.add(Perm.WAYPOINT_VIEW);
						}
					}
				}
				for (String id : s.getStringList("invites")) {
					group.invites.add(UUID.fromString(id));
				}
				GROUPS.put(group.id, group);
			} catch (RuntimeException e) {
				owner.getLogger().log(Level.WARNING, "Skipping a broken group " + key, e);
			}
		}
		for (Player online : Bukkit.getOnlinePlayers()) {
			refreshName(online.getUniqueId());
		}
	}

	static void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		SHOWN_TAG.forEach((id, group) -> yaml.set("shown-tag." + id, group));
		for (Group group : GROUPS.values()) {
			String path = "groups." + group.id;
			yaml.set(path + ".name", group.name);
			yaml.set(path + ".tag", group.tag);
			yaml.set(path + ".discount", group.discount);
			yaml.set(path + ".leader", group.leader.toString());
			List<Map<String, Object>> roles = new ArrayList<>();
			for (Role role : group.roles) {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put("id", role.id);
				map.put("name", role.name);
				map.put("perms", role.perms.stream().map(Enum::name).toList());
				roles.add(map);
			}
			yaml.set(path + ".roles", roles);
			group.memberRoles.forEach((id, role) -> {
				yaml.set(path + ".members." + id + ".role", role);
				yaml.set(path + ".members." + id + ".name", group.memberNames.getOrDefault(id, "?"));
			});
			yaml.set(path + ".invites", group.invites.stream().map(UUID::toString).toList());
			yaml.set(path + ".waypoint-perms", true);
			List<Map<String, Object>> waypoints = new ArrayList<>();
			for (Waypoint waypoint : group.waypoints) {
				Map<String, Object> map = new LinkedHashMap<>();
				map.put("name", waypoint.name);
				map.put("world", waypoint.world);
				map.put("x", waypoint.x);
				map.put("y", waypoint.y);
				map.put("z", waypoint.z);
				map.put("yaw", (double) waypoint.yaw);
				map.put("pitch", (double) waypoint.pitch);
				waypoints.add(map);
			}
			yaml.set(path + ".waypoints", waypoints);
		}
		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save groups.yml", e);
		}
	}

	/** Group members' tags on joining, invitations waiting, and no fighting where a role forbids it. */
	static final class Events implements Listener {
		@EventHandler
		public void onJoin(PlayerJoinEvent event) {
			Player player = event.getPlayer();
			for (Group group : of(player.getUniqueId())) {
				group.memberNames.put(player.getUniqueId(), player.getName());
			}
			refreshName(player.getUniqueId());
			int invites = invitesFor(player.getUniqueId()).size();
			if (invites > 0) {
				player.sendMessage(Component.text("받은 그룹 초대가 " + invites + "개 있습니다. /trade → 그룹에서 확인하세요.",
						NamedTextColor.GOLD));
			}
		}

		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onDamage(EntityDamageByEntityEvent event) {
			if (!(event.getEntity() instanceof Player victim)) {
				return;
			}
			Entity damager = event.getDamager();
			if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
				damager = shooter;
			}
			if (!(damager instanceof Player attacker) || attacker == victim) {
				return;
			}
			for (Group group : shared(attacker.getUniqueId(), victim.getUniqueId())) {
				if (group.has(attacker.getUniqueId(), Perm.NO_PVP) || group.has(victim.getUniqueId(), Perm.NO_PVP)) {
					event.setCancelled(true);
					return;
				}
			}
		}
	}
}
