package io.github.sharket1111.indexprescript.paper;

import net.kyori.adventure.text.format.TextColor;

/**
 * A skill as the original shows it in a clash: base power, coin power, coins,
 * plus its name, colour (sin) and a line of description. {@code ticks} is
 * about how long the skill plays. The coin types follow the original's icons
 * as the videos show them: round coins plain, red octagons unbreakable,
 * green octagons execution coins. {@code clashes} is false for counters and guards, which
 * never clash.
 */
record SkillCard(String name, TextColor colour, int base, int coinPower, int coins, String description, int ticks,
		boolean clashes, String types) {

	/** Coin types, one letter per coin in {@code types}: plain, unbreakable (red), execution (green, unbreakable, and destroys red). */
	static final char COIN_PLAIN = 'N';
	static final char COIN_UNBREAKABLE = 'R';
	static final char COIN_EXECUTION = 'G';

	char type(int index) {
		return index < types.length() ? types.charAt(index) : COIN_PLAIN;
	}

	/** The same skill with every plain coin made unbreakable (the Black God's Authority). */
	SkillCard unbreakable() {
		return new SkillCard(name, colour, base, coinPower, coins, description, ticks, clashes, types.replace(COIN_PLAIN, COIN_UNBREAKABLE)
				+ String.valueOf(COIN_UNBREAKABLE).repeat(Math.max(0, coins - types.length())));
	}

	private static final TextColor WRATH = TextColor.color(0xE03C28);
	private static final TextColor LUST = TextColor.color(0xF07828);
	private static final TextColor SLOTH = TextColor.color(0xF0C83C);
	private static final TextColor GLUTTONY = TextColor.color(0x9ACD32);
	private static final TextColor GLOOM = TextColor.color(0x3C8CDC);
	private static final TextColor PRIDE = TextColor.color(0x2850B4);
	private static final TextColor ENVY = TextColor.color(0x9650C8);

	/** The power the coins are expected to give: base plus half the coins heads. */
	double expected() {
		return base + coinPower * coins * 0.5;
	}

	// --- Caduceus ---------------------------------------------------------------------
	static final SkillCard FURIOSO = new SkillCard("Furioso-Replica", WRATH, 10, 1, 2,
			"합 위력 +5. 매 타격마다 새 무기를 형성하고, 두 번째 코인이 7번 더 쏟아진다(모두 9타). 마지막은 대낫의 대각선 일격.", 250, true, "RR");

	// --- Rien's right-click skills, by [Hermes] (RienSkill) -------------------------------
	static final SkillCard[] RIEN = {
		new SkillCard("무게를 실어 내려쳐, 몸을 무너뜨리고", GLUTTONY, 2, 2, 2, "타격 무기면 피해 +20%, 아니면 -10%. 호흡.", 80, true, "NN"),
		new SkillCard("끝을 세워, 심지까지 밀어 넣고", GLUTTONY, 2, 2, 2, "관통 무기면 피해 +20%, 아니면 -10%. 호흡.", 60, true, "NN"),
		new SkillCard("날을 눕혀, 숨결이 갈라지듯 잘라내고", GLUTTONY, 2, 2, 2, "참격 무기면 피해 +20%, 아니면 -10%. 호흡.", 80, true, "NN"),
		new SkillCard("휘둘러 떨어트려, 땅과 하나로 만들어", PRIDE, 2, 2, 3, "타격 무기면 피해 +30%, 아니면 -15%. 호흡 2, 침잠.", 95, true, "NNN"),
		new SkillCard("점 하나를 겨눠, 안쪽에서 울리며", PRIDE, 2, 2, 3, "관통 무기면 피해 +30%, 아니면 -15%. 호흡 2, 침잠.", 85, true, "NNN"),
		new SkillCard("결을 낮게 깔아, 남은 것을 벗겨내고", PRIDE, 2, 2, 3, "참격 무기면 피해 +30%, 아니면 -15%. 호흡 2, 침잠.", 85, true, "NNN"),
		new SkillCard("소리를 부수고, 생각을 납작하게 짓이겨라", GLOOM, 3, 3, 4, "타격 무기면 피해 +50%, 아니면 -25%. 호흡 2·횟수 2, 침잠.", 120, true, "NNNN"),
		new SkillCard("고요의 중심을 찌르고, 기억을 꿰뚫어라", GLOOM, 3, 3, 4, "관통 무기면 피해 +50%, 아니면 -25%. 호흡 2·횟수 2, 침잠.", 135, true, "NNNN"),
		new SkillCard("날카롭게 벼린 비밀로, 형태를 베어내라", GLOOM, 3, 3, 4, "참격 무기면 피해 +50%, 아니면 -25%. 호흡 2·횟수 2, 침잠.", 135, true, "NNNN"),
	};

	// --- Laevatein ----------------------------------------------------------------------
	static final SkillCard RELEASE_1 = new SkillCard("포장 하나 뜯는다!", ENVY, 15, 1, 3,
			"회색 휘두르기 2번 뒤 껍데기를 깨뜨리고 1단계로 해방, 화염 베기.", 110, true, "RRR");
	static final SkillCard RELEASE_2 = new SkillCard("두 번째 포장지까지 벗기게 되는군.", ENVY, 14, 1, 4,
			"화염 초승달 연타 뒤 거대 폭발과 함께 2단계로 해방.", 172, true, "RRRR");
	static final SkillCard RELEASE_3 = new SkillCard("레바테인 - 여기까지 꺼내는 것도 오랜만이군!", ENVY, 19, 1, 4,
			"보라 낙뢰와 화염 베기 뒤 초대형 화염과 함께 완전 해방.", 175, true, "RRRR");
	static final SkillCard STOMP = new SkillCard("짓밟기", WRATH, 11, 3, 2,
			"돌진해 두 번 짓밟는다. 출혈. 대상이 출혈 중이면 피해 증가.", 48, true, "NR");
	static final SkillCard HEATED = new SkillCard("후끈해지지 않나?!", PRIDE, 15, 1, 3,
			"화염 호, 찌르기, 화염 초승달과 폭발. 화상, 속박, 마비.", 110, true, "RRR");
	static final SkillCard MELT = new SkillCard("슬슬 녹아 죽을 차례다", PRIDE, 15, 1, 3,
			"분홍 베기, 화염 호와 찌르기, 거대 화염 초승달. 화상, 속박, 마비.", 115, true, "RRR");
	static final SkillCard STAB = new SkillCard("칼빵[레바테인]", PRIDE, 15, 1, 5,
			"불타는 5연타와 넓은 초승달. 화상 10.", 160, true, "RRRRR");
	static final SkillCard RULE_VIOLATION = new SkillCard("규칙 위반", ENVY, 14, 2, 2,
			"반격: 회색 호 2번. 출혈, 체력 회복.", 104, false, "NN");
	static final SkillCard EXECUTION = new SkillCard("즉결처형[레바테인]", ENVY, 20, 1, 5,
			"반격: 베기, 발차기, 칼 던지기, 드롭킥, 사슬로 회수해 흰 초승달.", 250, false, "RRRRR");

	// --- the Renoir hammer --------------------------------------------------------------
	static final SkillCard SEVER = new SkillCard("탄생을 끊으리라", SLOTH, 4, 3, 3,
			"못 두 개를 떨어뜨리고 빛기둥, 금빛 휘두르기, 도약해 내려찍기. 보존 +4.", 137, true, "NNN");
	static final SkillCard AGAIN = new SkillCard("거듭 내리쳐, 탄생을 끊으리라", SLOTH, 5, 4, 3,
			"주변 8블록의 적에게 못비와 휘두르기, 높이 도약해 내려찍는다. 못 3개 이상이면 피해 증가.", 180, true, "NNN");

	// --- the Black God (docs/black_god.md) ----------------------------------------------
	static final SkillCard[] BLACK_GOD = {
		new SkillCard("하아…이제 좀 돌아가 주지?", PRIDE, 3, 2, 2, "보존 +3, 호흡. 초승달 두 번.", 96, true, "NN"),
		new SkillCard("퇴근 좀 하자고.", GLOOM, 4, 3, 2, "보존 +5. 대각 광선과 시계. 못[Console] 1, 2.", 66, true, "NR"),
		new SkillCard("'심판'", SLOTH, 3, 3, 3, "보존을 모두 써서 강화. 마지막 타격은 잃은 체력만큼 강해지고 못[Breakpoint] 4.", 216, true, "RRR"),
		new SkillCard("'신 앞에 뉘우쳐라'던가?", ENVY, 5, 5, 1, "가드: 다음 공격을 막고 빛기둥으로 반격.", 60, false, "N"),
		new SkillCard("'속죄하라'", PRIDE, 3, 2, 3, "권능 15를 써서 강화. 타격마다 못[Console].", 245, true, "NNN"),
		new SkillCard("'형벌받으라'", GLOOM, 3, 2, 3, "권능 15를 써서 강화, 보존 +6. 타격마다 못[Console].", 225, true, "NNN"),
		new SkillCard("시지프 시스템을 진짜 망가트리려고?", SLOTH, 4, 2, 5, "최대 7명. 네 번째 타격에 모든 못을 터뜨리고, 마지막에 못[Exception] 9.", 233, true, "NNNNN"),
		new SkillCard("강제 퇴장", GLUTTONY, 23, 8, 1, "처형: 최대 체력 200% 고정 피해.", 260, true, "G"),
		new SkillCard("Shift+Delete", SLOTH, 23, 8, 3, "처형: 최대 체력 50%, 50%, 200% 고정 피해.", 340, true, "GGG"),
	};
}
