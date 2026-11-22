# Tides of War: config reference (`config/tidesofwar.json`)

Created on first start with every option and its default. Run `/tidesofwar reload` after editing. Missing keys keep their defaults.
Item ids are like `minecraft:gunpowder`; a leading `#` means an item tag (`#minecraft:planks`).

## `cannons`
| Key | Default | Meaning |
|---|---|---|
| `requireAmmo` | `true` | Cannons use ammo from the cargo hold. `false` = free shots. |
| `ammo` | `[{item: minecraft:gunpowder, count: 1}]` | Items per cannon fired. The first entry the cargo can pay for is used. |
| `reloadSeconds` | `5.0` | Reload time per side after a broadside. |
| `captainCanFire` | `true` | The captain can fire with Space on the side they look at. |
| `muzzleSpeed` | `2.4` | Cannonball speed in blocks per tick. |
| `gravity` | `0.05` | How fast balls drop. |
| `spreadDegrees` | `1.5` | Random aim error. |
| `minElevation` / `maxElevation` | `-5` / `25` | Aim limits up and down, in degrees. |
| `traverse` | `30` | Aim limit left and right of straight out, in degrees. |
| `maxFlightTicks` | `200` | A ball that flies this long is removed. |

## `damage`
| Key | Default | Meaning |
|---|---|---|
| `shipDamage` | `12.0` | Hull points a hit takes off a ship. |
| `entityDamage` | `10.0` | Damage to a directly hit living thing. |
| `splashRadius` | `2.5` | Splash radius when `blockDamage` is off. |
| `pvp` | `false` | Cannons can hurt players. |
| `mobs` | `true` | Cannons can hurt mobs and animals. |
| `blockDamage` | `true` | Impacts explode and break blocks. `false` = effects and splash damage only. Set `false` if your protection plugins ignore explosions. |
| `explosionPower` | `1.6` | Explosion size (TNT is 4). |
| `shipCombat` | `true` | Ships can damage other ships. |
| `friendlyFire` | `false` | Ships can damage ships of the same owner. |

## `hull`
| Key | Default | Meaning |
|---|---|---|
| `sloop` / `brigantine` / `galleon` | `60` / `100` / `160` | Maximum hull points. |
| `repairItem` | `#minecraft:planks` | Item used to repair at the tiller. |
| `repairPerItem` | `4` | Hull points restored per item. |
| `combatLockSeconds` | `30` | After a hit, the ship can't be packed up, recalled or repaired for this long. |

## `wrecks`
| Key | Default | Meaning |
|---|---|---|
| `lootable` | `"anyone"` | `"anyone"` or `"owner"`: who can open a wreck's cargo. |
| `raiseHullPercent` | `25` | Hull percentage a raised wreck comes back with. |
| `raiseCost` | sloop: 16 planks + 4 iron ingots; brigantine: 32 planks + 8 iron; galleon: 64 planks + 2 iron blocks | What the owner pays to raise a wreck (all items listed). |
| `lifetimeDays` | `0` | Wrecks older than this many in-game days vanish and spill their cargo. `0` = never. |
