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
package net.raphimc.viabedrock.protocol.storage;

import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.libs.fastutil.ints.IntObjectPair;
import net.lenni0451.mcstructs_bedrock.forms.Form;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.dynamic.BundleContainer;
import net.raphimc.viabedrock.api.model.container.player.ArmorContainer;
import net.raphimc.viabedrock.api.model.container.player.HudContainer;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.model.container.player.OffhandContainer;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.experimental.model.inventory.BedrockInventoryTransaction;
import net.raphimc.viabedrock.experimental.model.inventory.InventoryTransactionData;
import net.raphimc.viabedrock.experimental.rewriter.InventoryTransactionRewriter;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ComplexInventoryTransaction_Type;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerID;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ModalFormCancelReason;
import net.raphimc.viabedrock.protocol.data.generated.bedrock.CustomItemTags;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class InventoryTracker extends StoredObject {

    private static final int PLAYER_INVENTORY_REFRESH_DELAY_TICKS = 2;
    private static final int OFFHAND_SWAP_OPEN_TIMEOUT_TICKS = 40;

    private final InventoryContainer inventoryContainer = new InventoryContainer(this.user());
    private final OffhandContainer offhandContainer = new OffhandContainer(this.user());
    private final ArmorContainer armorContainer = new ArmorContainer(this.user());
    private final HudContainer hudContainer = new HudContainer(this.user());
    private final Map<FullContainerName, BundleContainer> dynamicContainerRegistry = new HashMap<>();

    private Container currentContainer = null;
    private Container pendingCloseContainer = null;
    private IntObjectPair<Form> currentForm = null;
    private int playerInventoryRefreshTicks;
    private boolean playerInventoryResyncPending;
    private boolean offhandSwapPending;
    private boolean offhandSwapInProgress;
    private boolean syntheticInventoryOpen;
    private int offhandSwapOpenTicks;

    public InventoryTracker(final UserConnection user) {
        super(user);
    }

    public Container getContainerClientbound(final byte containerId, final FullContainerName containerName, final BedrockItem storageItem) {
        if (containerId == this.inventoryContainer.containerId()) return this.inventoryContainer;
        if (containerId == this.offhandContainer.containerId()) return this.offhandContainer;
        if (containerId == this.armorContainer.containerId()) return this.armorContainer;
        if (containerId == this.hudContainer.containerId()) return this.hudContainer;
        if (containerId == ContainerID.CONTAINER_ID_REGISTRY.getValue() && containerName.name() == ContainerEnumName.DynamicContainer) {
            final String itemTag = BedrockProtocol.MAPPINGS.getBedrockCustomItemTags().get(this.user().get(ItemRewriter.class).getItems().inverse().get(storageItem.identifier()));
            if (!storageItem.isEmpty() && CustomItemTags.BUNDLE.equals(itemTag)) {
                return this.dynamicContainerRegistry.computeIfAbsent(containerName, cn -> new BundleContainer(this.user(), cn));
            } else {
                return null;
            }
        }
        if (this.currentContainer != null && containerId == this.currentContainer.containerId()) {
            return this.currentContainer;
        }
        return null;
    }

    public Container getContainerServerbound(final byte containerId) {
        if (this.currentContainer != null && containerId == this.currentContainer.javaContainerId()) {
            return this.currentContainer;
        }
        return null;
    }

    public Container getContainerFromName(final FullContainerName containerName, final int slot) {
        if (containerName == null || containerName.name() == null) return null;
        return switch (containerName.name()) {
            case InventoryContainer, HotbarContainer, CombinedHotbarAndInventoryContainer -> this.inventoryContainer;
            case OffhandContainer -> this.offhandContainer;
            case ArmorContainer -> this.armorContainer;
            case CursorContainer, CraftingInputContainer, CraftingOutputPreviewContainer, CreatedOutputContainer -> this.hudContainer;
            case DynamicContainer -> this.dynamicContainerRegistry.get(containerName);
            default -> this.currentContainer != null && containerName.equals(this.currentContainer.getFullContainerName(slot)) ? this.currentContainer : null;
        };
    }

    public BundleContainer getDynamicContainer(final FullContainerName containerName) {
        return this.dynamicContainerRegistry.get(containerName);
    }

    public void removeDynamicContainer(final FullContainerName containerName) {
        this.dynamicContainerRegistry.remove(containerName);
    }

    public void markPendingClose(final Container container) {
        if (this.pendingCloseContainer != null) {
            throw new IllegalStateException("There is already another container pending close");
        }
        if (this.currentContainer == container) {
            this.currentContainer = null;
        }
        this.pendingCloseContainer = container;
    }

    public void setCurrentContainerClosed(final boolean serverInitiated) {
        if (serverInitiated) {
            PacketFactory.sendBedrockContainerClose(this.user(), this.currentContainer.containerId(), ContainerType.NONE);
        }
        this.currentContainer = null;
        this.pendingCloseContainer = null;
    }

    public void closeCurrentForm() {
        if (this.currentForm == null) {
            throw new IllegalStateException("There is no form currently open");
        }
        final PacketWrapper modalFormResponse = PacketWrapper.create(ServerboundBedrockPackets.MODAL_FORM_RESPONSE, this.user());
        modalFormResponse.write(BedrockTypes.UNSIGNED_VAR_INT, this.currentForm.leftInt()); // id
        modalFormResponse.write(Types.BOOLEAN, false); // has response
        modalFormResponse.write(Types.BOOLEAN, true); // has cancel reason
        modalFormResponse.write(Types.BYTE, (byte) ModalFormCancelReason.UserClosed.getValue()); // cancel reason
        modalFormResponse.sendToServer(BedrockProtocol.class);
        this.currentForm = null;
    }

    public void tick() {
        if (this.offhandSwapPending && --this.offhandSwapOpenTicks <= 0) {
            this.offhandSwapPending = false;
            PacketFactory.sendBedrockContainerClose(this.user(), (byte) -1, ContainerType.NONE);
        }

        if (this.playerInventoryRefreshTicks > 0 && --this.playerInventoryRefreshTicks == 0) {
            PacketFactory.sendJavaContainerSetContent(this.user(), this.inventoryContainer);
            if (this.playerInventoryResyncPending) {
                this.playerInventoryResyncPending = false;
                this.requestPlayerInventoryResync();
            }
        }

        if (this.currentContainer != null && this.currentContainer.position() != null) {
            if (this.currentContainer.type() == ContainerType.INVENTORY) return;

            final ChunkTracker chunkTracker = this.user().get(ChunkTracker.class);
            final BlockStateRewriter blockStateRewriter = this.user().get(BlockStateRewriter.class);
            final int blockState = chunkTracker.getBlockState(this.currentContainer.position());
            final String tag = blockStateRewriter.tag(blockState);
            if (!this.currentContainer.isValidBlockTag(tag)) {
                ViaBedrock.getPlatform().getLogger().log(Level.INFO, "Closing " + this.currentContainer.type() + " because block state is not valid for container type: " + blockState);
                this.forceCloseCurrentContainer();
                return;
            }

            final EntityTracker entityTracker = this.user().get(EntityTracker.class);
            final Position3f containerPosition = new Position3f(this.currentContainer.position().x() + 0.5F, this.currentContainer.position().y() + 0.5F, this.currentContainer.position().z() + 0.5F);
            final Position3f playerPosition = entityTracker.getClientPlayer().position();
            if (playerPosition.distanceTo(containerPosition) > 6) {
                ViaBedrock.getPlatform().getLogger().log(Level.INFO, "Closing " + this.currentContainer.type() + " because player is too far away (" + playerPosition.distanceTo(containerPosition) + " > 6)");
                this.forceCloseCurrentContainer();
            }
        }
    }

    public void schedulePlayerPickupRefresh() {
        // Client-hosted worlds can omit InventorySlot after TakeItemActor because Bedrock clients
        // predict pickups locally. Publish that prediction, then ask the host for the authoritative
        // slot contents and stack-network IDs. Coalesce nearby pickups into one round trip.
        this.playerInventoryRefreshTicks = PLAYER_INVENTORY_REFRESH_DELAY_TICKS;
        this.playerInventoryResyncPending = true;
    }

    public void schedulePlayerInventoryResync() {
        this.playerInventoryRefreshTicks = Math.max(this.playerInventoryRefreshTicks, 1);
        this.playerInventoryResyncPending = true;
    }

    public void requestOffhandSwap() {
        if (this.offhandSwapPending || this.offhandSwapInProgress || this.pendingCloseContainer != null || this.currentForm != null) {
            return;
        }
        if (this.currentContainer instanceof InventoryContainer inventory) {
            this.offhandSwapInProgress = true;
            this.user().get(InventoryRequestTracker.class).executeWhenIdle(() -> this.executeOffhandSwap(inventory));
            return;
        }
        if (this.currentContainer != null) return;

        this.offhandSwapPending = true;
        this.offhandSwapOpenTicks = OFFHAND_SWAP_OPEN_TIMEOUT_TICKS;
        PacketFactory.sendBedrockOpenInventory(this.user());
    }

    public void onInventoryContainerOpened(final InventoryContainer inventory) {
        if (!this.offhandSwapPending) return;

        this.offhandSwapPending = false;
        this.offhandSwapOpenTicks = 0;
        this.offhandSwapInProgress = true;
        this.syntheticInventoryOpen = true;
        this.user().get(InventoryRequestTracker.class).executeWhenIdle(() -> this.executeOffhandSwap(inventory));
    }

    private void executeOffhandSwap(final InventoryContainer inventory) {
        final InventoryRequestTracker requestTracker = this.user().get(InventoryRequestTracker.class);
        if (!inventory.handleSwapWithOffhand(this)) {
            this.finishOffhandSwap(inventory);
            return;
        }
        requestTracker.executeWhenIdle(() -> this.finishOffhandSwap(inventory));
    }

    private void finishOffhandSwap(final InventoryContainer inventory) {
        this.offhandSwapInProgress = false;
        if (!this.syntheticInventoryOpen) return;

        this.syntheticInventoryOpen = false;
        if (this.currentContainer != inventory) return;
        this.markPendingClose(inventory);
        PacketFactory.sendBedrockContainerClose(this.user(), inventory.containerId(), ContainerType.NONE);
    }

    private void requestPlayerInventoryResync() {
        final InventoryTransactionRewriter transactionRewriter = this.user().get(InventoryTransactionRewriter.class);
        if (transactionRewriter == null) return;

        // InventoryMismatch is Bedrock's client-to-server request for a full authoritative
        // inventory resend. It has no actions or type-specific payload.
        final BedrockInventoryTransaction transaction = new BedrockInventoryTransaction(
            0,
            List.of(),
            List.of(),
            ComplexInventoryTransaction_Type.InventoryMismatch,
            new InventoryTransactionData.MismatchTransactionData()
        );
        final PacketWrapper packet = PacketWrapper.create(ServerboundBedrockPackets.INVENTORY_TRANSACTION, this.user());
        packet.write(transactionRewriter.getInventoryTransactionType(), transaction);
        packet.sendToServer(BedrockProtocol.class);
    }

    public boolean isContainerOpen() {
        return this.currentContainer != null || this.pendingCloseContainer != null;
    }

    public boolean isAnyScreenOpen() {
        return this.isContainerOpen() || this.currentForm != null;
    }

    public InventoryContainer getInventoryContainer() {
        return this.inventoryContainer;
    }

    public OffhandContainer getOffhandContainer() {
        return this.offhandContainer;
    }

    public ArmorContainer getArmorContainer() {
        return this.armorContainer;
    }

    public HudContainer getHudContainer() {
        return this.hudContainer;
    }

    public Container getCurrentContainer() {
        return this.currentContainer;
    }

    public void setCurrentContainer(final Container container) {
        if (this.isContainerOpen()) {
            throw new IllegalStateException("There is already another container open");
        }
        this.currentContainer = container;
    }

    public Container getPendingCloseContainer() {
        return this.pendingCloseContainer;
    }

    public IntObjectPair<Form> getCurrentForm() {
        return this.currentForm;
    }

    public void setCurrentForm(final IntObjectPair<Form> currentForm) {
        this.currentForm = currentForm;
    }

    private void forceCloseCurrentContainer() {
        this.markPendingClose(this.currentContainer);
        PacketFactory.sendJavaContainerClose(this.user(), this.pendingCloseContainer.javaContainerId());
        PacketFactory.sendBedrockContainerClose(this.user(), this.pendingCloseContainer.containerId(), ContainerType.NONE);
    }

}
