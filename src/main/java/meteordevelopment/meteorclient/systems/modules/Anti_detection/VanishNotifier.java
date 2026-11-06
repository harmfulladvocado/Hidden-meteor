/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import meteordevelopment.meteorclient.events.entity.EntityDestroyEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.profiles.Profile;
import meteordevelopment.meteorclient.systems.profiles.Profiles;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3d;

import java.util.*;

public class VanishNotifier extends Module {
    public enum DetectionReason {
        CreativeInvisible("Creative + Invisible"),
        Spectator("Spectator"),
        TabVanish("In World but not in Tab"),
        GhostSound("Sound with no nearby Entity");

        private final String title;

        DetectionReason(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum ReactAction {
        None,
        DisableCheats,
        LoadProfile,
        RunCommand,
        Disconnect,
        Panic
    }

    // Setting groups
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgFiltering = settings.createGroup("Filtering");
    private final SettingGroup sgNotification = settings.createGroup("Notification");
    private final SettingGroup sgReaction = settings.createGroup("Reaction");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Detection Settings
    private final Setting<Double> detectionRadius = sgDetection.add(new DoubleSetting.Builder()
        .name("detection-radius")
        .description("Maximum distance around you to check for vanished players.")
        .defaultValue(64.0)
        .min(1.0)
        .sliderRange(5.0, 128.0)
        .build()
    );

    private final Setting<Double> keepAfterLost = sgDetection.add(new DoubleSetting.Builder()
        .name("keep-after-lost")
        .description("Seconds to keep showing a vanished player's last known location. 0 = until they return.")
        .defaultValue(120.0)
        .min(0.0)
        .sliderRange(0.0, 300.0)
        .build()
    );

    private final Setting<Boolean> detectCreativeInvis = sgDetection.add(new BoolSetting.Builder()
        .name("detect-creative-invisible")
        .description("Detect players who are both in Creative mode and invisible.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSpectator = sgDetection.add(new BoolSetting.Builder()
        .name("detect-spectator")
        .description("Detect players who are in Spectator mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectMissingFromTab = sgDetection.add(new BoolSetting.Builder()
        .name("detect-missing-from-tab")
        .description("Detect players rendered in world but missing from the player tab list (classic vanish plugins).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectGhostSounds = sgDetection.add(new BoolSetting.Builder()
        .name("detect-ghost-sounds")
        .description("Detect player-like sounds (footsteps, doors, chests, eating) emitted where no visible player exists.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> ghostSoundRadius = sgDetection.add(new DoubleSetting.Builder()
        .name("ghost-sound-radius")
        .description("Detection radius around you for ghost sounds.")
        .defaultValue(32.0)
        .min(1.0)
        .sliderRange(5.0, 64.0)
        .visible(detectGhostSounds::get)
        .build()
    );

    // Filtering Settings
    private final Setting<Boolean> ignoreFriends = sgFiltering.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not trigger on players on your friends list.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgFiltering.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Do not trigger on yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> ignoredNames = sgFiltering.add(new StringListSetting.Builder()
        .name("ignored-names")
        .description("Specific player names/usernames to ignore.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    // Notification Settings
    private final Setting<Boolean> chatAlert = sgNotification.add(new BoolSetting.Builder()
        .name("chat-alert")
        .description("Send an alert message in client chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> playSoundAlert = sgNotification.add(new BoolSetting.Builder()
        .name("sound-alert")
        .description("Play an audible warning chime when vanish is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> alertCooldown = sgNotification.add(new DoubleSetting.Builder()
        .name("alert-cooldown")
        .description("Seconds before notifying again for the same player.")
        .defaultValue(5.0)
        .min(0.5)
        .sliderRange(1.0, 30.0)
        .build()
    );

    // Reaction Settings
    private final Setting<ReactAction> reactionAction = sgReaction.add(new EnumSetting.Builder<ReactAction>()
        .name("reaction-action")
        .description("Automatic reaction behavior upon detecting vanish.")
        .defaultValue(ReactAction.DisableCheats)
        .build()
    );

    private final Setting<List<Module>> cheatsToDisable = sgReaction.add(new ModuleListSetting.Builder()
        .name("cheats-to-disable")
        .description("Specific modules to turn off when vanish is detected.")
        .defaultValue(new ArrayList<>())
        .visible(() -> reactionAction.get() == ReactAction.DisableCheats)
        .build()
    );

    private final Setting<String> profileToLoad = sgReaction.add(new ProvidedStringSetting.Builder()
        .name("profile-to-load")
        .description("The profile name to automatically load.")
        .defaultValue("")
        .supplier(this::getProfileNames)
        .visible(() -> reactionAction.get() == ReactAction.LoadProfile)
        .build()
    );

    private final Setting<String> customCommand = sgReaction.add(new StringSetting.Builder()
        .name("custom-command")
        .description("Command to execute (e.g. /spawn, /home, /tp).")
        .defaultValue("/spawn")
        .visible(() -> reactionAction.get() == ReactAction.RunCommand)
        .build()
    );

    private final Setting<String> disconnectReason = sgReaction.add(new StringSetting.Builder()
        .name("disconnect-reason")
        .description("Reason message displayed on disconnect.")
        .defaultValue("Timed out.")
        .visible(() -> reactionAction.get() == ReactAction.Disconnect)
        .build()
    );

    // Render Settings
    private final Setting<Boolean> renderBox = sgRender.add(new BoolSetting.Builder()
        .name("render-box")
        .description("Render a bounding box around the detected vanish location.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the bounding box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(renderBox::get)
        .build()
    );

    private final Setting<Double> boxScale = sgRender.add(new DoubleSetting.Builder()
        .name("box-scale-expansion")
        .description("Expand box dimensions to make it large and conspicuous.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderRange(0.0, 2.0)
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the box sides.")
        .defaultValue(new SettingColor(255, 0, 0, 45))
        .visible(renderBox::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the box lines.")
        .defaultValue(new SettingColor(255, 20, 20, 255))
        .visible(renderBox::get)
        .build()
    );

    private final Setting<Boolean> renderNametag = sgRender.add(new BoolSetting.Builder()
        .name("render-nametag")
        .description("Render floating text showing the vanisher's name and detection reason.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> nametagScale = sgRender.add(new DoubleSetting.Builder()
        .name("nametag-scale")
        .description("Scale of the vanish indicator nametag.")
        .defaultValue(1.25)
        .min(0.5)
        .sliderRange(0.5, 3.0)
        .visible(renderNametag::get)
        .build()
    );

    // Internal State
    private static class VanishTarget {
        final UUID uuid;
        final String name;
        final DetectionReason reason;
        double x, y, z;
        double width, height;
        long firstDetected;
        long lastSeen;
        long lastAlerted;

        VanishTarget(UUID uuid, String name, DetectionReason reason, double x, double y, double z, double width, double height) {
            this.uuid = uuid;
            this.name = name;
            this.reason = reason;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.firstDetected = System.currentTimeMillis();
            this.lastSeen = this.firstDetected;
            this.lastAlerted = 0;
        }
    }

    private static class PendingTarget {
        final UUID uuid;
        final String name;
        final DetectionReason reason;
        final double x, y, z;
        final double width, height;
        final long confirmAt;

        PendingTarget(UUID uuid, String name, DetectionReason reason, double x, double y, double z, double width, double height, long confirmAt) {
            this.uuid = uuid;
            this.name = name;
            this.reason = reason;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.confirmAt = confirmAt;
        }
    }

    private static final long TAB_VANISH_CONFIRM_MS = 1000L;
    private static final long UNVANISH_GRACE_MS = 500L;

    private final Map<UUID, VanishTarget> activeTargets = new HashMap<>();
    private final Map<UUID, PendingTarget> pendingTargets = new HashMap<>();
    private final Map<UUID, Long> tabVanishSince = new HashMap<>();
    private final Vector3d nametagPos = new Vector3d();
    private boolean reactionTriggered = false;

    public VanishNotifier() {
        super(Categories.Anti_detection, "vanish-notifier", "Detects vanished staff/players near you and triggers customizable alerts & reactions.");
        defaultActive = false;
    }

    @Override
    public void onActivate() {
        activeTargets.clear();
        pendingTargets.clear();
        tabVanishSince.clear();
        reactionTriggered = false;
    }

    @Override
    public void onDeactivate() {
        activeTargets.clear();
        pendingTargets.clear();
        tabVanishSince.clear();
        reactionTriggered = false;
    }

    private String[] getProfileNames() {
        if (Profiles.get() == null) return new String[0];
        List<String> names = new ArrayList<>();
        for (Profile p : Profiles.get()) {
            if (p != null && p.name != null && p.name.get() != null) {
                names.add(p.name.get());
            }
        }
        return names.toArray(new String[0]);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        double maxDistSq = detectionRadius.get() * detectionRadius.get();
        long now = System.currentTimeMillis();

        Set<UUID> seenThisTick = new HashSet<>();

        confirmPendingTargets(now);

        for (Player player : mc.level.players()) {
            if (!isTrackable(player)) continue;
            if (player.distanceToSqr(mc.player) > maxDistSq) continue;

            DetectionReason reason = classify(player);
            if (reason == null) {
                tabVanishSince.remove(player.getUUID());
                continue;
            }

            // entity present but missing from tab: confirm it isn't just a leave race
            if (reason == DetectionReason.TabVanish && !activeTargets.containsKey(player.getUUID())) {
                long since = tabVanishSince.computeIfAbsent(player.getUUID(), k -> now);
                if (now - since < TAB_VANISH_CONFIRM_MS) continue;
            } else {
                tabVanishSince.remove(player.getUUID());
            }

            seenThisTick.add(player.getUUID());

            VanishTarget target = activeTargets.get(player.getUUID());
            if (target == null) {
                target = createTarget(player, reason);
                activeTargets.put(player.getUUID(), target);
                triggerDetection(target);
            } else {
                updateTarget(target, player, now);

                if (now - target.lastAlerted >= (long) (alertCooldown.get() * 1000.0)) {
                    triggerNotification(target);
                }
            }
        }

        pruneTargets(now, seenThisTick);
        tabVanishSince.keySet().removeIf(uuid -> mc.level.getPlayerByUUID(uuid) == null);
    }

    private void confirmPendingTargets(long now) {
        pendingTargets.entrySet().removeIf(entry -> {
            PendingTarget pending = entry.getValue();
            if (now < pending.confirmAt) return false;

            // still in tab = still connected, entity gone = hidden from world
            if (mc.level.getPlayerByUUID(pending.uuid) == null && isInTab(pending.uuid)) {
                VanishTarget target = activeTargets.get(pending.uuid);
                if (target == null) {
                    target = new VanishTarget(
                        pending.uuid,
                        pending.name,
                        pending.reason,
                        pending.x,
                        pending.y,
                        pending.z,
                        pending.width,
                        pending.height
                    );
                    activeTargets.put(pending.uuid, target);
                    triggerDetection(target);
                } else {
                    target.lastSeen = now;
                }
            }

            return true;
        });
    }

    private void pruneTargets(long now, Set<UUID> seenThisTick) {
        double keepSeconds = keepAfterLost.get();

        activeTargets.entrySet().removeIf(entry -> {
            VanishTarget target = entry.getValue();

            if (target.reason == DetectionReason.GhostSound) {
                return now - target.lastSeen > 4000L;
            }

            boolean entityPresent = mc.level.getPlayerByUUID(target.uuid) != null;
            if (entityPresent) {
                // seen again as a normal player = no longer vanished
                return !seenThisTick.contains(target.uuid) && now - target.lastSeen > UNVANISH_GRACE_MS;
            }

            // entity gone: keep last known spot instead of forgetting it
            return keepSeconds > 0 && now - target.lastSeen > (long) (keepSeconds * 1000.0);
        });

        if (activeTargets.isEmpty()) {
            reactionTriggered = false;
        }
    }

    private DetectionReason classify(Player player) {
        if (detectCreativeInvis.get() && player.isCreative() && player.isInvisible()) {
            return DetectionReason.CreativeInvisible;
        }

        if (detectSpectator.get() && player.isSpectator()) {
            return DetectionReason.Spectator;
        }

        if (detectMissingFromTab.get() && !isInTab(player.getUUID())) {
            return DetectionReason.TabVanish;
        }

        return null;
    }

    private boolean isTrackable(Player player) {
        if (player == null || player.isRemoved()) return false;
        if (ignoreSelf.get() && player == mc.player) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;

        return !isNameIgnored(player.getName().getString());
    }

    private boolean isInTab(UUID uuid) {
        return mc.getConnection() == null || mc.getConnection().getPlayerInfo(uuid) != null;
    }

    private VanishTarget createTarget(Player player, DetectionReason reason) {
        return new VanishTarget(
            player.getUUID(),
            player.getName().getString(),
            reason,
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getBbWidth(),
            player.getBbHeight()
        );
    }

    private void updateTarget(VanishTarget target, Player player, long now) {
        target.x = player.getX();
        target.y = player.getY();
        target.z = player.getZ();
        target.width = player.getBbWidth();
        target.height = player.getBbHeight();
        target.lastSeen = now;
    }

    private boolean isNameIgnored(String name) {
        if (name == null || name.isEmpty()) return false;
        for (String ignored : ignoredNames.get()) {
            if (name.equalsIgnoreCase(ignored.trim())) return true;
        }
        return false;
    }

    @EventHandler
    private void onEntityDestroy(EntityDestroyEvent event) {
        if (!(event.entity instanceof Player player) || mc.player == null || mc.level == null) return;
        if (!isTrackable(player)) return;
        if (player.distanceToSqr(mc.player) > detectionRadius.get() * detectionRadius.get()) return;

        DetectionReason reason;
        if (detectSpectator.get() && player.isSpectator()) {
            reason = DetectionReason.Spectator;
        } else if (detectCreativeInvis.get() && player.isCreative() && player.isInvisible()) {
            reason = DetectionReason.CreativeInvisible;
        } else if (detectMissingFromTab.get() && isInTab(player.getUUID())) {
            // entity hidden while still in tab, confirm tab stays after a leave race
            reason = DetectionReason.TabVanish;
        } else {
            return;
        }

        if (reason == DetectionReason.TabVanish) {
            long now = System.currentTimeMillis();
            pendingTargets.put(player.getUUID(), new PendingTarget(
                player.getUUID(),
                player.getName().getString(),
                reason,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getBbWidth(),
                player.getBbHeight(),
                now + TAB_VANISH_CONFIRM_MS
            ));
            return;
        }

        VanishTarget target = activeTargets.get(player.getUUID());
        if (target == null) {
            target = createTarget(player, reason);
            activeTargets.put(player.getUUID(), target);
            triggerDetection(target);
        } else {
            updateTarget(target, player, System.currentTimeMillis());
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        // Detect ghost sounds: sounds at a location where no player is visible
        if (detectGhostSounds.get() && event.packet instanceof ClientboundSoundPacket soundPacket) {
            String soundPath = soundPacket.getSound().value().location().getPath();
            if (isPlayerSound(soundPath)) {
                double sx = soundPacket.getX();
                double sy = soundPacket.getY();
                double sz = soundPacket.getZ();

                double myDistSq = mc.player.distanceToSqr(sx, sy, sz);
                double maxGhostSq = ghostSoundRadius.get() * ghostSoundRadius.get();

                if (myDistSq > 1.5 && myDistSq <= maxGhostSq) {
                    boolean visiblePlayerNearby = false;
                    for (Player p : mc.level.players()) {
                        if (p == mc.player) continue;
                        if (p.distanceToSqr(sx, sy, sz) <= 4.0 && !p.isInvisible() && !p.isSpectator()) {
                            visiblePlayerNearby = true;
                            break;
                        }
                    }

                    if (!visiblePlayerNearby) {
                        UUID ghostId = UUID.nameUUIDFromBytes(("ghost:" + ((long) (sx * 10)) + ":" + ((long) (sy * 10)) + ":" + ((long) (sz * 10))).getBytes());
                        VanishTarget target = new VanishTarget(
                            ghostId,
                            "Ghost Sound (" + soundPath + ")",
                            DetectionReason.GhostSound,
                            sx,
                            sy,
                            sz,
                            0.6,
                            1.8
                        );
                        activeTargets.put(ghostId, target);
                        triggerDetection(target);
                    }
                }
            }
        }
    }

    private boolean isPlayerSound(String path) {
        if (path == null) return false;
        return path.contains("step")
            || path.contains("chest")
            || path.contains("door")
            || path.contains("eat")
            || path.contains("drink")
            || path.contains("potion")
            || path.contains("equip");
    }

    private void triggerDetection(VanishTarget target) {
        triggerNotification(target);
        triggerReaction(target);
    }

    private void triggerNotification(VanishTarget target) {
        target.lastAlerted = System.currentTimeMillis();

        if (chatAlert.get()) {
            ChatUtils.sendMsg(
                target.uuid.hashCode(),
                ChatFormatting.RED,
                "[VanishNotifier] (highlight)%s(default) detected near you! (%s) at (highlight)%.1f, %.1f, %.1f(default)",
                target.name,
                target.reason.toString(),
                target.x,
                target.y,
                target.z
            );
        }

        if (playSoundAlert.get() && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f));
        }
    }

    private void triggerReaction(VanishTarget target) {
        if (reactionTriggered) return;
        reactionTriggered = true;

        switch (reactionAction.get()) {
            case None -> {}

            case DisableCheats -> {
                int disabled = 0;
                for (Module mod : cheatsToDisable.get()) {
                    if (mod != null && mod.isActive()) {
                        mod.toggle();
                        disabled++;
                    }
                }
                if (chatAlert.get() && disabled > 0) {
                    ChatUtils.infoPrefix("VanishNotifier", "Turned off %d active cheat module(s).", disabled);
                }
            }

            case LoadProfile -> {
                String name = profileToLoad.get();
                if (name != null && !name.isEmpty() && Profiles.get() != null) {
                    Profile profile = Profiles.get().get(name);
                    if (profile != null) {
                        profile.load();
                        if (chatAlert.get()) {
                            ChatUtils.infoPrefix("VanishNotifier", "Loaded profile '%s'.", name);
                        }
                    } else if (chatAlert.get()) {
                        ChatUtils.warningPrefix("VanishNotifier", "Profile '%s' not found.", name);
                    }
                }
            }

            case RunCommand -> {
                String cmd = customCommand.get();
                if (cmd != null && !cmd.isEmpty()) {
                    ChatUtils.sendPlayerMsg(cmd);
                }
            }

            case Disconnect -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(Component.literal(disconnectReason.get())));
                }
            }

            case Panic -> {
                Module panicMod = Modules.get().get(Panic.class);
                if (panicMod != null && !panicMod.isActive()) {
                    panicMod.toggle();
                }
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderBox.get() || activeTargets.isEmpty()) return;

        double expand = boxScale.get();

        for (VanishTarget target : activeTargets.values()) {
            double halfW = (target.width / 2.0) + expand;
            double h = target.height + (expand * 2.0);

            double minX = target.x - halfW;
            double minY = target.y - expand;
            double minZ = target.z - halfW;

            double maxX = target.x + halfW;
            double maxY = target.y + h - expand;
            double maxZ = target.z + halfW;

            event.renderer.box(
                minX, minY, minZ,
                maxX, maxY, maxZ,
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!renderNametag.get() || activeTargets.isEmpty() || mc.player == null) return;

        TextRenderer text = TextRenderer.get();
        double scaleVal = nametagScale.get();

        for (VanishTarget target : activeTargets.values()) {
            nametagPos.set(target.x, target.y + target.height + 0.6, target.z);

            if (!NametagUtils.to2D(nametagPos, scaleVal)) continue;

            NametagUtils.begin(nametagPos, event.graphics);

            String title = target.name;
            String reasonText = "[" + target.reason.toString() + "]";

            double titleW = text.getWidth(title);
            double reasonW = text.getWidth(reasonText);
            double maxW = Math.max(titleW, reasonW);

            double h = text.getHeight() * 2 + 2;
            double halfW = maxW / 2.0;

            // Background quad
            meteordevelopment.meteorclient.renderer.Renderer2D.COLOR.begin();
            meteordevelopment.meteorclient.renderer.Renderer2D.COLOR.quad(
                -halfW - 2, -1,
                maxW + 4, h + 2,
                new Color(0, 0, 0, 160)
            );
            meteordevelopment.meteorclient.renderer.Renderer2D.COLOR.render();

            // Text rendering
            text.begin(event.graphics, 1.0, false, true);
            text.render(title, -titleW / 2.0, 0, Color.RED, true);
            text.render(reasonText, -reasonW / 2.0, text.getHeight() + 1, new Color(255, 180, 80), true);
            text.end();

            NametagUtils.end(event.graphics);
        }
    }
}
