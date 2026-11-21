# pirate-ships
Minecraft Pirate Ships Mod for Minecraft Java 26.3 (Fabric).

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
- **Lost your ship?** `/pirateships recall` brings your nearest ship back to your inventory, from anywhere.

Ships run aground at the shore. They stop instead of sailing onto land.

## Admin commands
- `/pirateships give <player> <sloop|brigantine|galleon>`: give a ship item.
- `/pirateships list`: list every ship afloat, its owner and position.

## Installing on the server (simplegamehosting.com)
1. **Back up your world first** (download the world folder, or use the panel's backup option).
2. In the control panel, change the server type to **Fabric** for Minecraft **26.3** and reinstall or apply. Your world is kept.
3. Start the server once so it creates the `mods` folder, then stop it.
4. Unzip `Pirate-Ships-Server-Mods.zip` and upload **both** jars (`pirate-ships-…jar` and `fabric-api-…jar`) into the `mods` folder.
5. Start the server. The console should show `Pirate Ships ready: 3 ship types`.

## Players: joining with CurseForge
1. Open the CurseForge app, then go to **Minecraft → Create Custom Profile → Import** (the small "import" link).
2. Choose `Pirate-Ships-CurseForge-Profile.zip`. CurseForge installs Minecraft 26.3, Fabric and the mods automatically.
3. Click **Play**, then **Multiplayer**, and add your server's address.

Players can also join with the normal Minecraft launcher. The ships still work because everything runs on the server.

## Building
GitHub Actions builds the mod on every push to `main` and runs an automated self-test on a throwaway server
(launch, sail, run aground, turn, save/load, pack up with cargo). The finished files are pushed to the `ci-output` branch.

## Roadmap
- Phase 2: more ship types and looks
- Phase 3: cannons, hull damage and sinking
- Phase 4: upgrades for cannons, sails (speed), tiller (turning) and hull (armour), plus a flight upgrade
