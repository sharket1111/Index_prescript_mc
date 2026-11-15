package io.github.sharket1111.indexprescript.paper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

/**
 * The Extraction screen and its scene, after the original.
 *
 * The screen: the standard banner, Lunacy, tickets and Ideality, one pull
 * (130 Lunacy or a ticket) or ten (1300 or ten tickets), the Ideality exchange
 * and the rates.
 *
 * The scene, played in front of the player: the ball of chains (orange and
 * black; gold from the start when a three-star or E.G.O is coming, at times;
 * or turning gold as it finishes), rattling and tightening; then the chains
 * snap and each result bursts out of it in turn: a white burst for one-star,
 * red for two-star, gold for three-star, red-orange trailing a chain for an
 * E.G.O; each leaves its card with its rarity (Ø, ØØ, ØØØ) and name. The
 * results screen follows.
 */
final class Extraction {
	private static final Map<UUID, Scene> RUNNING = new ConcurrentHashMap<>();

	private Extraction() {
	}

	// --- the screen ---------------------------------------------------------------------------------

	static void open(Player p) {
		MdGui gui = new MdGui(6, "추출");
		gui.set(4, MdGui.item(Material.NETHER_STAR, "상시 추출", Gacha.THREE_COLOUR,
				"E.G.O " + Gacha.EGO_RATE + "% (가지지 않은 E.G.O만)",
				Gacha.stars(3) + " " + Gacha.THREE_RATE + "% · 이 서버의 무기: 카두케우스, 레바테인, 르누아르의 망치, 미미크리",
				Gacha.stars(2) + " " + Gacha.TWO_RATE + "% · 원작 2성 인격의 무기 " + Gacha.weapons().size() + "종",
				Gacha.stars(1) + " 80.8% · 인격 경험치 티켓 III",
				"10회 추출의 10번째는 " + Gacha.stars(2) + " 이상 확정",
				"이미 가진 무기가 나오면 에고 조각 (" + Gacha.stars(2) + " 그 수감자의 조각 " + Gacha.DUP_TWO_SHARDS + "개, " + Gacha.stars(3) + " 범용 조각 "
						+ Gacha.DUP_THREE_SHARDS + "개)"),
				(Runnable) null);
		int lunacy = Gacha.lunacy(p);
		int tickets = Gacha.count(p, Gacha.Kind.TICKET);
		int ideality = Gacha.ideality(p);
		ItemStack moon = MdGui.item(Material.POPPY, "광기 " + lunacy + " · 유료 광기 " + Gacha.paidLunacy(p), TextColor.color(0xE03C3C),
				"광기: 거울 던전 주간 보너스·패스·투영", "유료 광기: 상점", "추출은 광기를 먼저, 그다음 유료 광기를 써요");
		gui.set(19, Rewards.dress(moon, "lunacy"), (Runnable) null);
		gui.set(20, MdGui.item(Material.PAPER, "추출권 " + tickets + "장 · 10회 추출권 " + Gacha.count(p, Gacha.Kind.DECA) + "장", Gacha.ONE_COLOUR,
				"패스·투영·상점에서 얻어요"), (Runnable) null);
		int deca3 = Gacha.count(p, Gacha.Kind.DECA3);
		if (deca3 > 0) {
			gui.set(31, MdGui.item(Material.FILLED_MAP, "3성 확정 10회 추출 (" + deca3 + "장)", Gacha.THREE_COLOUR,
					Gacha.stars(3) + " 무기가 반드시 1개 이상 · E.G.O는 나오지 않아요", "클릭해서 추출"), () -> startGuarantee(p));
		}
		gui.set(21, MdGui.item(Material.AMETHYST_SHARD, "이상성 " + ideality + "/" + Gacha.IDEALITY_EXCHANGE, TextColor.color(0xC9A0FF),
				"추출 1회마다 1", Gacha.IDEALITY_EXCHANGE + "이면 " + Gacha.stars(3) + " 무기 하나와 교환", "클릭해서 교환"), () -> exchange(p));
		gui.set(23, MdGui.item(Material.IRON_BARS, "추출 1회", Gacha.TWO_COLOUR,
				"추출권 1장 또는 광기 " + Gacha.SINGLE_LUNACY, "(추출권을 먼저 써요)"), () -> confirm(p, 1));
		gui.set(25, MdGui.item(Material.GOLD_BLOCK, "추출 10회", Gacha.THREE_COLOUR, "10회 추출권 1장, 추출권 10장 또는 광기 " + Gacha.TEN_LUNACY,
				"10번째는 " + Gacha.stars(2) + " 이상 확정"), () -> confirm(p, 10));
		gui.set(40, MdGui.item(Material.WRITABLE_BOOK, "확률 정보", NamedTextColor.WHITE, "E.G.O 3.5% · " + Gacha.stars(3) + " 2.9% · "
				+ Gacha.stars(2) + " 12.8% · " + Gacha.stars(1) + " 80.8%", "10번째: E.G.O 3.5% · " + Gacha.stars(3) + " 2.9% · " + Gacha.stars(2)
				+ " 93.6%", "E.G.O를 모두 가지면: " + Gacha.stars(3) + " 3% · " + Gacha.stars(2) + " 13% · " + Gacha.stars(1) + " 84%"), (Runnable) null);
		gui.set(49, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> Ego.Menu.open(p));
		gui.fill().open(p);
	}

	private static void exchange(Player p) {
		if (Gacha.ideality(p) < Gacha.IDEALITY_EXCHANGE) {
			Hud.message(p, Component.text("이상성이 " + Gacha.IDEALITY_EXCHANGE + " 필요해요", NamedTextColor.GRAY));
			return;
		}
		MdGui gui = new MdGui(3, "이상성 교환");
		int slot = 11;
		for (Gacha.Three t : Gacha.Three.values()) {
			gui.set(slot++, MdGui.item(Material.NETHER_STAR, Gacha.stars(3) + " " + t.name, Gacha.THREE_COLOUR,
					Gacha.owns(p, t.id()) ? "이미 가졌어요 (범용 조각 " + Gacha.DUP_THREE_SHARDS + "개로 바뀌어요)" : "클릭해서 교환"), () -> {
						if (Gacha.exchange(p, t)) {
							Hud.message(p, Component.text("이상성 교환: " + t.name, Gacha.THREE_COLOUR));
						}
						open(p);
					});
		}
		gui.set(22, MdGui.item(Material.ARROW, "뒤로", NamedTextColor.WHITE), () -> open(p));
		gui.fill().open(p);
	}

	/** Pays for {@code count} pulls (tickets first) and plays the scene. */
	private static void start(Player p, int count) {
		if (RUNNING.containsKey(p.getUniqueId())) {
			return;
		}
		// The original's order: tickets (a Decaextraction Ticket for ten) before Lunacy, free Lunacy before paid.
		boolean paid = count == 10 && Gacha.take(p, Gacha.Kind.DECA, 1) || Gacha.take(p, Gacha.Kind.TICKET, count);
		if (!paid) {
			int cost = count == 10 ? Gacha.TEN_LUNACY : Gacha.SINGLE_LUNACY;
			if (!Gacha.spendLunacy(p, cost)) {
				Hud.message(p, Component.text("추출권이나 광기가 모자라요", NamedTextColor.RED));
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.6F);
				return;
			}
		}
		List<Gacha.Pull> pulls = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			pulls.add(Gacha.roll(p, count == 10 && i == 9));
		}
		play(p, pulls, count);
	}

	/** A 3★ Guarantee Decaextraction Ticket: ten pulls without E.G.O, at least one three-star. */
	private static void startGuarantee(Player p) {
		if (RUNNING.containsKey(p.getUniqueId()) || !Gacha.take(p, Gacha.Kind.DECA3, 1)) {
			return;
		}
		List<Gacha.Pull> pulls = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			pulls.add(Gacha.rollNoEgo(p, i == 9));
		}
		if (pulls.stream().noneMatch(x -> x.rarity() == 3)) {
			pulls.set(ThreadLocalRandom.current().nextInt(10), Gacha.rollThree(p));
		}
		play(p, pulls, 10);
	}

	private static void play(Player p, List<Gacha.Pull> pulls, int count) {
		// Results are given at once (a crash mid-scene loses nothing); the scene only shows them.
		for (Gacha.Pull pull : pulls) {
			Gacha.grant(p, pull);
		}
		Gacha.setIdeality(p, Gacha.ideality(p) + count);
		p.closeInventory();
		Scene scene = new Scene(p, pulls);
		RUNNING.put(p.getUniqueId(), scene);
		scene.begin();
	}

	static void tick() {
		RUNNING.values().removeIf(scene -> {
			try {
				return !scene.tick();
			} catch (RuntimeException e) {
				IndexPrescriptPlugin.instance().getLogger().log(java.util.logging.Level.WARNING, "Extraction scene failed", e);
				scene.clear();
				return true;
			}
		});
	}

	static void endAll() {
		RUNNING.values().forEach(Scene::clear);
		RUNNING.clear();
	}

	/** The original's confirmation: what the pulls cost and what is left after (tickets first, then free Lunacy, then paid). */
	static void confirm(Player p, int count) {
		MdGui gui = new MdGui(3, "추출 진행");
		List<String> lore = new ArrayList<>();
		boolean ok;
		if (count == 10 && Gacha.count(p, Gacha.Kind.DECA) > 0) {
			lore.add("10회 추출권 1장을 써서 " + count + "회 추출해요");
			lore.add("10회 추출권 " + Gacha.count(p, Gacha.Kind.DECA) + " → " + (Gacha.count(p, Gacha.Kind.DECA) - 1));
			ok = true;
		} else if (Gacha.count(p, Gacha.Kind.TICKET) >= count) {
			lore.add("추출권 " + count + "장을 써서 " + count + "회 추출해요");
			lore.add("추출권 " + Gacha.count(p, Gacha.Kind.TICKET) + " → " + (Gacha.count(p, Gacha.Kind.TICKET) - count));
			ok = true;
		} else {
			int cost = count == 10 ? Gacha.TEN_LUNACY : Gacha.SINGLE_LUNACY;
			int free = Gacha.lunacy(p);
			int paid = Gacha.paidLunacy(p);
			int useFree = Math.min(free, cost);
			lore.add("광기 " + cost + "을(를) 소비하여 " + count + "회 추출해요");
			lore.add("광기 " + (free + paid) + " (유료 " + paid + " 무료 " + free + ")");
			lore.add("→ " + Math.max(0, free + paid - cost) + " (유료 " + Math.max(0, paid - (cost - useFree)) + " 무료 " + (free - useFree) + ")");
			lore.add("※ 무료 광기부터 먼저 사용됩니다");
			ok = free + paid >= cost;
		}
		gui.set(4, MdGui.item(Material.PAPER, "추출 진행", Gacha.THREE_COLOUR, lore.toArray(String[]::new)), (Runnable) null);
		gui.set(11, MdGui.item(Material.RED_CONCRETE, "취소", NamedTextColor.RED), () -> open(p));
		gui.set(15, MdGui.item(ok ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, ok ? "확인" : "추출권이나 광기가 모자라요",
				ok ? NamedTextColor.GREEN : NamedTextColor.GRAY, lore.toArray(String[]::new)), () -> {
					if (ok) {
						start(p, count);
					}
				});
		gui.fill().open(p);
	}

	/** Clicks on the scene's shards and its ball (Interaction entities): right-click or left-click. */
	static final class Events implements org.bukkit.event.Listener {
		@org.bukkit.event.EventHandler
		public void onUse(org.bukkit.event.player.PlayerInteractEntityEvent event) {
			if (click(event.getPlayer(), event.getRightClicked())) {
				event.setCancelled(true);
			}
		}

		@org.bukkit.event.EventHandler
		public void onHit(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
			if (click(event.getPlayer(), event.getAttacked())) {
				event.setCancelled(true);
			}
		}

		private static boolean click(Player p, Entity e) {
			Scene scene = RUNNING.get(p.getUniqueId());
			return scene != null && scene.clicked(e);
		}
	}

	// --- the scene ----------------------------------------------------------------------------------

	/**
	 * The original's extraction: chains close in on a small burning ball of chain, which grows; the screen goes
	 * dark and a blade's flash cuts through; then the ball hangs open with a chain to a shard for every result,
	 * grey for one-star, red for two-star, gold for three-star, red-orange for E.G.O. Clicking a shard breaks it and
	 * shows that one result; clicking the ball shows all that are left. When all are shown, the results screen.
	 */
	private static final class Scene {
		static final int INTRO = 50;
		static final int DARK = 12;
		static final int OPEN = INTRO + DARK;
		/** Without a click for this long, everything left is shown. */
		static final int IDLE = 30 * 20;
		final Player player;
		final List<Gacha.Pull> pulls;
		final boolean high;
		final Location centre;
		final Vector forward;
		final Vector side;
		final Vector up = new Vector(0, 1, 0);
		final List<Entity> shown = new ArrayList<>();
		final List<BlockDisplay> intro = new ArrayList<>();
		final Entity[] nodes;
		final Entity[] links;
		final Entity[] hits;
		final boolean[] done;
		Entity ballHit;
		ItemDisplay ball;
		TextDisplay focus;
		int focusUntil;
		int tick;
		int lastClick;
		int revealing = -1;
		int finishAt = -1;

		Scene(Player player, List<Gacha.Pull> pulls) {
			this.player = player;
			this.pulls = pulls;
			this.high = pulls.stream().anyMatch(x -> x.rarity() == 3 || x.ego());
			Location eye = player.getEyeLocation();
			Vector dir = eye.getDirection().setY(0);
			if (dir.lengthSquared() < 1.0E-4) {
				dir = new Vector(0, 0, 1);
			}
			this.forward = dir.normalize();
			this.side = new Vector(-forward.getZ(), 0, forward.getX());
			this.centre = eye.clone().add(forward.clone().multiply(7)).add(0, 0.2, 0);
			nodes = new Entity[pulls.size()];
			links = new Entity[pulls.size()];
			hits = new Entity[pulls.size()];
			done = new boolean[pulls.size()];
		}

		private static int rgb(Gacha.Pull pull) {
			return pull.ego() ? 0xE8402A : pull.rarity() == 3 ? 0xFFB020 : pull.rarity() == 2 ? 0xE02818 : 0xD8D8D8;
		}

		/** Where shard {@code i} hangs: round the ball, in the plane facing the player. */
		private Location node(int i) {
			int n = pulls.size();
			double a = n == 1 ? Math.PI / 4 : Math.PI / 2 - 2 * Math.PI * i / n;
			double rx = n == 1 ? 2.2 : 3.6;
			double ry = n == 1 ? 1.6 : 2.3;
			return centre.clone().add(side.clone().multiply(Math.cos(a) * rx)).add(up.clone().multiply(Math.sin(a) * ry));
		}

		private BlockDisplay chain(Location from, Location to, int glow) {
			Vector d = to.toVector().subtract(from.toVector());
			float len = (float) d.length();
			Vector3f dir = new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ()).normalize();
			Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
			Vector3f t = q.transform(new Vector3f(-0.3F, 0, -0.3F));
			return from.getWorld().spawn(from, BlockDisplay.class, b -> {
				b.setBlock(Material.IRON_CHAIN.createBlockData());
				b.setPersistent(false);
				b.setBrightness(new Display.Brightness(13, 13));
				b.setTransformation(new Transformation(t, q, new Vector3f(0.6F, len, 0.6F), new Quaternionf()));
				b.setInterpolationDuration(4);
				if (glow >= 0) {
					b.setGlowing(true);
					b.setGlowColorOverride(Color.fromRGB(glow));
				}
			});
		}

		/** Reach for the shards, which hang farther off than a player can normally click. */
		private static final org.bukkit.NamespacedKey REACH = new org.bukkit.NamespacedKey(Items.NAMESPACE, "extraction_reach");

		void begin() {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 120, 9, false, false, false));
			org.bukkit.attribute.AttributeInstance reach = player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
			if (reach != null) {
				reach.removeModifier(REACH);
				reach.addTransientModifier(new org.bukkit.attribute.AttributeModifier(REACH, 10, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
			}
			World w = centre.getWorld();
			ball = w.spawn(centre, ItemDisplay.class, d -> {
				d.setItemStack(ItemStack.of(Material.MAGMA_BLOCK));
				d.setPersistent(false);
				d.setBrightness(new Display.Brightness(15, 15));
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.2F), new Quaternionf()));
				d.setInterpolationDuration(4);
			});
			shown.add(ball);
			// The chains of the corridor, closing in on it.
			for (int i = 0; i < 10; i++) {
				double a = 2 * Math.PI * i / 10;
				Location far = centre.clone().add(side.clone().multiply(Math.cos(a) * 9)).add(up.clone().multiply(Math.sin(a) * 4.5));
				BlockDisplay c = chain(far, centre, -1);
				intro.add(c);
				shown.add(c);
			}
			w.playSound(centre, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0F, 0.5F);
			w.playSound(centre, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.8F, 0.5F);
		}

		boolean tick() {
			if (!player.isOnline()) {
				clear();
				return false;
			}
			World w = centre.getWorld();
			if (tick < INTRO) {
				// The ball grows and turns, sparks fly; the chains wind tighter.
				float t = tick / (float) INTRO;
				float spin = tick * (0.06F + tick * 0.003F);
				ball.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(spin, 0.3F, 1, 0.2F)),
						new Vector3f(0.2F + 1.4F * t), new Quaternionf()));
				Particle.DustOptions ember = new Particle.DustOptions(Color.fromRGB(0xFF6A10), 1.3F);
				w.spawnParticle(Particle.DUST, centre, 6, 0.5 + t, 0.5 + t, 0.5 + t, 0, ember);
				w.spawnParticle(Particle.FLAME, centre, 3, 0.4 + t, 0.4 + t, 0.4 + t, 0.02);
				w.spawnParticle(Particle.LAVA, centre, tick % 4 == 0 ? 1 : 0, 0.3, 0.3, 0.3, 0);
				if (tick % 10 == 0) {
					w.playSound(centre, Sound.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 1.0F, 0.5F + tick * 0.015F);
				}
			} else if (tick == INTRO) {
				// Dark.
				player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, DARK + 6, 0, false, false, false));
				w.playSound(centre, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.6F);
			} else if (tick == OPEN - 3) {
				// The blade's flash through the dark.
				Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(0x60C8FF), 2.0F);
				Location a = centre.clone().add(side.clone().multiply(-2.5)).add(0, 2.0, 0);
				Location b = centre.clone().add(side.clone().multiply(2.5)).add(0, -1.5, 0);
				for (double s = 0; s <= 1; s += 0.04) {
					Location at = a.clone().add(b.toVector().subtract(a.toVector()).multiply(s));
					w.spawnParticle(Particle.DUST, at, 2, 0.02, 0.02, 0.02, 0, blue);
					w.spawnParticle(Particle.END_ROD, at, 1, 0, 0, 0, 0);
				}
				w.playSound(centre, Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 1.0F, 1.4F);
				w.playSound(centre, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.6F);
			} else if (tick == OPEN) {
				open();
			} else if (tick > OPEN) {
				idle(w);
				if (revealing >= 0 && tick % 4 == 0) {
					// The ball was clicked: the rest break one after another.
					int next = nextLeft();
					if (next < 0) {
						revealing = -1;
					} else {
						shatter(next, false);
					}
				}
				if (focus != null && tick >= focusUntil) {
					focus.remove();
					focus = null;
				}
				if (finishAt < 0 && nextLeft() < 0) {
					finishAt = tick + 40;
					burstBall();
				}
				if (finishAt >= 0 && tick >= finishAt) {
					clear();
					results();
					return false;
				}
				if (finishAt < 0 && revealing < 0 && tick - lastClick > IDLE) {
					revealing = 0;
				}
			}
			tick++;
			return true;
		}

		private int nextLeft() {
			for (int i = 0; i < done.length; i++) {
				if (!done[i]) {
					return i;
				}
			}
			return -1;
		}

		/** The ball opens: a chain to a shard for every result, each to be clicked. */
		private void open() {
			World w = centre.getWorld();
			for (BlockDisplay c : intro) {
				c.remove();
			}
			intro.clear();
			ball.setItemStack(ItemStack.of(high ? Material.RAW_GOLD_BLOCK : Material.MAGMA_BLOCK));
			ball.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.9F), new Quaternionf()));
			w.spawnParticle(Particle.FLASH, centre, 1, 0, 0, 0, 0, Color.fromRGB(0xFFE0A0));
			w.spawnParticle(Particle.EXPLOSION, centre, 3, 0.6, 0.6, 0.6, 0);
			w.playSound(centre, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
			w.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6F, 1.4F);
			for (int i = 0; i < pulls.size(); i++) {
				Gacha.Pull pull = pulls.get(i);
				Location at = node(i);
				int colour = rgb(pull);
				links[i] = chain(centre, at, pull.rarity() >= 2 || pull.ego() ? colour : -1);
				shown.add(links[i]);
				nodes[i] = w.spawn(at, ItemDisplay.class, d -> {
					d.setItemStack(ItemStack.of(Material.FIRE_CHARGE));
					d.setPersistent(false);
					d.setBillboard(Display.Billboard.CENTER);
					d.setBrightness(new Display.Brightness(15, 15));
					d.setGlowing(true);
					d.setGlowColorOverride(Color.fromRGB(colour));
					d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1F), new Quaternionf()));
				});
				shown.add(nodes[i]);
				hits[i] = w.spawn(at.clone().add(0, -0.6, 0), org.bukkit.entity.Interaction.class, it -> {
					it.setInteractionWidth(1.2F);
					it.setInteractionHeight(1.2F);
					it.setPersistent(false);
					it.setResponsive(true);
				});
				shown.add(hits[i]);
			}
			ballHit = w.spawn(centre.clone().add(0, -1.0, 0), org.bukkit.entity.Interaction.class, it -> {
				it.setInteractionWidth(2.0F);
				it.setInteractionHeight(2.0F);
				it.setPersistent(false);
				it.setResponsive(true);
			});
			shown.add(ballHit);
			lastClick = tick;
			player.sendActionBar(Component.text("조각을 눌러 하나씩 · 사슬 공을 눌러 남은 것 모두 보기", NamedTextColor.GOLD));
		}

		/** Every few ticks the shards smoulder in their colour. */
		private void idle(World w) {
			if (tick % 5 != 0) {
				return;
			}
			for (int i = 0; i < nodes.length; i++) {
				if (done[i] || nodes[i] == null) {
					continue;
				}
				Gacha.Pull pull = pulls.get(i);
				Location at = nodes[i].getLocation();
				if (pull.rarity() == 3 || pull.ego()) {
					w.spawnParticle(Particle.FLAME, at, 3, 0.35, 0.35, 0.35, 0.01);
					w.spawnParticle(Particle.DUST, at, 3, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.fromRGB(rgb(pull)), 1.4F));
				} else if (pull.rarity() == 2) {
					w.spawnParticle(Particle.DUST, at, 3, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.fromRGB(0xE02818), 1.2F));
				} else {
					w.spawnParticle(Particle.WHITE_ASH, at, 4, 0.4, 0.4, 0.4, 0);
				}
			}
			if (tick % 20 == 0 && finishAt < 0) {
				player.sendActionBar(Component.text("조각을 눌러 하나씩 · 사슬 공을 눌러 남은 것 모두 보기", NamedTextColor.GOLD));
			}
		}

		boolean clicked(Entity e) {
			if (tick <= OPEN || finishAt >= 0) {
				return e == ballHit || java.util.Arrays.asList(hits).contains(e);
			}
			if (e == ballHit) {
				if (revealing < 0) {
					revealing = 0;
					lastClick = tick;
				}
				return true;
			}
			for (int i = 0; i < hits.length; i++) {
				if (hits[i] == e) {
					if (!done[i] && revealing < 0 && (focus == null || tick >= focusUntil - 25)) {
						shatter(i, true);
						lastClick = tick;
					}
					return true;
				}
			}
			return false;
		}

		/** Shard {@code i} breaks: its result shows, alone and close if {@code close}. */
		private void shatter(int i, boolean close) {
			done[i] = true;
			Gacha.Pull pull = pulls.get(i);
			World w = centre.getWorld();
			Location at = node(i);
			for (Entity e : new Entity[] {nodes[i], links[i], hits[i]}) {
				if (e != null) {
					e.remove();
				}
			}
			Particle.DustOptions burst = new Particle.DustOptions(Color.fromRGB(rgb(pull)), pull.rarity() == 3 ? 2.0F : 1.4F);
			w.spawnParticle(Particle.FLASH, at, 1, 0, 0, 0, 0, Color.fromRGB(rgb(pull)));
			w.spawnParticle(Particle.DUST, at, pull.rarity() == 3 ? 70 : 30, 0.5, 0.5, 0.5, 0, burst);
			w.spawnParticle(Particle.FIREWORK, at, pull.rarity() == 3 ? 30 : 8, 0.2, 0.2, 0.2, 0.08);
			w.playSound(at, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0F, 1.2F);
			TextColor colour = Gacha.colour(pull);
			if (pull.rarity() == 3 || pull.ego()) {
				w.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.3, 0.5, 0.3, 0.4);
				w.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
				player.showTitle(Title.title(Component.text(pull.ego() ? "E.G.O" : Gacha.stars(3), colour).decorate(TextDecoration.BOLD),
						Component.text(pull.name(), colour), Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1400), Duration.ofMillis(300))));
			} else {
				w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.8F, pull.rarity() == 2 ? 1.2F : 1.6F);
			}
			Component card = Component.text(pull.ego() ? "E.G.O" : Gacha.stars(pull.rarity()), colour).decorate(TextDecoration.BOLD)
					.append(Component.newline()).append(Component.text(pull.name(), NamedTextColor.WHITE))
					.append(pull.duplicate() ? Component.newline().append(Component.text("→ 에고 조각", NamedTextColor.GRAY)) : Component.empty());
			// The small card stays where the shard was.
			shown.add(w.spawn(at, TextDisplay.class, d -> styleCard(d, card, 0.5F)));
			if (close) {
				if (focus != null) {
					focus.remove();
				}
				Location near = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(2.6)).add(0, -0.3, 0);
				focus = w.spawn(near, TextDisplay.class, d -> styleCard(d, card, 0.9F));
				shown.add(focus);
				focusUntil = tick + 45;
			}
		}

		private static void styleCard(TextDisplay d, Component card, float scale) {
			d.text(card);
			d.setPersistent(false);
			d.setBillboard(Display.Billboard.CENTER);
			d.setAlignment(TextDisplay.TextAlignment.CENTER);
			d.setLineWidth(140);
			d.setShadowed(true);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setBackgroundColor(Color.fromARGB(210, 22, 12, 12));
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf()));
		}

		private void burstBall() {
			World w = centre.getWorld();
			if (ball != null) {
				ball.remove();
			}
			if (ballHit != null) {
				ballHit.remove();
			}
			w.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 1);
			w.spawnParticle(Particle.DUST, centre, 80, 1.5, 1.5, 1.5, 0, new Particle.DustOptions(Color.fromRGB(high ? 0xFFD040 : 0xFF6A10), 1.6F));
			w.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7F, 1.3F);
		}

		private void results() {
			MdGui gui = new MdGui(2, "추출 결과");
			for (int i = 0; i < pulls.size(); i++) {
				Gacha.Pull pull = pulls.get(i);
				Material m = pull.ego() ? Material.RED_DYE : pull.rarity() == 3 ? Material.NETHER_STAR : pull.rarity() == 2 ? Material.GOLD_INGOT
						: Material.EXPERIENCE_BOTTLE;
				ItemStack icon = MdGui.item(m, Component.text((pull.ego() ? "E.G.O " : Gacha.stars(pull.rarity()) + " ") + pull.name(), Gacha.colour(pull))
						.decoration(TextDecoration.ITALIC, false), List.of(Component.text(pull.duplicate() ? "이미 가져서 에고 조각으로 바뀌었어요"
								: pull.ego() ? "/메뉴 → E.G.O 장착에서 낄 수 있어요" : pull.rarity() == 1 ? "인벤토리에 들어갔어요"
								: "/메뉴 → 무기 선택에서 들 수 있어요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
				if (pull.rarity() == 1 && !pull.ego()) {
					Rewards.dress(icon, "train3");
				}
				if (pull.rarity() == 3) {
					MdGui.glow(icon);
				}
				gui.set(i < 5 ? 2 + i : 11 + (i - 5), icon, (Runnable) null);
			}
			gui.set(9, MdGui.item(Material.ARROW, "돌아가기", NamedTextColor.WHITE), () -> Extraction.open(player));
			gui.set(17, MdGui.item(Material.IRON_BARS, "재추출 (" + pulls.size() + "회)", Gacha.TWO_COLOUR), () -> confirm(player, pulls.size()));
			gui.fill().open(player);
		}

		void clear() {
			shown.forEach(Entity::remove);
			shown.clear();
			intro.clear();
			player.removePotionEffect(PotionEffectType.SLOWNESS);
			player.removePotionEffect(PotionEffectType.BLINDNESS);
			org.bukkit.attribute.AttributeInstance reach = player.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
			if (reach != null) {
				reach.removeModifier(REACH);
			}
		}
	}

}
