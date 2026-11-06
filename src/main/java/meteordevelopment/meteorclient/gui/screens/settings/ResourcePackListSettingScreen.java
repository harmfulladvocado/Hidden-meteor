/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.settings.base.CollectionListSettingScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.ResourcePackListSetting;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ResourcePackListSettingScreen extends CollectionListSettingScreen<Pack> {
    private final ResourcePackListSetting packSetting;

    public ResourcePackListSettingScreen(GuiTheme theme, ResourcePackListSetting setting) {
        super(theme, "Select Resource Packs", setting, packs(setting), availablePacks());

        packSetting = setting;
    }

    private static List<Pack> packs(ResourcePackListSetting setting) {
        List<Pack> packs = new ArrayList<>();
        PackRepository repository = mc.getResourcePackRepository();
        if (repository == null) return packs;

        for (String id : setting.get()) {
            Pack pack = repository.getPack(id);
            if (pack != null) packs.add(pack);
        }

        return packs;
    }

    private static List<Pack> availablePacks() {
        PackRepository repository = mc.getResourcePackRepository();
        return repository == null ? new ArrayList<>() : new ArrayList<>(repository.getAvailablePacks());
    }

    @Override
    protected void addValue(Pack value) {
        if (collection.contains(value)) return;

        collection.add(value);
        syncSetting();
    }

    @Override
    protected void removeValue(Pack value) {
        if (!collection.remove(value)) return;

        syncSetting();
    }

    private void syncSetting() {
        packSetting.get().clear();
        for (Pack pack : collection) packSetting.get().add(pack.getId());

        packSetting.onChanged();
        invalidateTable();
    }

    @Override
    protected boolean includeValue(Pack value) {
        return !value.isRequired();
    }

    @Override
    protected WWidget getValueWidget(Pack value) {
        return theme.label(value.getTitle().getString());
    }

    @Override
    protected String[] getValueNames(Pack value) {
        return new String[]{value.getTitle().getString(), value.getId()};
    }
}
