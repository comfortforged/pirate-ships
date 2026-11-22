# Tides of War — Phase 4 plan: upgrades and flight

Target version **0.3.0**. All 9 nodes (26.1/26.2/26.3 × Fabric/NeoForge/Forge) green after every step.
Same rules as before: server-side only, vanilla clients, no registry additions, no custom packets.

## 0. User decisions (don't re-ask)

- **Getting upgrades:** craft an upgrade item, then hold it and right-click the ship's tiller to apply it.
- **Tiers:** 3 per part (I, II, III).
- **Flight:** a "Flight Core" upgrade. **Ctrl (sprint)** at the helm toggles flying; the ship climbs and dives
  with the captain's look pitch. No fuel by default.
- **Persistence:** upgrades always stay with the ship: saved in `ships.json`, kept on the packed item,
  kept through sinking and raising.

## 1. Upgrade parts and default bonuses

| Part | Base item (with custom data, name, glint) | I | II | III |
|---|---|---|---|---|
| Hull plating | `minecraft:prismarine_shard` | +25% max hull | +50% | +100% |
| Sails | `minecraft:paper` | ×1.15 top speed and acceleration | ×1.30 | ×1.50 |
| Rudder (tiller) | `minecraft:prismarine_crystals` | ×1.20 turn rate | ×1.40 | ×1.70 |
| Cannons | `minecraft:flint` | reload ×0.80, damage ×1.20 | ×0.65, ×1.40 | ×0.50, ×1.70 |
| Flight Core | `minecraft:heart_of_the_sea` | flying (one tier) | – | – |

Every number is configurable (§4). Base items were chosen because right-clicking them does nothing in vanilla
(no placing, no throwing). Known small risk: a vanilla recipe would accept e.g. the custom flint as plain flint;
the item name and lore make clear what it is. Mention in docs.

**Recipes** (data-driven JSON like the ship recipes, result with `custom_data` `{"tidesofwar_upgrade": "hull", "tidesofwar_tier": 1}`,
`item_name`, `lore`, `enchantment_glint_override`, `rarity`). Tier material: I = iron, II = diamond, III = netherite.
Vanilla shaped recipes can't require the previous tier's item (ingredients can't match custom data), so each tier
is crafted from raw materials and the *ship* must already have the previous tier to apply it.
M = tier material (iron ingot / diamond / netherite ingot).
- Hull plating: `M P M / P M P / M P M` (P = `#minecraft:planks`).
- Sails: `W W W / W M W / W W W` (W = `#minecraft:wool`).
- Rudder: `_ M _ / L M L / _ L _` (L = `#minecraft:logs`).
- Cannons: `M G M / G M G / M G M` (G = gunpowder).
- Flight Core: `P E P / E Y E / P E P` (P = phantom membrane, E = ender pearl, Y = elytra). Expensive on purpose.
Exact patterns are the executor's call within these materials; list them in the README.

## 2. Applying an upgrade

Tiller right-click priority while holding an item (afloat ship only):
1. Repair item and hull below max → repair (Phase 3, unchanged).
2. Upgrade item → try to apply:
   - owner or admin only ("Only <owner> can upgrade this ship.");
   - not in combat (combat lock message);
   - must be exactly the next tier ("This ship needs Sails II first." / "Sails are already at tier III.");
   - Flight Core: refused if already installed, or if `flight.enabled` is false;
   - consume one item (not in creative), set the level, message `Sails upgraded to tier II: top speed 10.4 blocks/s`,
     sound `SoundEvents.SMITHING_TABLE_USE` (check name in dump; fallback anvil use), respawn the visuals (§5).
3. Otherwise → take the helm (unchanged).
Wrecks and sinking ships refuse upgrades. No removing/downgrading in this phase.

## 3. Data model

- `core/Upgrades.java`: a small mutable class `{int hull, sails, rudder, cannons; boolean flight}` with
  `copy()`, `toJson()/fromJson()`, `toTag()/fromTag()` (for the item), `describe()` (lore lines), and
  `enum Part { HULL, SAILS, RUDDER, CANNONS, FLIGHT }` with display names, base item, max tier.
- `Ship.upgrades` (never null). Saved in `ships.json` under `"upgrades"`; missing = all zero (old saves load fine).
- Flight state on `Ship`: `boolean flying`, `boolean onGround` (resting on land, not water). Saved too, so a ship
  parked on a hilltop stays there after a restart.
- **Item:** `ShipItems.create(type, hull, upgrades)` writes `tidesofwar_upgrades` (compound) and adds lore lines
  (`Hull III, Sails II, Flight Core`). `upgradesOf(stack)`. `packUp` stores, `launchFromItem` restores.
  `hullOf` and the "store hull only when below max" rule use the *upgraded* max hull.
- `UpgradeItems.java`: `create(part, tier)`, `partOf(stack)`, `tierOf(stack)`; used by recipes' identical output,
  by `/tidesofwar give <player> upgrade <part> <tier>` (admin) and by the self-test.

## 4. Config additions — `upgrades` and `flight` sections

```json
"upgrades": {
  "hullBonusPercent":   [25, 50, 100],
  "sailsSpeedMultiplier": [1.15, 1.30, 1.50],
  "rudderTurnMultiplier": [1.20, 1.40, 1.70],
  "cannonReloadMultiplier": [0.80, 0.65, 0.50],
  "cannonDamageMultiplier": [1.20, 1.40, 1.70]
},
"flight": {
  "enabled": true,
  "speedMultiplier": 1.0,
  "climbRate": 0.25,
  "maxHeightAboveGround": 48,
  "maxY": 300,
  "allowedDimensions": ["minecraft:overworld", "minecraft:the_end"],
  "fuel": []
}
```
`fuel` is a list of `Cost` (empty = free). If not empty, one fuel set is taken from cargo every `fuelSeconds`
(add key, default 30) while airborne; out of fuel → the ship glides down. (Cheap to add now since `Cost` and
`ItemMatch` exist; default stays free as the user chose.) `fillMissing()` must handle the new sections and
arrays of the wrong length (pad with defaults).

## 5. Stats and looks

- **`ShipStats`** becomes the only source of tuned numbers: `maxHull(ship)`, `maxSpeed(ship)`, `acceleration(ship)`,
  `turnRate(ship)`, `reloadTicks(ship)`, `shipDamage(ship)` (attacker's cannons), `entityDamage(ship)`,
  `muzzleSpeed(ship)`. Replace every direct read of `type.maxSpeed`, `type.turnRate`, `type.acceleration`,
  `Config.maxHull` in `Ship`, `ShipItems`, `Cannons`, `Impacts`, commands and the overlay.
- Applying a hull upgrade raises current hull by the same amount the max went up (repair what you paid for).
- **Looks** (so others can see a ship is upgraded): `ShipModel.of(type, Look)` where `Look` is a record derived from
  upgrades (`hullTier`, `sailsTier`, `flight`); cache by `(type, look)` (at most 3×4×4×2 models).
  - Hull: a thin rail trim strip: I = `cut_copper`, II = `prismarine_bricks`, III = `gold_block` (scaled 0.15 high).
  - Sails: I = white (as now), II = light gray wool, III = black wool with white trim (Compat wool helpers already exist).
  - Flight Core: two `end_rod`-like thin glowing pieces under the hull (`sea_lantern` slabs scaled 0.2) and a
    `glowstone` keel strip; while flying, `ParticleTypes.END_ROD` drips from the keel every 10 ticks.
  - After an upgrade: `despawn` + `spawn` the ship so the new parts appear (riders are re-seated by index).

## 6. Flight

**Controls (captain only):**
- **Ctrl (sprint), press edge** → toggle flight (`getLastClientInput().sprint()`, same edge logic as Space).
  Requires Flight Core, `flight.enabled`, dimension in `allowedDimensions`, AFLOAT, not rising.
- While flying: W/S/A/D as on water (speed × `flight.speedMultiplier`). Vertical speed =
  `-sin(pitch) × climbRate × (0.3 + 0.7 × |speed|/maxSpeed)` so you must be moving to climb fast; looking level holds height.
- Overlay: `✈ Flying | Altitude 23 | Speed … | Hull …% | Ctrl lands`.
- **Fallback if Ctrl doesn't reach the server while seated** (the same open question as Space): `/tidesofwar fly`
  (captain only, toggles). Build it in step 3 regardless, so players always have a way.

**Physics / collision** (new `Ship.clearAt(level, x, y, z, yaw)`):
- Airborne ships ignore the water requirement. Instead, the hull box (footprint points × heights y−0.9, y, y+1.1, plus
  a mast-top point per mast) must have no collision shapes. Check moves axis by axis (horizontal move, then turn,
  then vertical) so a ship slides along a cliff instead of sticking.
- Height limits: `y ≤ maxY`, and `y − groundY ≤ maxHeightAboveGround` (groundY = highest motion-blocking block under
  the centre, found with `level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)`; check heightmap API in dump).
- Leaving `allowedDimensions` can't happen mid-flight (no portals for display entities), but a reload that removes the
  dimension makes the ship land (treat like toggling off).
- **Toggle off / landing:** the ship descends at `climbRate` until either
  - its waterline reaches a water surface → snap to `water.getY() + 1 − DRAFT`, `flying = false`, normal sailing; or
  - its keel hits ground → `onGround = true`, `flying = false`. On ground: no sailing (W/S do nothing, overlay says
    "Beached. Ctrl to take off."), cannons still work, pack-up works. Taking off again needs Flight Core.
- A ship with `onGround` and no Flight Core (can only happen via admin edits) can be packed up.
- **Sinking in the air:** zero hull while flying → riders get **Slow Falling** for 15 s before being ejected
  (`MobEffects.SLOW_FALLING`; Holder vs plain field differs by version → `Compat.slowFalling()`), then the ship falls
  (accelerating, cap 1.5 blocks/tick) until it hits water (then normal sinking to the seabed) or ground
  (then it becomes a wreck on the ground at a roll, `wreckY` = ground). Raising a wreck that lies on land: allowed
  only if the ship has a Flight Core (it rises 3 blocks and lands → `onGround`); otherwise "Too far from water to raise".
- **Pack-up / recall:** allowed while flying only if not in combat; the ship just disappears into the item as now.
  Launching from an item always needs water (unchanged).
- Saving while flying: `flying`/`onGround`/`y` saved; after restart a flying ship is still in the air and keeps flying
  (if no captain it hovers: speed decays to 0, vertical speed 0).
- **Cannonballs vs flying ships:** hit test already uses the ship's `y`, works unchanged. Hits on a flying ship's
  underside are fine.
- Performance: the collision check is ~(footprint points × 3 heights + masts) block lookups per moving tick;
  footprint already exists. Only run when the ship actually moves.

## 7. Commands

- `/tidesofwar give <player> upgrade <hull|sails|rudder|cannons|flight> [tier]` (admin).
- `/tidesofwar upgrades` (anyone): shows the upgrades of the ship you're on / your nearest ship, and its stats.
- `/tidesofwar fly` (captain): toggle flight (fallback control).
- `/tidesofwar list` adds a short upgrade summary (`H3 S2 R0 C1 F`).

## 8. Self-test additions

New scenario after the wreck tests (same arena; add a 12-high glass ceiling over part of the pool and a 3-block
glass "island" outside the water for landing):
1. Upgrade items: `UpgradeItems.create(SAILS, 2)` round-trips part and tier; a plain paper isn't an upgrade.
2. Applying out of order fails (Sails II on a stock ship), then I → II works; each tier raises
   `ShipStats.maxSpeed` (I > stock, II > I). Rudder raises `turnRate`. Cannons lower `reloadTicks` and raise `shipDamage`.
3. Hull upgrade: max hull goes up 25% and current hull goes up by the same amount.
4. Persistence: save/load keeps upgrades; packed item keeps upgrades and hull above the stock max; relaunch keeps
   them; old ships.json without `upgrades` loads stock.
5. Sinking and raising keep upgrades.
6. Flight: refuse flight without the core; with core, a sprint press edge (via `testInput`) sets `flying`;
   with pitch up and forward input the ship rises > 4 blocks in 100 ticks; it stops under the glass ceiling
   (never overlaps a block); `maxHeightAboveGround` respected.
7. Landing: toggle off over water → back at the waterline, `flying=false`, sails normally (moves forward).
   Toggle off over the island → `onGround`, forward input doesn't move it.
8. Sinking in the air: hull to 0 while flying → falls, ends as WRECK at the water bed, riders had Slow Falling.
9. Dimension rule: with the overworld removed from `allowedDimensions`, flight toggle is refused.
Keep `TIDESOFWAR SELFTEST PASS`.
For 6–8 the test needs a fake look pitch: add `Ship.testPitch` (used instead of the captain's pitch when `testInput` is set).

## 9. Steps (CI green on all 9 nodes after each)

1. **Data + items + recipes:** `Upgrades`, `UpgradeItems`, recipes JSON (15 files: 4 parts × 3 tiers + flight core),
   save/load, item storage, `give … upgrade`, `upgrades` command. Self-tests 1, 4.
2. **Stats + applying + looks:** `ShipStats` everywhere, tiller apply flow, config `upgrades` section, model `Look`,
   respawn on upgrade. Self-tests 2, 3, 5.
3. **Flight core movement:** `flight` config, Ctrl toggle + `/tidesofwar fly`, airborne movement and collision,
   height limits, overlay, flight particles. Self-tests 6, 9.
4. **Landing, beaching, air sinking, fuel:** landing on water/ground, `onGround` rules, falling wreck, Slow Falling,
   raising on land, optional fuel. Self-tests 7, 8.
5. **Docs + package:** README/guide (upgrade table, recipes, flight controls), config reference additions
   (`docs/CONFIG.md` + project doc `claude/tides-of-war-config-reference.md`), project guide doc,
   `mod.version` 0.3.0 (also the manifest version in `.github/workflows/build.yml`), rebuild the 4 zips + guides and
   copy to the OneDrive folder (`C:\Users\oscar\OneDrive\Documents\Bound by Code\Comfort Forged\Minecraft Mods\Pirate Ships`).

## 10. Watch-outs

- **Unverified controls:** Space (fire, Phase 3) and Ctrl (flight) both rely on `getLastClientInput()` while riding a
  Display entity. Nobody has played it yet. Ask the user to test Space in game before step 3 if possible; the
  `/tidesofwar fly` fallback means flight is usable either way.
- Recipe result components: ship recipes already use `custom_data`/`item_name`/`lore` in all 9 nodes, so reuse that exact
  JSON shape. Check the self-test confirms every new recipe loads (extend the existing recipe check loop).
- `ShipModel` cache key changes: anything that uses `ShipModel.of(type)` (self-test counts, seat indexes, cannons)
  must use `ship.model()`. Seat and cannon indexes must not change with looks (looks only add parts).
- Changing max hull via config or upgrades must clamp loaded hull to the new max, as now.
- Version-sensitive names (Heightmap API, `MobEffects.SLOW_FALLING`, `SMITHING_TABLE_USE`, `END_ROD`): add the classes
  to `.github/api-classes.txt` first and read the dumps (`net.minecraft.world.level.levelgen.Heightmap`,
  `net.minecraft.world.effect.MobEffects`, `net.minecraft.world.effect.MobEffectInstance`); wrap differences in `Compat`.
- Flying ships over claims: they don't break blocks, so no protection issue; cannons from the air follow `blockDamage`.

## 11. Model workflow

Plan done on **Opus**. Execute on **Sonnet**, starting with step 1. Switch back to **Opus** (tell the user) if:
Ctrl/Space input turns out not to reach the server while seated and controls need a redesign, the airborne
collision approach causes ships to get stuck in ways that need a different design, or for planning the next phase
(economy / buying ships, Phase 2 ship types).
