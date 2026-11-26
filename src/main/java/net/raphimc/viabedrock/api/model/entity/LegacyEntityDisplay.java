/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.raphimc.viabedrock.api.model.entity;

import com.viaversion.viaversion.api.minecraft.Vector3d;
import com.viaversion.viaversion.api.minecraft.Vector3f;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes26_3;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.data.generated.java.EntityDataFields;
import net.raphimc.viabedrock.protocol.rewriter.resourcepack.CustomEntityResourceRewriter;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;

import java.util.List;
import java.util.UUID;

/** Keeps the replacement mesh independent of the original mob's hitbox, health and passengers. */
final class LegacyEntityDisplay extends Entity {

    private final Entity owner;
    private final CustomEntityResourceRewriter.LegacyEntityModel model;

    LegacyEntityDisplay(final Entity owner, final CustomEntityResourceRewriter.LegacyEntityModel model) {
        super(owner.user, 0L, 0L, null, owner.user.get(EntityTracker.class).getNextJavaEntityId(), UUID.randomUUID(),
            EntityTypes26_3.ITEM_DISPLAY);
        this.owner = owner;
        this.model = model;
    }

    void spawn() {
        final PacketWrapper spawn = PacketWrapper.create(ClientboundPackets26_3.ADD_ENTITY, this.user);
        spawn.write(Types.VAR_INT, this.javaId);
        spawn.write(Types.UUID, this.javaUuid);
        spawn.write(Types.VAR_INT, this.javaType.getId());
        this.writePosition(spawn);
        spawn.write(Types.LOW_PRECISION_VECTOR, Vector3d.ZERO);
        spawn.write(Types.BYTE, MathUtil.float2Byte(this.owner.rotation.x()));
        spawn.write(Types.BYTE, MathUtil.float2Byte(this.owner.rotation.y()));
        spawn.write(Types.BYTE, MathUtil.float2Byte(this.owner.rotation.y()));
        spawn.write(Types.VAR_INT, 0);
        spawn.send(BedrockProtocol.class);

        final var data = ProtocolConstants.createStructuredDataContainer();
        data.set(StructuredDataKey.ITEM_MODEL, this.model.itemModel());
        data.set(StructuredDataKey.CUSTOM_MODEL_DATA1_21_4,
            CustomEntityResourceRewriter.getCustomModelData(CustomEntityResourceRewriter.LEGACY_MODEL_VARIANT));
        final var item = new StructuredItem(BedrockProtocol.MAPPINGS.getJavaItems().get("minecraft:paper"), 1, data);
        final var types = VersionedTypes.V26_3.entityDataTypes();
        final float scale = this.model.scale();
        final PacketWrapper metadata = PacketWrapper.create(ClientboundPackets26_3.SET_ENTITY_DATA, this.user);
        metadata.write(Types.VAR_INT, this.javaId);
        metadata.write(VersionedTypes.V26_3.entityDataList, List.of(
            new EntityData(this.getJavaEntityDataIndex(EntityDataFields.ITEM_STACK), types.itemType, item),
            new EntityData(this.getJavaEntityDataIndex(EntityDataFields.SCALE), types.vector3FType, new Vector3f(scale, scale, scale)),
            new EntityData(this.getJavaEntityDataIndex(EntityDataFields.TRANSLATION), types.vector3FType, new Vector3f(0F, scale * 0.5F, 0F))
        ));
        metadata.send(BedrockProtocol.class);
    }

    void sync() {
        final PacketWrapper update = PacketWrapper.create(ClientboundPackets26_3.ENTITY_POSITION_SYNC, this.user);
        update.write(Types.VAR_INT, this.javaId);
        update.write(Types.VAR_INT, 1); // Stepped
        update.write(Types.VAR_INT, 1); // One step
        this.writePosition(update);
        update.write(Types.VAR_INT, 3); // Interpolation ticks
        update.write(Types.FLOAT, this.owner.rotation.y());
        update.write(Types.FLOAT, this.owner.rotation.x());
        update.write(Types.BOOLEAN, this.owner.onGround);
        update.send(BedrockProtocol.class);
    }

    @Override
    public void remove() {
        final PacketWrapper remove = PacketWrapper.create(ClientboundPackets26_3.REMOVE_ENTITIES, this.user);
        remove.write(Types.VAR_INT_ARRAY_PRIMITIVE, new int[]{this.javaId});
        remove.send(BedrockProtocol.class);
    }

    private void writePosition(final PacketWrapper wrapper) {
        wrapper.write(Types.DOUBLE, (double) this.owner.position.x());
        wrapper.write(Types.DOUBLE, (double) this.owner.position.y());
        wrapper.write(Types.DOUBLE, (double) this.owner.position.z());
    }
}
