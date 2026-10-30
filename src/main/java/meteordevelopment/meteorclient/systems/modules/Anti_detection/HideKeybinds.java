/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.Anti_detection;

import meteordevelopment.meteorclient.mixin.KeyMappingAccessor;
import meteordevelopment.meteorclient.mixin.OptionsSubScreenAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HideKeybinds extends Module {
    private static final boolean CONTROLLING_PRESENT =
        FabricLoader.getInstance().isModLoaded("controlling");

    private static final Set<String> OPTIONS_KEYS_TO_STRIP = Set.of(
        "key_key.meteor-client.open-gui",
        "key_key.meteor-client.open-commands"
    );

    private static Class<? extends Screen> controllingScreenClass;
    private static boolean wasHiddenBeforeSave = false;

    private static Method controllingKeyEntryListenersRegister;
    private static Method controllingKeyEntryListenersUnregister;
    private static Object controllingEventHandler;

    private final Setting<Boolean> stripOptionsFile = settings.getDefaultGroup().add(
        new BoolSetting.Builder()
            .name("strip-options-file")
            .description("Also removes the Meteor keybind entries from options.txt on enable and on boot.")
            .defaultValue(true)
            .build()
    );

    static {
        // Pre-load Controlling event registration methods via reflection
        if (CONTROLLING_PRESENT) {
            try {
                Class<?> controllingEventsClass = Class.forName("com.blamejared.controlling.api.event.ControllingEvents");
                Class<?> eventClass = Class.forName("net.fabricmc.fabric.api.event.Event");

                Object keyEntryListenersEvent = controllingEventsClass.getField("KEY_ENTRY_LISTENERS_EVENT").get(null);
                controllingKeyEntryListenersRegister = eventClass.getMethod("register", Object.class);
                controllingKeyEntryListenersUnregister = eventClass.getMethod("unregister", Object.class);
            } catch (Exception ignored) {}
        }
    }

    public HideKeybinds() {
        super(Categories.Anti_detection, "hide-keybinds",
            "Removes the Meteor keybind category from the controls screen.");
        runInMainMenu = true;
        defaultActive = true;
    }

    @Override
    public void onActivate() {
        hideKeys();
        if (CONTROLLING_PRESENT) {
            registerControllingHandler();
        }
        refreshScreen();
    }

    @Override
    public void onDeactivate() {
        if (CONTROLLING_PRESENT) {
            unregisterControllingHandler();
        }
        restoreKeys();
        refreshScreen();
    }

    public static void hideKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyMappings != null) {
            List<KeyMapping> list = new ArrayList<>(Arrays.asList(mc.options.keyMappings));
            list.remove(KeyBinds.OPEN_GUI);
            list.remove(KeyBinds.OPEN_COMMANDS);
            ((meteordevelopment.meteorclient.mixin.OptionsAccessor) mc.options).setKeyMappings(list.toArray(KeyMapping[]::new));
        }

        Map<String, KeyMapping> all = KeyMappingAccessor.getKeysById();
        if (all != null) {
            all.remove(KeyBinds.OPEN_GUI.getName());
            all.remove(KeyBinds.OPEN_COMMANDS.getName());
        }

        HideKeybinds instance = Modules.get() != null ? Modules.get().get(HideKeybinds.class) : null;
        if (instance != null && instance.stripOptionsFile.get()) {
            stripOptionsFile();
        }
    }

    public static void restoreKeys() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyMappings != null) {
            List<KeyMapping> list = new ArrayList<>(Arrays.asList(mc.options.keyMappings));
            if (!list.contains(KeyBinds.OPEN_GUI)) list.add(KeyBinds.OPEN_GUI);
            if (!list.contains(KeyBinds.OPEN_COMMANDS)) list.add(KeyBinds.OPEN_COMMANDS);
            ((meteordevelopment.meteorclient.mixin.OptionsAccessor) mc.options).setKeyMappings(list.toArray(KeyMapping[]::new));
        }

        Map<String, KeyMapping> all = KeyMappingAccessor.getKeysById();
        if (all != null) {
            all.put(KeyBinds.OPEN_GUI.getName(), KeyBinds.OPEN_GUI);
            all.put(KeyBinds.OPEN_COMMANDS.getName(), KeyBinds.OPEN_COMMANDS);
        }
    }

    /**
     * Called once on boot (before the module system fully loads) to strip the
     * keybind lines in case Minecraft regenerated them while the module was off.
     * Safe to call even when the module is not yet known to be active; the
     * caller (e.g. a mixin into Options.load) should guard with a stored flag.
     */
    public static void stripOptionsFileIfNeeded() {
        if (Modules.get() == null) return;
        HideKeybinds instance = Modules.get().get(HideKeybinds.class);
        if (instance != null && instance.isActive() && instance.stripOptionsFile.get()) {
            stripOptionsFile();
        }
    }

    /**
     * Rewrites options.txt in-place, dropping any line whose key matches one of
     * the Meteor keybind entries. Uses an atomic write (temp file + move) so a
     * crash mid-write cannot corrupt the file.
     */
    private static void stripOptionsFile() {
        Path optionsFile = FabricLoader.getInstance()
            .getGameDir()
            .resolve("options.txt");

        if (!Files.exists(optionsFile)) return;

        try {
            List<String> lines = Files.readAllLines(optionsFile, StandardCharsets.UTF_8);
            List<String> filtered = new ArrayList<>(lines.size());
            boolean changed = false;

            for (String line : lines) {
                // Each options.txt line is "key:value"; grab the part before the first colon.
                int colon = line.indexOf(':');
                String key = colon >= 0 ? line.substring(0, colon) : line;
                if (OPTIONS_KEYS_TO_STRIP.contains(key)) {
                    changed = true;
                } else {
                    filtered.add(line);
                }
            }

            if (changed) {
                Path tmp = optionsFile.resolveSibling("options.txt.tmp");
                Files.write(tmp, filtered, StandardCharsets.UTF_8);
                Files.move(tmp, optionsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException e) {
            // Non-fatal; worst case the lines stay in the file until the next save cycle.
            e.printStackTrace();
        }
    }

    public static void restoreIfHidden() {
        if (Modules.get() != null && Modules.get().isActive(HideKeybinds.class)) {
            wasHiddenBeforeSave = true;
            restoreKeys();
        }
    }

    public static void rehideIfWasHidden() {
        if (wasHiddenBeforeSave) {
            wasHiddenBeforeSave = false;
            hideKeys();
        }
    }

    private void refreshScreen() {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.gui.screen();
        if (current == null) return;

        Options opts = mc.options;
        Screen parent = null;
        if (current instanceof OptionsSubScreen subScreen) {
            parent = ((OptionsSubScreenAccessor) subScreen).getLastScreen();
        }

        if (CONTROLLING_PRESENT && isControllingScreen(current)) {
            Screen s = buildControllingScreen(parent, opts);
            if (s != null) {
                mc.gui.setScreen(s);
                return;
            }
        }

        if (current instanceof KeyBindsScreen) {
            mc.gui.setScreen(new KeyBindsScreen(parent, opts));
        }
    }

    private static boolean isControllingScreen(Screen screen) {
        Class<? extends Screen> cls = resolveControllingClass();
        return cls != null && cls.isInstance(screen);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Screen> resolveControllingClass() {
        if (controllingScreenClass != null) return controllingScreenClass;
        try {
            controllingScreenClass = (Class<? extends Screen>)
                Class.forName("com.blamejared.controlling.client.NewKeyBindsScreen");
        } catch (ClassNotFoundException ignored) {}
        return controllingScreenClass;
    }

    private static Screen buildControllingScreen(Screen parent, Options opts) {
        Class<? extends Screen> cls = resolveControllingClass();
        if (cls == null) return null;
        try {
            Constructor<? extends Screen> ctor = cls.getConstructor(Screen.class, Options.class);
            return ctor.newInstance(parent, opts);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void registerControllingHandler() {
        try {
            Class<?> controllingEventsClass = Class.forName("com.blamejared.controlling.api.event.ControllingEvents");
            Object keyEntryListenersEvent = controllingEventsClass.getField("KEY_ENTRY_LISTENERS_EVENT").get(null);

            // Create a dynamic proxy handler
            Object handler = createControllingEventHandler();

            // Invoke the register method
            Method registerMethod = keyEntryListenersEvent.getClass().getMethod("register", Object.class);
            registerMethod.invoke(keyEntryListenersEvent, handler);

            controllingEventHandler = handler;
        } catch (Exception ignored) {}
    }

    private void unregisterControllingHandler() {
        if (controllingEventHandler == null) return;
        try {
            Class<?> controllingEventsClass = Class.forName("com.blamejared.controlling.api.event.ControllingEvents");
            Object keyEntryListenersEvent = controllingEventsClass.getField("KEY_ENTRY_LISTENERS_EVENT").get(null);

            Method unregisterMethod = keyEntryListenersEvent.getClass().getMethod("unregister", Object.class);
            unregisterMethod.invoke(keyEntryListenersEvent, controllingEventHandler);

            controllingEventHandler = null;
        } catch (Exception ignored) {}
    }

    private Object createControllingEventHandler() throws Exception {
        // Use a Java proxy to implement the IEventHandler interface
        Class<?> iEventHandlerClass = Class.forName("com.blamejared.controlling.api.event.IEventHandler");
        Class<?> keyEntryListenersEventClass = Class.forName("com.blamejared.controlling.api.event.KeyEntryListenersEvent");

        return java.lang.reflect.Proxy.newProxyInstance(
            HideKeybinds.class.getClassLoader(),
            new Class[]{iEventHandlerClass},
            (proxy, method, args) -> {
                if ("handle".equals(method.getName()) && args.length == 1) {
                    Object event = args[0];

                    // Get the entry from the event
                    Method entryMethod = event.getClass().getMethod("entry");
                    Object entry = entryMethod.invoke(event);

                    // Get the listeners list
                    Method listenersMethod = event.getClass().getMethod("listeners");
                    @SuppressWarnings("unchecked")
                    List<Object> listeners = (List<Object>) listenersMethod.invoke(event);

                    // Remove Meteor keybinds from the list
                    filterMeteorKeybindsFromControlling(entry, listeners);

                    return listeners;
                }
                return null;
            }
        );
    }

    private static void filterMeteorKeybindsFromControlling(Object entry, List<?> listeners) {
        try {
            // Get the KeyMapping from the entry
            Method getKeyMappingMethod = entry.getClass().getMethod("getKeyMapping");
            KeyMapping keyMapping = (KeyMapping) getKeyMappingMethod.invoke(entry);

            // Check if it's a Meteor keybind
            if (keyMapping == KeyBinds.OPEN_GUI || keyMapping == KeyBinds.OPEN_COMMANDS) {
                listeners.clear();
            }
        } catch (Exception ignored) {}
    }
}
