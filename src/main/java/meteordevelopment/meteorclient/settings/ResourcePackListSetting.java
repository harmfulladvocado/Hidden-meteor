/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ResourcePackListSetting extends Setting<List<String>> {
    public ResourcePackListSetting(String name, String description, List<String> defaultValue, Consumer<List<String>> onChanged, Consumer<Setting<List<String>>> onModuleActivated, IVisible visible) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
    }

    @Override
    public void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    @Override
    protected List<String> parseImpl(String str) {
        List<String> ids = new ArrayList<>();

        for (String value : str.split(",")) {
            String id = value.trim();
            if (!id.isEmpty()) ids.add(id);
        }

        return ids;
    }

    @Override
    protected boolean isValueValid(List<String> value) {
        return true;
    }

    @Override
    public Iterable<String> getSuggestions() {
        PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
        if (repository == null) return List.of();

        List<String> ids = new ArrayList<>();
        for (Pack pack : repository.getSelectedPacks()) ids.add(pack.getId());

        return ids;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag valueTag = new ListTag();
        for (String id : get()) valueTag.add(StringTag.valueOf(id));
        tag.put("value", valueTag);

        return tag;
    }

    @Override
    public List<String> load(CompoundTag tag) {
        get().clear();

        for (Tag tagI : tag.getListOrEmpty("value")) get().add(tagI.asString().orElse(""));

        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<String>, ResourcePackListSetting> {
        public Builder() {
            super(new ArrayList<>());
        }

        public Builder defaultValue(String... defaults) {
            return defaultValue(defaults != null ? Arrays.asList(defaults) : new ArrayList<>());
        }

        @Override
        public ResourcePackListSetting build() {
            return new ResourcePackListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible);
        }
    }
}
