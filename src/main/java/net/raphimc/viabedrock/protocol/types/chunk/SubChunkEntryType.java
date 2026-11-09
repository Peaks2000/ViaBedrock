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
package net.raphimc.viabedrock.protocol.types.chunk;

import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.SubChunkPacketPayload_SubChunkRequestResult;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

public final class SubChunkEntryType extends Type<SubChunkEntryType.Entry> {

    public SubChunkEntryType() {
        super(Entry.class);
    }

    @Override
    public Entry read(final ByteBuf buffer) {
        final BlockPosition offset = BedrockTypes.SUB_CHUNK_OFFSET.read(buffer);
        final SubChunkPacketPayload_SubChunkRequestResult result = SubChunkPacketPayload_SubChunkRequestResult.getByValue(
            buffer.readUnsignedByte(), SubChunkPacketPayload_SubChunkRequestResult.Undefined);
        final byte[] data = buffer.readBoolean() ? BedrockTypes.BYTE_ARRAY.read(buffer) : new byte[0];
        buffer.readByte(); // heightmap result
        if (buffer.readBoolean()) {
            buffer.skipBytes(272);
        }
        buffer.readByte(); // render heightmap result
        if (buffer.readBoolean()) {
            buffer.skipBytes(272);
        }
        final Long blobId = buffer.readBoolean() ? buffer.readLongLE() : null;
        return new Entry(offset, result, data, blobId);
    }

    @Override
    public void write(final ByteBuf buffer, final Entry entry) {
        throw new UnsupportedOperationException("Subchunk responses are clientbound only");
    }

    public record Entry(BlockPosition offset, SubChunkPacketPayload_SubChunkRequestResult result, byte[] data, Long blobId) {
    }

}
