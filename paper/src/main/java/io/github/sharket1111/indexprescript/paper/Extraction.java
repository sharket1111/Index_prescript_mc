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
	private static final int BALL = 70;
	private static final int GAP = 6;

	private Extraction() {
	}

	// --- the screen ---------------------------------------------------------------------------------

	static void open(Player p) {
		MdGui gui = new MdGui(6, "추출");
		gui.set(4, MdGui.item(Material.NETHER_STAR, "상시 추출", Gacha.THREE_COLOUR,
				"E.G.O " + Gacha.EGO_RATE + "% (가지지 않은 E.G.O만)",
				Gacha.stars(3) + " " + Gacha.THREE_RATE + "% · 이 서버의 무기: 카두케우스, 레바테인, 르누아르의 망치, 미미크리, 검은 신",
				Gacha.stars(2) + " " + Gacha.TWO_RATE + "% · 원작 2성 인격의 무기 " + Gacha.weapons().size() + "종",
				Gacha.stars(1) + " 83% · 경험치 티켓",
				"10회 추출의 10번째는 " + Gacha.stars(2) + " 이상 확정",
				"이미 가진 무기가 나오면 끈 (" + Gacha.stars(2) + " " + Gacha.DUP_TWO_THREADS + "개, " + Gacha.stars(3) + " " + Gacha.DUP_THREE_THREADS + "개)"),
				(Runnable) null);
		int lunacy = Gacha.lunacy(p);
		int tickets = Gacha.count(p, Gacha.Kind.TICKET);
		int ideality = Gacha.ideality(p);
		ItemStack moon = MdGui.item(Material.POPPY, "광기 " + lunacy, TextColor.color(0xE03C3C), "거울 던전에서 얻어요");
		gui.set(19, moon, (Runnable) null);
		gui.set(20, MdGui.item(Material.PAPER, "추출권 " + tickets + "장", Gacha.ONE_COLOUR, "거울 던전에서 얻어요"), (Runnable) null);
		gui.set(21, MdGui.item(Material.AMETHYST_SHARD, "이상성 " + ideality + "/" + Gacha.IDEALITY_EXCHANGE, TextColor.color(0xC9A0FF),
				"추출 1회마다 1", Gacha.IDEALITY_EXCHANGE + "이면 " + Gacha.stars(3) + " 무기 하나와 교환", "클릭해서 교환"), () -> exchange(p));
		gui.set(23, MdGui.item(Material.IRON_BARS, "추출 1회", Gacha.TWO_COLOUR,
				"추출권 1장 또는 광기 " + Gacha.SINGLE_LUNACY, "(추출권을 먼저 써요)"), () -> start(p, 1));
		gui.set(25, MdGui.item(Material.GOLD_BLOCK, "추출 10회", Gacha.THREE_COLOUR, "추출권 10장 또는 광기 " + Gacha.TEN_LUNACY,
				"10번째는 " + Gacha.stars(2) + " 이상 확정"), () -> start(p, 10));
		gui.set(40, MdGui.item(Material.WRITABLE_BOOK, "확률 정보", NamedTextColor.WHITE, "E.G.O 1.3% · " + Gacha.stars(3) + " 2.9% · "
				+ Gacha.stars(2) + " 12.8% · " + Gacha.stars(1) + " 83%", "10번째: E.G.O 1.3% · " + Gacha.stars(3) + " 2.9% · " + Gacha.stars(2)
				+ " 95.8%", "E.G.O를 모두 가지면: " + Gacha.stars(3) + " 3% · " + Gacha.stars(2) + " 13% · " + Gacha.stars(1) + " 84%"), (Runnable) null);
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
					Gacha.owns(p, t.id()) ? "이미 가졌어요 (끈 " + Gacha.DUP_THREE_THREADS + "개로 바뀌어요)" : "클릭해서 교환"), () -> {
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
		boolean paid = Gacha.take(p, Gacha.Kind.TICKET, count);
		if (!paid) {
			int cost = count == 10 ? Gacha.TEN_LUNACY : Gacha.SINGLE_LUNACY;
			if (Gacha.lunacy(p) < cost) {
				Hud.message(p, Component.text("추출권이나 광기가 모자라요", NamedTextColor.RED));
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8F, 0.6F);
				return;
			}
			Gacha.addLunacy(p, -cost);
		}
		List<Gacha.Pull> pulls = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			pulls.add(Gacha.roll(p, count == 10 && i == 9));
		}
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

	// --- the scene ----------------------------------------------------------------------------------

	private static final class Scene {
		final Player player;
		final List<Gacha.Pull> pulls;
		final boolean high;
		/** Gold from the start (half the times a three-star or E.G.O is coming), or turning gold at the end. */
		final boolean goldFirst;
		final Location centre;
		final List<Entity> shown = new ArrayList<>();
		final List<BlockDisplay> chains = new ArrayList<>();
		ItemDisplay ball;
		boolean gold;
		int tick;

		Scene(Player player, List<Gacha.Pull> pulls) {
			this.player = player;
			this.pulls = pulls;
			this.high = pulls.stream().anyMatch(x -> x.rarity() == 3);
			this.goldFirst = high && ThreadLocalRandom.current().nextBoolean();
			Location eye = player.getEyeLocation();
			Vector dir = eye.getDirection().setY(0);
			if (dir.lengthSquared() < 1.0E-4) {
				dir = new Vector(0, 0, 1);
			}
			this.centre = eye.clone().add(dir.normalize().multiply(5)).add(0, 0.6, 0);
			this.gold = goldFirst;
		}

		void begin() {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BALL + pulls.size() * GAP + 60, 9, false, false, false));
			World w = centre.getWorld();
			ball = w.spawn(centre, ItemDisplay.class, d -> {
				d.setItemStack(ItemStack.of(gold ? Material.GOLD_BLOCK : Material.MAGMA_BLOCK));
				d.setPersistent(false);
				d.setBrightness(new Display.Brightness(15, 15));
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.6F, 1.6F, 1.6F), new Quaternionf()));
				d.setInterpolationDuration(3);
				d.setTeleportDuration(2);
			});
			shown.add(ball);
			// The chains wound round it.
			for (int i = 0; i < 14; i++) {
				int idx = i;
				BlockDisplay chain = w.spawn(centre, BlockDisplay.class, d -> {
					d.setBlock(Material.IRON_CHAIN.createBlockData());
					d.setPersistent(false);
					d.setBrightness(new Display.Brightness(12, 12));
					d.setInterpolationDuration(3);
					float a = (float) (Math.PI * 2 * idx / 14);
					d.setTransformation(new Transformation(new Vector3f(-0.5F, -1.6F, -0.5F), new Quaternionf(new AxisAngle4f(a, 0, 1, 0)),
							new Vector3f(1.0F, 3.2F, 1.0F), new Quaternionf(new AxisAngle4f((float) (Math.PI / 2 * (idx % 2 == 0 ? 0.7 : -0.7)), 1, 0, 0))));
				});
				chains.add(chain);
				shown.add(chain);
			}
			w.playSound(centre, Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.0F, 0.6F);
			w.playSound(centre, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.8F, 0.6F);
		}

		boolean tick() {
			if (!player.isOnline()) {
				clear();
				return false;
			}
			World w = centre.getWorld();
			Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(gold ? 0xFFD040 : 0xFF6A10), 1.4F);
			Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(gold ? 0xFFF2A0 : 0x1A1210), 1.2F);
			if (tick < BALL) {
				// The ball turns and the chains tighten, faster and faster.
				float spin = tick * (0.05F + tick * 0.002F);
				ball.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(spin, 0.3F, 1, 0.2F)),
						new Vector3f(1.6F - tick * 0.004F), new Quaternionf()));
				for (int i = 0; i < chains.size(); i++) {
					BlockDisplay chain = chains.get(i);
					float a = (float) (Math.PI * 2 * i / chains.size()) + spin * 1.5F;
					chain.setTransformation(new Transformation(new Vector3f(-0.5F, -1.6F, -0.5F), new Quaternionf(new AxisAngle4f(a, 0, 1, 0)),
							new Vector3f(1.0F, 3.2F - tick * 0.012F, 1.0F), new Quaternionf(new AxisAngle4f((float) (Math.PI / 2 * (i % 2 == 0 ? 0.7 : -0.7)), 1, 0, 0))));
				}
				w.spawnParticle(Particle.DUST, centre, 8, 0.9, 0.9, 0.9, 0, orange);
				w.spawnParticle(Particle.DUST, centre, 4, 1.1, 1.1, 1.1, 0, black);
				w.spawnParticle(Particle.FLAME, centre, 2, 1.2, 1.2, 1.2, 0.01);
				if (tick % 12 == 0) {
					w.playSound(centre, Sound.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 1.0F, 0.5F + tick * 0.01F);
				}
				if (tick == BALL - 18 && high && !goldFirst) {
					// It turns gold as it finishes.
					gold = true;
					ball.setItemStack(ItemStack.of(Material.GOLD_BLOCK));
					w.spawnParticle(Particle.FLASH, centre, 1, 0, 0, 0, 0, Color.fromRGB(0xFFD040));
					w.playSound(centre, Sound.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0F, 1.2F);
				}
			} else if (tick == BALL) {
				// The chains snap.
				for (BlockDisplay chain : chains) {
					chain.remove();
				}
				chains.clear();
				ball.remove();
				w.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 1);
				w.spawnParticle(Particle.DUST, centre, 80, 1.5, 1.5, 1.5, 0, orange);
				w.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.8F, 1.2F);
				w.playSound(centre, Sound.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
			} else {
				int i = (tick - BALL - 4) / GAP;
				if ((tick - BALL - 4) % GAP == 0 && i >= 0 && i < pulls.size()) {
					reveal(i);
				}
				if (tick >= BALL + 4 + pulls.size() * GAP + 50) {
					clear();
					results();
					return false;
				}
			}
			tick++;
			return true;
		}

		/** Card {@code i}: where it lands, two rows of five round the ball's place. */
		private Location slot(int i) {
			Vector forward = centre.toVector().subtract(player.getEyeLocation().toVector()).setY(0).normalize();
			Vector side = new Vector(-forward.getZ(), 0, forward.getX());
			int n = pulls.size();
			int row = n == 1 ? 0 : i / 5;
			int col = n == 1 ? 2 : i % 5;
			return centre.clone().add(side.multiply((col - 2) * 1.25)).add(0, n == 1 ? 0 : 0.9 - row * 1.5, 0);
		}

		private void reveal(int i) {
			Gacha.Pull pull = pulls.get(i);
			World w = centre.getWorld();
			Location at = slot(i);
			TextColor colour = Gacha.colour(pull);
			int rgb = pull.ego() ? 0xE8402A : pull.rarity() == 3 ? 0xFFC030 : pull.rarity() == 2 ? 0xE03020 : 0xF0F0F0;
			Particle.DustOptions burst = new Particle.DustOptions(Color.fromRGB(rgb), pull.rarity() == 3 ? 2.0F : 1.4F);
			// The burst flies from the ball's place to the card's.
			Vector from = centre.toVector();
			Vector to = at.toVector();
			for (double t = 0; t <= 1; t += 0.1) {
				w.spawnParticle(Particle.DUST, from.clone().multiply(1 - t).add(to.clone().multiply(t)).toLocation(w), 2, 0.05, 0.05, 0.05, 0, burst);
			}
			w.spawnParticle(Particle.DUST, at, pull.rarity() == 3 ? 70 : 30, 0.4, 0.4, 0.4, 0, burst);
			w.spawnParticle(Particle.FIREWORK, at, pull.rarity() == 3 ? 30 : 8, 0.2, 0.2, 0.2, 0.08);
			if (pull.ego()) {
				for (double t = 0; t < 1.6; t += 0.1) {
					w.spawnParticle(Particle.DUST, at.clone().add(-t, -t * 0.4, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0x808080), 1.0F));
				}
			}
			if (pull.rarity() == 3) {
				w.spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.3, 0.5, 0.3, 0.4);
				w.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
				player.showTitle(Title.title(Component.text(Gacha.stars(3), Gacha.THREE_COLOUR).decorate(TextDecoration.BOLD),
						Component.text(pull.name(), colour), Title.Times.times(Duration.ofMillis(80), Duration.ofMillis(1600), Duration.ofMillis(300))));
			} else {
				w.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.8F, pull.rarity() == 2 ? 1.2F : 1.6F);
			}
			Component card = Component.text(pull.ego() ? "E.G.O" : Gacha.stars(pull.rarity()), colour).decorate(TextDecoration.BOLD)
					.append(Component.newline()).append(Component.text(pull.name(), NamedTextColor.WHITE))
					.append(pull.duplicate() ? Component.newline().append(Component.text("→ 끈", NamedTextColor.GRAY)) : Component.empty());
			TextDisplay display = w.spawn(at, TextDisplay.class, d -> {
				d.text(card);
				d.setPersistent(false);
				d.setBillboard(Display.Billboard.CENTER);
				d.setAlignment(TextDisplay.TextAlignment.CENTER);
				d.setLineWidth(120);
				d.setShadowed(true);
				d.setBrightness(new Display.Brightness(15, 15));
				d.setBackgroundColor(Color.fromARGB(200, 22, 12, 12));
				d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55F), new Quaternionf()));
			});
			shown.add(display);
		}

		private void results() {
			MdGui gui = new MdGui(2, "추출 결과");
			for (int i = 0; i < pulls.size(); i++) {
				Gacha.Pull pull = pulls.get(i);
				Material m = pull.ego() ? Material.RED_DYE : pull.rarity() == 3 ? Material.NETHER_STAR : pull.rarity() == 2 ? Material.GOLD_INGOT
						: Material.EXPERIENCE_BOTTLE;
				ItemStack icon = MdGui.item(m, Component.text((pull.ego() ? "E.G.O " : Gacha.stars(pull.rarity()) + " ") + pull.name(), Gacha.colour(pull))
						.decoration(TextDecoration.ITALIC, false), List.of(Component.text(pull.duplicate() ? "이미 가져서 끈으로 바뀌었어요"
								: pull.ego() ? "/메뉴 → E.G.O 장착에서 낄 수 있어요" : "인벤토리에 들어갔어요", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
				if (pull.rarity() == 3) {
					MdGui.glow(icon);
				}
				gui.set(i < 5 ? 2 + i : 11 + (i - 5), icon, (Runnable) null);
			}
			gui.set(9, MdGui.item(Material.IRON_BARS, "한 번 더 (" + pulls.size() + "회)", Gacha.TWO_COLOUR), () -> start(player, pulls.size()));
			gui.set(17, MdGui.item(Material.ARROW, "추출 화면으로", NamedTextColor.WHITE), () -> open(player));
			gui.fill().open(player);
		}

		void clear() {
			shown.forEach(Entity::remove);
			shown.clear();
			chains.clear();
			player.removePotionEffect(PotionEffectType.SLOWNESS);
		}
	}

}
