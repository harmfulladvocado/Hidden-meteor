# Hidden-Meteor

A fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) for Minecraft Fabric, built for anarchy servers with a focus on hiding the fact you are running a modified client.

![Java](https://img.shields.io/badge/Java-25-blue)
![Minecraft](https://img.shields.io/badge/Minecraft-26.3-green)
![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.19.5-orange)
![Fabric API](https://img.shields.io/badge/Fabric%20API-0.162.0%2B26.3-purple)

## Contents

- [About this fork](#about-this-fork)
- [Modules](#modules)
- [Detection hardening](#detection-hardening)
- [Mod spoofing](#mod-spoofing)
- [Usage](#usage)
- [Project structure](#project-structure)
- [Contributions](#contributions)
- [Licensing](#licensing)
- [Credits](#credits)

## About this fork

Hidden-Meteor keeps the Meteor Client feature set and adds anti-detection modules under the `Anti-detection` category. The mod also spoofs its Fabric metadata and mixin names so the running client reports as Lithium instead of Meteor.

## Modules

### Empty screenshots

When you press F2, this module intercepts the screenshot key before Minecraft handles it, temporarily disables active modules and hides the HUD, waits for a clean frame, captures the screenshot, and restores everything.

Settings:

- `disable-modules` - temporarily disables active modules before the shot (default: on)
- `disable-hud` - temporarily hides the HUD (default: on)
- `customize-pending-frames` - lets you set the frame delay manually (default: off)
- `pending-frames` - number of frames to wait (default: 4)
- `module-exceptions` - modules that stay enabled during a screenshot

### Panic

A one-shot trigger that wipes visible traces of the client. Toggling it on runs the cleanup and then immediately toggles itself back off.

What it does on activation:

- Closes any open client screen
- Enables `Hide Keybinds` first so it stays on through the cleanup
- Disables active modules except `Hide Keybinds`, `Fix Inconsistencies`, `Resist Translation Detection`, `No Capes`, and your exceptions
- Hides the HUD
- Unbinds the GUI and command keys, and clears keybinds from non-exempt modules
- Hides the proxies and account buttons from the main menu
- Disables client chat feedback
- Clears the chat history
- Sanitizes `latest.log`, `debug.log`, rotated logs, and crash reports
- Optionally deletes crash reports, rotated logs, screenshots, and config contents

Settings:

- `disable-modules` - disables active modules (default: on)
- `disable-commands` - blocks client chat commands (default: on)
- `disable-hud` - hides the HUD (default: on)
- `clear-keybinds` - unbinds the GUI and command keys and wipes module keybinds (default: on)
- `hide-keybinds-from-list` - enables `Hide Keybinds` as part of cleanup (default: on)
- `hide-gui-buttons` - hides the proxies and account buttons (default: on)
- `disable-chat-feedback` - turns off client chat messages (default: on)
- `clear-meteor-chat-feedback` - clears the chat history (default: on)
- `close-screen` - closes any open screen (default: on)
- `persist-across-restarts` - writes a flag file so the cleaned state survives a restart (default: off)
- `delete-crash-reports` - deletes the contents of `crash-reports` (default: on)
- `delete-rotated-logs` - deletes rotated files in `logs` (default: on)
- `delete-screenshots` - deletes the contents of `screenshots` (default: off)
- `clear-config` - deletes the contents of the config folder on shutdown (default: off)
- `module-exceptions` - modules that are not disabled or unbound

When `persist-across-restarts` is on, a hidden flag file is written to the config directory. On the next launch the flag is detected and deleted, and the HUD, chat feedback, GUI buttons, and command access are restored.

### Hide keybinds

Removes the client keybind entries from the vanilla Controls screen so a screen share does not reveal a modified client. The keys still work in game. Enabled by default.

Settings:

- `strip-options-file` - removes the keybind lines from `options.txt` on enable and on boot (default: on)

### Fix inconsistencies

Fixes visual inconsistencies caused by client modifications.

Settings:

- `spoof-mod-amount` - subtracts 11 from the mod count shown on the title screen (default: on)

### Resist translation detection

Blocks servers from detecting the client through the sign translation vulnerability (MC-265322). Translation and keybind keys owned by the client are returned as raw keys instead of translated text. Enabled by default and active in the main menu.

### No capes

Disables Meteor capes and the HTTP requests to `meteorclient.com` used to load cape owners and textures. The module is enabled by default. Enabling capes at runtime requires disabling this module.

### Hide resource packs

Hides selected resource packs from the pack selection list. Hold the reveal key to show them again.

Settings:

- `packs` - resource packs to hide
- `reveal-key` - key to hold to show hidden packs

## Detection hardening

### Telemetry suppression

`OnlinePlayers.update()` and `OnlinePlayers.leave()` are no-ops. The client does not send the periodic ping or leave requests to `meteorclient.com`.

### Cape requests

Cape owner and texture requests to `meteorclient.com` are skipped while `No Capes` is enabled, which is the default.

### Crash report suppression

The crash report mixin has been removed from the mixin configuration. Crash reports do not include client version, build number, module names, or HUD element names.

### Mixin names

Mixin configs, the mixin package, the mixin plugin, and the access widener use `lithium` names instead of Meteor names. Mixin errors report paths such as `lithium.mixin.*` and `lithium.MixinPlugin`.

### Keybind keys

Keybind translation keys now use `lithium` names. The translation detection block list covers both the old Meteor keys and the new Lithium keys.

### Title screen and Discord

Title screen credits are disabled. Discord presence is not enabled automatically on first launch.

### Hitboxes

Adds a `spoof-visual-size` setting to the existing Hitboxes module. When enabled, the F3+B debug view renders hitboxes at their original unexpanded size.

Settings:

- `spoof-visual-size` - hides the hitbox expansion from the F3+B debug view (default: on)

## Mod spoofing

The fork reports itself as [Lithium](https://github.com/CaffeineMC/lithium-fabric). To change the spoof target, update these files.

### Metadata

`src/main/resources/fabric.mod.json` contains the spoofed name, id, description, authors, contact links, license, icon, entrypoint, mixin list, and access widener.

### Assets

The assets folder must match the mod id:

```text
src/main/resources/assets/lithium/
```

Update the `icon` field in `fabric.mod.json` if you rename the folder.

### Mixins

Mixin configs are named `lithium*.mixins.json`, and their package and plugin fields point at `lithium.mixin` and `lithium.MixinPlugin`.

### Access widener

`src/main/resources/lithium.classtweaker` is the access widener used by `build.gradle.kts`.

### Log sanitizer

The log sanitizer strips Meteor-related tokens from `latest.log`, `debug.log`, rotated logs, and crash reports. If you spoof a different mod, add its id to `DENIED_TOKENS` in `src/main/java/meteordevelopment/meteorclient/utils/misc/LogSanitizer.java`.

### Fix inconsistencies

`Fix Inconsistencies` subtracts 11 from the mod count shown on the title screen to match a clean Lithium install.

## Usage

### Building

1. Clone the repository.
2. Run the build.

```bash
git clone <repo>
cd hidden-meteor
./gradlew build
```

The built jar is in `build/libs/`.

### Installation

Place the jar in your Fabric `mods` folder and launch the game with Fabric Loader 0.19.5 or newer.

This mod uses the `lithium` mod id, so it cannot be installed alongside the real Lithium optimization mod. Remove Lithium before launching.

## Project structure

```text
src/main/java/lithium/mixin/                  Mixin classes
src/main/java/lithium/mixininterface/         Mixin accessor interfaces
src/main/java/meteordevelopment/meteorclient/ Client source
src/main/java/meteordevelopment/meteorclient/systems/modules/Anti_detection/ Anti-detection modules
src/main/resources/                           Metadata, mixin configs, assets
src/launcher/java/lithium/                    Standalone jar launcher
```

## Contributions

Pull requests are welcome when these guidelines are met:

- The license header is present in all Java source files.
- IDE and system files stay in `.gitignore`.
- The existing code style is followed.
- Readability is preferred over compactness.

See the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for reference.

## Licensing

This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).

If you use code from this source:

- You must disclose the source code of your modified work and the source code taken from this project.
- You must inform all end users that you are using code from this project.
- Your application must be licensed under the same license.

See [LICENSE](./LICENSE) for the full text.

## Credits

[MeteorDevelopment](https://github.com/MeteorDevelopment) for [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), which this fork is based on

[Cabaletta](https://github.com/cabaletta) and [WagYourTail](https://github.com/wagyourtail) for [Baritone](https://github.com/cabaletta/baritone)

The [Fabric Team](https://github.com/FabricMC) for [Fabric](https://github.com/FabricMC/fabric-loader) and [Yarn](https://github.com/FabricMC/yarn)
