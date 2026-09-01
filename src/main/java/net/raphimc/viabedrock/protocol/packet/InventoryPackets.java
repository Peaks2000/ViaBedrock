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
package net.raphimc.viabedrock.protocol.packet;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.Holder;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandlers;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.libs.fastutil.ints.IntObjectPair;
import com.viaversion.viaversion.libs.mcstructs.converter.impl.v1_21_5.NbtConverter_v1_21_5;
import com.viaversion.viaversion.libs.mcstructs.core.Identifier;
import com.viaversion.viaversion.libs.mcstructs.dialog.ActionButton;
import com.viaversion.viaversion.libs.mcstructs.dialog.AfterAction;
import com.viaversion.viaversion.libs.mcstructs.dialog.Dialog;
import com.viaversion.viaversion.libs.mcstructs.dialog.Input;
import com.viaversion.viaversion.libs.mcstructs.dialog.action.CustomAllAction;
import com.viaversion.viaversion.libs.mcstructs.dialog.body.PlainMessageBody;
import com.viaversion.viaversion.libs.mcstructs.dialog.impl.MultiActionDialog;
import com.viaversion.viaversion.libs.mcstructs.dialog.impl.NoticeDialog;
import com.viaversion.viaversion.libs.mcstructs.dialog.input.BooleanInput;
import com.viaversion.viaversion.libs.mcstructs.dialog.input.NumberRangeInput;
import com.viaversion.viaversion.libs.mcstructs.dialog.input.SingleOptionInput;
import com.viaversion.viaversion.libs.mcstructs.dialog.input.TextInput;
import com.viaversion.viaversion.libs.mcstructs.dialog.serializer.DialogSerializer;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import com.viaversion.viaversion.libs.mcstructs.text.components.StringComponent;
import com.viaversion.viaversion.libs.mcstructs.text.components.TranslationComponent;
import com.viaversion.viaversion.protocols.v1_21_11to26_1.packet.ClientboundPackets26_1;
import com.viaversion.viaversion.protocols.v1_21_11to26_1.packet.ServerboundPackets26_1;
import net.lenni0451.mcstructs_bedrock.forms.Form;
import net.lenni0451.mcstructs_bedrock.forms.elements.*;
import net.lenni0451.mcstructs_bedrock.forms.serializer.FormSerializer;
import net.lenni0451.mcstructs_bedrock.forms.types.ActionForm;
import net.lenni0451.mcstructs_bedrock.forms.types.CustomForm;
import net.lenni0451.mcstructs_bedrock.forms.types.ModalForm;
import net.lenni0451.mcstructs_bedrock.text.utils.BedrockTextUtils;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.chunk.BedrockBlockEntity;
import net.raphimc.viabedrock.api.model.container.AnvilContainer;
import net.raphimc.viabedrock.api.model.container.ChestContainer;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.CraftingTableContainer;
import net.raphimc.viabedrock.api.model.container.FurnaceContainer;
import net.raphimc.viabedrock.api.model.container.MerchantContainer;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.api.util.TextUtil;
import net.raphimc.viabedrock.experimental.model.inventory.BedrockInventoryTransaction;
import net.raphimc.viabedrock.experimental.model.inventory.InventoryActionData;
import net.raphimc.viabedrock.experimental.rewriter.InventoryTransactionRewriter;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ComplexInventoryTransaction_Type;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.*;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.ContainerInput;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.EquipmentSlot;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.GameMode;
import net.raphimc.viabedrock.protocol.data.generated.bedrock.CustomBlockTags;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.BedrockTradeOffer;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.*;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class InventoryPackets {

    private static final int DIALOG_BUTTON_WIDTH = 200;
    private static final int DIALOG_FAKE_BUTTON_WIDTH = 300;
    private static final String DIALOG_FAKE_BUTTON_TEXT = "This is not actually a button, but has to be one because dialogs don't support adding text only elements. Clicking it has the same effect as closing the dialog.";

    public static void register(final BedrockProtocol protocol) {
        protocol.registerClientbound(ClientboundBedrockPackets.CONTAINER_OPEN, ClientboundPackets26_1.OPEN_SCREEN, wrapper -> {
            final ChunkTracker chunkTracker = wrapper.user().get(ChunkTracker.class);
            final BlockStateRewriter blockStateRewriter = wrapper.user().get(BlockStateRewriter.class);
            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final byte containerId = wrapper.read(Types.BYTE); // container id
            final byte rawType = wrapper.read(Types.BYTE); // type
            final ContainerType type = ContainerType.getByValue(rawType);
            if (type == null) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Unknown ContainerType: " + rawType);
                wrapper.cancel();
                return;
            }
            final BlockPosition position = wrapper.read(BedrockTypes.BLOCK_POSITION); // position
            final long entityUniqueId = wrapper.read(BedrockTypes.VAR_LONG); // entity unique id

            if (inventoryTracker.isAnyScreenOpen()) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Server tried to open container while another container is open");
                PacketFactory.sendBedrockContainerClose(wrapper.user(), (byte) -1, ContainerType.NONE);
                wrapper.cancel();
                return;
            }
            final BedrockBlockEntity blockEntity = chunkTracker.getBlockEntity(position);
            final String blockTag = blockStateRewriter.tag(chunkTracker.getBlockState(position));
            TextComponent title = new TranslationComponent("container." + blockTag);
            if (blockEntity != null && blockEntity.tag().get("CustomName") instanceof StringTag customNameTag) {
                title = TextUtil.stringToTextComponent(wrapper.user().get(ResourcePackStorage.class).getTexts().translate(customNameTag.getValue()));
            }

            final Container container;
            switch (type) {
                case INVENTORY -> {
                    final InventoryContainer inventory = new InventoryContainer(wrapper.user(), containerId, position, inventoryTracker.getInventoryContainer());
                    inventoryTracker.setCurrentContainer(inventory);
                    inventoryTracker.onInventoryContainerOpened(inventory);
                    wrapper.cancel();
                    return;
                }
                case CONTAINER -> container = new ChestContainer(wrapper.user(), containerId, title, position,
                    blockContainerSize(blockTag, blockEntity), blockTag);
                case WORKBENCH -> container = new CraftingTableContainer(wrapper.user(), containerId, new TranslationComponent("container.crafting"));
                case ANVIL -> container = new AnvilContainer(wrapper.user(), containerId, title, position);
                case FURNACE, BLAST_FURNACE, SMOKER -> container = new FurnaceContainer(wrapper.user(), containerId, type, title, position);
                case TRADE -> container = new MerchantContainer(wrapper.user(), containerId,
                    new TranslationComponent(entityUniqueId == -1L ? "entity.minecraft.wandering_trader" : "entity.minecraft.villager"));
                case NONE, CAULDRON, JUKEBOX, ARMOR, HAND, HUD, DECORATED_POT -> { // Bedrock client can't open these containers
                    wrapper.cancel();
                    return;
                }
                default -> {
                    // throw new IllegalStateException("Unhandled ContainerType: " + type);
                    wrapper.cancel();
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to open unimplemented container: " + type);
                    PacketFactory.sendBedrockContainerClose(wrapper.user(), containerId, ContainerType.NONE);
                    return;
                }
            }
            inventoryTracker.setCurrentContainer(container);

            wrapper.write(Types.VAR_INT, (int) containerId); // container id
            final int javaMenu = container instanceof ChestContainer && container.size() == 54
                ? BedrockProtocol.MAPPINGS.getJavaMenu("minecraft:generic_9x6")
                : BedrockProtocol.MAPPINGS.getBedrockToJavaContainers().get(type);
            wrapper.write(Types.VAR_INT, javaMenu); // type
            wrapper.write(Types.TAG, TextUtil.textComponentToNbt(container.title())); // title
        });
        protocol.registerClientbound(ClientboundBedrockPackets.UPDATE_TRADE, ClientboundPackets26_1.MERCHANT_OFFERS, wrapper -> {
            final byte containerId = wrapper.read(Types.BYTE);
            final byte rawType = wrapper.read(Types.BYTE);
            wrapper.read(BedrockTypes.VAR_INT); // size
            final int traderTier = wrapper.read(BedrockTypes.VAR_INT);
            wrapper.read(BedrockTypes.VAR_LONG); // trader unique entity id
            wrapper.read(BedrockTypes.VAR_LONG); // last trading player unique entity id
            final String displayName = wrapper.read(BedrockTypes.STRING);
            wrapper.read(Types.BOOLEAN); // use new trade screen
            final boolean economyTrade = wrapper.read(Types.BOOLEAN);
            final Tag offersTag = wrapper.read(BedrockTypes.NETWORK_TAG);

            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            Container current = inventoryTracker.getCurrentContainer();
            if (rawType != ContainerType.TRADE.getValue() || !(offersTag instanceof CompoundTag offersCompound)) {
                wrapper.cancel();
                return;
            }
            if (current == null) {
                final String translatedName = wrapper.user().get(ResourcePackStorage.class).getTexts().translate(displayName);
                final MerchantContainer openedMerchant = new MerchantContainer(
                    wrapper.user(), containerId, TextUtil.stringToTextComponent(translatedName)
                );
                inventoryTracker.setCurrentContainer(openedMerchant);
                current = openedMerchant;

                // Unlike ordinary block inventories, UpdateTrade itself is allowed to open the
                // Bedrock trading UI. Publish Java's merchant screen before forwarding its offers.
                final PacketWrapper openScreen = PacketWrapper.create(ClientboundPackets26_1.OPEN_SCREEN, wrapper.user());
                openScreen.write(Types.VAR_INT, (int) containerId);
                openScreen.write(Types.VAR_INT, BedrockProtocol.MAPPINGS.getBedrockToJavaContainers().get(ContainerType.TRADE));
                openScreen.write(Types.TAG, TextUtil.textComponentToNbt(openedMerchant.title()));
                openScreen.send(BedrockProtocol.class);
            }
            if (!(current instanceof MerchantContainer merchant) || merchant.containerId() != containerId) {
                wrapper.cancel();
                return;
            }

            final ItemRewriter itemRewriter = wrapper.user().get(ItemRewriter.class);
            final List<BedrockTradeOffer> offers = new ArrayList<>();
            final ListTag<CompoundTag> recipes = offersCompound.getListTag("Recipes", CompoundTag.class);
            if (recipes != null) {
                for (int index = 0; index < recipes.size(); index++) {
                    final BedrockTradeOffer offer = BedrockTradeOffer.fromTag(recipes.get(index), itemRewriter, index + 1);
                    if (!offer.costA().isEmpty() && !offer.result().isEmpty()) offers.add(offer);
                }
            }
            merchant.setOffers(offers);

            wrapper.write(Types.VAR_INT, (int) containerId);
            wrapper.write(Types.VAR_INT, offers.size());
            for (BedrockTradeOffer offer : offers) {
                wrapper.write(VersionedTypes.V26_2.itemCost, itemRewriter.javaItem(offer.costA()));
                wrapper.write(VersionedTypes.V26_2.item, itemRewriter.javaItem(offer.result()));
                wrapper.write(VersionedTypes.V26_2.optionalItemCost,
                    offer.costB().isEmpty() ? null : itemRewriter.javaItem(offer.costB()));
                wrapper.write(Types.BOOLEAN, offer.outOfStock());
                wrapper.write(Types.INT, offer.uses());
                wrapper.write(Types.INT, offer.maxUses());
                wrapper.write(Types.INT, offer.traderExperience());
                wrapper.write(Types.INT, 0); // Bedrock already applies demand and discounts to buyA's count
                wrapper.write(Types.FLOAT, 0F);
                wrapper.write(Types.INT, 0);
            }
            wrapper.write(Types.VAR_INT, Math.max(1, Math.min(5, traderTier + 1)));
            wrapper.write(Types.VAR_INT, 0); // current villager experience is not part of UpdateTrade
            wrapper.write(Types.BOOLEAN, economyTrade);
            wrapper.write(Types.BOOLEAN, economyTrade);
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CONTAINER_CLOSE, ClientboundPackets26_1.CONTAINER_CLOSE, new PacketHandlers() {
            @Override
            protected void register() {
                map(Types.BYTE, Types.VAR_INT); // container id
                handler(wrapper -> {
                    final ContainerType containerType = ContainerType.getByValue(wrapper.read(Types.BYTE)); // type
                    final boolean serverInitiated = wrapper.read(Types.BOOLEAN); // server initiated

                    final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
                    final Container container = serverInitiated ? inventoryTracker.getCurrentContainer() : inventoryTracker.getPendingCloseContainer();
                    if (container == null) {
                        wrapper.cancel();
                        return;
                    }

                    if (serverInitiated && containerType != container.type()) {
                        ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Server tried to close container, but container type was not correct");
                        wrapper.cancel();
                        return;
                    }
                    inventoryTracker.setCurrentContainerClosed(serverInitiated);
                });
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CONTAINER_SET_DATA, ClientboundPackets26_1.CONTAINER_SET_DATA, wrapper -> {
            final byte containerId = wrapper.read(Types.BYTE);
            final int property = wrapper.read(BedrockTypes.VAR_INT);
            final int value = wrapper.read(BedrockTypes.VAR_INT);
            final Container container = wrapper.user().get(InventoryTracker.class).getCurrentContainer();
            if (container == null || container.containerId() != containerId
                || (container.type() != ContainerType.FURNACE
                    && container.type() != ContainerType.BLAST_FURNACE
                    && container.type() != ContainerType.SMOKER)) {
                wrapper.cancel();
                return;
            }

            wrapper.write(Types.VAR_INT, (int) containerId);
            wrapper.write(Types.SHORT, (short) property);
            wrapper.write(Types.SHORT, (short) value);
        });
        protocol.registerClientbound(ClientboundBedrockPackets.INVENTORY_CONTENT, ClientboundPackets26_1.CONTAINER_SET_CONTENT, wrapper -> {
            final ItemRewriter itemRewriter = wrapper.user().get(ItemRewriter.class);
            final int containerId = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // container id
            final BedrockItem[] items = wrapper.read(itemRewriter.newItemArrayType()); // items
            final FullContainerName containerName = wrapper.read(BedrockTypes.FULL_CONTAINER_NAME); // container name
            final BedrockItem storageItem = wrapper.read(itemRewriter.newItemType()); // storage item

            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final Container container = inventoryTracker.getContainerClientbound((byte) containerId, containerName, storageItem);
            if (container != null && container.setItems(items)) {
                if (preserveCreativeCursor(wrapper.user(), inventoryTracker, container)) {
                    wrapper.cancel();
                    // Bedrock's HUD container owns its cursor. A full HUD-content update after a
                    // rejected creative offhand request carries an empty cursor and would erase
                    // the rejected Java item which the stack response just restored. There are no
                    // HUD slots to publish while Java's creative inventory is the active player UI.
                    if (container != inventoryTracker.getHudContainer()) {
                        for (int slot = 0; slot < container.size(); slot++) {
                            PacketFactory.sendJavaContainerSetSlot(wrapper.user(), container, slot);
                        }
                    }
                    return;
                }
                final Container javaContainer = container.type() == ContainerType.HUD && inventoryTracker.getCurrentContainer() instanceof CraftingTableContainer
                    ? inventoryTracker.getCurrentContainer()
                    : container;
                PacketFactory.writeJavaContainerSetContent(wrapper, javaContainer);
            } else {
                wrapper.cancel();
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.INVENTORY_SLOT, ClientboundPackets26_1.CONTAINER_SET_SLOT, wrapper -> {
            final ItemRewriter itemRewriter = wrapper.user().get(ItemRewriter.class);
            final int containerId = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // container id
            final int slot = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // slot
            final FullContainerName containerName = wrapper.read(BedrockTypes.OPTIONAL_FULL_CONTAINER_NAME); // container name
            final BedrockItem storageItem = wrapper.read(itemRewriter.optionalNewItemType()); // storage item
            final BedrockItem item = wrapper.read(itemRewriter.newItemType()); // item

            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final Container container = inventoryTracker.getContainerClientbound((byte) containerId, containerName, storageItem);
            if (container != null && container.setItem(slot, item)) {
                if (container.type() == ContainerType.HUD && slot == 0) { // cursor item
                    wrapper.setPacketType(ClientboundPackets26_1.SET_CURSOR_ITEM);
                } else if (container == inventoryTracker.getInventoryContainer()
                    || container == inventoryTracker.getArmorContainer()
                    || container == inventoryTracker.getOffhandContainer()) {
                    // Bedrock sends authoritative player inventory changes (including item pickups) as
                    // individual slots. Refresh Java's combined player inventory so its hotbar, armor and
                    // offhand views all observe the same update, even while another screen is open.
                    // Send a fresh packet instead of changing the mapped slot packet's type in place. Some
                    // protocol pipelines retain the original packet mapping during dispatch, which can make
                    // the Java client decode or discard the rewritten full-content payload as a slot update.
                    wrapper.cancel();
                    if (preserveCreativeCursor(wrapper.user(), inventoryTracker, container)) {
                        // Java owns the creative carried item locally. A full content packet includes
                        // Bedrock's empty HUD cursor and erases armor/items from the mouse while moving them.
                        PacketFactory.sendJavaContainerSetSlot(wrapper.user(), container, slot);
                    } else {
                        PacketFactory.sendJavaContainerSetContent(wrapper.user(), inventoryTracker.getInventoryContainer());
                    }
                    return;
                } else {
                    final Container javaContainer = container.type() == ContainerType.HUD
                        && inventoryTracker.getCurrentContainer() instanceof CraftingTableContainer
                        && ((slot >= 32 && slot <= 40) || slot == 50)
                        ? inventoryTracker.getCurrentContainer()
                        : container;
                    wrapper.write(Types.VAR_INT, (int) javaContainer.javaContainerId()); // container id
                    wrapper.write(Types.VAR_INT, 0); // revision
                    wrapper.write(Types.SHORT, (short) javaContainer.javaSlot(slot)); // slot
                }
                wrapper.write(VersionedTypes.V26_2.item, container.getJavaItem(slot)); // item
            } else {
                wrapper.cancel();
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.INVENTORY_TRANSACTION, null, wrapper -> {
            wrapper.cancel();

            final BedrockInventoryTransaction transaction = wrapper.read(wrapper.user().get(InventoryTransactionRewriter.class).getInventoryTransactionType());
            if (transaction.legacyRequestId() != 0) {
                return;
            }

            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final Map<Container, Set<Integer>> correctedSlots = new IdentityHashMap<>();
            if (transaction.actions() != null) {
                for (InventoryActionData action : transaction.actions()) {
                    if (action.source().type() != InventorySourceType.Container_Inventory) {
                        continue;
                    }

                    final Container container = inventoryTracker.getContainerClientbound((byte) action.source().containerId(), null, null);
                    if (container != null && container.setItem(action.slot(), action.toItem())) {
                        correctedSlots.computeIfAbsent(container, key -> new HashSet<>()).add(action.slot());
                    } else if (container == null) {
                        ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received inventory action for unknown container ID: " + action.source().containerId());
                    }
                }
            }

            boolean refreshCombinedPlayerInventory = false;
            for (Map.Entry<Container, Set<Integer>> entry : correctedSlots.entrySet()) {
                final Container container = entry.getKey();
                if (preserveCreativeCursor(wrapper.user(), inventoryTracker, container)) {
                    // A normal Bedrock InventoryTransaction can immediately follow a rejected
                    // creative offhand move. Publishing a combined player or HUD refresh here
                    // would carry Bedrock's empty HUD cursor and erase the item we just restored.
                    if (container != inventoryTracker.getHudContainer()) {
                        for (int slot : entry.getValue()) {
                            PacketFactory.sendJavaContainerSetSlot(wrapper.user(), container, slot);
                        }
                    }
                } else if (isPlayerInventoryContainer(inventoryTracker, container)) {
                    refreshCombinedPlayerInventory = true;
                } else {
                    PacketFactory.sendJavaContainerSetContent(wrapper.user(), container);
                }
            }
            if (refreshCombinedPlayerInventory) {
                PacketFactory.sendJavaContainerSetContent(wrapper.user(), inventoryTracker.getInventoryContainer());
            }
            if (transaction.transactionType() != ComplexInventoryTransaction_Type.NormalTransaction) {
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received unsupported inventory transaction type: " + transaction.transactionType());
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CRAFTING_DATA, null, wrapper -> {
            wrapper.cancel();
            wrapper.user().get(CraftingRecipeStorage.class).read(wrapper);
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CREATIVE_CONTENT, null, wrapper -> {
            wrapper.cancel();
            final ItemRewriter itemRewriter = wrapper.user().get(ItemRewriter.class);
            final CreativeContentStorage creativeContent = wrapper.user().get(CreativeContentStorage.class);
            creativeContent.clear();

            final int groupCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
            for (int i = 0; i < groupCount; i++) {
                // CreativeContent was converted to Cereal in protocol 2168. The category
                // changed from a fixed little-endian int to its generated uint8 enum.
                wrapper.read(Types.UNSIGNED_BYTE); // category
                wrapper.read(BedrockTypes.STRING); // name
                // CreativeContent uses NetworkItemInstanceDescriptorData in 2168, not the
                // similarly named NetworkItemStackDescriptorData used by inventory slots.
                wrapper.read(itemRewriter.itemType()); // icon
            }

            final int itemCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
            for (int i = 0; i < itemCount; i++) {
                final int networkId = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
                final BedrockItem bedrockItem = wrapper.read(itemRewriter.itemType());
                wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // group index
                creativeContent.add(networkId, bedrockItem, itemRewriter.javaItem(bedrockItem.copy()));
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.ITEM_STACK_RESPONSE, null, wrapper -> {
            wrapper.cancel();
            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final InventoryRequestTracker requestTracker = wrapper.user().get(InventoryRequestTracker.class);
            final int responseCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
            for (int responseIndex = 0; responseIndex < responseCount; responseIndex++) {
                final ItemStackNetResult result = ItemStackNetResult.getByValue(wrapper.read(Types.UNSIGNED_BYTE));
                final int requestId = wrapper.read(BedrockTypes.VAR_INT);
                final InventoryRequestTracker.PendingRequest pending = requestTracker.remove(requestId);
                final Set<Container> correctedContainers = Collections.newSetFromMap(new IdentityHashMap<>());
                final List<StackResponseCorrection> corrections = new ArrayList<>();

                final boolean containersFieldPresent = wrapper.read(Types.BOOLEAN);
                final boolean containersPresent = containersFieldPresent && wrapper.read(Types.BOOLEAN);
                if (containersPresent) {
                    final int containerCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
                    for (int containerIndex = 0; containerIndex < containerCount; containerIndex++) {
                        final FullContainerName containerName = wrapper.read(BedrockTypes.FULL_CONTAINER_NAME);
                        final int slotCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
                        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                            final int slot = wrapper.read(Types.UNSIGNED_BYTE);
                            wrapper.read(Types.UNSIGNED_BYTE); // hotbar slot
                            final int amount = wrapper.read(Types.UNSIGNED_BYTE);
                            Integer stackNetworkId = null;
                            if (wrapper.read(Types.BOOLEAN) && wrapper.read(Types.BOOLEAN)) {
                                stackNetworkId = wrapper.read(BedrockTypes.VAR_INT);
                            }
                            wrapper.read(BedrockTypes.STRING); // custom name
                            wrapper.read(BedrockTypes.STRING); // filtered custom name
                            wrapper.read(BedrockTypes.VAR_INT); // durability correction

                            corrections.add(new StackResponseCorrection(containerName, slot, amount, stackNetworkId));
                        }
                    }
                }

                if (pending == null) {
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received item stack response for unknown request ID: " + requestId);
                    requestTracker.runQueuedRequests();
                    continue;
                }
                final boolean javaClientManagedFailure =
                    result != ItemStackNetResult.Success && pending.javaClientManaged();
                if (result != ItemStackNetResult.Success) {
                    final String creativeModeContext;
                    if (pending.actions().stream().anyMatch(InventoryStackRequest.CraftCreative.class::isInstance)) {
                        final var clientPlayer = wrapper.user().get(EntityTracker.class).getClientPlayer();
                        creativeModeContext = "; javaGameMode=" + clientPlayer.javaGameMode()
                            + ", playerGameType=" + clientPlayer.gameType()
                            + ", levelGameType=" + wrapper.user().get(GameSessionStorage.class).getLevelGameType();
                    } else {
                        creativeModeContext = "";
                    }
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING,
                        "Inventory request " + requestId + " failed: " + result + creativeModeContext
                            + "; actions=" + pending.actions());
                    for (var entry : pending.snapshots().entrySet()) {
                        entry.getKey().setItems(entry.getValue());
                        correctedContainers.add(entry.getKey());
                    }
                    // The rollback restores our last prediction, which can itself be stale after
                    // client-predicted pickups or a game-mode transition. Ask Bedrock to resend
                    // the real slot contents before the next Java click reuses invalid stack IDs.
                    inventoryTracker.schedulePlayerInventoryResync(!javaClientManagedFailure);
                }

                // Java's creative screen applies SetCreativeModeSlot changes itself and keeps its carried item
                // only on the client. A successful Bedrock response must update our internal stack IDs without
                // sending a full inventory packet: that packet would contain Bedrock's empty HUD cursor and make
                // the item on Java's mouse disappear halfway through a move. Failed requests restore the affected
                // slots and Java cursor individually so an unaccepted prediction cannot become a real duplication.
                final boolean javaClientManagedSuccess = result == ItemStackNetResult.Success && pending.javaClientManaged();

                // Successful stack responses carry authoritative amounts and stack-network IDs.
                // Apply them after any rollback so acknowledged predictions never retain their
                // temporary negative client request ID.
                for (StackResponseCorrection correction : corrections) {
                    final Container container = inventoryTracker.getContainerFromName(correction.containerName(), correction.slot());
                    if (container == null) continue;
                    final int storageSlot = container.stackResponseSlot(correction.slot());
                    if (storageSlot < 0 || storageSlot >= container.size()) continue;
                    if (correction.amount() == 0) {
                        container.setItem(storageSlot, BedrockItem.empty());
                        correctedContainers.add(container);
                        continue;
                    }

                    final BedrockItem expected = container.getItem(storageSlot);
                    if (expected.isEmpty()) {
                        ViaBedrock.getPlatform().getLogger().log(Level.WARNING,
                            "Could not apply non-empty inventory correction for " + correction.containerName()
                                + " slot " + correction.slot() + " because the tracked item is empty");
                        continue;
                    }
                    final BedrockItem corrected = expected.copy();
                    corrected.setAmount(correction.amount());
                    if (correction.stackNetworkId() != null) corrected.setNetId(correction.stackNetworkId());
                    container.setItem(storageSlot, corrected);
                    correctedContainers.add(container);
                }

                if (javaClientManagedFailure) {
                    if (pending.javaCursorOnFailure() != null) {
                        // Minecraft 26.2 deliberately ignores SET_CURSOR_ITEM while its creative
                        // inventory screen is open. Restore the combined player inventory and the
                        // rejected carried item atomically instead; the creative menu delegates its
                        // carried stack to that player inventory menu, so the dragged item survives.
                        PacketFactory.sendJavaContainerSetContent(
                            wrapper.user(), inventoryTracker.getInventoryContainer(), pending.javaCursorOnFailure());
                    } else {
                        // An empty creative update has no carried item to restore. Publish only its
                        // rejected slots so Bedrock's empty HUD cursor cannot clear another drag.
                        for (Container container : correctedContainers) {
                            for (int slot = 0; slot < container.size(); slot++) {
                                PacketFactory.sendJavaContainerSetSlot(wrapper.user(), container, slot);
                            }
                        }
                    }
                } else if (!javaClientManagedSuccess) {
                    for (Container container : correctedContainers) {
                        if (container == inventoryTracker.getHudContainer() && inventoryTracker.getCurrentContainer() instanceof CraftingTableContainer) {
                            PacketFactory.sendJavaContainerSetContent(wrapper.user(), inventoryTracker.getCurrentContainer());
                        } else if (container != inventoryTracker.getHudContainer()) {
                            PacketFactory.sendJavaContainerSetContent(wrapper.user(), container);
                        }
                    }
                    PacketFactory.sendJavaContainerSetContent(wrapper.user(), inventoryTracker.getInventoryContainer());
                }
                requestTracker.runQueuedRequests();
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.MODAL_FORM_REQUEST, ClientboundPackets26_1.SHOW_DIALOG, wrapper -> {
            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final int id = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // id
            final String data = wrapper.read(BedrockTypes.STRING); // data

            if (inventoryTracker.getCurrentContainer() != null || inventoryTracker.getCurrentForm() != null) {
                final PacketWrapper modalFormResponse = PacketWrapper.create(ServerboundBedrockPackets.MODAL_FORM_RESPONSE, wrapper.user());
                modalFormResponse.write(BedrockTypes.UNSIGNED_VAR_INT, id); // id
                modalFormResponse.write(Types.BOOLEAN, false); // has response
                modalFormResponse.write(Types.BOOLEAN, true); // has cancel reason
                modalFormResponse.write(Types.BYTE, (byte) ModalFormCancelReason.UserBusy.getValue()); // cancel reason
                modalFormResponse.sendToServer(BedrockProtocol.class);
                wrapper.cancel();
                return;
            }

            final Form form;
            try {
                form = FormSerializer.deserialize(data);
            } catch (Throwable e) { // Bedrock client shows error modal form
                ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Error while deserializing form data: " + data, e);
                wrapper.cancel();
                return;
            }
            final ResourcePackStorage resourcePackStorage = wrapper.user().get(ResourcePackStorage.class);
            form.setTranslator(resourcePackStorage.getTexts()::translate);
            inventoryTracker.setCurrentForm(IntObjectPair.of(id, form));

            final Identifier responseIdentifier = Identifier.of("viabedrock", "form/" + id);
            final CompoundTag exitButtonAdditions = new CompoundTag();
            exitButtonAdditions.putBoolean("exit", true);
            final ActionButton exitButton = new ActionButton(new StringComponent(resourcePackStorage.getTexts().get("gui.close")), DIALOG_BUTTON_WIDTH, new CustomAllAction(responseIdentifier, exitButtonAdditions));

            final Dialog dialog;
            if (form instanceof ModalForm modalForm) {
                final MultiActionDialog actionDialog = new MultiActionDialog(TextUtil.stringToTextComponent(form.getTitle()), true, false, AfterAction.CLOSE, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), exitButton, 1);
                addTextToDialog(wrapper.user(), actionDialog, modalForm.getText());
                final CompoundTag button1Additions = new CompoundTag();
                button1Additions.putInt("button_id", 0);
                actionDialog.getActions().add(new ActionButton(TextUtil.stringToTextComponent(modalForm.getButton1()), DIALOG_BUTTON_WIDTH, new CustomAllAction(responseIdentifier, button1Additions)));
                final CompoundTag button2Additions = new CompoundTag();
                button2Additions.putInt("button_id", 1);
                actionDialog.getActions().add(new ActionButton(TextUtil.stringToTextComponent(modalForm.getButton2()), DIALOG_BUTTON_WIDTH, new CustomAllAction(responseIdentifier, button2Additions)));
                dialog = actionDialog;
            } else if (form instanceof ActionForm actionForm) {
                if (actionForm.getElements().length == 0) { // Text only form
                    final NoticeDialog noticeDialog = new NoticeDialog(TextUtil.stringToTextComponent(form.getTitle()), true, false, AfterAction.CLOSE, new ArrayList<>(), new ArrayList<>(), exitButton);
                    addTextToDialog(wrapper.user(), noticeDialog, actionForm.getText());
                    dialog = noticeDialog;
                } else {
                    final MultiActionDialog actionDialog = new MultiActionDialog(TextUtil.stringToTextComponent(form.getTitle()), true, false, AfterAction.CLOSE, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), exitButton, 1);
                    addTextToDialog(wrapper.user(), actionDialog, actionForm.getText());
                    int buttonIndex = 0;
                    for (int elementIndex = 0; elementIndex < actionForm.getElements().length; elementIndex++) {
                        final FormElement element = actionForm.getElements()[elementIndex];
                        if (element instanceof ButtonFormElement button) {
                            final CompoundTag buttonAdditions = new CompoundTag();
                            buttonAdditions.putInt("button_id", buttonIndex);
                            actionDialog.getActions().add(new ActionButton(TextUtil.stringToTextComponent(button.getText()), DIALOG_BUTTON_WIDTH, new CustomAllAction(responseIdentifier, buttonAdditions)));
                            buttonIndex++;
                        } else if (element instanceof HeaderFormElement header) {
                            actionDialog.getActions().add(new ActionButton(TextUtil.stringToTextComponent(header.getText()), new StringComponent(DIALOG_FAKE_BUTTON_TEXT), DIALOG_FAKE_BUTTON_WIDTH, exitButton.getAction()));
                        } else if (element instanceof LabelFormElement label) {
                            actionDialog.getActions().add(new ActionButton(TextUtil.stringToTextComponent(label.getText()), new StringComponent(DIALOG_FAKE_BUTTON_TEXT), DIALOG_FAKE_BUTTON_WIDTH, exitButton.getAction()));
                        } else if (element instanceof DividerFormElement) {
                        } else {
                            throw new IllegalArgumentException("Unhandled form element type: " + element.getClass().getSimpleName());
                        }
                    }
                    dialog = actionDialog;
                }
            } else if (form instanceof CustomForm customForm) {
                final MultiActionDialog actionDialog = new MultiActionDialog(TextUtil.stringToTextComponent(form.getTitle()), true, false, AfterAction.CLOSE, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), exitButton, 1);
                for (int elementIndex = 0; elementIndex < customForm.getElements().length; elementIndex++) {
                    final FormElement element = customForm.getElements()[elementIndex];
                    final String inputKey = String.valueOf(elementIndex);
                    if (element instanceof CheckboxFormElement checkbox) {
                        final BooleanInput booleanInput = new BooleanInput(TextUtil.stringToTextComponent(checkbox.getText()));
                        booleanInput.setInitial(checkbox.getDefaultValue());
                        actionDialog.getInputs().add(new Input(inputKey, booleanInput));
                    } else if (element instanceof DropdownFormElement dropdown) {
                        final SingleOptionInput singleOptionInput = new SingleOptionInput(new ArrayList<>(dropdown.getOptions().length), TextUtil.stringToTextComponent(dropdown.getText()));
                        for (int dropdownIndex = 0; dropdownIndex < dropdown.getOptions().length; dropdownIndex++) {
                            final String option = dropdown.getOptions()[dropdownIndex];
                            singleOptionInput.getOptions().add(new SingleOptionInput.Entry(String.valueOf(dropdownIndex), TextUtil.stringToTextComponent(option), dropdownIndex == dropdown.getDefaultOption()));
                        }
                        actionDialog.getInputs().add(new Input(inputKey, singleOptionInput));
                    } else if (element instanceof SliderFormElement slider) {
                        final NumberRangeInput numberRangeInput = new NumberRangeInput(TextUtil.stringToTextComponent(slider.getText()), new NumberRangeInput.Range(slider.getMin(), slider.getMax(), slider.getDefaultValue(), slider.getStep()));
                        actionDialog.getInputs().add(new Input(inputKey, numberRangeInput));
                    } else if (element instanceof StepSliderFormElement stepSlider) {
                        final SingleOptionInput singleOptionInput = new SingleOptionInput(new ArrayList<>(stepSlider.getSteps().length), TextUtil.stringToTextComponent(stepSlider.getText()));
                        for (int stepIndex = 0; stepIndex < stepSlider.getSteps().length; stepIndex++) {
                            final String step = stepSlider.getSteps()[stepIndex];
                            final String stepKey = String.valueOf(stepIndex);
                            singleOptionInput.getOptions().add(new SingleOptionInput.Entry(stepKey, TextUtil.stringToTextComponent(step), stepIndex == stepSlider.getDefaultStep()));
                        }
                        actionDialog.getInputs().add(new Input(inputKey, singleOptionInput));
                    } else if (element instanceof TextFieldFormElement textField) {
                        final TextInput textInput = new TextInput(TextUtil.stringToTextComponent(textField.getText()));
                        textInput.setMaxLength(100);
                        textInput.setInitial(textField.getDefaultValue());
                        actionDialog.getInputs().add(new Input(inputKey, textInput));
                    } else if (element instanceof HeaderFormElement header) {
                        addTextToDialog(wrapper.user(), actionDialog, header.getText());
                    } else if (element instanceof LabelFormElement label) {
                        addTextToDialog(wrapper.user(), actionDialog, label.getText());
                    } else if (element instanceof DividerFormElement) {
                        if (wrapper.user().getProtocolInfo().protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
                            final TextInput textInput = new TextInput(new StringComponent());
                            textInput.setLabelVisible(false);
                            textInput.setMaxLength(Integer.MAX_VALUE);
                            textInput.setMultiline(new TextInput.MultilineOptions(null, 1));
                            actionDialog.getInputs().add(new Input("dummy", textInput));
                        }
                    } else {
                        throw new IllegalArgumentException("Unhandled form element type: " + element.getClass().getSimpleName());
                    }
                }
                actionDialog.getActions().add(new ActionButton(TextUtil.stringToTextComponent(resourcePackStorage.getTexts().get("gui.submit")), DIALOG_BUTTON_WIDTH, new CustomAllAction(responseIdentifier, null)));
                dialog = actionDialog;
            } else {
                throw new IllegalArgumentException("Unhandled form type: " + form.getClass().getSimpleName());
            }

            wrapper.write(Types.TRUSTED_COMPOUND_TAG_HOLDER, Holder.of((CompoundTag) DialogSerializer.V1_21_6.getDirectCodec().serialize(NbtConverter_v1_21_5.INSTANCE, dialog).get())); // dialog data
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CLOSE_FORM, ClientboundPackets26_1.CLEAR_DIALOG, wrapper -> {
            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            if (inventoryTracker.getCurrentForm() != null) {
                inventoryTracker.closeCurrentForm();
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.PLAYER_HOTBAR, ClientboundPackets26_1.SET_HELD_SLOT, wrapper -> {
            final InventoryContainer inventoryContainer = wrapper.user().get(InventoryTracker.class).getInventoryContainer();
            final int slot = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // selected slot
            final byte containerId = wrapper.read(Types.BYTE); // container id
            final boolean shouldSelectSlot = wrapper.read(Types.BOOLEAN); // should select slot
            if (slot >= 0 && slot < 9 && containerId == inventoryContainer.containerId() && shouldSelectSlot) {
                wrapper.write(Types.VAR_INT, slot); // slot
            } else {
                wrapper.cancel();
                if (containerId != inventoryContainer.containerId()) { // Bedrock client doesn't render hotbar selection and held item anymore
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to set hotbar slot with wrong container id: " + containerId);
                }
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.CONTAINER_REGISTRY_CLEANUP, null, wrapper -> {
            wrapper.cancel();
            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            final FullContainerName[] removedContainers = wrapper.read(BedrockTypes.FULL_CONTAINER_NAME_ARRAY); // removed containers
            for (FullContainerName containerName : removedContainers) {
                inventoryTracker.removeDynamicContainer(containerName);
            }
        });
        protocol.registerClientbound(ClientboundBedrockPackets.PLAYER_ARMOR_DAMAGE, ClientboundPackets26_1.SET_EQUIPMENT, wrapper -> {
            if (!wrapper.user().get(GameSessionStorage.class).isInventoryServerAuthoritative()) {
                wrapper.cancel();
                return;
            }
            final int size = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT); // size
            if (size <= 0) {
                wrapper.cancel();
                return;
            }
            final Container armorContainer = wrapper.user().get(InventoryTracker.class).getArmorContainer();

            wrapper.write(Types.VAR_INT, wrapper.user().get(EntityTracker.class).getClientPlayer().javaId()); // entity id
            for (int i = 0; i < size; i++) {
                final int rawArmorSlot = wrapper.read(BedrockTypes.VAR_INT); // armor slot
                final SharedTypes_Legacy_ArmorSlot armorSlot = SharedTypes_Legacy_ArmorSlot.getByValue(rawArmorSlot);
                if (armorSlot == null) { // Bedrock client ignores the whole packet if an unknown armor slot is sent
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Unknown SharedTypes_Legacy_ArmorSlot: " + rawArmorSlot);
                    wrapper.cancel();
                    return;
                }
                final short damage = wrapper.read(BedrockTypes.SHORT_LE); // damage

                final BedrockItem item = armorSlot.getValue() < armorContainer.size() ? armorContainer.getItem(armorSlot.getValue()) : BedrockItem.empty();
                if (item.tag() == null) {
                    item.setTag(new CompoundTag());
                }
                item.tag().putInt("Damage", damage);

                final EquipmentSlot equipmentSlot = switch (armorSlot) {
                    case Head -> EquipmentSlot.HEAD;
                    case Torso -> EquipmentSlot.CHEST;
                    case Legs -> EquipmentSlot.LEGS;
                    case Feet -> EquipmentSlot.FEET;
                    case Body -> EquipmentSlot.BODY;
                };
                wrapper.write(Types.BYTE, (byte) (equipmentSlot.ordinal() | (i < (size - 1) ? Byte.MIN_VALUE : 0))); // slot
                wrapper.write(VersionedTypes.V26_2.item, wrapper.user().get(ItemRewriter.class).javaItem(item)); // item
            }
        });

        protocol.registerServerbound(ServerboundPackets26_1.CONTAINER_CLICK, null, wrapper -> {
            wrapper.cancel();
            final int containerId = wrapper.read(Types.VAR_INT); // container id
            final int revision = wrapper.read(Types.VAR_INT); // revision
            final short slot = wrapper.read(Types.SHORT); // slot
            final byte button = wrapper.read(Types.BYTE); // button
            final ContainerInput action = ContainerInput.values()[wrapper.read(Types.VAR_INT)]; // action
            final UserConnection user = wrapper.user();
            user.get(InventoryRequestTracker.class).executeWhenIdle(() ->
                handleContainerClick(user, containerId, revision, slot, button, action));
        });
        protocol.registerServerbound(ServerboundPackets26_1.SELECT_TRADE, null, wrapper -> {
            wrapper.cancel();
            final int index = wrapper.read(Types.VAR_INT);
            final Container current = wrapper.user().get(InventoryTracker.class).getCurrentContainer();
            if (current instanceof MerchantContainer merchant) merchant.selectTrade(index);
        });
        protocol.registerServerbound(ServerboundPackets26_1.SET_CREATIVE_MODE_SLOT, null, wrapper -> {
            wrapper.cancel();
            final short slot = wrapper.read(Types.SHORT); // slot
            final Item item = wrapper.read(VersionedTypes.V26_2.lengthPrefixedItem).copy(); // item
            final UserConnection user = wrapper.user();
            user.get(InventoryRequestTracker.class).executeWhenIdle(() -> handleCreativeSlot(user, slot, item));
        });
        protocol.registerServerbound(ServerboundPackets26_1.CUSTOM_CLICK_ACTION, ServerboundBedrockPackets.MODAL_FORM_RESPONSE, wrapper -> {
            final String id = wrapper.read(Types.STRING); // id
            final CompoundTag payload = (CompoundTag) wrapper.read(Types.CUSTOM_CLICK_ACTION_TAG); // payload
            final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
            if (inventoryTracker.getCurrentForm() == null) {
                wrapper.cancel();
                return;
            }

            final Form form = inventoryTracker.getCurrentForm().right();
            final int formId = inventoryTracker.getCurrentForm().leftInt();
            if (!id.equals("viabedrock:form/" + formId)) {
                wrapper.cancel();
                return;
            }

            inventoryTracker.setCurrentForm(null);
            if (payload.contains("exit") && payload.getBoolean("exit")) {
                wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, formId); // id
                wrapper.write(Types.BOOLEAN, false); // has response
                wrapper.write(Types.BOOLEAN, true); // has cancel reason
                wrapper.write(Types.BYTE, (byte) ModalFormCancelReason.UserClosed.getValue()); // cancel reason
                return;
            }

            if (form instanceof ModalForm modalForm) {
                modalForm.setClickedButton(payload.getInt("button_id"));
            } else if (form instanceof ActionForm actionForm) {
                actionForm.setClickedButton(payload.getInt("button_id"));
            } else if (form instanceof CustomForm customForm) {
                for (int elementIndex = 0; elementIndex < customForm.getElements().length; elementIndex++) {
                    final String inputKey = String.valueOf(elementIndex);
                    if (!payload.contains(inputKey)) continue;
                    final FormElement element = customForm.getElements()[elementIndex];
                    if (element instanceof CheckboxFormElement checkbox) {
                        checkbox.setChecked(payload.getBoolean(inputKey));
                    } else if (element instanceof DropdownFormElement dropdown) {
                        dropdown.setSelected(Integer.parseInt(payload.getString(inputKey)));
                    } else if (element instanceof SliderFormElement slider) {
                        slider.setCurrent(payload.getFloat(inputKey));
                    } else if (element instanceof StepSliderFormElement stepSlider) {
                        stepSlider.setSelected(Integer.parseInt(payload.getString(inputKey)));
                    } else if (element instanceof TextFieldFormElement textField) {
                        textField.setValue(payload.getString(inputKey));
                    }
                }
            } else {
                throw new IllegalArgumentException("Unhandled form type: " + form.getClass().getSimpleName());
            }

            wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, formId); // id
            wrapper.write(Types.BOOLEAN, true); // has response
            wrapper.write(BedrockTypes.STRING, form.serializeResponse() + '\n'); // response
            wrapper.write(Types.BOOLEAN, false); // has cancel reason
        });
        protocol.registerServerbound(ServerboundPackets26_1.CONTAINER_CLOSE, ServerboundBedrockPackets.CONTAINER_CLOSE, new PacketHandlers() {
            @Override
            protected void register() {
                map(Types.VAR_INT, Types.BYTE); // container id
                create(Types.BYTE, (byte) ContainerType.NONE.getValue()); // type
                create(Types.BOOLEAN, false); // server initiated
                handler(wrapper -> {
                    final InventoryTracker inventoryTracker = wrapper.user().get(InventoryTracker.class);
                    final byte containerId = wrapper.get(Types.BYTE, 0);
                    final Container container = inventoryTracker.getContainerServerbound(containerId);
                    if (container == null) {
                        wrapper.cancel();
                        return;
                    }

                    if (container.javaContainerId() != container.containerId()) {
                        wrapper.set(Types.BYTE, 0, container.containerId());
                    }
                    inventoryTracker.markPendingClose(container);
                });
            }
        });
        protocol.registerServerbound(ServerboundPackets26_1.SET_CARRIED_ITEM, ServerboundBedrockPackets.MOB_EQUIPMENT, wrapper -> {
            final short slot = wrapper.read(Types.SHORT); // slot
            wrapper.user().get(InventoryTracker.class).getInventoryContainer().setSelectedHotbarSlot((byte) slot, wrapper); // slot
        });
        protocol.registerServerbound(ServerboundPackets26_1.PICK_ITEM_FROM_BLOCK, ServerboundBedrockPackets.BLOCK_PICK_REQUEST, wrapper -> {
            wrapper.passthroughAndMap(Types.BLOCK_POSITION1_14, BedrockTypes.BLOCK_POSITION); // position
            wrapper.passthrough(Types.BOOLEAN); // include data
            wrapper.write(Types.UNSIGNED_BYTE, (short) 9); // number of empty hotbar slots (vanilla client always sends 9)
        });
        protocol.registerServerbound(ServerboundPackets26_1.PICK_ITEM_FROM_ENTITY, ServerboundBedrockPackets.ENTITY_PICK_REQUEST, wrapper -> {
            final int entityId = wrapper.read(Types.VAR_INT); // entity id
            final boolean includeData = wrapper.read(Types.BOOLEAN); // include data

            final Entity entity = wrapper.user().get(EntityTracker.class).getEntityByJid(entityId);
            if (entity == null) {
                wrapper.cancel();
                return;
            }

            wrapper.write(BedrockTypes.LONG_LE, entity.uniqueId()); // entity unique id
            wrapper.write(Types.UNSIGNED_BYTE, (short) 9); // number of empty hotbar slots (vanilla client always sends 9)
            wrapper.write(Types.BOOLEAN, includeData); // include data
        });
    }

    public static int blockContainerSize(final String blockTag, final BedrockBlockEntity blockEntity) {
        if ((CustomBlockTags.CHEST.equals(blockTag) || CustomBlockTags.TRAPPED_CHEST.equals(blockTag))
            && blockEntity != null
            && blockEntity.tag().get("pairx") instanceof IntTag
            && blockEntity.tag().get("pairz") instanceof IntTag) {
            return 54;
        }
        return 27;
    }

    private static void addTextToDialog(final UserConnection userConnection, final Dialog dialog, final String text) {
        if (dialog.getInputs().isEmpty()) {
            for (String line : BedrockTextUtils.split(text, "\n")) {
                dialog.getBody().add(new PlainMessageBody(TextUtil.stringToTextComponent(line)));
            }
        } else {
            if (userConnection.getProtocolInfo().protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
                for (String line : BedrockTextUtils.split(text, "\n")) {
                    final TextInput textInput = new TextInput(TextUtil.stringToTextComponent(line));
                    textInput.setMaxLength(Integer.MAX_VALUE);
                    textInput.setMultiline(new TextInput.MultilineOptions(null, 1));
                    dialog.getInputs().add(new Input("dummy", textInput));
                }
            } else { // VB compatibility
                dialog.getInputs().add(new Input("dummy", new BooleanInput(TextUtil.stringToTextComponent(text))));
            }
        }
    }

    private static CreativeSlot creativeSlot(final InventoryTracker inventoryTracker, final short javaSlot) {
        if (javaSlot >= 1 && javaSlot <= 4) {
            return new CreativeSlot(inventoryTracker.getHudContainer(), javaSlot + 27);
        }
        if (javaSlot >= 5 && javaSlot <= 8) {
            return new CreativeSlot(inventoryTracker.getArmorContainer(), javaSlot - 5);
        }
        if (javaSlot >= 9 && javaSlot <= 44) {
            return new CreativeSlot(inventoryTracker.getInventoryContainer(), inventoryTracker.getInventoryContainer().bedrockSlot(javaSlot));
        }
        if (javaSlot == 45) {
            return new CreativeSlot(inventoryTracker.getOffhandContainer(), 0);
        }
        return null;
    }

    private static boolean preserveCreativeCursor(final UserConnection user, final InventoryTracker inventoryTracker,
                                                  final Container container) {
        final GameMode javaGameMode = user.get(EntityTracker.class).getClientPlayer().javaGameMode();
        return shouldPreserveCreativeCursor(javaGameMode, isPlayerInventoryContainer(inventoryTracker, container))
            || shouldSuppressCreativeHudFullRefresh(
                javaGameMode,
                container == inventoryTracker.getHudContainer(),
                inventoryTracker.getCurrentContainer() != null
            );
    }

    public static boolean shouldPreserveCreativeCursor(final GameMode javaGameMode, final boolean playerInventoryContainer) {
        return javaGameMode == GameMode.CREATIVE && playerInventoryContainer;
    }

    /**
     * Bedrock's player-only UI container owns the Bedrock cursor and redirects full Java content
     * updates to container 0. While Java's creative inventory is active there is no tracked
     * Bedrock container, and Java owns that cursor locally. Suppress the full HUD refresh so a
     * trailing authoritative empty cursor cannot erase a rejected offhand item after rollback.
     */
    public static boolean shouldSuppressCreativeHudFullRefresh(final GameMode javaGameMode,
                                                               final boolean hudContainer,
                                                               final boolean bedrockContainerOpen) {
        return javaGameMode == GameMode.CREATIVE && hudContainer && !bedrockContainerOpen;
    }

    private static boolean isPlayerInventoryContainer(final InventoryTracker inventoryTracker, final Container container) {
        return container == inventoryTracker.getInventoryContainer()
            || container == inventoryTracker.getArmorContainer()
            || container == inventoryTracker.getOffhandContainer();
    }

    private static void handleContainerClick(final UserConnection user, final int containerId, final int revision,
                                             final short slot, final byte button, final ContainerInput action) {
        final InventoryTracker inventoryTracker = user.get(InventoryTracker.class);
        if (inventoryTracker.getPendingCloseContainer() != null) return;

        final Container container = inventoryTracker.getContainerServerbound((byte) containerId);
        if (container == null) {
            if (containerId == ContainerID.CONTAINER_ID_INVENTORY.getValue()) {
                // Bedrock client can send multiple OpenInventory requests if the server doesn't respond, so this is fine here.
                PacketFactory.sendBedrockOpenInventory(user);
                PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            }
            return;
        }
        if (!container.handleClick(revision, slot, button, action)) {
            if (container.type() != ContainerType.INVENTORY) {
                PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            }
            PacketFactory.sendJavaContainerSetContent(user, container);
        }
    }

    private static void handleCreativeSlot(final UserConnection user, final short slot, final Item item) {
        final InventoryTracker inventoryTracker = user.get(InventoryTracker.class);
        if (inventoryTracker.getPendingCloseContainer() != null
            || user.get(EntityTracker.class).getClientPlayer().javaGameMode() != GameMode.CREATIVE) {
            PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            return;
        }

        final CreativeSlot target = creativeSlot(inventoryTracker, slot);
        if (item.isEmpty()) {
            if (target == null) {
                if (slot != -1) PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
                return;
            }

            final BedrockItem existingItem = target.container().getItem(target.slot());
            if (existingItem.isEmpty()) return;

            final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
            final boolean sent = user.get(InventoryRequestTracker.class).send(requestId -> {
                snapshots.put(target.container(), target.container().getItems());
                target.container().setPredictedItem(target.slot(), BedrockItem.empty());
                return List.of(new InventoryStackRequest.Destroy(
                    existingItem.amount(), requestSlot(target.container(), target.slot(), existingItem)
                ));
            }, snapshots, true);
            if (!sent) PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            return;
        }

        if (item.amount() < 1 || item.amount() > 64 || (target == null && slot != -1)) {
            PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            return;
        }

        // Entering creative can make Java echo every existing inventory slot. Sending those
        // no-op updates to Bedrock creates needless predictions and stale client request IDs.
        if (target != null && target.container().getJavaItem(target.slot()).equals(item)) return;

        final CreativeContentStorage.CreativeItem creativeItem = user.get(CreativeContentStorage.class).find(item);
        if (creativeItem == null) {
            PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
            return;
        }
        if (target != null && isCreativeInventoryEcho(
            target.container().getItem(target.slot()), creativeItem.bedrockItem())) {
            return;
        }

        final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
        final boolean sent = user.get(InventoryRequestTracker.class).send(requestId -> {
            final BedrockItem output = creativeItem.bedrockItem();
            final List<InventoryStackRequest.Action> actions = new ArrayList<>();
            actions.add(new InventoryStackRequest.CraftCreative(creativeItem.networkId(), 1));
            actions.add(new InventoryStackRequest.CraftResultsDeprecated(List.of(output.copy()), 1));

            final InventoryStackRequest.Slot createdOutput = new InventoryStackRequest.Slot(
                new FullContainerName(ContainerEnumName.CreatedOutputContainer, null), 50, requestId
            );
            if (target == null) {
                actions.add(new InventoryStackRequest.Drop(output.amount(), createdOutput, false));
                return actions;
            }

            snapshots.put(target.container(), target.container().getItems());
            final BedrockItem existingItem = target.container().getItem(target.slot());
            if (!existingItem.isEmpty()) {
                actions.add(new InventoryStackRequest.Destroy(
                    existingItem.amount(), requestSlot(target.container(), target.slot(), existingItem)
                ));
            }
            // Destroy leaves an empty destination. Its authoritative stack ID is therefore 0,
            // not the ID of the item which was just removed.
            if (target.container() == inventoryTracker.getOffhandContainer()) {
                final InventoryStackRequest.Slot cursor = requestSlot(inventoryTracker.getHudContainer(), 0, BedrockItem.empty());
                actions.add(new InventoryStackRequest.Take(output.amount(), createdOutput, cursor));
                actions.add(new InventoryStackRequest.Place(
                    output.amount(),
                    new InventoryStackRequest.Slot(cursor.container(), cursor.slot(), requestId),
                    requestSlot(target.container(), target.slot(), BedrockItem.empty())
                ));
            } else {
                actions.add(new InventoryStackRequest.Take(
                    output.amount(), createdOutput, requestSlot(target.container(), target.slot(), BedrockItem.empty())
                ));
            }

            final BedrockItem predictedItem = output.copy();
            predictedItem.setNetId(requestId);
            target.container().setPredictedItem(target.slot(), predictedItem);
            return actions;
        }, snapshots, true, target != null ? item : null);
        if (!sent) PacketFactory.sendJavaContainerSetContent(user, inventoryTracker.getInventoryContainer());
    }

    /**
     * Java can echo an already-populated creative slot with components that do not round-trip
     * exactly through item translation. Compare the authoritative Bedrock representation so
     * opening the creative screen does not manufacture a queue of redundant CraftCreative requests.
     */
    public static boolean isCreativeInventoryEcho(final BedrockItem trackedItem, final BedrockItem creativeItem) {
        return trackedItem != null && creativeItem != null
            && trackedItem.amount() == creativeItem.amount()
            && !trackedItem.isDifferent(creativeItem);
    }

    private static InventoryStackRequest.Slot requestSlot(final Container container, final int slot, final BedrockItem item) {
        return new InventoryStackRequest.Slot(
            container.getFullContainerName(slot), container.stackRequestSlot(slot), item.netId() != null ? item.netId() : 0
        );
    }

    private record StackResponseCorrection(FullContainerName containerName, int slot, int amount, Integer stackNetworkId) {
    }

    private record CreativeSlot(Container container, int slot) {
    }

}
