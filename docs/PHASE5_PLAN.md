# Tides of War: Phase 5 plan: walkable, solid ships

Target version **0.4.0**. All 9 nodes (26.1/26.2/26.3 × Fabric/NeoForge/Forge) green after every step.

## 1. Decisions (user, 2026-11-22)
- **The mod is now required on players' PCs too** (the CurseForge profile already installs it). Players without it can
  still join and ride seats, and get a one-time message telling them to install it.
- **Walk anytime**: players standing on the deck are carried along while the ship sails, turns, climbs and dives.
- **Solid parts**: deck floor (main deck and raised stern deck), rails, masts, and the hull sides from outside.
- Fixes the user's notes: you can stand on the ship without a seat, and leaving the tiller puts you on the deck
  instead of dropping you through it.

## 2. How it works (overview)
The ship's look stays exactly as it is (display entities). On top of that we add **invisible collision**:

- Each ship has a list of **local boxes** (ship coordinates: +z bow, +x port, y = 0 waterline) built from `ShipModel`.
- A **common mixin on `Entity.collide(Vec3)`** (runs on both sides) clips movement against those boxes after the
  vanilla block collision. Rotation is handled by turning the movement into ship coordinates, colliding against the
  ship's axis-aligned local boxes there (`Entity.collideWithShapes`), and turning the result back.
  Because the clip happens inside `collide`, vanilla `move()` sets `onGround`, fall distance and step sounds normally.
- **Who collides where:**
  - On the **client**: only the local player (the player's PC is in charge of its own movement, so walking is smooth).
  - On the **server**: every entity **except players** (mobs, items, armor stands).
  - Players are left out on the server on purpose. The server's "moved wrongly" check re-runs each move with server
    collision, and the server's ship position runs 1 to 2 ticks ahead of what the player sees. Clipping players on the
    server too would make them rubber-band.
- **Carrying.** Each tick, anyone whose feet are inside a ship's **deck zone** (the hull outline, from deck height to
  2.5 blocks above it) is moved by that tick's ship motion: rotated by the change in yaw around the ship's origin, then
  shifted by the change in position. The player's view turns with the ship. The deck zone covers jumping, so a jump on a
  moving ship doesn't leave the player behind.
  - Local player: done on the client, at the start of the client tick.
  - Mobs and items: done on the server in `Ship.tick` after the ship moves.
- **Where the client gets the ship from:** the server sends a `ship_shape` packet (ship key, anchor entity id, boxes)
  when a ship comes into range or changes, and `ship_remove` when it leaves.
  - The **pose comes from the anchor entity**: any hull `BlockDisplay`, since every part sits at the ship origin with
    `yRot = yaw` and the bob already applied.
  - The client reads that entity's interpolated position and yaw, so the collision lines up exactly with what the
    player sees, and no per-tick pose packets are needed.
- **Only solid while `AFLOAT` and not rising**, which includes flying and beached. Sinking ships, wrecks and rising ships
  are not solid, because of the roll. Collision ignores the small gunfire heel.

## 3. Collision boxes (`ShipCollision.boxes(type, look)`, cached like `ShipModel`)
| Box | Local extent |
|---|---|
| Hull, per merged slice | x ±w, y −0.9 → deck top 0.62, z slice |
| Rails, per slice, both sides | 0.4 wide at the hull edge, y 0.62 → **1.62** (taller than the look, so you can't walk off but can still jump over: jump height is 1.25) |
| Transom and bow plate | as in the model, up to 1.62 |
| Quarterdeck | x ±wStern, y 0.62 → 2.12, z stern → stern + castleLen |
| Quarterdeck rails | sides and back edge, 2.12 → 3.12 |
| Steps up to the quarterdeck | 2 collision-only steps of 0.5 at its front edge, centred, 2 wide (visible steps can come later) |
| Masts | 0.5 × 0.5, from deck to mast height |

Not solid: sails, bowsprit, cannons, lanterns, seats and buttons (they stay clickable). Flight Core pods aren't solid either.

## 4. Boarding and leaving
- **Dismount** (leaving the tiller, a bench or a gun seat): put the player at the seat position plus 0.05 up, which is on
  the deck. Players without the client mod are moved to the nearest shore or water spot instead, as now.
- **Boarding ladders:** an `Interaction` button on each hull side amidships at the waterline. Right-clicking it moves you
  onto the deck at that side. Swimmers can't climb the hull, because the hull is solid from outside.
- Sitting down still works by right-clicking a seat.

## 5. Server-side fixes
- **Floating kick:** standing on a flying ship looks like flying to the vanilla anti-cheat. Use an accessor on
  `ServerGamePacketListenerImpl` to reset `aboveGroundTickCount` for players whose feet are in a deck zone.
  As a last resort, the README tells admins to set `allow-flight=true`.
- **Air sinking:** players standing in a deck zone also get Slow Falling, not only seated riders.
- **Mob and item collision and carrying** on the server, as described in section 2.

## 6. Networking and loaders
- Payloads use vanilla `CustomPacketPayload` + `StreamCodec`, defined in common code.
  - `ShipShapePayload(int key, int anchorId, float[] boxes)` and `ShipRemovePayload(int key)`, server to client.
  - `HelloPayload(String version)`, client to server, sent on join.
- A `Net` interface in core does the sending and receiving. Each platform class implements it:
  - **Fabric:** `PayloadTypeRegistry` + `ServerPlayNetworking` / `ClientPlayNetworking`. The `client` entrypoint goes in
    `fabric.mod.json`.
  - **NeoForge:** `RegisterPayloadHandlersEvent`, registrar `.optional()` so vanilla clients can still connect. Client
    setup goes in a `Dist.CLIENT` class.
  - **Forge:** the Forge `ChannelBuilder` payload channel, optional. Client setup is guarded by `FMLEnvironment.dist`.
- **Client code lives in `core/client/` and `platform/client/`**, and is only referenced from client entrypoints, so
  dedicated servers never load it.
- **Mixins:** `tidesofwar.mixins.json`, with `common` and `client` lists. Since 26.x is unobfuscated, no refmap is
  needed. It is declared in `fabric.mod.json`, in `neoforge.mods.toml` (`[[mixins]]`), and for Forge in `mods.toml`
  `[[mixins]]` or the jar's `MixinConfigs` manifest entry (whichever ForgeGradle 7 supports; step 1 finds out).
- **Shape tracker:** on the server, every 10 ticks per player, diff the solid ships in the player's dimension within 160
  blocks against what was sent. Send new or changed shapes (a respawn after an upgrade gives a new anchor id) and send
  removes. Clear the record on logout.

## 7. Self-test additions (server)
The client half can't run in CI, so the user tests walking in game. Steps 5 to 6 are the risky part.
1. Mixin applied: the common mixin sets a static flag the first time it runs; the server checks it.
2. Shapes: each type has boxes, the top of the deck box is at 0.62, the rails reach 1.62, and the quarterdeck is at 2.12.
3. Math: local ↔ world round trip at yaw 0, 90 and 37 degrees; a move into a rail is clipped; a downward move onto the
   deck stops at deck height.
4. An armor stand dropped above a stopped sloop's deck lands on the deck, not in the water.
5. An item resting on the deck is carried: after 40 ticks of sailing it is still on the deck and has moved with the ship.
6. Wrecks and sinking ships aren't solid: an armor stand above a wreck falls through.
7. Payload codec round trip for all three payloads.
8. Dismount position: leaving seat 0 gives a point above the quarterdeck surface.
9. Ladder buttons exist on both sides of a spawned ship.

## 8. Steps (CI green on all 9 nodes after each)
1. **Scaffolding.** Add the `api-classes.txt` entries (Entity, `ServerGamePacketListenerImpl`, `CustomPacketPayload`,
   `StreamCodec`, `RegistryFriendlyByteBuf`, the Fabric networking classes, NeoForge `RegisterPayloadHandlersEvent` /
   `PayloadRegistrar`, the Forge `ChannelBuilder` / `SimpleChannel` / payload channel classes, and the mixin bootstrap
   classes per loader). Wire the mixin config into all 3 loaders with one no-op common mixin and an empty client mixin,
   and add client entrypoints that just log. Self-test 1.
2. **Shapes and math.** `ShipCollision` (boxes and cache), `ShipFrame` (local/world transforms, `clip`), and
   `ShipColliders` (`near(level, aabb)`; the server version backed by `ShipManager`). Self-tests 2 and 3.
3. **Server collision and carrying.** Fill in the common mixin (non-players on the server) and add carrying for mobs and
   items in `Ship.tick`. Self-tests 4 to 6.
4. **Networking.** Payloads, `Net` and the 3 loader implementations, the shape tracker, and the hello handshake with the
   missing-mod message. Self-test 7.
5. **Client.** `ClientShips` (receives shapes and gets the pose from the anchor entity), the local-player branch of the
   collision mixin, and carrying at the start of the client tick (including view yaw). Jars build on all 9 nodes;
   behaviour is checked by the user in game.
6. **Server fixes and boarding.** Floating-kick accessor, dismount onto the deck, ladders, Slow Falling for players on
   deck. Self-tests 8 and 9.
7. **Docs and package.** README and guide (client mod now required, walking, ladders, the `allow-flight` note),
   `mod.version` 0.4.0 in `stonecutter.properties.toml` and `build.yml`, rebuild the zips, copy them to OneDrive,
   update the project docs.

## 9. Risks
- **The client can't be tested in CI.** The user has to test walking, carrying and turning in game after step 5. Most
  likely problems:
  - A little sliding while the ship turns, because collision uses the interpolated pose but carrying uses tick deltas.
    The fix is to carry using the same interpolated anchor.
  - A jolt when a player steps onto a moving deck.
- **Forge networking and mixins on ForgeGradle 7 / 26.x** are the least known. Step 1 finds out through the API dump
  before any real code depends on them. If Forge mixins don't work at all, switch to Opus: one option is to ship walkable
  decks on Fabric and NeoForge first.
- **Anti-cheat:** fast ships (9 blocks/s) carrying players are well under the "moved too quickly" limit. Flying ships rely
  on the floating-kick reset.
- **Players with an older client jar:** the hello payload carries the version; if it doesn't match, tell them to update.

## 10. Not in this phase
Walking below deck, solid sails, ship-to-ship collision (ships bumping each other), visible steps and ladders as blocks.

## Model note
Plan done on **Opus**. Execute on **Sonnet**, starting with step 1. Switch back to **Opus** (tell the user) if Forge
mixins or networking turn out not to be possible, or if in-game testing after step 5 shows sliding or jitter that the
simple fixes above don't solve.
