/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.api.model.container;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;

/** Java crafting-table view backed by Bedrock's player-only UI container. */
public final class CraftingTableContainer extends Container {

    public CraftingTableContainer(final UserConnection user, final byte containerId, final TextComponent title) {
        // Crafting tables do not have a block-entity tag, so Bedrock's close packet owns their lifetime.
        super(user, containerId, ContainerType.WORKBENCH, title, null, 10);
    }

    @Override
    public Item[] getJavaItems() {
        final InventoryTracker tracker = this.user.get(InventoryTracker.class);
        final Container hud = tracker.getHudContainer();
        final Item[] inventory = tracker.getInventoryContainer().getJavaItems();
        final Item[] items = StructuredItem.emptyArray(46);
        items[0] = hud.getJavaItem(50);
        for (int slot = 1; slot <= 9; slot++) {
            items[slot] = hud.getJavaItem(slot + 31);
        }
        System.arraycopy(inventory, 9, items, 10, 27);
        System.arraycopy(inventory, 36, items, 37, 9);
        return items;
    }

    @Override
    public BedrockItem getItem(final int slot) {
        return this.user.get(InventoryTracker.class).getHudContainer().getItem(slot);
    }

    @Override
    public BedrockItem[] getItems() {
        return this.user.get(InventoryTracker.class).getHudContainer().getItems();
    }

    @Override
    public boolean setItem(final int slot, final BedrockItem item) {
        return this.user.get(InventoryTracker.class).getHudContainer().setItem(slot, item);
    }

    @Override
    public boolean setItems(final BedrockItem[] items) {
        return this.user.get(InventoryTracker.class).getHudContainer().setItems(items);
    }

    @Override
    public int javaSlot(final int slot) {
        if (slot >= 32 && slot <= 40) return slot - 31;
        if (slot == 50) return 0;
        return slot;
    }

    @Override
    public int bedrockSlot(final int slot) {
        if (slot >= 1 && slot <= 9) return slot + 31;
        if (slot == 0) return 50;
        return slot;
    }

    @Override
    public FullContainerName getFullContainerName(final int slot) {
        if (slot >= 32 && slot <= 40) return new FullContainerName(ContainerEnumName.CraftingInputContainer, null);
        if (slot == 50) return new FullContainerName(ContainerEnumName.CraftingOutputPreviewContainer, null);
        return super.getFullContainerName(slot);
    }
}
