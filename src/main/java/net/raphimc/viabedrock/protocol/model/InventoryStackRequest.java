/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.protocol.model;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemStackRequestActionType;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.List;

/** Protocol-2168 Cereal encoding for the basic server-authoritative inventory actions. */
public final class InventoryStackRequest {

    private InventoryStackRequest() {
    }

    public static void send(final UserConnection user, final int requestId, final List<Action> actions) {
        final PacketWrapper request = PacketWrapper.create(ServerboundBedrockPackets.ITEM_STACK_REQUEST, user);
        request.write(BedrockTypes.UNSIGNED_VAR_INT, 1); // requests
        request.write(BedrockTypes.VAR_INT, requestId);
        request.write(BedrockTypes.UNSIGNED_VAR_INT, actions.size());
        for (Action action : actions) {
            request.write(BedrockTypes.UNSIGNED_VAR_INT, action.mappedType());
            request.write(Types.BYTE, (byte) action.type().getValue()); // Cereal variant discriminator
            action.write(request);
        }
        request.write(BedrockTypes.UNSIGNED_VAR_INT, 0); // filter strings
        request.write(BedrockTypes.INT_LE, -1); // no text-processing origin
        request.sendToServer(BedrockProtocol.class);
    }

    private static void writeSlot(final PacketWrapper request, final Slot slot) {
        request.write(BedrockTypes.FULL_CONTAINER_NAME, slot.container());
        request.write(Types.UNSIGNED_BYTE, (short) slot.slot());
        request.write(BedrockTypes.INT_LE, slot.stackNetworkId());
    }

    public record Slot(FullContainerName container, int slot, int stackNetworkId) {
    }

    public sealed interface Action permits Take, Place, Swap, Drop, Consume, CraftRecipe, CraftResults {

        ItemStackRequestActionType type();

        default int mappedType() {
            return this.type().getValue();
        }

        void write(PacketWrapper request);
    }

    public record Take(int count, Slot source, Slot destination) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Take;
        }

        @Override
        public void write(final PacketWrapper request) {
            request.write(Types.UNSIGNED_BYTE, (short) this.count);
            writeSlot(request, this.source);
            writeSlot(request, this.destination);
        }
    }

    public record Place(int count, Slot source, Slot destination) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Place;
        }

        @Override
        public void write(final PacketWrapper request) {
            request.write(Types.UNSIGNED_BYTE, (short) this.count);
            writeSlot(request, this.source);
            writeSlot(request, this.destination);
        }
    }

    public record Swap(Slot source, Slot destination) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Swap;
        }

        @Override
        public void write(final PacketWrapper request) {
            writeSlot(request, this.source);
            writeSlot(request, this.destination);
        }
    }

    public record Drop(int count, Slot source, boolean randomly) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Drop;
        }

        @Override
        public void write(final PacketWrapper request) {
            request.write(Types.UNSIGNED_BYTE, (short) this.count);
            writeSlot(request, this.source);
            request.write(Types.BOOLEAN, this.randomly);
        }
    }

    public record Consume(int count, Slot source) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Consume;
        }

        @Override
        public void write(final PacketWrapper request) {
            request.write(Types.UNSIGNED_BYTE, (short) this.count);
            writeSlot(request, this.source);
        }
    }

    public record CraftRecipe(int recipeNetworkId, int requestedCrafts) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftRecipe;
        }

        @Override
        public int mappedType() {
            return 10; // Protocol 2168 removes the two deprecated item-container actions from the mapped enum.
        }

        @Override
        public void write(final PacketWrapper request) {
            request.write(BedrockTypes.UNSIGNED_VAR_INT, this.recipeNetworkId);
            request.write(Types.BYTE, (byte) this.requestedCrafts);
        }
    }

    public record CraftResults(List<BedrockItem> results, int timesCrafted) implements Action {
        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftResults;
        }

        @Override
        public int mappedType() {
            return 17;
        }

        @Override
        public void write(final PacketWrapper request) {
            request.write(BedrockTypes.UNSIGNED_VAR_INT, this.results.size());
            for (BedrockItem result : this.results) {
                writeCraftResult(request, result);
            }
            request.write(Types.BYTE, (byte) this.timesCrafted);
        }
    }

    private static void writeCraftResult(final PacketWrapper request, final BedrockItem item) {
        if (item.isEmpty()) {
            request.write(BedrockTypes.UNSIGNED_VAR_INT, 0); // invalid descriptor
            request.write(Types.BYTE, (byte) 0); // Cereal variant discriminator
            request.write(BedrockTypes.SHORT_LE, (short) 0);
            request.write(BedrockTypes.UNSIGNED_VAR_INT, 0);
            request.write(BedrockTypes.UNSIGNED_VAR_INT, 0);
            return;
        }

        final String identifier = request.user().get(ItemRewriter.class).getItems().inverse().get(item.identifier());
        if (identifier == null) {
            throw new IllegalArgumentException("Unknown Bedrock crafting result runtime id: " + item.identifier());
        }

        request.write(BedrockTypes.UNSIGNED_VAR_INT, 1); // default descriptor
        request.write(Types.BYTE, (byte) 1); // Cereal variant discriminator
        request.write(BedrockTypes.STRING, identifier);
        request.write(BedrockTypes.VAR_INT, (int) item.data());
        request.write(BedrockTypes.SHORT_LE, (short) item.amount());
        request.write(BedrockTypes.UNSIGNED_VAR_INT, item.blockRuntimeId());

        final ByteBuf userData = request.user().getChannel().alloc().buffer();
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

            request.write(BedrockTypes.UNSIGNED_VAR_INT, userData.readableBytes());
            request.write(Types.REMAINING_BYTES, ByteBufUtil.getBytes(userData));
        } finally {
            userData.release();
        }
    }
}
