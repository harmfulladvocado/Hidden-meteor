<p align="center">
<img src="https://meteorclient.com/icon.png" alt="meteor-client-logo" width="15%"/>
</p>

<h1 align="center">Hidden-Meteor</h1>

<p align="center">
A fork of <a href="https://github.com/MeteorDevelopment/meteor-client">Meteor Client</a> for Minecraft Fabric,
built for anarchy servers with a focus on hiding the fact you're running a modified client.
</p>

<div align="center">
    <a href="https://discord.gg/bBGQZvd"><img src="https://img.shields.io/discord/689197705683140636?logo=discord" alt="Discord"/></a>
    <br>
    <img src="https://img.shields.io/github/last-commit/MeteorDevelopment/meteor-client" alt="GitHub last commit"/>
    <img src="https://img.shields.io/github/commit-activity/w/MeteorDevelopment/meteor-client" alt="GitHub commit activity"/>
    <img src="https://img.shields.io/github/contributors/MeteorDevelopment/meteor-client" alt="GitHub contributors"/>
    <br>
    <img src="https://img.shields.io/github/languages/code-size/MeteorDevelopment/meteor-client" alt="GitHub code size in bytes"/>
    <img src="https://img.shields.io/endpoint?url=https://ghloc.vercel.app/api/MeteorDevelopment/meteor-client/badge?filter=.java$&label=lines%20of%20code&color=blue" alt="GitHub lines of code"/>
</div>

---

## About This Fork

Hidden-Meteor is a fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) with additional
anti-detection modules and a mod identity spoofing system designed to keep your client hidden from detection.
All fork ***module*** additions live under the `Anti-detection` category in the module list.

---

<h2 align="center">Fork Additions</h2>

---

### Empty Screenshots

When you press F2, this module intercepts the screenshot key before Minecraft handles it,
temporarily disables all active modules and hides the HUD, waits a configurable number of frames
for a clean render, then captures the screenshot and restores everything automatically.

Your screenshots will never contain Meteor overlays, HUD elements, or module indicators.

**Settings**

- `disable-modules` - temporarily disables all active modules before the shot is taken (default: on)
- `disable-hud` - temporarily hides the HUD (default: on)
- `customize-pending-frames` - reveals a slider to manually set how many frames to wait before
  capturing; useful if screenshots are coming out blank (default: off, auto uses 5 frames)
- `pending-frames` - number of frames to wait; increase if shots come out empty (visible only
  when `customize-pending-frames` is on, default: 4)
- `module-exceptions` - modules that will not be disabled during a screenshot

---

### Panic

A one-shot trigger that instantly wipes all visible traces of Meteor.
Toggling it on runs the cleanup and then immediately toggles itself back off,
so it always acts as a single-fire action rather than a persistent state.

**What it does on activation**

- Closes any open Meteor screen
- Enables `HideKeybinds` first (so it stays on through the rest of the cleanup)
- Disables all active modules except `HideKeybinds`, `FixInconsistencies`, and anything in your exceptions list
- Hides the HUD
- Unbinds the GUI and command keys, and clears keybinds from all non-exempt modules
- Hides the proxies and account buttons from the main menu
- Disables Meteor chat feedback messages
- Clears existing chat messages that contain `[Meteor]`
- Sanitizes `latest.log` and `debug.log` on the spot
- Optionally writes a flag file so the cleaned state persists across game restarts

**Settings**

- `disable-modules` - disables all active modules (default: on)
- `disable-commands` - blocks Meteor chat commands from running after panic (default: on)
- `disable-hud` - hides the HUD (default: on)
- `clear-keybinds` - unbinds the GUI/command keys and wipes all module keybinds (default: on)
- `hide-keybinds-from-list` - enables `HideKeybinds` as part of cleanup (default: on)
- `hide-gui-buttons` - hides the proxies and account buttons (default: on)
- `disable-chat-feedback` - turns off Meteor chat messages (default: on)
- `clear-meteor-chat-feedback` - deletes existing `[Meteor]` messages from chat (default: on)
- `close-screen` - closes any open GUI screen immediately (default: on)
- `persist-across-restarts` - writes a flag file so the cleaned state survives a restart (default: off)
- `module-exceptions` - modules that will not be disabled or unbound when panic fires

When `persist-across-restarts` is on, a hidden flag file is written to your config directory.
On the next launch, the flag is detected and deleted, and the HUD, chat feedback, GUI buttons,
and command access are all restored automatically.

---

### Hide Keybinds

Removes Meteor's keybind entries (Open GUI, Open Commands) from the vanilla Options → Controls screen,
so a shoulder-surf or screen share will not reveal a modified client through its listed keybinds.
The keys continue to work normally in-game; only their entries in the Controls list are hidden.
Enabled by default.

**Settings**

- `strip-options-file` - also removes the Meteor keybind lines from `options.txt` on enable and
  on boot, in case Minecraft regenerated them (default: on). Uses an atomic write so a crash
  mid-rewrite cannot corrupt the file.

---

### Fix Inconsistencies

Fixes visual inconsistencies that could reveal the presence of a modified client.

**Settings**

- `spoof-mod-amount` - subtracts 11 from the mod count shown in the bottom-left of the title screen
  (e.g. `Minecraft 1.21 (15 mods)` becomes `4 mods`) to match a clean Lithium install. Enabled by default.

The offset of 11 accounts for Meteor and all of its bundled internal dependencies.
Singular/plural is handled correctly, so `1 mods` will never appear.

---

### Resist Translation Detection

Blocks servers from detecting Meteor via the Sign Translation Vulnerability (MC-265322).

Servers exploiting this vulnerability silently force-open the sign editor on the client, which
resolves any translation or keybind keys written on the sign into plain text before sending them
back. Since Meteor registers its own translation and keybind keys, a server can check whether
those keys resolve to see if the client is running Meteor.

This module intercepts resolution of all Meteor-owned translation and keybind keys at the
`TranslatableContents` and `KeybindContents` level, returning the raw key string instead of the
translated value. From the server's perspective the keys are unknown, exactly as they would be
on a vanilla client with no Meteor installed. Enabled by default and active in the main menu.

---

### Detection Hardening

A collection of small, targeted fixes that close detection channels beyond the Sign Translation
Vulnerability. None of these require a module toggle.

#### Telemetry Suppression

Meteor's original `OnlinePlayers` class periodically POSTs to `https://meteorclient.com/api/online/ping`
every five minutes and sends a final POST to `…/leave` on disconnect. Any server-side plugin that
logs or inspects outgoing HTTP traffic (or any network-level monitor) would immediately identify
these requests as coming from a Meteor instance.

Both `update()` and `leave()` are now no-ops; the requests are never made.

**File changed:** [`OnlinePlayers.java`](file:///C:/Users/Bonte/Documents/Projects/Hidden-meteor/src/main/java/meteordevelopment/meteorclient/utils/network/OnlinePlayers.java)

#### Crash Report Suppression

Minecraft crash reports include an optional client details section. Previously, `CrashReportMixin`
appended a dedicated section `"-- Meteor Client --"` listing client version, build number, and all
active module and HUD element names. If a player's client crashed or a crash log was shared,
this section would conclusively identify Meteor Client.

`CrashReportMixin` has been completely removed from the mixin configuration, ensuring no
custom client sections or module telemetry are injected into Minecraft crash reports.

**File changed:** [`meteor-client.mixins.json`](file:///C:/Users/Bonte/Documents/Projects/Hidden-meteor/src/main/resources/meteor-client.mixins.json)

#### Keybind Category Key Gap

`ResistTranslationDetection.shouldBlock()` previously only matched keys starting with
`"key.meteor-client."`, leaving the keybind *category* translation key
`"key.category.meteor-client.meteor-client"` unblocked. If a server sent that exact key via
the sign translation exploit it would resolve to `"Meteor Client"` on an unpatched client.

The prefix `"key.category.meteor-client."` has been added to the block-list so the category
key is also returned as-is rather than translated.

**File changed:** [`ResistTranslationDetection.java`](file:///C:/Users/Bonte/Documents/Projects/Hidden-meteor/src/main/java/meteordevelopment/meteorclient/systems/modules/Anti_detection/ResistTranslationDetection.java)

---

### Hitboxes

Adds a `spoof-visual-size` setting to the existing Hitboxes module. When enabled, the F3+B debug
view renders hitboxes at their original un-expanded size, hiding the expansion from view.

**Settings**

- `spoof-visual-size` - hides the hitbox expansion from the F3+B debug view so the visual size appears normal (default: on)

---

### Mod Spoofing

Hidden-Meteor spoofs its mod identity to appear as a different, innocent-looking mod to anything
that queries the Fabric mod list. By default it impersonates
[Lithium](https://github.com/CaffeineMC/lithium-fabric), a well-known server optimization mod.

To change what mod it impersonates, update the following files:

#### 1. `fabric.mod.json`

Change these fields to match your target mod:

```json
"name": "Lithium",
"id": "lithium",
"description": "No-bullshit, game-changing performance enhancements for Minecraft.",
"authors": ["JellySquid", "2No2Name"],
"contact": {
    "homepage": "https://jellysquid.me/projects/",
    "sources": "https://github.com/CaffeineMC/lithium-fabric",
    "issues": "https://github.com/CaffeineMC/lithium-fabric/issues"
},
"license": "LGPL-3.0-only",
"icon": "assets/lithium/icon.png",
"environment": "",
"custom": {
    "lithium:build_number": "${build_number}",
    "lithium:color": "145,61,226",
    "lithium:commit": "${commit}"
}
```

Remove or rename the `custom` block keys if impersonating a mod that does not use them.

#### 2. Assets folder

The assets folder name must match your chosen mod ID. Rename:

```
src/main/resources/assets/lithium/
```

For example, if spoofing Sodium (`"id": "sodium"`), rename it to:

```
src/main/resources/assets/sodium/
```

Then update the `"icon"` field in `fabric.mod.json` to match, e.g. `"assets/sodium/icon.png"`.

#### 3. Icon

Replace the icon at:

```
src/main/resources/assets/lithium/icon.png
```

Use the real icon of the mod you are impersonating. Square PNG, ideally 512×512.

#### 4. Log Sanitizer

The log sanitizer strips Meteor-related tokens from `latest.log` and `debug.log` on startup,
shutdown, and whenever Panic fires. If spoofing a different mod, add its ID to `DENIED_TOKENS`:

```java
private static final String[] DENIED_TOKENS = {
    "lithium",      // add your spoof target's mod ID here
    "[Meteor]"
};
```

Located at:

```
src/main/java/meteordevelopment/meteorclient/utils/misc/LogSanitizer.java
```

#### 5. Fix Inconsistencies

Meteor bundles multiple internal dependencies, adding 11 entries to the mod count shown on the
title screen. The `spoof-mod-amount` setting in `FixInconsistencies` subtracts exactly 11 to keep
the displayed count consistent with your spoofed identity. This is enabled by default and requires
no extra configuration when switching spoof targets.

---

## Usage

### Building

```bash
git clone <repo>
cd hidden-meteor
./gradlew build
```

### Installation

Follow the [installation guide](https://meteorclient.com/faq/installation) on the Meteor wiki.

---

## Contributions

Pull requests are welcome as long as the following guidelines are met:

- The license header must be present in all Java source files.
- IDE and system files belong in `.gitignore`, never in commits.
- Match the existing code style as closely as reasonable.
- Favour readability over compactness.
- See the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for reference.

Bug reports and suggestions go in the [issue tracker](https://github.com/MeteorDevelopment/meteor-client/issues).
Please include as much detail as possible.

---

## Licensing

This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).

If you use any code from this source:

- You must disclose the source code of your modified work and the source code taken from this project.
  Closed-source or obfuscated use is not permitted.
- You must clearly inform all end users that you are using code from this project.
- Your application must be licensed under the same license.

---

## Credits

[MeteorDevelopment](https://github.com/MeteorDevelopment) for [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), which this fork is based on  
[Cabaletta](https://github.com/cabaletta) and [WagYourTail](https://github.com/wagyourtail) for [Baritone](https://github.com/cabaletta/baritone)  
The [Fabric Team](https://github.com/FabricMC) for [Fabric](https://github.com/FabricMC/fabric-loader) and [Yarn](https://github.com/FabricMC/yarn)

*Questions? Check the [FAQ](https://meteorclient.com/faq) or join the [Discord](https://meteorclient.com/discord).*
