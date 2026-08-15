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
package net.raphimc.viabedrock.protocol.types.item;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.fastutil.ints.Int2ObjectMap;
import com.viaversion.viaversion.libs.fastutil.ints.IntSortedSet;
import io.netty.buffer.ByteBuf;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

public class BedrockItemType extends Type<BedrockItem> {

    private final int blockingId;
    private final Int2ObjectMap<IntSortedSet> blockItemValidBlockStates;

    public BedrockItemType(final int blockingId, final Int2ObjectMap<IntSortedSet> blockItemValidBlockStates) {
        super(BedrockItem.class);

        this.blockingId = blockingId;
        this.blockItemValidBlockStates = blockItemValidBlockStates;
    }

    @Override
    public BedrockItem read(ByteBuf buffer) {
        final int id = BedrockTypes.VAR_INT.read(buffer);
        final boolean empty = id == 0 || id == -1;
        final BedrockItem item = empty ? BedrockItem.empty() : new BedrockItem(id);
        item.setAmount(buffer.readUnsignedShortLE());
        item.setData(BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
        item.setBlockRuntimeId(BedrockTypes.VAR_INT.read(buffer));

        if (!empty) {
            final IntSortedSet validBlockStates = this.blockItemValidBlockStates.get(item.identifier());
            if (validBlockStates != null) { // Block item
                item.setData(0);
                if (!validBlockStates.contains(item.blockRuntimeId())) {
                    item.setBlockRuntimeId(validBlockStates.firstInt());
                }
            } else { // Meta item
                item.setBlockRuntimeId(0);
            }
        }

        final ByteBuf userData = buffer.readSlice(BedrockTypes.UNSIGNED_VAR_INT.read(buffer));
        try {
            final short marker = userData.readShortLE();
            if (marker == 0) {
                return item;
            } else if (marker != -1) { // Bedrock client crashes if marker isn't -1
                throw new IllegalStateException("Expected -1 marker but got " + marker);
            }
            final byte version = userData.readByte();
            if (version == 1) {
                item.setTag((CompoundTag) BedrockTypes.TAG_LE.read(userData));
                item.setCanPlace(BedrockTypes.UTF8_STRING_ARRAY.read(userData));
                item.setCanBreak(BedrockTypes.UTF8_STRING_ARRAY.read(userData));
                if (item.identifier() == this.blockingId) {
                    item.setBlockingTicks(userData.readLongLE());
                }
            }
        } catch (IndexOutOfBoundsException ignored) {
            // Bedrock client stops reading at whatever point and loads whatever it has read successfully
        }

        return item;
    }

    @Override
    public void write(ByteBuf buffer, BedrockItem value) {
        final boolean empty = value.isEmpty();
        BedrockTypes.VAR_INT.write(buffer, empty ? 0 : value.identifier());
        buffer.writeShortLE(empty ? 0 : value.amount());
        BedrockTypes.UNSIGNED_VAR_INT.write(buffer, empty ? 0 : (int) value.data());
        BedrockTypes.VAR_INT.write(buffer, empty ? 0 : value.blockRuntimeId());
        if (empty) {
            BedrockTypes.UNSIGNED_VAR_INT.write(buffer, 0);
            return;
        }

        final ByteBuf userData = buffer.alloc().buffer();
        if (value.tag() != null) {
            userData.writeShortLE(-1);
            userData.writeByte(1);
            BedrockTypes.TAG_LE.write(userData, value.tag());
        } else {
            userData.writeShortLE(0);
        }
        BedrockTypes.UTF8_STRING_ARRAY.write(userData, value.canPlace());
        BedrockTypes.UTF8_STRING_ARRAY.write(userData, value.canBreak());
        if (value.identifier() == this.blockingId) {
            userData.writeLongLE(value.blockingTicks());
        }

        BedrockTypes.UNSIGNED_VAR_INT.write(buffer, userData.readableBytes());
        buffer.writeBytes(userData);
        userData.release();
    }

}
