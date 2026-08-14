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
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.libs.fastutil.ints.Int2ObjectOpenHashMap;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.protocol.types.inventory.InventoryStackRequestType;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class InventoryRequestTracker extends StoredObject {

    private final Int2ObjectOpenHashMap<PendingRequest> pendingRequests = new Int2ObjectOpenHashMap<>();
    private int nextRequestId = -1;

    public InventoryRequestTracker(final UserConnection user) {
        super(user);
    }

    public boolean send(final IntFunction<List<InventoryStackRequest.Action>> actionFactory, final Map<Container, BedrockItem[]> snapshots) {
        final int requestId = this.nextRequestId;
        final List<InventoryStackRequest.Action> actions = List.copyOf(actionFactory.apply(requestId));
        if (actions.isEmpty()) {
            return false;
        }
        this.nextRequestId -= 2; // Bedrock client request IDs are negative odd numbers
        this.pendingRequests.put(requestId, new PendingRequest(snapshots));
        final ItemRewriter itemRewriter = this.user().get(ItemRewriter.class);
        final InventoryStackRequestType requestType = new InventoryStackRequestType(runtimeId -> itemRewriter.getItems().inverse().get(runtimeId));
        final PacketWrapper request = PacketWrapper.create(ServerboundBedrockPackets.ITEM_STACK_REQUEST, this.user());
        request.write(BedrockTypes.UNSIGNED_VAR_INT, 1); // requests
        request.write(requestType, new InventoryStackRequest(requestId, actions));
        request.sendToServer(BedrockProtocol.class);
        return true;
    }

    public PendingRequest remove(final int requestId) {
        return this.pendingRequests.remove(requestId);
    }

    public record PendingRequest(Map<Container, BedrockItem[]> snapshots) {
    }
}
