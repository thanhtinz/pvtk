# PVTK Assets

The complete, original **Phong Vân** game assets, preserved from the legacy
client and shipped with the project so it builds and runs as a complete game.
Paths match the originals, so both the server (content tables) and the graphical
clients (sprites/maps/UI) load them exactly as the original game did
(`vn.pvtk.protocol.data.AssetPaths`).

## Layout

| Folder / file | Count | Format | Purpose |
|---------------|-------|--------|---------|
| `ani/`     | ~1700 | `.pd` packs + `.png` | character / effect animations |
| `common/`  | ~690  | `.fr` + `.png` | shared sprites: icons, effects, fonts |
| `map/`     | ~1450 | `.m` `.n` `.pd` `.pn` `.png` | world maps: tiles + collision |
| `mission/` | ~365  | `.mss` | mission / quest definitions |
| `ui/`      | ~152  | `.ui` | UI layout descriptors |
| `*.txt`    | 13    | UTF-16LE TSV | content database (see below) |

## Content database (`*.txt`)

Tab-separated, UTF-16LE, with a header row. Loaded by
`vn.pvtk.protocol.data.DataTable` and served by `vn.pvtk.server.data.GameData`.

| File | Rows | Contents |
|------|------|----------|
| `item.txt` | ~1937 | item database (weapons, armor, consumables, stats) |
| `skill.txt` | ~504 | skill definitions |
| `shop.txt` | ~235 | shop inventories |
| `monster.txt` | ~49 | monster stats |
| `monsterGroup.txt` / `monsterAI.txt` / `monster_reward.txt` | — | spawn groups, AI, drops |
| `player.txt` / `player_skill.txt` / `job_setting.txt` | — | starting classes & skills |
| `skill_shop.txt`, `playericon_set.txt`, `bag.txt`, `drop_time.txt` | — | misc tuning |

### `item.txt` columns
`id, name, icon, color, bagIcon, info, type, grade, reqLv, reqStr, reqCon,
reqAgi, reqIlt, reqWis, atkType, atk_time, atkMin, atkMax, def_str, def_agi,
def_mag, hitrate, round, area, power1, powerValue1, ... price, autoBinding,
stackNum, attachCount, itemSet, ..., skills_id, skills_lv, icon1, icon2, icon3`

## Notes

* Asset bytes are unmodified from the original client. No proprietary code is
  included — only media/data resources.
* The graphical clients resolve these via `Gdx.files.internal(...)`; the build
  is configured to run from the repo root (desktop) and to bundle `../assets`
  into the APK (Android) / IPA (iOS).
