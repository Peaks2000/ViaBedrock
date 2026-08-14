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
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemStackRequestActionType;
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
            request.write(BedrockTypes.UNSIGNED_VAR_INT, action.type().getValue()); // mapped action type
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

    public sealed interface Action permits Take, Place, Swap, Drop {

        ItemStackRequestActionType type();

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
}
