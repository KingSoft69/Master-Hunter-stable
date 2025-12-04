# MasterHunter
A 2b2t Meteor Client utility based addon.

## Table of Contents
- [Original Modules](#original-modules)
- [Ported Modules](#ported-modules)
- [Module Details](#module-details)
  - [Link Filter](#link-filter)
  - [Loose Items Waypoints](#loose-items-waypoints)
  - [Broken Portal Waypoints](#broken-portal-waypoints)

*Note: The Module Details section provides in-depth documentation for modules with complex features and settings. All modules are listed in the sections below.*

## Original Modules
**<ins>Utility</ins>**
+ AutoLog Plus - A module that disconnects the player according to specific parameters (Based off of [Numby-hack](https://github.com/cqb13/Numby-hack))
+ Portal Auto Log - Disconnects the player when within a configurable range of a portal (default: 2 blocks)
+ Auto Shulker - When your inventory is full it places and opens a shulker box and then puts your items in the shulker box
+ Elytra Swap - When your equipped elytra reaches a configurable durability threshold, it swaps to a new one
+ Chat Tracker - Logs chat to a file with optional filtering
+ Player History - Logs player information when they are spotted
+ Link Filter - Filters out chat messages containing Discord invites or website links

**<ins>Renders</ins>**
+ VanityESP - An ESP for vanity items such as banners and item frames
+ Mob Item ESP - An ESP for mobs that spawn with items that they normally wouldn't spawn with
+ Overworld Mob Highlight - Highlights overworld-only mobs in the Nether with red boxes and tracers

**<ins>Movement</ins>**
+ Elytra Redeploy - Jumps and flies away if you hit the ground with this enabled

**<ins>Hunting</ins>**
+ Mob Tracker Plus - Creates waypoints when a certain number of mobs are encountered in the Nether
+ Loose Items Waypoints - Creates waypoints for loose valuable items like shulker boxes, netherite armor, enchanted golden apples, firework rockets, and totems
+ Broken Portal Waypoints - Creates waypoints for portals that aren't lit or are missing obsidian blocks

**<ins>HUD</ins>**
+ TotemCount - A HUD module that shows the amount of totems in your inventory
+ CrystalCount - A HUD module that shows the amount of end crystals in your inventory
+ Dub Count - A HUD module that shows what is output from the Dub Counter module 
+ ETA - Displays the estimated time left to reach the baritone goal/goal in GUI based on current speed
+ Lag Detector - Displays the server's TPS and if there are lagbacks also shows a lag warning when lagging

## Ported Modules
**<ins>Utility</ins>**
+ DiscordNotifications - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon) - **I added the ability test the Discord webhook**
+ PortalMaker - originally from [xqyet](https://github.com/xqyet)
+ AntiSpam - ported from [Asteroide](https://github.com/asteroide-development/Asteroide)
+ Dub Counter - ported from [IKEA](https://github.com/Nooniboi/Public-Ikea) - **I changed this module a bit to allow the HUD element to work properly**
+ GrimAirPlace - ported from [Jeff Mod](https://github.com/miles352/meteor-stashhunting-addon)
+ Map Exporter - originally from [VexTheIV](https://github.com/Vextheiv)

**<ins>Renders</ins>**
+ PearlOwner - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)

**<ins>Hunting</ins>**
+ StashFinderPlus - ported from [Jeff Mod](https://github.com/miles352/meteor-stashhunting-addon) - **I added the ability to bulk add potential stashes to waypoints, auto-disconnect when a stash is found and added more information when a Discord webhook is sent**
+ NewChunksPlus - ported from [Trouser Streak](https://github.com/etianl/Trouser-Streak)
+ TrailFollower - originally from [WarriorLost](https://github.com/WarriorLost) - **I added an option to auto disconnect depending on chunk loading speeds and a cardinal direction priority mode**

**<ins>Movement</ins>**
+ Pitch40Util - ported from [Jeff Mod](https://github.com/miles352/meteor-stashhunting-addon)
+ SearchArea - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)
+ AFKVanillaFly - originally from [xqyet](https://github.com/xqyet)
+ GrimScaffold - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)

**<ins>HUD</ins>**
+ EntityList - ported from [BepHaxAddon](https://github.com/dekrom/BepHaxAddon)

---

## Module Details

### Link Filter

**Overview**  
The Link Filter module prevents chat messages containing Discord invites, website links, and IP addresses from being displayed in your Minecraft chat. This is useful for:
- Avoiding spam and advertisement messages
- Preventing accidental clicks on malicious links
- Keeping your chat clean and focused on gameplay
- Blocking server advertisements in multiplayer servers

**How It Works**  
The module intercepts incoming chat messages before they're displayed and checks them against multiple detection patterns. When a message contains a filtered link type, it's automatically hidden from your chat.

**Settings**

- **Filter Discord Links** (Default: Enabled)  
  Hides messages containing Discord invitation links. Detects:
  - `discord.gg/invite-code`
  - `discordapp.com/invite/invite-code`
  - `.gg/invite-code` (common shorthand)

- **Filter Website Links** (Default: Enabled)  
  Hides messages containing website URLs. Detects:
  - URLs with `http://` or `https://` prefixes
  - URLs starting with `www.`
  - Domain names with common TLDs like `.com`, `.net`, `.org`, `.gg`, etc.
  - Supports 100+ top-level domains

- **Filter IP Addresses** (Default: Disabled)  
  Hides messages containing IPv4 addresses. Detects:
  - IP addresses in format `xxx.xxx.xxx.xxx`
  - IP addresses with ports: `xxx.xxx.xxx.xxx:port`
  - Note: This is disabled by default as legitimate server information may include IPs

**Usage Tips**

1. **Enable Before Joining Servers**: Activate the module before joining multiplayer servers where spam is common.
2. **Combine with AntiSpam**: Use alongside the AntiSpam module for comprehensive chat filtering. LinkFilter handles links, while AntiSpam handles keyword-based filtering.
3. **IP Filtering**: Only enable IP filtering if you frequently encounter IP-based spam. Keep it disabled in general use to see legitimate server information.
4. **Testing**: You can test the filter by having a friend send messages with links, or by checking if spam messages are being blocked.

**Detection Examples**

Discord Links (Filtered):
- `Join my discord discord.gg/abc123`
- `Check out .gg/xyz789`
- `Visit discordapp.com/invite/test123`

Website Links (Filtered):
- `Visit https://example.com`
- `Check www.test.net`
- `Go to google.com`
- `Join server.gg`

IP Addresses (Filtered when enabled):
- `Join 192.168.1.1:25565`
- `Server at 10.0.0.1`
- `Connect to 127.0.0.1:8080`

Allowed Messages:
- `This is a normal message`
- `Check my items`
- `Trading at spawn`

**Combining with Other Modules**

- **With AntiSpam**: LinkFilter blocks messages with links, AntiSpam blocks messages with specific keywords. Together they provide comprehensive chat filtering.
- **With Chat Tracker**: The Chat Tracker module logs chat messages. LinkFilter blocks messages before they're displayed, so filtered messages won't appear in your logs if both are enabled.

---

### Loose Items Waypoints

**Overview**  
The Loose Items Waypoints module creates waypoints for loose valuable items like shulker boxes, netherite armor, enchanted golden apples, firework rockets, and totems. This is useful for:
- Finding dropped loot from PvP battles
- Locating items left behind by other players
- Tracking down valuable items after death
- Identifying stashes or bases by valuable item drops

**Trackable Items**

- **Shulker Boxes** (Default: Enabled) - Tracks all shulker box color variants
- **Netherite Armor** (Default: Enabled) - Tracks all netherite armor pieces (helmet, chestplate, leggings, boots)
- **Enchanted Golden Apples** (Default: Enabled) - Tracks enchanted golden apples (god apples)
- **Firework Rockets** (Default: Enabled) - Tracks firework rockets used for elytra flight
- **Totems of Undying** (Default: Enabled) - Tracks totems

**Settings**

- **Scan Radius** (Default: 64 blocks, Range: 8-256 blocks)  
  The radius in blocks to scan for items around the player.

- **Minimum Item Count** (Default: 1, Range: 1-64)  
  Minimum number of items to create a waypoint (for stackable items like rockets).

- **Duplicate Radius** (Default: 16 blocks, Range: 1-128 blocks)  
  Prevents duplicate waypoints within this radius of existing waypoints.

- **Notifications** (Default: Enabled)  
  Send notifications when valuable items are found.

**Waypoint Colors**

Waypoints are color-coded based on item rarity:
- **Gold/Yellow**: Enchanted golden apples (highest priority)
- **Purple**: Netherite armor
- **Green**: Totems of undying
- **Light Blue**: Shulker boxes
- **White**: Firework rockets
- **Gray**: Other/mixed items

**Info Display**  
The module displays the count of waypoints created in the module info string.

**Performance Notes**
- The module checks for items every second (20 ticks) to reduce performance impact
- Items at similar locations are grouped together to prevent waypoint spam
- Efficient scanning only checks entities within the specified radius

**Usage Tips**

1. **PvP Areas**: Enable this module when flying through high-traffic PvP areas to spot dropped loot
2. **Scan Radius**: Increase scan radius when flying fast with elytra, decrease for ground exploration
3. **Minimum Item Count**: Increase to only track larger item drops (e.g., from deaths or stashes)
4. **Combine with Other Modules**: Use alongside StashFinderPlus for comprehensive base hunting

---

### Broken Portal Waypoints

**Overview**  
The Broken Portal Waypoints module automatically detects and marks locations of nether portals that aren't lit or are missing obsidian blocks. This is useful for basehunting as players often leave unfinished or unlit portals near their bases:
- Portals left unlit to avoid detection
- Portal frames abandoned during construction
- Portals intentionally broken to prevent use
- Emergency escape portals not yet completed

**How It Works**  
The module periodically scans the area around the player for obsidian blocks and checks if they form a nether portal frame pattern. It identifies two types of portal frames:
1. **Unlit Portals**: Complete 10-block obsidian frames without active portal blocks inside
2. **Incomplete Portals**: Portal frames missing 1-5 obsidian blocks (configurable)

**Settings**

- **Scan Radius** (Default: 32 blocks, Range: 8-128 blocks)  
  The radius in blocks to scan for portal frames around the player.

- **Duplicate Radius** (Default: 16 blocks, Range: 4-64 blocks)  
  Minimum distance before creating another waypoint. Prevents duplicate waypoints for the same portal.

- **Detect Unlit** (Default: Enabled)  
  When enabled, detects complete portal frames (10 obsidian blocks) that don't have active portal blocks inside.

- **Detect Incomplete** (Default: Enabled)  
  When enabled, detects portal frames that are missing some obsidian blocks.

- **Max Missing Blocks** (Default: 2, Range: 1-5)  
  Maximum number of missing obsidian blocks to still consider the structure a portal frame. Only visible when "Detect Incomplete" is enabled.

- **Notifications** (Default: Enabled)  
  Send chat notifications when broken portals are found.

**Waypoint Types**

- **Unlit Portal**: Purple waypoint marked with "P"
- **Broken Portal**: Orange waypoint marked with "B" (shows number of missing blocks)

**Usage Tips**

1. **Basehunting**: Enable this module while exploring to find evidence of player activity
2. **Scan Radius**: Increase when flying with elytra, decrease for ground exploration to improve performance
3. **Max Missing Blocks**: Keep low (1-2) for more precise detection, increase if you want to catch more partial structures

**Performance Notes**
- The module checks every 2 seconds (40 ticks) to reduce performance impact
- Checked locations are cached to avoid re-scanning the same areas
- Cache is cleared every 2 minutes to allow re-detection of changed areas

**Example Scenarios**

- **Abandoned Base Entrance**: A player might have an unlit portal frame near their base entrance that they light only when needed for security
- **Portal Room in Progress**: Players building a base may have left portal frames incomplete while gathering more obsidian
- **Decoy Portals**: Some players create unlit portal frames as decoys to confuse basehunters
- **Quick Escape Routes**: Players sometimes pre-build portal frames at multiple locations and only light them when needed

