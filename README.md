# Index Prescript

림버스 컴퍼니에 나오는 뤼엔(Rien)의 무기 카두세우스와 지령 단말기를 추가하는 Fabric 모드입니다. 비공식 팬메이드입니다.

- 대상 버전: Minecraft 26.3 / Fabric Loader 0.20.1 / Fabric API 0.165.0+26.3
- 필요 Java: 25 이상

## 추가되는 아이템

| 아이템 | ID | 설명 |
| --- | --- | --- |
| 카두세우스 | `index_prescript:caduceus` | 사슬이 달린 황동 봉. 공격할 때마다 금속성 액체를 응고시켜 무작위 무기를 만듭니다. |
| 뤼엔의 단말기 | `index_prescript:rien_terminal` | 별표 화면이 달린 지령 단말기. 우클릭하면 무작위 지령을 받습니다. 재사용 대기시간은 5초입니다. |

두 아이템은 크리에이티브 인벤토리의 "전투" 탭에 있어요. 명령어로도 받을 수 있습니다.

```
/give @s index_prescript:caduceus
/give @s index_prescript:rien_terminal
```

## 카두세우스의 형태

공격할 때마다 아래 9가지 중 하나를 무작위로 만듭니다. 같은 형태는 연속으로 나오지 않아요. 2초 동안 공격하지 않으면 다시 봉으로 녹아내립니다. 기본 봉의 공격력은 6이고, 형태마다 추가 피해와 효과가 붙습니다.

| 형태 | 추가 피해 | 효과 |
| --- | --- | --- |
| 손도끼 | +3 | 대상에게 나약함 3초 |
| 스틸레토 | +2 | 대상에게 시듦(출혈) 3초 |
| 바스타드 소드 | +5 | — |
| 레이피어 | +2 | 자신에게 신속 II 2초 |
| 망치 | +6 | 대상에게 구속 III 2초 |
| 대검 | +7 | — |
| 랜스 | +4 | 대상을 강하게 밀쳐냄 |
| 채찍 | +2 | 대상을 자신 쪽으로 끌어당김 |
| 대낫 | +3 | 주변 3블록 안의 다른 적에게 4 피해 |

인벤토리에서는 픽셀 스프라이트로 보이고, 손에 들면 3D 모델로 보입니다.

## 빌드

GitHub Actions가 푸시할 때마다 빌드합니다. 저장소의 **Actions** 탭에서 최신 `build` 실행을 열고, **Artifacts**에 있는 `index-prescript`를 내려받으세요. 압축을 풀면 나오는 `.jar` 파일을 `mods` 폴더에 넣으면 됩니다. 이 모드와 함께 Fabric API도 설치해야 합니다.

PC에서 직접 빌드하려면 Java 25를 설치한 뒤 `gradle build`를 실행하세요.

## 텍스처와 모델 다시 만들기

텍스처와 3D 모델은 `tools/`의 스크립트로 만듭니다. Python과 Pillow가 필요해요.

```
python3 tools/textures.py src/main/resources/assets/index_prescript
python3 tools/forms.py src/main/resources/assets/index_prescript/textures/item
python3 tools/gen3d.py src/main/resources/assets/index_prescript preview.png
```
