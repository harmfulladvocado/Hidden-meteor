/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package lithium;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {
    private static final String mixinPackage = "lithium.mixin";

    private static boolean loaded;

    private static boolean isOriginsPresent;
    private static boolean isIndigoPresent;
    public static boolean isSodiumPresent;
    private static boolean isLithiumPresent;
    public static boolean isIrisPresent;
    private static boolean isBaritonePresent;
    public static boolean isControllingPresent;
    private static boolean isMetallumPresent;

    @Override
    public void onLoad(String mixinPackage) {
        if (loaded) return;

        isIndigoPresent = FabricLoader.getInstance().isModLoaded("fabric-renderer-indigo");
        isOriginsPresent = FabricLoader.getInstance().isModLoaded("origins");
        isSodiumPresent = FabricLoader.getInstance().isModLoaded("sodium");
        // our id is lithium, so check the real mod class instead
        isLithiumPresent = isClassPresent("net.caffeinemc.mods.lithium.common.LithiumMod");
        isIrisPresent = FabricLoader.getInstance().isModLoaded("iris");
        isBaritonePresent = FabricLoader.getInstance().isModLoaded("baritone") || FabricLoader.getInstance().isModLoaded("baritone-meteor");
        isControllingPresent = FabricLoader.getInstance().isModLoaded("controlling");
        isMetallumPresent = FabricLoader.getInstance().isModLoaded("metallum");

        meteordevelopment.meteorclient.utils.misc.LogSanitizer.install();

        loaded = true;
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, MixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(mixinPackage)) {
            throw new RuntimeException("Mixin " + mixinClassName + " is not in the mixin package");
        } else if (mixinClassName.endsWith("PlayerEntityRendererMixin")) {
            return !isOriginsPresent;
        } else if (mixinClassName.startsWith(mixinPackage + ".sodium")) {
            return isSodiumPresent;
        } else if (mixinClassName.startsWith(mixinPackage + ".indigo")) {
            return isIndigoPresent;
        } else if (mixinClassName.startsWith(mixinPackage + ".lithium")) {
            return isLithiumPresent;
        } else if (mixinClassName.startsWith(mixinPackage + ".baritone")) {
            return isBaritonePresent;
        } else if (mixinClassName.startsWith(mixinPackage + ".controlling")) {
            return isControllingPresent;
        } else if (mixinClassName.startsWith(mixinPackage + ".metallum")) {
            return isMetallumPresent;
        }


        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
