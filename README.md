# Tides of War
A Minecraft Java mod for **26.1, 26.2 and 26.3** on **Fabric, NeoForge and Forge**. (Formerly "Pirate Ships".)

Summon big pirate ships with giant sails from your inventory, sail them around, carry passengers and cargo.
Everything runs on the server. The ships are built from Minecraft's own display blocks, so they also work
for players who join without the mod installed.

## Ships

| Ship | Size | Speed | Turning | Cargo | Passenger seats |
|---|---|---|---|---|---|
| Pirate Sloop | 11 long, 1 mast | fastest (9 blocks/s) | sharpest | 27 slots | 3 |
| Pirate Brigantine | 16 long, 2 masts | 7.6 blocks/s | medium | 27 slots | 5 |
| Pirate Galleon | 24 long, 3 masts | 6 blocks/s | slow | 54 slots | 7 |

### Recipes (crafting table)
- **Sloop:** wool in a T on top (`_W_` / `WWW`), then `Planks, Any boat, Planks` on the bottom row.
- **Brigantine:** `Wool, Iron ingot, Wool` / `Wool, Wool, Wool` / `Log, Any boat, Log`.
- **Galleon:** `Wool, Diamond, Wool` / `Wool, Iron block, Wool` / `Log, Any boat, Log`.

## How to play
- **Launch:** hold the ship item and right-click on open water. The ship needs enough water around it.
- **Steer:** right-click the **tiller** (the post on the raised back deck) to take the helm.
  **W** fills the sails (speed builds up gradually), **S** slows down or backs up, **A/D** steer, **Shift** leaves the helm.
- **Passengers:** right-click a bench on the deck to sit down.
- **Cargo:** right-click the barrel on deck to open the cargo hold.
- **Pack up:** **Shift + right-click** the tiller. The ship goes back into your inventory with its cargo inside.
  Only the owner (or an admin) can do this.
- **Lost your ship?** `/tidesofwar recall` brings your nearest ship back to your inventory, from anywhere.

Ships run aground at the shore. They stop instead of sailing onto land.

## Admin commands
The old `/pirateships ...` command still works as an alias.
- `/tidesofwar give <player> <sloop|brigantine|galleon>`: give a ship item.
- `/tidesofwar list`: list every ship afloat, its owner and position.

## Downloads
Every push builds all nine jars (see the `ci-output` branch, one folder per version and loader):

| Minecraft | Fabric | NeoForge | Forge |
|---|---|---|---|
| 26.1 (26.1 to 26.1.2) | `tides-of-war-fabric-0.1.0+26.1.2.jar` | `tides-of-war-neoforge-0.1.0+26.1.2.jar` | `tides-of-war-forge-0.1.0+26.1.2.jar` |
| 26.2 | `tides-of-war-fabric-0.1.0+26.2.jar` | `tides-of-war-neoforge-0.1.0+26.2.jar` | `tides-of-war-forge-0.1.0+26.2.jar` |
| 26.3 | `tides-of-war-fabric-0.1.0+26.3.jar` | `tides-of-war-neoforge-0.1.0+26.3.jar` | `tides-of-war-forge-0.1.0+26.3.jar` |

## Installing on a server
1. **Back up your world first.**
2. Set the server type to **Fabric**, **NeoForge** or **Forge** for your Minecraft version, start it once so the `mods` folder exists, then stop it.
3. Upload the matching jar into `mods`. **Fabric servers also need [Fabric API](https://modrinth.com/mod/fabric-api)**;
   `TidesOfWar-Server-Fabric-26.3.zip` has both jars for Fabric 26.3.
4. Start the server. The console shows `Tides of War ready: 3 ship types`.

Upgrading from "Pirate Ships"? Replace the old jar with the new one. Your ships, ship items and cargo carry over.
The first start copies `pirateships/ships.json` to `tidesofwar/ships.json` in the world folder (the old file stays as a backup).

## Players: joining with CurseForge
1. Open the CurseForge app, then go to **Minecraft → Create Custom Profile → Import**.
2. Choose `TidesOfWar-CurseForge-Profile.zip` (Fabric 26.3). CurseForge installs Minecraft, Fabric and the mods.
3. Click **Play**, then **Multiplayer**, and add your server's address.

Players can also join with the normal Minecraft launcher with no mods. The ships still work because everything runs on the server.

## Building
One codebase, [Stonecutter](https://stonecutter.kikugie.dev) for versions and loaders:
`src/main/java/.../core` is loader-neutral, `platform/` holds the thin Fabric, NeoForge and Forge entry classes,
and `core/Compat.java` holds the Minecraft-version differences.
GitHub Actions builds all nine versions on every push to `main` and runs an automated self-test on a throwaway server for each
(launch, sail, run aground, turn, save/load, pack up with cargo, plus checks that data from the old "Pirate Ships" still loads).
Jars are pushed to the `ci-output` branch.

## License
All Rights Reserved. See `LICENSE`.

## Roadmap
- Phase 2: more ship types and looks
- Phase 3: cannons, hull damage and sinking
- Phase 4: upgrades for cannons, sails (speed), tiller (turning) and hull (armour), plus a flight upgrade
