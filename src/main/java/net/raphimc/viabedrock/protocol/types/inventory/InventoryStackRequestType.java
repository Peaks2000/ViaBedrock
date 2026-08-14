/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.protocol.types.inventory;

import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.function.IntFunction;

/** Writes the protocol-2168 Cereal representation of an item stack request. */
public final class InventoryStackRequestType extends Type<InventoryStackRequest> {

    private final IntFunction<String> itemIdentifierLookup;

    public InventoryStackRequestType(final IntFunction<String> itemIdentifierLookup) {
        super(InventoryStackRequest.class);
        this.itemIdentifierLookup = itemIdentifierLookup;
    }

    @Override
    public InventoryStackRequest read(final ByteBuf buffer) {
        throw new UnsupportedOperationException("Inventory stack requests are serverbound only");
    }

    @Override
    public void write(final ByteBuf buffer, final InventoryStackRequest request) {
        BedrockTypes.VAR_INT.write(buffer, request.requestId());
        BedrockTypes.UNSIGNED_VAR_INT.write(buffer, request.actions().size());
        for (InventoryStackRequest.Action action : request.actions()) {
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, action.mappedType());
            buffer.writeByte(action.type().getValue()); // Cereal variant discriminator, separate from the mapped ID
            this.writeAction(buffer, action);
        }
        BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 0); // filter strings
        buffer.writeIntLE(-1); // no text-processing origin
    }

    private void writeAction(final ByteBuf buffer, final InventoryStackRequest.Action action) {
        if (action instanceof InventoryStackRequest.Take take) {
            this.writeTransfer(buffer, take.count(), take.source(), take.destination());
        } else if (action instanceof InventoryStackRequest.Place place) {
            this.writeTransfer(buffer, place.count(), place.source(), place.destination());
        } else if (action instanceof InventoryStackRequest.Swap swap) {
            this.writeSlot(buffer, swap.source());
            this.writeSlot(buffer, swap.destination());
        } else if (action instanceof InventoryStackRequest.Drop drop) {
            buffer.writeByte(drop.count());
            this.writeSlot(buffer, drop.source());
            buffer.writeBoolean(drop.randomly());
        } else if (action instanceof InventoryStackRequest.Consume consume) {
            buffer.writeByte(consume.count());
            this.writeSlot(buffer, consume.source());
        } else if (action instanceof InventoryStackRequest.Create create) {
            buffer.writeByte(create.slot());
        } else if (action instanceof InventoryStackRequest.CraftRecipe craftRecipe) {
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, craftRecipe.recipeNetworkId());
            buffer.writeByte(craftRecipe.requestedCrafts());
        } else if (action instanceof InventoryStackRequest.CraftResultsDeprecated craftResults) {
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, craftResults.results().size());
            for (BedrockItem result : craftResults.results()) {
                this.writeCraftResult(buffer, result);
            }
            buffer.writeByte(craftResults.timesCrafted());
        } else {
            throw new IllegalArgumentException("Unsupported inventory stack request action: " + action.getClass().getName());
        }
    }

    private void writeTransfer(final ByteBuf buffer, final int count, final InventoryStackRequest.Slot source, final InventoryStackRequest.Slot destination) {
        buffer.writeByte(count);
        this.writeSlot(buffer, source);
        this.writeSlot(buffer, destination);
    }

    private void writeSlot(final ByteBuf buffer, final InventoryStackRequest.Slot slot) {
        BedrockTypes.FULL_CONTAINER_NAME.write(buffer, slot.container());
        buffer.writeByte(slot.slot());
        buffer.writeIntLE(slot.stackNetworkId());
    }

    private void writeCraftResult(final ByteBuf buffer, final BedrockItem item) {
        final String identifier = this.itemIdentifierLookup.apply(item.identifier());
        if (identifier == null) {
            throw new IllegalArgumentException("Unknown Bedrock crafting result runtime id: " + item.identifier());
        }

        BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 1); // default descriptor
        buffer.writeByte(1); // Cereal variant discriminator
        BedrockTypes.STRING.write(buffer, identifier);
        BedrockTypes.VAR_INT.write(buffer, (int) item.data());
        buffer.writeShortLE(item.amount());
        BedrockTypes.UNSIGNED_VAR_INT.write(buffer, item.blockRuntimeId());

        final ByteBuf userData = buffer.alloc().buffer();
        try {
            if (item.tag() != null) {
                userData.writeShortLE(-1);
                userData.writeByte(1);
                BedrockTypes.TAG_LE.write(userData, item.tag());
            } else {
                userData.writeShortLE(0);
            }
            BedrockTypes.UTF8_STRING_ARRAY.write(userData, item.canPlace());
            BedrockTypes.UTF8_STRING_ARRAY.write(userData, item.canBreak());
            if ("minecraft:shield".equals(identifier)) {
                userData.writeLongLE(item.blockingTicks());
            }

            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, userData.readableBytes());
            buffer.writeBytes(userData);
        } finally {
            userData.release();
        }
    }
}
