# Tides of War — Phase 3 plan: cannons, hull damage, sinking, wrecks

Written on Opus 2026-11-21, to be executed on **Sonnet**. Codebase: Stonecutter multi-loader (9 nodes, see
`docs/MULTILOADER_PLAN.md`). Everything stays **server-side only** (vanilla clients must keep working):
no new items, blocks, entity types, keybinds or packets. Every node must stay green in CI after each step.

## 0. User decisions (don't re-ask)

| Topic | Decision |
|---|---|
| Firing | **Both**: gunner seats fire their own side; the captain can also fire (side he is looking at) |
| Targets | Other ships, mobs, terrain blocks. **Players only if PvP is turned on** in config |
| Hull at zero | **Sinks and leaves a wreck** on the seabed; the owner can salvage/repair (refloat) it |
| Rules | **Everything configurable**: ammo on/off, PvP on/off, block damage, damage numbers, etc. |

## 1. Controls (vanilla inputs only)

- **Fire = Space (jump)** while seated. `ServerPlayer.getLastClientInput().jump()`; fire on the press edge
  (false → true), not while held. Works with an empty hand (right-click in air with an empty hand sends nothing).
- **Gunner seats:** one port gunner seat and one starboard gunner seat per ship, at midship by the rail, with
  an Interaction button like the benches. Seated gunner fires their side's whole broadside.
- **Captain:** at the helm, Space fires the broadside on the side the captain is looking at (look yaw relative
  to ship heading: left half = port, right half = starboard). Only if `captainCanFire` and that side's
  gunner seat is empty (a manned side belongs to its gunner).
- **Aim:** cannons fire perpendicular to the hull. Elevation = shooter's pitch, clamped to
  [`minElevation`, `maxElevation`] (default −5°..+25°). Horizontal: shooter's look yaw clamped to
  ±`traverse` (default 30°) around perpendicular. So you aim by looking.
- Overlay (action bar) for gunners: `Port guns: READY | Hull 82% | Ammo 14` / `Reloading 2.1s`.
  Captain overlay gets `Hull 82%` appended to the existing speed line.

## 2. Config — `config/tidesofwar.json`

New `core/Config.java`: Gson, written with defaults on first start if missing, unknown keys ignored,
missing keys take defaults (so later versions can add keys). Loaded in `TidesOfWar.onServerStarted`
before `ShipManager.start`. Path: server root dir + `config/` (check the 26.x accessor —
`MinecraftServer.getServerDirectory()` — in the API dump; put it in `Compat` if it differs).
Command `/tidesofwar reload` (admin) re-reads it.

```json
{
  "cannons": {
    "requireAmmo": true,
    "ammo": [{ "item": "minecraft:gunpowder", "count": 1 }],
    "reloadSeconds": 5.0,
    "captainCanFire": true,
    "muzzleSpeed": 2.4,
    "gravity": 0.05,
    "spreadDegrees": 1.5,
    "minElevation": -5, "maxElevation": 25, "traverse": 30,
    "maxFlightTicks": 200
  },
  "damage": {
    "shipDamage": 12.0,
    "entityDamage": 10.0,
    "splashRadius": 2.5,
    "pvp": false,
    "mobs": true,
    "blockDamage": true,
    "explosionPower": 1.6,
    "shipCombat": true,
    "friendlyFire": false
  },
  "hull": {
    "sloop": 60, "brigantine": 100, "galleon": 160,
    "repairItem": "#minecraft:planks",
    "repairPerItem": 4,
    "combatLockSeconds": 30
  },
  "wrecks": {
    "lootable": "anyone",
    "raiseHullPercent": 25,
    "raiseCost": {
      "sloop":      [{ "item": "#minecraft:planks", "count": 16 }, { "item": "minecraft:iron_ingot", "count": 4 }],
      "brigantine": [{ "item": "#minecraft:planks", "count": 32 }, { "item": "minecraft:iron_ingot", "count": 8 }],
      "galleon":    [{ "item": "#minecraft:planks", "count": 64 }, { "item": "minecraft:iron_block", "count": 2 }]
    },
    "lifetimeDays": 0
  }
}
```

Meaning of the less obvious keys:
- `ammo`: cost **per cannon**. A broadside fires as many cannons as the cargo can pay for; none → "Out of ammo".
  Ammo comes from **that ship's cargo hold**. `requireAmmo: false` → free, reload time only.
- Item ids starting with `#` are item tags (`ItemStack.is(TagKey)`); otherwise an item id.
- `pvp`: whether cannonballs (direct hit and explosion) hurt **players**. `mobs`: whether they hurt non-player
  living entities. `blockDamage`: whether impacts break blocks (explosion with `explosionPower`).
- `shipCombat`: whether cannonballs damage **ships owned by other players**. `friendlyFire`: whether they damage
  the shooter's own ships (and ships with the same owner).
- `combatLockSeconds`: after a ship takes damage it can't be packed up, recalled or repaired for this long
  (stops "pack up to escape sinking").
- `lootable`: `"anyone"` or `"owner"` — who can open a wreck's cargo.
- `lifetimeDays`: 0 = wrecks stay forever; otherwise a wreck older than this (in-game days) is removed and its
  cargo dropped at its position the next time it's loaded.

## 3. Data model changes

**ShipType** — add `cannonsPerSide` (sloop 1, brigantine 3, galleon 5). Hull HP comes from config, not the enum.

**Ship** — new fields, all saved in `ships.json` (old files without them load as: full hull, AFLOAT):
- `float hull` (current), `maxHull()` from config.
- `enum State { AFLOAT, SINKING, WRECK }`, `int stateTicks`, `long wreckedAtGameTime`.
- `long lastDamagedAt` (game time) → `inCombat()`.
- `int reloadPort, reloadStarboard` (ticks remaining; not saved).
- `boolean lastJump[]` per seat for press-edge detection (not saved).
- For sinking/wreck visuals: `float roll` (degrees, saved), `double wreckY` (saved).

**Ship item** — `ShipItems.create` also stores `tidesofwar_hull` in custom_data (float); `launchFromItem` uses
it (missing → full). Lore shows `Hull 75%` if not full. Packing keeps hull. Wrecks can't be packed.

**ShipModel** — add:
- `cannons`: list of `{x, y, z, side}` (side −1 port / +1 starboard), evenly spaced along the deck between
  stern castle and bow, at the rail (`±(halfBeam+0.2)`), deck height. Visual per cannon: a short dark
  BlockDisplay barrel (e.g. `polished_blackstone` 0.35×0.35×1.0 pointing outboard) on a small `spruce_slab`
  carriage. Add them to `parts` like other blocks.
- two gunner seats (port, starboard) at midship, inboard of the cannons, appended **after** the passenger
  seats: `gunnerSeat(side)` returns the seat index. Buttons for them work like bench buttons.
- `hullHeight` for hit tests (deck top + 1.5).

Seat indexing rule (keep existing behaviour): 0 = helm, 1..n = benches, then port gunner, starboard gunner.
`firstFreePassengerSeat` must skip gunner seats.

## 4. New core classes

- `core/Config.java` — see §2. Holds static `current`.
- `core/Cannons.java` — firing logic: `tryFire(Ship, int side, ServerPlayer shooter /*nullable*/, float lookYaw, float lookPitch)`.
  Checks state AFLOAT, reload, ammo; for each cannon on that side (staggered 3 ticks apart for a rolling
  broadside — queue them in the ship), computes muzzle world position and velocity
  (direction from aim + random spread, × muzzleSpeed, + ship's own velocity vector), spawns a `Cannonball`.
  Muzzle effects: `ParticleTypes.EXPLOSION` + `CAMPFIRE_COSY_SMOKE` via `level.sendParticles`, sound
  `SoundEvents.GENERIC_EXPLODE` at pitch ~0.6 (check names per version in the API dump, wrap in `Compat`).
- `core/Cannonball.java` — **not an entity type**: plain data (pos, vel, shooter UUID, source ship id, age) plus
  one BlockDisplay visual (`coal_block`, scale 0.3, `PART_TAG` so stale-part cleanup removes leftovers).
  Owned by `ShipManager` (list per level), ticked in `tickLevel`, never saved (in flight on shutdown = gone).
  Per tick, from p0 to p1 = p0 + vel (then vel.y −= gravity, vel *= 0.99):
  1. **Ships**: for each other AFLOAT/SINKING ship in the same dimension within 40 blocks, test the segment
     against its hull (transform to ship-local coords; inside `|x| ≤ halfBeam+0.3`, `0 ≤ z ≤ length` (or
     whatever the footprint uses), `y` within waterline..hullHeight). Never hit the firing ship.
  2. **Entities**: `level.getEntities(null, segment AABB inflated 0.4)`, excluding ship parts, cannonballs,
     the shooter, spectators, items; first hit along the segment wins. Apply `Rules.canHurt(...)`.
  3. **Blocks**: `level.clip(new ClipContext(p0, p1, COLLIDER, NONE, empty))`.
  4. **Water**: if p1 is in water → splash (`ParticleTypes.SPLASH`, sound `GENERIC_SPLASH`) and remove.
  Earliest hit along the segment wins → `impact(...)`. Remove after `maxFlightTicks`.
  Move the visual with `snapTo` and interpolation duration 1.
- `core/Rules.java` — one place for who may hurt what (shooter player/null, target entity/ship, config):
  `canHurtEntity`, `canHurtShip`. Shooter null (self-test or no player) counts as the firing ship's owner.
- Impact: direct entity hit → `entityDamage`; ship hit → `ship.damage(shipDamage, shooter)`;
  then if `blockDamage` → explosion at impact point with `explosionPower`, using a custom
  `ExplosionDamageCalculator` whose entity checks call `Rules` (so PvP-off explosions don't hurt players and
  never touch ship parts); if `!blockDamage` → particles + sound + manual splash damage to entities within
  `splashRadius` (scaled by distance, filtered by `Rules`). Damage source: explosion-type source from
  `level.damageSources()` with the shooter as attacker if present (check method names in the dump; `Compat`).
- `core/Wrecks.java` (or inside Ship) — sinking, wreck interaction, raising.

## 5. Hull damage, sinking, wreck, repair

- `Ship.damage(amount, shooter)`: only when AFLOAT; `hull -= amount`; `lastDamagedAt = now`; wood-break sound and
  `ParticleTypes.BLOCK` (dark oak planks) at the hit; tell captain/gunners the new hull %. At ≤ 0 → `startSinking()`.
- Damage visuals while afloat: hull < 50% → occasional `LARGE_SMOKE` from the deck; < 25% → also `FLAME` and
  max speed × 0.6 ("taking on water").
- **Sinking** (state SINKING, 100 ticks): dismount everyone (they end up in the water, like leaving a seat
  at sea today), speed 0, find seabed: scan down from the waterline for the first block with a collision
  shape (cap 40 blocks); `wreckY` = seabed top + 0.2 (if no seabed within the cap, wreck at waterline − 6).
  Each tick: `y` moves toward `wreckY`, `roll` toward 18°, bubbles (`BUBBLE_COLUMN_UP`) + creak sounds.
  Roll is applied by rotating every part's `Transformation` about the ship's long axis (left rotation quaternion
  and rotated translation) via `Compat.setTransformation`, sent at most every 5 ticks with transformation
  interpolation 5 (`setTransformationInterpolationDuration` + `setTransformationInterpolationDelay(0)` — via
  the same reflection helper). Seats/buttons follow the new positions.
- **Wreck** (state WRECK): no movement, no ticking except visuals; saved. Helm and benches can't be used.
  Cargo barrel button works per `lootable`. Tiller button:
  - owner (or admin) right-click → chat message with the raise cost from config and whether they have it;
  - owner Shift + right-click with the full cost in inventory → consume it, state AFLOAT, hull =
    `raiseHullPercent` of max, rises to the surface over 60 ticks and rolls back to 0. Needs open water at
    the surface (`fits` at the original water level, which we save as `waterY` when it sank); if not,
    say "Not enough open water above the wreck to raise it" and consume nothing.
  - Others: "The wreck of <owner>'s <type>".
  - `/tidesofwar recall` skips wrecks (and says so if the only ships are wrecks). `/tidesofwar list` shows state.
  - Expiry per `lifetimeDays` (checked on load/tick): drop cargo as item entities at the wreck, remove ship.
- **Repair afloat**: right-click the tiller while holding the repair item (and not `inCombat()`) → consume one
  item per click (`repairPerItem` HP), message `Hull 64% → 71%`. Holding a repair item means repair instead
  of taking the helm. At full hull, take the helm as normal.
- Pack up / recall blocked while `inCombat()` ("Can't pack up during battle — Ns").

## 6. Commands (additions under `/tidesofwar`)

- `reload` (admin) — reload config.
- `repair` (admin) — fully repair the nearest ship (also refloats a wreck in place if water allows).
- `hull` — show hull/state of the ship you're on or the nearest of yours.
- `list` shows hull % and state.

## 7. Loader impact

None expected: all of Phase 3 is core code reacting to existing hooks (tick, use entity). Keep loader code
untouched. Everything version-sensitive (particle/sound constants, explode signature, damage source methods,
`hurtServer` vs `hurt`, ClipContext, ExplosionDamageCalculator method signatures, server directory) goes in
`Compat`. **Before writing it, add these classes to `.github/api-classes.txt` and read the dumps for all three
MC versions:** `net.minecraft.world.level.Level`, `net.minecraft.world.level.ExplosionDamageCalculator`,
`net.minecraft.world.level.Explosion`, `net.minecraft.world.damagesource.DamageSources`,
`net.minecraft.world.entity.Entity`, `net.minecraft.world.level.ClipContext`,
`net.minecraft.core.particles.ParticleTypes`, `net.minecraft.sounds.SoundEvents`,
`net.minecraft.server.MinecraftServer`, `net.minecraft.server.level.ServerLevel`,
`net.minecraft.tags.ItemTags`, `net.minecraft.world.entity.player.Input`.

## 8. Self-test additions (SelfTest, all loaders)

Add a second scenario after the existing one (same flat-water arena; extend it so two ships fit side by side
~20 blocks apart, broadside to each other). Use the `Cannons.tryFire(ship, side, null, …)` entry point with a
fixed aim (no player needed). Load a test config in code (`Config.current = …`) rather than from disk.
Checks:
1. Firing with `requireAmmo=true` and no gunpowder fires nothing ("out of ammo"); with 64 gunpowder in cargo it
   fires `cannonsPerSide` balls and consumes that many.
2. Reload: an immediate second `tryFire` returns false.
3. A broadside from the brigantine at the galleon reduces the galleon's hull (within 60 ticks).
4. `shipCombat=false` and different owners → no hull loss.
5. Mob test: spawn a pig (or armor stand → use a pig; armor stands aren't "mobs") on a glass pad in the line of
   fire; with `mobs=true` it takes damage; with `mobs=false` it doesn't.
6. Block damage: a glass block target is gone after an impact when `blockDamage=true`, intact when false.
7. Set the sloop's hull to 1 and hit it → SINKING, then WRECK after ≤ 120 ticks, `y` lower than before,
   riders dismounted.
8. Save/load keeps hull, state WRECK and roll. Old-format JSON (no hull fields) loads AFLOAT at full hull.
9. Raise: give the test cost directly to a raise method that takes an inventory/container → AFLOAT, hull at
   `raiseHullPercent`.
10. Packed item keeps hull; `inCombat()` blocks packing.
Keep the final marker `TIDESOFWAR SELFTEST PASS`.

## 9. Steps (CI green on all 9 nodes after each)

1. **API dump + Config**: extend api-classes.txt, read dumps; `Config` with defaults/load/save; `/tidesofwar reload`.
2. **Data**: hull/state fields, save/load with defaults, item hull, `list`/`hull` commands, combat lock. Self-test 8 (old format) + 10.
3. **Model**: cannon visuals, gunner seats + buttons, seat indexing; existing tests still pass.
4. **Firing + cannonballs**: Cannons, Cannonball, Rules, ammo, reload, effects, Space press edge for helm and gunners. Self-tests 1–4.
5. **Impacts**: entity damage, explosion with calculator, block damage toggle, splash. Self-tests 5–6.
6. **Sinking + wrecks**: sinking animation, wreck interaction, raising, repair, expiry. Self-tests 7–9.
7. **Docs + package**: README/guide (controls, config reference table, wrecks), project docs
   `claude/tides-of-war-guide.md` + a new `claude/tides-of-war-config-reference.md`; bump `mod.version` to
   `0.2.0`; rebuild the 3 zips + All-Jars zip and copy them to the user's OneDrive folder
   (`C:\Users\oscar\OneDrive\Documents\Bound by Code\Comfort Forged\Minecraft Mods\Pirate Ships`).

## 10. Watch-outs

- Players must stay able to join with vanilla clients: no registry additions, no custom packets.
- Performance: cannonball checks only against ships within 40 blocks; entity query per segment AABB only.
  Cap live cannonballs per level (e.g. 200) — drop oldest.
- Don't let explosions damage our own Display/Interaction entities (calculator: `shouldDamageEntity` false for
  ship parts) or push them (they have no gravity/physics, but double-check `ignoreExplosion`).
- Spawn protection / claims: `blockDamage` uses vanilla explosion rules; mention in docs that admins should set
  `blockDamage:false` if they use protection plugins that don't hook explosions.
- Phase 4 (upgrades) will scale cannon damage/reload, hull max, speed and turning: route all of these through a
  `ShipStats` helper (type + config now; + upgrades later) instead of reading config directly in ship code.

## 11. Model workflow

Execute on **Sonnet**. Switch back to **Opus** (tell the user) if: firing via `jump` turns out not to reach the
server while riding a Display entity (then redesign controls), the roll transform looks wrong and needs a
different approach, or for planning Phase 4.
