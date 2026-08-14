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
package net.raphimc.viabedrock.api.model.entity;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes26_2;
import net.raphimc.viabedrock.protocol.model.BedrockItem;

import java.util.UUID;

/**
 * An item actor together with the stack descriptor Bedrock supplied when it
 * spawned. Bedrock does not include the collected stack in TakeItemActor, so
 * retaining it here lets the Java inventory mirror Bedrock's pickup prediction.
 */
public final class ItemEntity extends Entity {

    private final BedrockItem item;

    public ItemEntity(final UserConnection user, final long uniqueId, final long runtimeId,
                      final int javaId, final UUID javaUuid, final BedrockItem item) {
        super(user, uniqueId, runtimeId, "minecraft:item", javaId, javaUuid, EntityTypes26_2.ITEM);
        this.item = item.copy();
    }

    public BedrockItem item() {
        return this.item;
    }

    public void removeAmount(final int amount) {
        this.item.setAmount(Math.max(0, this.item.amount() - amount));
    }

}
