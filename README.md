# UniLoot

UniLoot provides **unique, per-player loot for all generated containers**, regardless of other plugins, loot tables, or structures. No more shared chests, no more conflicts — every player gets their own loot.

This is my first plugin of this scale, built to solve frustrations I had with other loot plugins. I wanted something lightweight, reliable, and fully compatible with anything else you throw at your server. (Yes, I used AI to help build it.)

# WARNING 

If you stumble across this and want to try it out, use "/unitloot scan" around players already placed containers (barrels/chests/etc)
Figuring out a better method to sort this issue, but with my limited knowledge and skills this is how it is for now.
For my small private server just a couple friends it's not issue, but I can see this being a bigger issue for.. bigger servers.

# WARNING

---

## Features
- Per-player instanced loot for all containers.
- Compatible with **all plugins and custom loot tables**.
- Integrates smoothly with structure plugins (e.g., BetterStructures, Iris (dimension engine), etc).
- Optional WorldGuard support.

---

## Commands
| Command       | Description                               | Permission        |
|---------------|-------------------------------------------|-------------------|
| `/uniloot reload` | Reloads the UniLoot configuration.       | `uniloot.reload` |
| `/uniloot scan`   | Scans chunks to protect existing containers. | `uniloot.scan`   |

---

## Permissions
| Permission        | Description                                      | Default |
|-------------------|--------------------------------------------------|---------|
| `uniloot.command` | Base permission for the UniLoot command.         | OP      |
| `uniloot.reload`  | Allows reloading the UniLoot configuration.      | OP      |
| `uniloot.scan`    | Allows scanning chunks to protect player chests. | OP      |

---

## Installation
1. Drop the UniLoot jar into your `plugins/` folder.  
2. Restart or reload your server.  
3. Configure as needed in `config.yml`.  
4. Done — all containers now generate instanced loot per player.

---

## Soft Dependencies
- [BetterStructures](https://www.spigotmc.org/resources/betterstructures.103241/)  
- [WorldGuard](https://enginehub.org/worldguard)  

---

## License
This project is licensed under the **MIT License** (see `LICENSE` file for details).

---

