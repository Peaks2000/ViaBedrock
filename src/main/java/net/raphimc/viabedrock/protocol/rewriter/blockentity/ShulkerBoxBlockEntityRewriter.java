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
package net.raphimc.viabedrock.protocol.rewriter.blockentity;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.blockentity.BlockEntity;
import net.raphimc.viabedrock.api.chunk.BedrockBlockEntity;
import net.raphimc.viabedrock.api.chunk.BlockEntityWithBlockState;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;

public final class ShulkerBoxBlockEntityRewriter extends LootableContainerBlockEntityRewriter {

    private static final String[] FACINGS = {"down", "up", "north", "south", "west", "east"};

    @Override
    public BlockEntity toJava(final UserConnection user, final BedrockBlockEntity bedrockBlockEntity) {
        final BlockEntity javaBlockEntity = super.toJava(user, bedrockBlockEntity);
        final int baseJavaBlockState = user.get(ChunkTracker.class).getJavaBlockState(bedrockBlockEntity.position());
        final BlockState baseState = BedrockProtocol.MAPPINGS.getJavaBlockStates().inverse().get(baseJavaBlockState);
        final BlockState orientedState = orientedState(baseState, bedrockBlockEntity.tag().getByte("facing", (byte) 1));
        final int javaBlockState = BedrockProtocol.MAPPINGS.getJavaBlockStates().getOrDefault(orientedState, baseJavaBlockState);
        return new BlockEntityWithBlockState(javaBlockEntity, javaBlockState);
    }

    public static BlockState orientedState(final BlockState baseState, final int bedrockFacing) {
        if (baseState == null || !baseState.identifier().endsWith("shulker_box")) return baseState;
        final int facing = bedrockFacing >= 0 && bedrockFacing < FACINGS.length ? bedrockFacing : 1;
        return baseState.withProperty("facing", FACINGS[facing]);
    }

}
