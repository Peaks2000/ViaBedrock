/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.protocol.task;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.storage.BlockPlacementPredictionTracker;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;

public final class BlockPlacementPredictionTickTask implements Runnable {

    @Override
    public void run() {
        for (UserConnection info : Via.getManager().getConnectionManager().getConnections()) {
            final BlockPlacementPredictionTracker predictionTracker = info.get(BlockPlacementPredictionTracker.class);
            final ChunkTracker chunkTracker = info.get(ChunkTracker.class);
            if (predictionTracker == null || chunkTracker == null) {
                continue;
            }

            info.getChannel().eventLoop().submit(() -> {
                if (!info.getChannel().isActive()) {
                    return;
                }
                try {
                    final BlockPlacementPredictionTracker.Expiration expiration = predictionTracker.expire(System.nanoTime());
                    for (BlockPosition position : expiration.resyncPositions()) {
                        PacketFactory.sendJavaBlockUpdate(info, position, chunkTracker.getJavaBlockState(position));
                    }
                    if (!expiration.resyncPositions().isEmpty()) {
                        PacketFactory.flushJavaBlockChangedAck(info);
                    }
                } catch (Throwable e) {
                    BedrockProtocol.kickForIllegalState(info, "Error ticking block placement predictions. See console for details.", e);
                }
            });
        }
    }
}
