/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.raphimc.viabedrock.protocol.storage;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.ChunkPosition;
import com.viaversion.viaversion.api.minecraft.Vector3d;
import com.viaversion.viaversion.api.minecraft.Vector3f;
import io.netty.util.AttributeKey;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage.CustomBlockModel;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes26_3;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ClientboundPackets26_3;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.data.generated.java.EntityDataFields;
import net.raphimc.viabedrock.protocol.rewriter.resourcepack.CustomBlockResourceRewriter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Display models occupy the same cells as their Java collision carriers. */
final class CustomBlockDisplays {

    private static final AttributeKey<Set<Long>> SLAB_CARRIERS = AttributeKey.valueOf("viafabricplus:bedrock_addon_slab_carriers");

    private final UserConnection user;
    private final Set<Long> slabCarriers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Map<BlockPosition, Display>> chunks = new HashMap<>();

    CustomBlockDisplays(final UserConnection user) {
        this.user = user;
        if (user.isClientSide()) {
            user.getChannel().attr(SLAB_CARRIERS).set(this.slabCarriers);
        }
    }

    boolean canRender(final String identifier) {
        final ResourcePackStorage packs = this.user.get(ResourcePackStorage.class);
        return packs != null && CustomBlockResourceRewriter.hasModel(packs, identifier, "world");
    }

    void update(final BlockPosition position, final CustomBlockModel model) {
        final long chunkKey = ChunkPosition.chunkKey(position.x() >> 4, position.z() >> 4);
        this.updateSlabCarrier(position, model);
        Map<BlockPosition, Display> displays = this.chunks.get(chunkKey);
        final Display previous = displays != null ? displays.get(position) : null;
        if (previous != null && previous.model().equals(model)) {
            return;
        }
        if (previous != null) {
            displays.remove(position);
            this.remove(new int[]{previous.entityId()});
        }
        if (model != null) {
            if (displays == null) {
                displays = this.chunks.computeIfAbsent(chunkKey, ignored -> new HashMap<>());
            }
            displays.put(position, new Display(model, this.spawn(position, model)));
        } else if (displays != null && displays.isEmpty()) {
            this.chunks.remove(chunkKey);
        }
    }

    void prepareChunk(final long key, final Map<BlockPosition, CustomBlockModel> models) {
        final Map<BlockPosition, Display> existing = this.chunks.get(key);
        if (existing != null) {
            existing.keySet().forEach(position -> this.updateSlabCarrier(position, models.get(position)));
        }
        models.forEach(this::updateSlabCarrier);
    }

    void replaceChunk(final long key, final Map<BlockPosition, CustomBlockModel> models) {
        final Map<BlockPosition, Display> existing = this.chunks.get(key);
        if (existing != null) {
            final int[] removed = existing.entrySet().stream()
                .filter(entry -> !entry.getValue().model().equals(models.get(entry.getKey())))
                .mapToInt(entry -> entry.getValue().entityId()).toArray();
            existing.entrySet().removeIf(entry -> {
                if (entry.getValue().model().equals(models.get(entry.getKey()))) {
                    return false;
                }
                this.updateSlabCarrier(entry.getKey(), models.get(entry.getKey()));
                return true;
            });
            this.remove(removed);
            if (existing.isEmpty()) {
                this.chunks.remove(key);
            }
        }
        models.forEach(this::update);
    }

    private void updateSlabCarrier(final BlockPosition position, final CustomBlockModel model) {
        if (!this.user.isClientSide()) {
            return;
        }
        // Java's BlockPos packing is shared by both embedded runtimes and the renderer.
        final long packed = ((long) position.x() & 0x3FFFFFFL) << 38
            | ((long) position.z() & 0x3FFFFFFL) << 12 | (position.y() & 0xFFFL);
        if (model != null && model.slabCarrier()) {
            this.slabCarriers.add(packed);
        } else {
            this.slabCarriers.remove(packed);
        }
    }

    private int spawn(final BlockPosition position, final CustomBlockModel model) {
        final int id = this.user.get(EntityTracker.class).getNextJavaEntityId();
        final UUID uuid = UUID.randomUUID();
        final EntityTypes26_3 type = EntityTypes26_3.ITEM_DISPLAY;
        final PacketWrapper spawn = PacketWrapper.create(ClientboundPackets26_3.ADD_ENTITY, this.user);
        spawn.write(Types.VAR_INT, id);
        spawn.write(Types.UUID, uuid);
        spawn.write(Types.VAR_INT, type.getId());
        spawn.write(Types.DOUBLE, position.x() + 0.5D);
        spawn.write(Types.DOUBLE, position.y() + 0.5D);
        spawn.write(Types.DOUBLE, position.z() + 0.5D);
        spawn.write(Types.LOW_PRECISION_VECTOR, Vector3d.ZERO);
        spawn.write(Types.BYTE, (byte) 0);
        spawn.write(Types.BYTE, (byte) Math.round(model.rotationY() * (256F / 360F)));
        spawn.write(Types.BYTE, (byte) 0);
        spawn.write(Types.VAR_INT, 0);
        spawn.send(BedrockProtocol.class);

        final var data = ProtocolConstants.createStructuredDataContainer();
        data.set(StructuredDataKey.ITEM_MODEL, model.variant().equals("world")
            ? CustomBlockResourceRewriter.getItemModel(model.identifier())
            : CustomBlockResourceRewriter.getNetworkItemModel(model.identifier()));
        data.set(StructuredDataKey.CUSTOM_MODEL_DATA1_21_4, CustomBlockResourceRewriter.getCustomModelData(model.variant()));
        final var item = new StructuredItem(BedrockProtocol.MAPPINGS.getJavaItems().get("minecraft:paper"), 1, data);
        final Entity entity = new Entity(this.user, 0L, 0L, model.identifier(), id, uuid, type);
        final var itemData = new EntityData(entity.getJavaEntityDataIndex(EntityDataFields.ITEM_STACK),
            VersionedTypes.V26_3.entityDataTypes.itemType, item);
        final PacketWrapper metadata = PacketWrapper.create(ClientboundPackets26_3.SET_ENTITY_DATA, this.user);
        metadata.write(Types.VAR_INT, id);
        final float scale = model.scale();
        final var scaleData = new EntityData(entity.getJavaEntityDataIndex(EntityDataFields.SCALE),
            VersionedTypes.V26_3.entityDataTypes.vector3FType, new Vector3f(scale, scale, scale));
        metadata.write(VersionedTypes.V26_3.entityDataList, List.of(itemData, scaleData));
        metadata.send(BedrockProtocol.class);
        return id;
    }

    private void remove(final int[] ids) {
        if (ids.length == 0) {
            return;
        }
        final PacketWrapper packet = PacketWrapper.create(ClientboundPackets26_3.REMOVE_ENTITIES, this.user);
        packet.write(Types.VAR_INT_ARRAY_PRIMITIVE, ids);
        packet.send(BedrockProtocol.class);
    }

    private record Display(CustomBlockModel model, int entityId) { }

}
