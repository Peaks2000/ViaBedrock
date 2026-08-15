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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.logging.Level;

/**
 * Holds stateful Bedrock play packets which overtake the expensive START_GAME
 * translation. Their payload depends on mappings created by START_GAME and
 * therefore cannot be decoded safely while the Java connection is still in
 * configuration.
 */
public final class PrePlayPacketQueue extends StoredObject {

    private static final int MAX_PACKETS = 2_048;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private final Queue<QueuedPacket> packets = new ArrayDeque<>();
    private int payloadBytes;
    private boolean overflowLogged;

    public PrePlayPacketQueue(final UserConnection user) {
        super(user);
    }

    public synchronized boolean enqueue(final ClientboundBedrockPackets packetType, final ByteBuf input) {
        final int length = input.readableBytes();
        if (this.packets.size() >= MAX_PACKETS || length > MAX_PAYLOAD_BYTES - this.payloadBytes) {
            if (!this.overflowLogged) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING,
                        "Pre-play packet queue limit reached; later state packets will be ignored");
                this.overflowLogged = true;
            }
            return false;
        }

        final byte[] payload = new byte[length];
        input.getBytes(input.readerIndex(), payload);
        this.packets.add(new QueuedPacket(packetType, payload));
        this.payloadBytes += length;
        return true;
    }

    public void replay() {
        int replayed = 0;
        QueuedPacket packet;
        while ((packet = this.poll()) != null) {
            final PacketWrapper wrapper = PacketWrapper.create(
                    packet.packetType(), Unpooled.wrappedBuffer(packet.payload()), this.user());
            wrapper.send(BedrockProtocol.class, false);
            replayed++;
        }

        if (replayed != 0) {
            ViaBedrock.getPlatform().getLogger().log(Level.INFO,
                    "Replayed " + replayed + " Bedrock packets received during Java configuration");
        }
    }

    private synchronized QueuedPacket poll() {
        final QueuedPacket packet = this.packets.poll();
        if (packet != null) {
            this.payloadBytes -= packet.payload().length;
        }
        return packet;
    }

    private record QueuedPacket(ClientboundBedrockPackets packetType, byte[] payload) {
    }

}
