# Tides of War — multi-loader / multi-version plan

Goal: one codebase (this repo, `comfortforged/pirate-ships`) that builds **9 jars**:
Fabric, NeoForge and Forge for Minecraft **26.1, 26.2 and 26.3**, using **Stonecutter**.

> **1.21.1 support is dropped for now** (user decision, 2026-11-21). Don't add 1.21.1 nodes. The 1.21.1
> notes in §1/§6 are kept only for a possible later port.
>
> **Repo rename:** the user is renaming the repo to `comfortforged/tides-of-war`. After that, attach it
> under the new name (`add_repo`), set `git remote set-url origin https://github.com/comfortforged/tides-of-war`,
> and use the new name in every `gh api` / clone command below (the old URL redirects for git, not reliably for the API).

Gameplay must stay exactly as it is today (see README). Your own server keeps running Fabric 26.3.

Status when this plan was written: Phase 1 works on Fabric 26.3 only (single-version Loom project,
package `io.github.comfortforged.pirateships`, CI self-test passing).

---

## 0. Decisions already made (don't re-ask)

| Item | Value |
|---|---|
| Public name | **Tides of War** |
| Mod ID | `tidesofwar` |
| Java package | `io.github.comfortforged.tidesofwar` |
| License | **All Rights Reserved** (replace the MIT LICENSE file; set `"license": "ARR"` / `license="All Rights Reserved"`) |
| Author | comfortforged |
| Targets | 26.1, 26.2, 26.3 × fabric, neoforge, forge (1.21.1 dropped for now) |
| Tooling | Stonecutter 0.10 + loom-back-compat (Fabric), ModDevGradle (NeoForge), ForgeGradle 7 (Forge) |
| Mod is server-side only | Players don't need it; it must still load harmlessly on clients |

## 1. Pinned versions (from maven metadata on 2026-11-21)

| MC | Fabric API | NeoForge | Forge | Java |
|---|---|---|---|---|
| 1.21.1 | `0.116.18+1.21.1` | `21.1.267` | `1.21.1-52.1.21` | 21 |
| 26.1 (build against 26.1.2) | `0.155.3+26.1.2` | `26.1.2.129` | `26.1.2-64.1.6` | 25 |
| 26.2 | `0.161.0+26.2` | `26.2.0.95` | `26.2-65.1.6` | 25 |
| 26.3 | `0.166.2+26.3` | `26.3.0.115` | `26.3-66.1.9` | 25 |

- Fabric Loader `0.20.1`, Loom `1.19-SNAPSHOT` (via loom-back-compat `0.4.3`)
- Stonecutter `0.10`, ModDevGradle `2.0.148`, ForgeGradle `net.minecraftforge.gradle` `[7.0.17,8)`
- Gradle 9.9.1 wrapper (template uses it; Forge MDK says 9.7.1+ works)
- Compat ranges: 1.21.1 → `>=1.21 <=1.21.1`; 26.1 → `>=26.1 <=26.1.2`; 26.2 → `~26.2`; 26.3 → `~26.3`
  (NeoForge/Forge: `[1.21,1.21.1]`, `[26.1,26.2)`, `[26.2,26.3)`, `[26.3,26.4)`)

## 2. Reference material (already fetched — use it, don't guess)

The workspace can't reach codeberg.org or the Forge/NeoForge/Fabric mavens. GitHub can. A workflow
`fetch-refs.yml` (run via `gh api -X POST repos/comfortforged/pirate-ships/actions/workflows/fetch-refs.yml/dispatches -f ref=main`)
pushed everything to the **`refs` branch**:

- `sc-template/` — official Stonecutter Fabric+NeoForge template (settings.gradle.kts,
  stonecutter.gradle.kts, stonecutter.properties.toml, build.fabric.gradle.kts, build.neoforge.gradle.kts).
  **Copy its structure.** It already handles 1.21.1 vs 26.x for Fabric/NeoForge.
- `forge-mdk-26.3/`, `forge-mdk-1.21.1/` — official Forge MDKs (build.gradle, mods.toml, example mod,
  EventBus 7 style: `FMLJavaModLoadingContext context`, `XxxEvent.getBus(modBusGroup).addListener(...)`).
- `neo-MDK-26.3-ModDevGradle/`, `neo-MDK-1.21.1-ModDevGradle/` — NeoForge MDKs.
- `meta/` — maven-metadata.xml for every dependency (look up newer versions here).

Clone with: `git clone -q --depth 1 -b refs https://github.com/comfortforged/pirate-ships /home/claude/refs`

## 3. Target layout

```
settings.gradle.kts          # Stonecutter: match("26.1"|"26.2"|"26.3", "fabric","neoforge","forge")
stonecutter.gradle.kts       # constants fabric/neoforge/forge, replacements (ResourceLocation->Identifier etc.)
stonecutter.properties.toml  # per-version/per-loader deps (table in §1)
build.fabric.gradle.kts      # from sc-template
build.neoforge.gradle.kts    # from sc-template
build.forge.gradle.kts       # NEW, modelled on forge-mdk-26.3/build.gradle (Kotlin DSL)
src/main/java/io/github/comfortforged/tidesofwar/
    core/      Ship, ShipModel, ShipType, ShipItems, ShipManager, ShipInteractions, ShipCommands, SelfTest
               (loader-neutral: only Minecraft classes, NO net.fabricmc / net.neoforged / net.minecraftforge imports)
    platform/  TidesOfWarFabric.java   //? if fabric
               TidesOfWarNeoForge.java //? if neoforge
               TidesOfWarForge.java    //? if forge
src/main/resources/
    fabric.mod.json, META-INF/neoforge.mods.toml, META-INF/mods.toml
    data/tidesofwar/recipe/*.json      (26.x format)   — 1.21.1 needs the older ingredient format, see §6
```

### Loader layer contract (the only loader-specific code)

Each `platform/*` class wires these events into `core` and nothing else:

| Core hook | Fabric | NeoForge | Forge |
|---|---|---|---|
| `ShipManager.start(server)` | `ServerLifecycleEvents.SERVER_STARTED` | `ServerStartedEvent` | `ServerStartedEvent` |
| `ShipManager.stop()` | `SERVER_STOPPING` | `ServerStoppingEvent` | `ServerStoppingEvent` |
| `ShipManager.saveIfRunning()` | `BEFORE_SAVE` | `LevelEvent.Save` (overworld only) | `LevelEvent.Save` |
| `ShipManager.tickLevel(level)` | `ServerTickEvents.END_LEVEL_TICK` | `LevelTickEvent.Post` (server levels) | `TickEvent.LevelTickEvent` post / server |
| `ShipManager.onEntityLoad(e)` | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinLevelEvent` (server) | `EntityJoinLevelEvent` |
| `ShipInteractions.onDisconnect(player)` | `ServerPlayConnectionEvents.DISCONNECT` | `PlayerLoggedOutEvent` | `PlayerLoggedOutEvent` |
| `ShipInteractions.useItem(...)` → result | `UseItemCallback` | `PlayerInteractEvent.RightClickItem` (cancel + set result) | same |
| `ShipInteractions.useEntity(...)` → result | `UseEntityCallback` | `PlayerInteractEvent.EntityInteract` | same |
| `ShipCommands.register(dispatcher)` | `CommandRegistrationCallback` | `RegisterCommandsEvent` | `RegisterCommandsEvent` |
| `SelfTest` (only with `-Dtidesofwar.selftest=true`) | `END_SERVER_TICK` | `ServerTickEvent.Post` | `TickEvent.ServerTickEvent` |

Move today's logic out of `PirateShips.java` lambdas into `core/ShipInteractions` and `core/ShipCommands`
so each platform class is ~60 lines. **Check exact event class names against each MDK / API dump**; the
table is a guide, not gospel. Note: `EntityJoinLevelEvent` must not discard during the event — queue it
(ShipManager already queues stale parts).

## 4. Rename + migration (do this first, on the current single-version project)

1. Package `pirateships` → `tidesofwar`; mod id, `MOD_ID`, logger name, fabric.mod.json name/description.
2. LICENSE → All Rights Reserved text; `"license": "ARR"`.
3. Data file: new path `<world>/tidesofwar/ships.json`. On load, if it's missing and
   `<world>/pirateships/ships.json` exists, load that and save to the new path (leave the old file).
4. Items: write `tidesofwar_type` into custom_data; `typeOf` must still accept the old `pirateships_type`.
5. Entity tag: new parts use `tidesofwar_part`; stale-part cleanup must also discard old `pirateships_part`.
6. Command: `/tidesofwar …`; keep `/pirateships` as a redirect alias.
7. Recipes move to `data/tidesofwar/recipe/` (ids `tidesofwar:pirate_sloop` etc.); ship *names* stay
   "Pirate Sloop/Brigantine/Galleon".
8. Self-test property → `tidesofwar.selftest`; add a check that an old-style item (`pirateships_type`)
   is still recognised and that an old `pirateships/ships.json` gets migrated.
9. CI green → commit "Rename to Tides of War".

## 5. Steps (each ends with CI green for the listed nodes before moving on)

1. **Stonecutter skeleton, Fabric 26.3 only.** Copy sc-template files, keep only `26.3-fabric`. Move code
   into core/platform. Port CI (see §7). Self-test PASS.
2. **Fabric 26.1 + 26.2.** Add nodes. Fix API drift with `//? if >=26.x {` blocks or `replacements`.
3. **NeoForge 26.1/26.2/26.3.** Add `build.neoforge.gradle.kts`, `neoforge.mods.toml`, platform class.
4. ~~1.21.1 Fabric + NeoForge~~ — dropped for now.
5. **Forge 26.1/26.2/26.3.** Write `build.forge.gradle.kts` from the Forge MDK (FG7, `minecraft.dependency(...)`,
   `minecraft.mavenizer(it)`, `fg.forgeMaven`, `fg.minecraftLibsMaven`). Add `constants { match(loader, "fabric","neoforge","forge") }`.
   Forge + Stonecutter is the least-trodden path: if FG7 fights Stonecutter's task model, fall back to
   generating the Forge build per node but keep the shared source. Also wire `createMinecraftArtifacts`-style
   task ordering like the NeoForge script does (`dependsOn("stonecutterGenerate")`).
6. **Package**: collect 9 jars; refresh `Pirate-Ships-Server-Mods.zip` → `TidesOfWar-Server-Fabric-26.3.zip`
   (mod + Fabric API) and the CurseForge profile; update README + project doc; copy to the user's folder
   `C:\Users\oscar\OneDrive\Documents\Bound by Code\Comfort Forged\Minecraft Mods\Pirate Ships`
   (via device_commit_files, staging from /mnt/user-data/outputs).

## 6. Known API differences (verify with the per-node API dump)

26.3 names used today → what older versions likely use:

| 26.3 | 26.1/26.2 | 1.21.1 (Mojang mappings) |
|---|---|---|
| `EntityTypes.BLOCK_DISPLAY` | check: may be `EntityType.BLOCK_DISPLAY` | `EntityType.BLOCK_DISPLAY` |
| `Blocks.WOOL.white()`, `Blocks.CONCRETE.white()` (ColorCollection) | check: may be `Blocks.WHITE_WOOL` | `Blocks.WHITE_WOOL`, `BLACK_WOOL`, `WHITE_CONCRETE` |
| `Identifier` | `Identifier` | `ResourceLocation` (template replacement handles it) |
| `entity.snapTo(...)` | probably same | `moveTo(...)` |
| `player.sendOverlayMessage(c)` | check | `displayClientMessage(c, true)` |
| `entity.entityTags()` | check | `getTags()` |
| `player.startRiding(e, true, true)` | check | `startRiding(e, true)` |
| `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` | check | `src -> src.hasPermission(2)` |
| `placeItemBackInInventory(item, Prediction.SERVER_ONLY)` | check | `placeItemBackInInventory(item)` |
| `ServerPlayer.getLastClientInput()` → `Input` record | same (1.21.2+) | **doesn't exist**: read `player.zza` (forward >0 / back <0) and `player.xxa` (left >0 / right <0); they're set from the steering packet while riding. Wrap in a tiny `core/Controls` record so `Ship.tick` stays version-neutral. |
| `level.dimension().identifier()` | check | `.location()` |
| `compoundTag.getStringOr(k, "")` | check | `getString(k)` |
| `Display.setPosRotInterpolationDuration` etc. are public | check | **private** in 1.21.1 → use a Mixin accessor/invoker (`tidesofwar.mixins.json`, Fabric + NeoForge + Forge all support it) or set via `EntityDataAccessor` reflection-free accessor mixin |
| `Interaction.setWidth/Height/Response` public | check | private → accessor mixin |
| `ItemStack.CODEC` with RegistryOps | same | same (`createSerializationContext` exists on HolderLookup.Provider since 1.20.5) |
| `EntitySpawnReason` | n/a here | n/a |
| recipe JSON key: `"P": "#minecraft:planks"` | same | 1.21.1 format: `"P": {"tag": "minecraft:planks"}` / `{"item": "minecraft:iron_ingot"}`; components on result same style. Ship two recipe sets and filter per node in processResources. |
| `server.getRecipeManager().byKey(ResourceKey)` | check | `byKey(ResourceLocation)` |
| `ServerTickEvents.END_LEVEL_TICK` (Fabric 26.x) | check | `END_WORLD_TICK` (Fabric API 0.116) |

Rule: prefer Stonecutter `replacements { string(...) }` for pure renames; `//? if >=26.1 {` blocks for
signature changes. Keep conditionals inside small helper methods (e.g. `core/Compat.java`) so the ship
logic itself has none.

## 7. CI

- Matrix job over the 9 nodes: `./gradlew :<node>:build` then the server self-test for that node
  (Fabric: `:<node>:runServer`; NeoForge: `:<node>:runServer`; Forge: `:<node>:runServer`), each with
  `-Dtidesofwar.selftest=true`, `eula=true`, flat world, `pause-when-empty-seconds=-1` (26.x only).
  Pass = log contains `TIDESOFWAR SELFTEST PASS`.
- Keep the per-node API dump step (javap of classes in `.github/api-classes.txt`) — it's how you get real
  method names without decompiled sources. Add a `printClasspath` task to each build script.
- Keep publishing outputs to the `ci-output` branch (artifact downloads are blocked from the workspace):
  one folder per node with build.log, server.log, api.txt, jar. The final job collects all 9 jars.
- `/home/claude/tools/ci.sh` waits for the run on HEAD and clones `ci-output` to `/home/claude/ci`
  (recreate it if the workspace was reset; it's ~15 lines). Use `gh api` REST calls, not GraphQL.
- Matrix jobs pushing to one branch will race: have each job push to `ci-output-<node>` and a final
  job merge, or upload per-node folders in one final job via `needs:`.

## 8. Definition of done

- 9 self-tests PASS in CI; all 9 jars on `ci-output`.
- Fabric 26.3 jar is drop-in for the user's server (old ships and items still work).
- README/guide updated (name, loaders, versions, install per loader), project doc `pirate-ships-guide.md`
  updated, files copied to the user's OneDrive folder.

## 9. Model workflow (user preference)

The user wants **Opus for planning, Sonnet for executing**, and wants to be told when to switch.
Execute §4–§6 on Sonnet. Switch back to Opus (tell the user) if: Forge+Stonecutter needs a redesign,
an approach in this plan turns out wrong, or for planning Phase 3 (cannons) afterwards.
