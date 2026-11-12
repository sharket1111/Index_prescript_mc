package io.github.sharket1111.indexprescript.paper;

import java.util.ArrayList;
import java.util.List;

/**
 * Mounting Adversities, after the original's EXTREME MIRROR: from floor 10 on,
 * every cleared floor offers that floor's list; the party takes as many as it
 * likes (or none). Each gives Adversity Score, paid as Starlight (15 a point)
 * when the run ends. The two that change E.G.O resource costs and the one that
 * raises enemy Sanity have nothing to act on here and are left out.
 */
enum MdAdversity {
	LEVEL_10(10, "레벨 상승", "모든 적 레벨 +3", 1, "level", 3),
	FRAIL_10(10, "허약", "아군 최대 체력 -10%", 1, "frail", 10),
	FIRE_1(10, "불의 낙인 I", "턴 끝마다 아군 모두 고정 피해 5 (매 턴 +3, 최대 20)", 2, "fire", 1),
	INFLATION_1(10, "인플레이션 I", "상점 비용 2배 (판매 제외)", 2, "inflation", 1),
	REDISTRIBUTION(10, "힘의 재분배", "적이 죽으면 남은 적의 공격 레벨 +3 (다음 턴까지)", 1, "redistribute", 1),

	LEVEL_11(11, "레벨 상승", "모든 적 레벨 +3", 1, "level", 3),
	FRAIL_11(11, "허약", "아군 최대 체력 -10%", 1, "frail", 10),
	MENTAL_1(11, "정신 이상 I", "턴 끝마다 아군 모두 정신력 -3", 2, "mental", 3),
	NERVE_1(11, "신경 가속 I", "적이 더 자주 행동해요 (행동 간격 -15%)", 2, "nerve", 1),
	FATIGUE_1(11, "정신력 피로 I", "전투 중 정신력이 35를 넘지 못해요", 3, "fatigue", 35),

	LEVEL_12(12, "레벨 상승", "모든 적 레벨 +3", 1, "level", 3),
	FRAIL_12(12, "허약", "아군 최대 체력 -10%", 1, "frail", 10),
	BARRIER(12, "진동 장벽", "적의 첫 흐트러짐 구간이 없어져요", 2, "barrier", 1),
	INFLATION_2(12, "인플레이션 II", "상점 비용 3배 (판매 제외)", 3, "inflation", 2),
	SHIELD(12, "보호막 생성", "모든 적이 보호막 3000을 가져요 (최대 체력의 절반까지)", 3, "shield", 1),

	LEVEL_13(13, "레벨 상승", "모든 적 레벨 +3", 1, "level", 3),
	FRAIL_13(13, "허약", "아군 최대 체력 -10%", 1, "frail", 10),
	VITALITY(13, "활력 증강", "적 최대 체력 +30%", 2, "vitality", 30),
	CRUELTY(13, "잔혹", "적 스킬의 코인 위력 +2 (마이너스 코인은 -2)", 3, "cruelty", 2),
	FIRE_2(13, "불의 낙인 II", "턴 끝마다 아군 모두 고정 피해 10 (매 턴 +5, 최대 30)", 3, "fire", 2),
	PROLIFERATION(13, "균열 증식", "적이 거는 화상·출혈·진동·파열·침잠이 2배", 3, "proliferate", 1),

	LEVEL_14(14, "레벨 상승", "모든 적 레벨 +3", 1, "level", 3),
	FRAIL_14(14, "허약", "아군 최대 체력 -10%", 1, "frail", 10),
	MENTAL_2(14, "정신 이상 II", "턴 끝마다 아군 모두 정신력 -5", 3, "mental", 5),
	NERVE_2(14, "신경 가속 II", "적이 훨씬 더 자주 행동해요 (행동 간격 -30%)", 3, "nerve", 2),
	FATIGUE_2(14, "정신력 피로 II", "전투 중 정신력이 30을 넘지 못해요", 5, "fatigue", 30),
	STATUS(14, "상태 부여", "매 턴 아군 모두에게 무작위 키워드 위력 5", 1, "status", 1);

	/** Starlight for each point of Adversity Score when the run ends. */
	static final int STARLIGHT_PER_SCORE = 15;

	final int floor;
	final String ko;
	final String text;
	final int score;
	final String kind;
	final int value;

	MdAdversity(int floor, String ko, String text, int score, String kind, int value) {
		this.floor = floor;
		this.ko = ko;
		this.text = text;
		this.score = score;
		this.kind = kind;
		this.value = value;
	}

	/** The list offered after clearing {@code floor}. */
	static List<MdAdversity> of(int floor) {
		List<MdAdversity> out = new ArrayList<>();
		for (MdAdversity a : values()) {
			if (a.floor == floor) {
				out.add(a);
			}
		}
		return out;
	}
}
