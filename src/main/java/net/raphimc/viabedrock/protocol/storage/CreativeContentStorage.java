/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.protocol.storage;

import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import net.raphimc.viabedrock.protocol.model.BedrockItem;

import java.util.ArrayList;
import java.util.List;

/** Caches the server-advertised creative items and their network IDs. */
public final class CreativeContentStorage extends StoredObject {

    private final List<CreativeItem> items = new ArrayList<>();

    public CreativeContentStorage(final UserConnection user) {
        super(user);
    }

    public void clear() {
        this.items.clear();
    }

    public void add(final int networkId, final BedrockItem bedrockItem, final Item javaItem) {
        if (networkId <= 0 || bedrockItem.isEmpty() || javaItem.isEmpty()) return;

        final Item normalizedJavaItem = javaItem.copy();
        normalizedJavaItem.setAmount(1);
        this.items.add(new CreativeItem(networkId, bedrockItem.copy(), normalizedJavaItem));
    }

    public CreativeItem find(final Item requestedItem) {
        if (requestedItem.isEmpty()) return null;

        final Item normalizedRequestedItem = requestedItem.copy();
        normalizedRequestedItem.setAmount(1);
        CreativeItem identifierFallback = null;
        for (CreativeItem creativeItem : this.items) {
            if (creativeItem.javaItem().identifier() != requestedItem.identifier()) continue;
            if (creativeItem.javaItem().equals(normalizedRequestedItem)) {
                return creativeItem.withAmount(requestedItem.amount());
            }
            if (identifierFallback == null) identifierFallback = creativeItem;
        }
        return identifierFallback != null ? identifierFallback.withAmount(requestedItem.amount()) : null;
    }

    public record CreativeItem(int networkId, BedrockItem bedrockItem, Item javaItem) {

        private CreativeItem withAmount(final int amount) {
            final BedrockItem item = this.bedrockItem.copy();
            item.setAmount(amount);
            return new CreativeItem(this.networkId, item, this.javaItem);
        }
    }
}
