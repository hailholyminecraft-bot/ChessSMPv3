# ChessSMP 2.2.0

Chess-inspired PvP for **Paper 26.2** (Java 25, Maven): six piece classes with abilities, the **Chess War**
team event, a Piece Trader, name tags, progression, plus the 2.2 additions: **Grandmaster Mace**, **Chess Grandmaster
title**, **Chess Clock**, **meteor with the green Meteor Trader**, `/chess trust`, `/chess announce`, `/chess give`
and a resource pack.

> **Status:** the code was written without access to a Minecraft server or the Paper API, so it has **not been compiled
> or play-tested yet**. The GitHub Action is the first real compile. If it fails or something misbehaves in game, send the
> error / description back and it will be fixed. See "Testing checklist" below.

## Getting the plugin JAR (GitHub)

1. Create a new GitHub repository and upload **everything in this folder keeping the folder structure**
   (`.github/`, `src/`, `resourcepack/`, `tools/`, `pom.xml`, `README.md`, `.gitignore`).
   - Easiest: install GitHub Desktop or use git (`git init`, `git add .`, `git commit`, `git push`).
   - Web upload: *Add file -> Upload files*, then **drag the folders themselves** (not a zip). The hidden `.github`
     folder must be included; if your browser skips it, use *Add file -> Create new file*, type
     `.github/workflows/build.yml` as the name and paste the contents of that file.
2. Open the **Actions** tab. The *Build ChessSMP* workflow runs on every push (or press *Run workflow*).
3. When it is green, open the run and download **ChessSMP-jar** (the plugin) and **ChessSMP-ResourcePack**.
4. Put `ChessSMP-2.2.0.jar` into your server's `plugins/` folder and restart.

Local build: `mvn clean verify` (JDK 25) -> `target/ChessSMP-2.2.0.jar`.

## Resource pack

`resourcepack/` (also a build artifact) contains:
- **Grandmaster Mace**: mace shape with a chess-board head (`chesssmp:grandmaster_mace` item model)
- **Meteor Remnant** texture (`chesssmp:remnant`)
- **Green Meteor Trader**: replaces the Nitwit villager outfit with a green robe and hood. The shop villager uses the
  Nitwit profession, so *every* Nitwit villager looks green while the pack is active.

Zip the *contents* of `resourcepack/` (so `pack.mcmeta` is at the zip root) and set it as `resource-pack` in
`server.properties` (needs a download URL + SHA-1) or give it to players. Without the pack, set
`resourcepack.use-item-models: false` in `config.yml` so items fall back to vanilla looks.
`tools/make_textures.py` regenerates the textures (needs Pillow).

## Commands

| Command | Description |
|---|---|
| `/chess choose [piece]` | Choose a piece (menu without argument) |
| `/chess ability <1-3>` | Use an ability |
| `/chessinfo [piece\|menu]` | Piece, stats, abilities, clickable buttons / GUI |
| `/chess trader` | Piece Trader item (permission `chesssmp.trader`) |
| `/chess wand` | Chess Scepter (right-click / sneak+right-click / F = abilities 1/2/3) |
| `/chess top`, `/chess stats [player]` | Leaderboard and statistics |
| `/chess trust <player>`, `/chess untrust <player>`, `/chess trust list` | Players you trust are never affected by *your* attacks (melee, projectiles, abilities, freeze, Chess Clock) |
| `/chess event start [joinSeconds]` / `stop` / `status` / `join` / `leave` | Chess War |
| `/chess event set <score\|time\|join\|minplayers> <n>`, `/chess event setspawn <white\|black>` | Event settings |
| `/chess give <player> <item> [amount]` | Items: `trader`, `ability_wand`, `grandmaster_mace`, `chess_grandmaster`, `remnant`, `chess_clock` |
| `/chess announce [-t color] [-s color] [-d seconds] [-chat] <title> \| <subtitle>` | Title on every player's screen |
| `/chess meteor spawn [random]`, `/chess meteor villager` | Build a meteor site / spawn the shop villager |
| `/chess admin <setpiece\|reset\|setpoints\|addpoints\|clearcooldowns\|setcooldown\|setduration\|setstat> ...` | Administration |
| `/chess reload` | Reload `config.yml` |

`/chess announce` examples (MiniMessage works in the text, colors are names or `#RRGGBB`):
```
/chess announce -t red -s gold DOUBLE POINTS | for the next 10 minutes
/chess announce -t #00ffaa -d 8 <bold>Meteor incoming!</bold> | Look to the sky
/chess announce <gradient:red:gold>Chess War in 5 minutes</gradient>
```

## Permissions
`chesssmp.player` (default: everyone), `chesssmp.event`, `chesssmp.trader`, `chesssmp.announce` (default: op),
`chesssmp.admin` (op, includes all of them).

## Features in short
- **Pieces**: Pawn, Knight, Bishop, Rook, Queen, King - 3 abilities each, cooldowns/durations/stats in `config.yml`.
  Cooldowns survive relogs and restarts; only one timed ability can be active at a time (no unintended stacking).
- **Chess War**: join period, balanced White/Black teams (count and piece strength), no friendly fire, kill and King-kill
  points, respawn modes (INSTANT / DELAYED / ELIMINATION), score and time limit, final scoreboard, rewards, reconnect grace.
- **Grandmaster Mace**: awarded to the MVP (most kills) of the winning team (`grandmaster-mace.recipient`). Right-click
  while holding it to dash. Offline winners receive it on next join. Kept on death.
- **Meteor**: `/chess meteor spawn` builds a 28-block-radius crater with a hollow medium meteor holding the green
  **Meteor Trader** (PvP items, maxed netherite gear, remnants) and a barrel with remnants. Blocks are placed in batches
  per tick. Optional automatic crashes: `meteor.auto-spawn.enabled`. The shop is fully configurable.
- **Crafting restrictions**: results listed in `crafting.blocked-results` (shield, wind charge, mace, golden apples, ...)
  cannot be crafted. Breeze rods are not craftable in vanilla; they are sold by the Meteor Trader.
- **Chess Grandmaster title**: 8 Meteor Remnants around a Nether Star in a crafting table. Right-click to claim:
  tag above your head, every player you kill adds their piece (they keep theirs, shown behind your name). All six pieces
  (your current piece counts) = permanent Speed III + Resistance I and a freezing hit (15 s, every 2 minutes). Dying drops the
  title and resets pieces.
- **Chess Clock**: right-click, everything within 20 blocks (players, mobs, projectiles) slows to a crawl for 10 s,
  except you and players you trust. Cooldown 50 s.
- **Name tags** use scoreboard teams named `csmp_*`; other plugins' teams are left alone (tab list is still updated).
- **Progression**: Chess Points, ranks (cosmetic titles/kill effects only), objectives, killstreak announcements.

## Notes and limitations
- Chess points from a kill are only awarded once per victim per 5 minutes (anti-farming, configurable).
- The Grandmaster title is shown in the name tag (prefix + pieces behind the name) - Minecraft cannot render two lines
  above a head with scoreboard teams alone.
- Chess War state is not saved across a server restart; a running event is ended cleanly on shutdown.
- Event item/command rewards are only handed to players who are online when the event ends (Chess Points are always awarded).
- Meteor sites cannot be removed automatically (use WorldEdit/rollback). Trust protects against *your* attacks only
  (not splash potions or TNT you set off).
- The pack does not change the villager outside the profession overlay, so the face/skin comes from the biome type.

## Testing checklist (do this on a test server)
Commands and permissions - all six pieces and all 18 abilities - cooldowns after relog/restart - Piece Trader purchases and
piece switching (try shift-click, number keys, double click) - `/chess event start` with 2+ players (balance, scoring,
King bonus, victory by score and by time) - name tags after class change and relog - `/chess give` for every item -
8 remnants + nether star crafting - title kill/drop/reset - freeze - Chess Clock - meteor spawn and shop - trust -
announce - mace dash.
