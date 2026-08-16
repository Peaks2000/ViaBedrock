/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.api.model.container;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.StructuredItem;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.ContainerInput;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.BedrockTradeOffer;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.InventoryRequestTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Java merchant view backed by Bedrock's trade UI slots. */
public final class MerchantContainer extends Container {

    private List<BedrockTradeOffer> offers = List.of();
    private int selectedOffer = -1;

    public MerchantContainer(final UserConnection user, final byte containerId, final TextComponent title) {
        super(user, containerId, ContainerType.TRADE, title, null, 3);
    }

    public void setOffers(final List<BedrockTradeOffer> offers) {
        this.offers = List.copyOf(offers);
        if (this.selectedOffer >= this.offers.size()) this.selectedOffer = -1;
    }

    public List<BedrockTradeOffer> offers() {
        return this.offers;
    }

    public static boolean matchesCost(final BedrockItem item, final BedrockItem cost) {
        if (cost == null || cost.isEmpty()) return true;
        if (item == null || item.isEmpty() || item.identifier() != cost.identifier()) return false;
        if (!cost.hasWildcardData()) return !item.isDifferent(cost);
        return (cost.blockRuntimeId() == 0 || item.blockRuntimeId() == cost.blockRuntimeId())
            && Objects.equals(item.tag(), cost.tag());
    }

    public void selectTrade(final int index) {
        if (index < 0 || index >= this.offers.size()) return;
        final BedrockTradeOffer offer = this.offers.get(index);
        if (offer.outOfStock() || offer.costA().isEmpty() || offer.result().isEmpty()) return;

        final InventoryTracker tracker = this.user.get(InventoryTracker.class);
        final InventoryContainer inventory = tracker.getInventoryContainer();
        final InventoryRequestTracker requestTracker = this.user.get(InventoryRequestTracker.class);
        requestTracker.executeWhenIdle(() -> {
            final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
            snapshots.put(this, this.getItems());
            snapshots.put(inventory, inventory.getItems());
            final boolean sent = requestTracker.send(requestId -> {
                final List<InventoryStackRequest.Action> actions = new ArrayList<>();
                if (!this.returnPayment(actions, inventory, 0, requestId)
                    || !this.returnPayment(actions, inventory, 1, requestId)
                    || !this.fillPayment(actions, inventory, 0, offer.costA(), requestId)
                    || !this.fillPayment(actions, inventory, 1, offer.costB(), requestId)) {
                    this.setItems(snapshots.get(this));
                    inventory.setItems(snapshots.get(inventory));
                    return List.of();
                }

                this.selectedOffer = index;
                this.setPredictedItem(2, offer.result().copy());
                return actions;
            }, snapshots);
            if (!sent) {
                this.setItems(snapshots.get(this));
                inventory.setItems(snapshots.get(inventory));
                PacketFactory.sendJavaContainerSetContent(this.user, this);
            }
        });
    }

    @Override
    public boolean handleClick(final int revision, final short slot, final byte button, final ContainerInput action) {
        if (slot != 2) return super.handleClick(revision, slot, button, action);
        if ((action != ContainerInput.PICKUP || button != 0) && action != ContainerInput.QUICK_MOVE) return false;
        if (this.selectedOffer < 0 || this.selectedOffer >= this.offers.size()) return false;

        final BedrockTradeOffer offer = this.offers.get(this.selectedOffer);
        if (offer.outOfStock() || !this.hasPayment(0, offer.costA()) || !this.hasPayment(1, offer.costB())) return false;

        final InventoryTracker tracker = this.user.get(InventoryTracker.class);
        final InventoryContainer inventory = tracker.getInventoryContainer();
        final Container cursor = tracker.getHudContainer();
        final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
        final boolean sent = this.user.get(InventoryRequestTracker.class).send(requestId -> {
            snapshots.put(this, this.getItems());
            snapshots.put(inventory, inventory.getItems());
            snapshots.put(cursor, cursor.getItems());

            final List<InventoryStackRequest.Action> actions = new ArrayList<>();
            actions.add(new InventoryStackRequest.CraftRecipe(offer.networkId(), 1));
            actions.add(new InventoryStackRequest.CraftResultsDeprecated(List.of(offer.result().copy()), 1));
            actions.add(new InventoryStackRequest.Consume(offer.costA().amount(), this.requestSlot(this, 0, this.getItem(0))));
            if (!offer.costB().isEmpty()) {
                actions.add(new InventoryStackRequest.Consume(offer.costB().amount(), this.requestSlot(this, 1, this.getItem(1))));
            }

            if (action == ContainerInput.QUICK_MOVE) {
                if (!this.takeResultToInventory(actions, inventory, offer.result(), requestId)) return List.of();
            } else {
                final BedrockItem carried = cursor.getItem(0);
                if (!carried.isEmpty() && (carried.isDifferent(offer.result())
                    || carried.amount() + offer.result().amount() > this.user.get(ItemRewriter.class).maxStackSize(offer.result()))) {
                    return List.of();
                }
                actions.add(new InventoryStackRequest.Take(
                    offer.result().amount(), this.createdOutputSlot(requestId), this.requestSlot(cursor, 0, carried)
                ));
                final BedrockItem result = carried.isEmpty() ? offer.result().copy() : carried.copy();
                result.setAmount(carried.amount() + offer.result().amount());
                result.setNetId(requestId);
                cursor.setPredictedItem(0, result);
            }

            this.consumePayment(0, offer.costA().amount(), requestId);
            if (!offer.costB().isEmpty()) this.consumePayment(1, offer.costB().amount(), requestId);
            this.setPredictedItem(2, BedrockItem.empty());
            return actions;
        }, snapshots);
        if (!sent) {
            if (snapshots.containsKey(this)) this.setItems(snapshots.get(this));
            if (snapshots.containsKey(inventory)) inventory.setItems(snapshots.get(inventory));
            if (snapshots.containsKey(cursor)) cursor.setItems(snapshots.get(cursor));
        }
        return sent;
    }

    private boolean returnPayment(final List<InventoryStackRequest.Action> actions, final InventoryContainer inventory,
                                  final int paymentSlot, final int requestId) {
        BedrockItem payment = this.getItem(paymentSlot);
        if (payment.isEmpty()) return true;
        for (int pass = 0; pass < 2 && !payment.isEmpty(); pass++) {
            for (int slot = 0; slot < inventory.size() && !payment.isEmpty(); slot++) {
                final BedrockItem target = inventory.getItem(slot);
                if (pass == 0 && (target.isEmpty() || target.isDifferent(payment)
                    || target.amount() >= this.user.get(ItemRewriter.class).maxStackSize(payment))) continue;
                if (pass == 1 && !target.isEmpty()) continue;
                final int count = Math.min(payment.amount(), target.isEmpty()
                    ? this.user.get(ItemRewriter.class).maxStackSize(payment)
                    : this.user.get(ItemRewriter.class).maxStackSize(payment) - target.amount());
                actions.add(new InventoryStackRequest.Place(
                    count, this.requestSlot(this, paymentSlot, payment), this.requestSlot(inventory, slot, target)
                ));
                final BedrockItem moved = target.isEmpty() ? payment.copy() : target.copy();
                moved.setAmount(target.amount() + count);
                moved.setNetId(requestId);
                inventory.setPredictedItem(slot, moved);
                payment = this.withRemovedAmount(payment, count, requestId);
                this.setPredictedItem(paymentSlot, payment);
            }
        }
        return payment.isEmpty();
    }

    private boolean fillPayment(final List<InventoryStackRequest.Action> actions, final InventoryContainer inventory,
                                final int paymentSlot, final BedrockItem cost, final int requestId) {
        if (cost.isEmpty()) {
            this.setPredictedItem(paymentSlot, BedrockItem.empty());
            return true;
        }
        int remaining = cost.amount();
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            final BedrockItem source = inventory.getItem(slot);
            if (!matchesCost(source, cost)) continue;
            final int count = Math.min(remaining, source.amount());
            final BedrockItem target = this.getItem(paymentSlot);
            if (!target.isEmpty() && target.isDifferent(source)) continue;
            actions.add(new InventoryStackRequest.Place(
                count, this.requestSlot(inventory, slot, source), this.requestSlot(this, paymentSlot, target)
            ));
            inventory.setPredictedItem(slot, this.withRemovedAmount(source, count, requestId));
            final BedrockItem filled = target.isEmpty() ? source.copy() : target.copy();
            filled.setAmount(target.amount() + count);
            filled.setNetId(requestId);
            this.setPredictedItem(paymentSlot, filled);
            remaining -= count;
        }
        return remaining == 0;
    }

    private boolean takeResultToInventory(final List<InventoryStackRequest.Action> actions,
                                          final InventoryContainer inventory, final BedrockItem result,
                                          final int requestId) {
        int remaining = result.amount();
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
                final BedrockItem target = inventory.getItem(slot);
                if (pass == 0 && (target.isEmpty() || target.isDifferent(result)
                    || target.amount() >= this.user.get(ItemRewriter.class).maxStackSize(result))) continue;
                if (pass == 1 && !target.isEmpty()) continue;
                final int count = Math.min(remaining, target.isEmpty()
                    ? this.user.get(ItemRewriter.class).maxStackSize(result)
                    : this.user.get(ItemRewriter.class).maxStackSize(result) - target.amount());
                actions.add(new InventoryStackRequest.Take(
                    count, this.createdOutputSlot(requestId), this.requestSlot(inventory, slot, target)
                ));
                final BedrockItem moved = target.isEmpty() ? result.copy() : target.copy();
                moved.setAmount(target.amount() + count);
                moved.setNetId(requestId);
                inventory.setPredictedItem(slot, moved);
                remaining -= count;
            }
        }
        return remaining == 0;
    }

    private boolean hasPayment(final int slot, final BedrockItem cost) {
        return matchesCost(this.getItem(slot), cost) && this.getItem(slot).amount() >= cost.amount();
    }

    private void consumePayment(final int slot, final int amount, final int requestId) {
        this.setPredictedItem(slot, this.withRemovedAmount(this.getItem(slot), amount, requestId));
    }

    private BedrockItem withRemovedAmount(final BedrockItem item, final int amount, final int requestId) {
        if (amount >= item.amount()) return BedrockItem.empty();
        final BedrockItem remaining = item.copy();
        remaining.setAmount(item.amount() - amount);
        remaining.setNetId(requestId);
        return remaining;
    }

    private InventoryStackRequest.Slot requestSlot(final Container container, final int slot, final BedrockItem item) {
        return new InventoryStackRequest.Slot(
            container.getFullContainerName(slot), container.stackRequestSlot(slot), item.netId() != null ? item.netId() : 0
        );
    }

    private InventoryStackRequest.Slot createdOutputSlot(final int requestId) {
        return new InventoryStackRequest.Slot(
            new FullContainerName(ContainerEnumName.CreatedOutputContainer, null), 50, requestId
        );
    }

    @Override
    public Item[] getJavaItems() {
        final Item[] player = this.user.get(InventoryTracker.class).getInventoryContainer().getJavaItems();
        final Item[] items = StructuredItem.emptyArray(39);
        items[0] = this.getJavaItem(0);
        items[1] = this.getJavaItem(1);
        items[2] = this.getJavaItem(2);
        System.arraycopy(player, 9, items, 3, 27);
        System.arraycopy(player, 36, items, 30, 9);
        return items;
    }

    @Override
    public FullContainerName getFullContainerName(final int slot) {
        return new FullContainerName(switch (slot) {
            case 0, 4 -> ContainerEnumName.Trade2Ingredient1Container;
            case 1, 5 -> ContainerEnumName.Trade2Ingredient2Container;
            case 2, 50 -> ContainerEnumName.Trade2ResultPreviewContainer;
            default -> ContainerEnumName.LevelEntityContainer;
        }, null);
    }

    @Override
    public int stackRequestSlot(final int slot) {
        return switch (slot) {
            case 0 -> 4;
            case 1 -> 5;
            case 2 -> 50;
            default -> slot;
        };
    }

    @Override
    public int stackResponseSlot(final int slot) {
        return switch (slot) {
            case 4 -> 0;
            case 5 -> 1;
            case 50 -> 2;
            default -> slot;
        };
    }
}
