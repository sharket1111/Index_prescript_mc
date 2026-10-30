# Index Prescript

림버스 컴퍼니에 나오는 뤼엔의 무기와 단말기를 추가하는 Fabric 모드입니다. 비공식 팬메이드입니다.

- 대상 버전: Minecraft 26.3 / Fabric Loader / Fabric API
- 필요 Java: 25 이상

## 추가되는 아이템

| 아이템 | ID | 설명 |
| --- | --- | --- |
| 뤼엔의 무기 | `index_prescript:ruen_weapon` | 네더라이트급 검. 바닐라 검보다 조금 빠르게 공격합니다. |
| 뤼엔의 단말기 | `index_prescript:ruen_terminal` | 우클릭하면 무작위 지령을 받습니다. 재사용 대기시간은 5초입니다. |

두 아이템은 크리에이티브 인벤토리의 "전투" 탭에 있어요. 명령어로도 받을 수 있습니다.

```
/give @s index_prescript:ruen_weapon
/give @s index_prescript:ruen_terminal
```

## 빌드

GitHub Actions가 푸시할 때마다 빌드합니다. 저장소의 **Actions** 탭에서 최신 `build` 실행을 열고, **Artifacts**에 있는 `index-prescript`를 내려받으세요. 압축을 풀면 나오는 `.jar` 파일을 `mods` 폴더에 넣으면 됩니다. 이 모드와 함께 Fabric API도 설치해야 합니다.

PC에서 직접 빌드하려면 Java 25를 설치한 뒤 `gradle build`를 실행하세요.
