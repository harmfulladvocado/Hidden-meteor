/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Panic extends Module {
    public static boolean COMMANDS_DISABLED = false;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgExceptions = settings.createGroup("Exceptions");

    private final Setting<Boolean> disableModules = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-modules")
        .description("Disables all active modules.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableCommands = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-commands")
        .description("Blocks meteor chat commands from being run.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableHud = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-hud")
        .description("Disables the HUD.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clearKeybinds = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-keybinds")
        .description("Clears all module keybinds and unbinds the GUI/commands keys.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideKeybindsFromList = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-keybinds-from-list")
        .description("Removes the Meteor category from the controls screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideGui = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-gui-buttons")
        .description("Hides the proxies and account buttons.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableChatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-chat-feedback")
        .description("Disables meteor chat feedback messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clearChatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-meteor-chat-feedback")
        .description("Deletes all existing chat messages containing '[Meteor]'.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> closeScreen = sgGeneral.add(new BoolSetting.Builder()
        .name("close-screen")
        .description("Closes any open GUI screen immediately.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> persistAcrossRestarts = sgGeneral.add(new BoolSetting.Builder()
        .name("persist-across-restarts")
        .description("Keeps panic active across game restarts via a flag file.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Module>> moduleExceptions = sgExceptions.add(new ModuleListSetting.Builder()
        .name("module-exceptions")
        .description("Modules that will NOT be disabled or unbound when panic is triggered.")
        .defaultValue(new java.util.ArrayList<>())
        .build()
    );

    public Panic() {
        super(Categories.Anti_detection, "panic", "Disables the mod, restart to revert.");
        this.runInMainMenu = true;
        this.chatFeedback = false;
    }

    public static void onStartup() {
        try {
            Path flag = FabricLoader.getInstance().getConfigDir().resolve(".lithium_panic");
            if (Files.exists(flag)) {
                Files.delete(flag);

                // Restore HUD
                Hud.get().active = true;

                // Restore chat feedback
                Config.get().chatFeedback.set(true);

                // Restore GUI buttons
                Config config = Config.get();
                config.proxiesButtonAnchor.set(Config.ButtonPosition.TopLeft);
                config.accountButtonAnchor.set(Config.ButtonPosition.TopLeft);
                config.showProxiesStatus.set(true);
                config.showAccountStatus.set(true);

                // Re-enable commands
                COMMANDS_DISABLED = false;
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onActivate() {
        if (closeScreen.get() && mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }

        // Enable HideKeybinds first, before disabling other modules, so it stays on
        if (hideKeybindsFromList.get()) {
            HideKeybinds hideKeybinds = Modules.get().get(HideKeybinds.class);
            if (hideKeybinds != null && !hideKeybinds.isActive()) {
                hideKeybinds.enable();
            }
        }

        // Disable all active modules except HideKeybinds, FixInconsistencies and exceptions
        if (disableModules.get()) {
            for (Module module : Modules.get().getAll()) {
                if (module == this) continue;
                if (module instanceof HideKeybinds || module instanceof FixInconsistencies) continue;
                if (moduleExceptions.get().contains(module)) continue;
                module.disable();
            }
        }

        if (disableHud.get()) {
            Hud.get().active = false;
        }

        if (clearKeybinds.get()) {
            KeyBinds.OPEN_GUI.setKey(InputConstants.UNKNOWN);
            KeyBinds.OPEN_COMMANDS.setKey(InputConstants.UNKNOWN);

            for (Module module : Modules.get().getAll()) {
                if (module instanceof HideKeybinds || module instanceof FixInconsistencies) continue;
                if (moduleExceptions.get().contains(module)) continue;
                module.keybind.set(Keybind.none());
            }
        }

        if (hideGui.get()) {
            Config config = Config.get();
            config.proxiesButtonAnchor.set(Config.ButtonPosition.Hidden);
            config.accountButtonAnchor.set(Config.ButtonPosition.Hidden);
            config.showProxiesStatus.set(false);
            config.showAccountStatus.set(false);
        }

        if (disableChatFeedback.get()) {
            Config.get().chatFeedback.set(false);
        }

        if (clearChatFeedback.get() && mc.gui != null) {
            try {
                for (java.lang.reflect.Field field : mc.gui.getClass().getDeclaredFields()) {
                    if (field.getType() == net.minecraft.client.gui.components.ChatComponent.class) {
                        field.setAccessible(true);
                        net.minecraft.client.gui.components.ChatComponent chat = (net.minecraft.client.gui.components.ChatComponent) field.get(mc.gui);
                        if (chat != null) chat.clearMessages(false);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (persistAcrossRestarts.get()) {
            try {
                Path flag = FabricLoader.getInstance().getConfigDir().resolve(".lithium_panic");
                if (!Files.exists(flag)) Files.createFile(flag);
            } catch (Exception ignored) {}
        }

        if (disableCommands.get()) {
            COMMANDS_DISABLED = true;
        }

        meteordevelopment.meteorclient.utils.misc.LogSanitizer.sanitizeLogFiles();

        this.toggle();
    }

    @Override
    public void onDeactivate() {}
}
