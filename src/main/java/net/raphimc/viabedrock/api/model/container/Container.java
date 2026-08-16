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
package net.raphimc.viabedrock.api.model.container;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.ContainerInput;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.model.InventoryStackRequest;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.storage.CraftingRecipeStorage;
import net.raphimc.viabedrock.protocol.storage.InventoryRequestTracker;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public abstract class Container {

    protected final UserConnection user;
    protected final byte containerId;
    protected final ContainerType type;
    protected final TextComponent title;
    protected final BlockPosition position;
    protected final BedrockItem[] items;
    protected final Set<String> validBlockTags;
    private int quickCraftType = -1;
    private final Set<Integer> quickCraftSlots = new LinkedHashSet<>();

    public Container(final UserConnection user, final byte containerId, final ContainerType type, final TextComponent title, final BlockPosition position, final int size, final String... validBlockTags) {
        this.user = user;
        this.containerId = containerId;
        this.type = type;
        this.title = title;
        this.position = position;
        this.items = BedrockItem.emptyArray(size);
        this.validBlockTags = Set.of(validBlockTags);
    }

    protected Container(final UserConnection user, final byte containerId, final ContainerType type, final TextComponent title, final BlockPosition position, final BedrockItem[] items, final Set<String> validBlockTags) {
        this.user = user;
        this.containerId = containerId;
        this.type = type;
        this.title = title;
        this.position = position;
        this.items = items;
        this.validBlockTags = validBlockTags;
    }

    public boolean handleClick(final int revision, final short slot, final byte button, final ContainerInput action) {
        final InventoryTracker inventoryTracker = this.user.get(InventoryTracker.class);
        final InventoryRequestTracker requestTracker = this.user.get(InventoryRequestTracker.class);
        if (action == ContainerInput.QUICK_CRAFT) {
            return this.handleQuickCraft(slot, button, inventoryTracker, requestTracker);
        }
        this.resetQuickCraft();
        if (slot == -1) return false;

        final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
        final boolean handled = requestTracker.send(requestId -> {
            this.snapshot(snapshots, inventoryTracker.getHudContainer());

            if ((this instanceof InventoryContainer || this instanceof CraftingTableContainer) && slot == 0) {
                final int gridStart = this instanceof InventoryContainer ? 28 : 32;
                final int gridWidth = this instanceof InventoryContainer ? 2 : 3;
                return this.handleCrafting(button, action, inventoryTracker, snapshots, gridStart, gridWidth, requestId);
            }
            return switch (action) {
                case PICKUP -> this.singleton(this.handlePickup(slot, button, inventoryTracker, snapshots, requestId));
                case PICKUP_ALL -> this.handlePickupAll(slot, button, inventoryTracker, snapshots, requestId);
                case SWAP -> this.singleton(this.handleHotbarSwap(slot, button, inventoryTracker, snapshots, requestId));
                case QUICK_MOVE -> this.handleQuickMove(slot, inventoryTracker, snapshots, requestId);
                case THROW -> this.singleton(this.handleThrow(slot, button, inventoryTracker, snapshots, requestId));
                default -> List.of();
            };
        }, snapshots);
        if (handled && (this.isCraftingInputSlot(slot)
            || (action == ContainerInput.PICKUP_ALL && (this instanceof InventoryContainer || this instanceof CraftingTableContainer)))) {
            this.updateCraftingOutput(inventoryTracker);
            PacketFactory.sendJavaContainerSetContent(this.user, this);
        }
        return handled;
    }

    public boolean handleSwapWithOffhand(final InventoryTracker tracker) {
        if (!(this instanceof InventoryContainer inventory)) return false;

        final Container offhand = tracker.getOffhandContainer();
        final int hotbarSlot = inventory.getSelectedHotbarSlot();
        final BedrockItem hotbarItem = inventory.getItem(hotbarSlot);
        final BedrockItem offhandItem = offhand.getItem(0);
        if (hotbarItem.isEmpty() && offhandItem.isEmpty()) return false;

        final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
        return this.user.get(InventoryRequestTracker.class).send(requestId -> {
            this.snapshot(snapshots, inventory);
            this.snapshot(snapshots, offhand);

            inventory.setPredictedItem(hotbarSlot, this.markModified(offhandItem.copy(), requestId));
            offhand.setPredictedItem(0, this.markModified(hotbarItem.copy(), requestId));

            if (hotbarItem.isEmpty()) {
                return List.of(new InventoryStackRequest.Place(
                    offhandItem.amount(),
                    this.requestSlot(offhand, 0, offhandItem),
                    this.requestSlot(inventory, hotbarSlot, hotbarItem)
                ));
            } else if (offhandItem.isEmpty()) {
                return List.of(new InventoryStackRequest.Place(
                    hotbarItem.amount(),
                    this.requestSlot(inventory, hotbarSlot, hotbarItem),
                    this.requestSlot(offhand, 0, offhandItem)
                ));
            }
            return List.of(new InventoryStackRequest.Swap(
                this.requestSlot(inventory, hotbarSlot, hotbarItem),
                this.requestSlot(offhand, 0, offhandItem)
            ));
        }, snapshots);
    }

    private boolean handleQuickCraft(final short javaSlot, final byte button, final InventoryTracker tracker,
                                     final InventoryRequestTracker requestTracker) {
        final int buttonValue = Byte.toUnsignedInt(button);
        final int stage = buttonValue & 3;
        final int type = (buttonValue >> 2) & 3;
        final Container cursor = tracker.getHudContainer();

        if (stage == 0) { // Start drag
            this.resetQuickCraft();
            if (type > 1 || cursor.getItem(0).isEmpty()) return false; // Middle-drag is creative-only.
            this.quickCraftType = type;
            return true;
        }
        if (type != this.quickCraftType) {
            this.resetQuickCraft();
            return false;
        }
        if (stage == 1) { // Add hovered slot
            final BedrockItem cursorItem = cursor.getItem(0);
            if (cursorItem.isEmpty()) {
                this.resetQuickCraft();
                return false;
            }
            if (javaSlot < 0 || this.quickCraftSlots.size() >= cursorItem.amount()) return true;

            final SlotRef target = this.resolveJavaSlot(javaSlot, tracker);
            if (target == null || (target.container() == cursor && target.slot() == 0)) return true;
            final BedrockItem targetItem = target.container().getItem(target.slot());
            if (targetItem.isEmpty()
                || (!targetItem.isDifferent(cursorItem) && targetItem.amount() < this.maxStackSize(cursorItem))) {
                this.quickCraftSlots.add((int) javaSlot);
            }
            return true;
        }
        if (stage != 2) {
            this.resetQuickCraft();
            return false;
        }

        final List<Integer> javaSlots = List.copyOf(this.quickCraftSlots);
        final int completedType = this.quickCraftType;
        this.resetQuickCraft();
        if (javaSlots.isEmpty()) return true;

        final Map<Container, BedrockItem[]> snapshots = new IdentityHashMap<>();
        final boolean handled = requestTracker.send(requestId ->
            this.finishQuickCraft(javaSlots, completedType, tracker, snapshots, requestId), snapshots
        );
        if (handled && (this instanceof InventoryContainer || this instanceof CraftingTableContainer)) {
            this.updateCraftingOutput(tracker);
            PacketFactory.sendJavaContainerSetContent(this.user, this);
        }
        return handled;
    }

    private List<InventoryStackRequest.Action> finishQuickCraft(final List<Integer> javaSlots, final int type,
                                                                final InventoryTracker tracker,
                                                                final Map<Container, BedrockItem[]> snapshots,
                                                                final int requestId) {
        final Container cursor = tracker.getHudContainer();
        BedrockItem cursorItem = cursor.getItem(0);
        if (cursorItem.isEmpty()) return List.of();

        final int placePerSlot = type == 0 ? cursorItem.amount() / javaSlots.size() : 1;
        if (placePerSlot <= 0) return List.of();

        final List<InventoryStackRequest.Action> actions = new ArrayList<>(javaSlots.size());
        for (int javaSlot : javaSlots) {
            if (cursorItem.isEmpty()) break;
            final SlotRef target = this.resolveJavaSlot(javaSlot, tracker);
            if (target == null || (target.container() == cursor && target.slot() == 0)) continue;

            final BedrockItem targetItem = target.container().getItem(target.slot());
            if (!targetItem.isEmpty() && targetItem.isDifferent(cursorItem)) continue;
            final int capacity = targetItem.isEmpty()
                ? this.maxStackSize(cursorItem) : this.maxStackSize(cursorItem) - targetItem.amount();
            final int count = Math.min(Math.min(placePerSlot, capacity), cursorItem.amount());
            if (count <= 0) continue;

            this.snapshot(snapshots, cursor);
            this.snapshot(snapshots, target.container());
            actions.add(new InventoryStackRequest.Place(
                count,
                this.requestSlot(cursor, 0, cursorItem),
                this.requestSlot(target.container(), target.slot(), targetItem)
            ));

            final BedrockItem placed = targetItem.isEmpty()
                ? this.withAmount(cursorItem, count)
                : this.withAmount(targetItem, targetItem.amount() + count);
            target.container().setPredictedItem(target.slot(), this.markModified(placed, requestId));
            cursorItem = this.markModified(this.withRemovedAmount(cursorItem, count), requestId);
            cursor.setPredictedItem(0, cursorItem);
        }
        return actions;
    }

    private void resetQuickCraft() {
        this.quickCraftType = -1;
        this.quickCraftSlots.clear();
    }

    private boolean isCraftingInputSlot(final int javaSlot) {
        return (this instanceof InventoryContainer && javaSlot >= 1 && javaSlot <= 4)
            || (this instanceof CraftingTableContainer && javaSlot >= 1 && javaSlot <= 9);
    }

    private void updateCraftingOutput(final InventoryTracker tracker) {
        final int gridStart = this instanceof InventoryContainer ? 28 : 32;
        final int gridWidth = this instanceof InventoryContainer ? 2 : 3;
        final BedrockItem[] gridItems = new BedrockItem[gridWidth * gridWidth];
        final int[] gridSlots = new int[gridItems.length];
        for (int i = 0; i < gridItems.length; i++) {
            gridSlots[i] = gridStart + i;
            gridItems[i] = tracker.getHudContainer().getItem(gridSlots[i]);
        }

        final CraftingRecipeStorage.Match recipe = this.user.get(CraftingRecipeStorage.class).find(gridItems, gridSlots, gridWidth);
        tracker.getHudContainer().setItem(50, recipe != null ? recipe.output() : BedrockItem.empty());
    }

    private List<InventoryStackRequest.Action> handleCrafting(final byte button, final ContainerInput action,
                                                              final InventoryTracker tracker, final Map<Container, BedrockItem[]> snapshots,
                                                              final int gridStart, final int gridWidth, final int requestId) {
        if ((action != ContainerInput.PICKUP || button != 0) && action != ContainerInput.QUICK_MOVE && action != ContainerInput.SWAP) {
            return List.of();
        }
        if (action == ContainerInput.SWAP && (button < 0 || button > 8)) return List.of();

        final Container hud = tracker.getHudContainer();
        final InventoryContainer inventory = tracker.getInventoryContainer();
        final BedrockItem output = hud.getItem(50);
        if (output.isEmpty()) return List.of();

        final int[] gridSlots = new int[gridWidth * gridWidth];
        final BedrockItem[] gridItems = new BedrockItem[gridSlots.length];
        for (int i = 0; i < gridSlots.length; i++) {
            gridSlots[i] = gridStart + i;
            gridItems[i] = hud.getItem(gridSlots[i]);
        }
        final CraftingRecipeStorage.Match recipe = this.user.get(CraftingRecipeStorage.class).find(output, gridItems, gridSlots, gridWidth);
        if (recipe == null) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Could not match Bedrock recipe for crafting output " + output.identifier());
            return List.of();
        }

        if (recipe.consumedSlots().isEmpty()) return List.of();
        int maxCrafts = 255;
        boolean hasRemainder = false;
        for (CraftingRecipeStorage.ConsumedSlot consumed : recipe.consumedSlots()) {
            if (consumed.count() <= 0) return List.of();
            final BedrockItem ingredient = hud.getItem(consumed.slot());
            maxCrafts = Math.min(maxCrafts, ingredient.amount() / consumed.count());
            maxCrafts = Math.min(maxCrafts, 64 / consumed.count());
            hasRemainder |= !this.craftingRemainder(ingredient).isEmpty();
        }
        if (maxCrafts <= 0) return List.of();

        final int crafts;
        final SlotRef destination;
        if (action == ContainerInput.PICKUP) {
            destination = new SlotRef(hud, 0);
            crafts = 1;
        } else if (action == ContainerInput.SWAP) {
            destination = new SlotRef(inventory, button);
            if (!destination.container().getItem(destination.slot()).isEmpty()) return List.of();
            crafts = 1;
        } else {
            destination = null;
            final int craftLimit = hasRemainder ? 1 : maxCrafts;
            crafts = Math.min(craftLimit, this.inventoryCapacity(output, inventory) / output.amount());
            if (crafts <= 0) return List.of();
        }

        if (hasRemainder && !this.craftingRemaindersFit(recipe.consumedSlots(), inventory, output, action, button, crafts)) {
            return List.of();
        }

        if (destination != null) {
            final BedrockItem destinationItem = destination.container().getItem(destination.slot());
            if (!destinationItem.isEmpty()
                && (destinationItem.isDifferent(output) || destinationItem.amount() + output.amount() > this.maxStackSize(output))) {
                return List.of();
            }
        }

        this.snapshot(snapshots, hud);
        this.snapshot(snapshots, inventory);
        if (destination != null) this.snapshot(snapshots, destination.container());
        final List<InventoryStackRequest.Action> actions = new ArrayList<>();
        actions.add(new InventoryStackRequest.CraftRecipe(recipe.networkId(), crafts));
        actions.add(new InventoryStackRequest.CraftResultsDeprecated(List.of(output.copy()), crafts));
        for (CraftingRecipeStorage.ConsumedSlot consumed : recipe.consumedSlots()) {
            final BedrockItem ingredient = hud.getItem(consumed.slot());
            final int consumedAmount = consumed.count() * crafts;
            actions.add(new InventoryStackRequest.Consume(consumedAmount, this.requestSlot(hud, consumed.slot(), ingredient)));
            if (!this.craftingRemainder(ingredient).isEmpty()) {
                actions.add(new InventoryStackRequest.Create(consumed.slot()));
            }
        }

        if (action == ContainerInput.QUICK_MOVE) {
            this.addCraftOutputToInventory(actions, inventory, output, crafts * output.amount(), requestId);
        } else {
            final BedrockItem destinationItem = destination.container().getItem(destination.slot());
            actions.add(new InventoryStackRequest.Take(
                output.amount(), this.createdOutputSlot(requestId),
                this.requestSlot(destination.container(), destination.slot(), destinationItem)
            ));
            final BedrockItem newDestination = destinationItem.isEmpty()
                ? this.withAmount(output, output.amount())
                : this.withAmount(destinationItem, destinationItem.amount() + output.amount());
            destination.container().setPredictedItem(destination.slot(), this.markModified(newDestination, requestId));
        }

        for (CraftingRecipeStorage.ConsumedSlot consumed : recipe.consumedSlots()) {
            final BedrockItem ingredient = hud.getItem(consumed.slot());
            final int consumedAmount = consumed.count() * crafts;
            BedrockItem remaining = this.withRemovedAmount(ingredient, consumedAmount);
            final BedrockItem remainder = this.craftingRemainder(ingredient);
            if (!remainder.isEmpty()) {
                int remainderAmount = remainder.amount() * consumedAmount;
                if (remaining.isEmpty()) {
                    final int amountInInput = Math.min(remainderAmount, this.maxStackSize(remainder));
                    remaining = this.withAmount(remainder, amountInInput);
                    remainderAmount -= amountInInput;
                } else if (!remaining.isDifferent(remainder)) {
                    final int amountInInput = Math.min(remainderAmount, this.maxStackSize(remainder) - remaining.amount());
                    remaining = this.withAmount(remaining, remaining.amount() + amountInInput);
                    remainderAmount -= amountInInput;
                }
                if (remainderAmount > 0) this.addToInventory(inventory, remainder, remainderAmount, requestId);
            }
            hud.setPredictedItem(consumed.slot(), this.markModified(remaining, requestId));
        }
        hud.setPredictedItem(50, BedrockItem.empty());
        return actions;
    }

    private void addCraftOutputToInventory(final List<InventoryStackRequest.Action> actions, final InventoryContainer inventory,
                                           final BedrockItem output, final int totalAmount, final int requestId) {
        int remaining = totalAmount;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < inventory.size() && remaining > 0; index++) {
                final int slot = index < inventory.size() - 9 ? index + 9 : index - (inventory.size() - 9);
                final BedrockItem item = inventory.getItem(slot);
                if (pass == 0 && (item.isEmpty() || item.isDifferent(output) || item.amount() >= this.maxStackSize(output))) continue;
                if (pass == 1 && !item.isEmpty()) continue;

                final int count = Math.min(remaining, item.isEmpty()
                    ? this.maxStackSize(output) : this.maxStackSize(output) - item.amount());
                actions.add(new InventoryStackRequest.Take(count, this.createdOutputSlot(requestId), this.requestSlot(inventory, slot, item)));
                final BedrockItem moved = item.isEmpty()
                    ? this.withAmount(output, count) : this.withAmount(item, item.amount() + count);
                inventory.setPredictedItem(slot, this.markModified(moved, requestId));
                remaining -= count;
            }
        }
    }

    private InventoryStackRequest.Action handlePickup(final short javaSlot, final byte button, final InventoryTracker tracker,
                                                      final Map<Container, BedrockItem[]> snapshots, final int requestId) {
        final Container cursor = tracker.getHudContainer();
        final BedrockItem cursorItem = cursor.getItem(0);
        if (javaSlot == -999) {
            if (cursorItem.isEmpty()) return null;
            final int count = button == 0 ? cursorItem.amount() : 1;
            cursor.setPredictedItem(0, this.markModified(this.withRemovedAmount(cursorItem, count), requestId));
            return new InventoryStackRequest.Drop(count, this.requestSlot(cursor, 0, cursorItem), false);
        }

        final SlotRef target = this.resolveJavaSlot(javaSlot, tracker);
        if (target == null) return null;
        final BedrockItem targetItem = target.container().getItem(target.slot());
        if (cursorItem.isEmpty() && targetItem.isEmpty()) return null;

        this.snapshot(snapshots, target.container());
        if (cursorItem.isEmpty()) {
            final int count = button == 0 ? targetItem.amount() : (targetItem.amount() + 1) / 2;
            cursor.setPredictedItem(0, this.markModified(this.withAmount(targetItem, count), requestId));
            target.container().setPredictedItem(target.slot(), this.markModified(this.withRemovedAmount(targetItem, count), requestId));
            return new InventoryStackRequest.Take(
                count,
                this.requestSlot(target.container(), target.slot(), targetItem),
                this.requestSlot(cursor, 0, BedrockItem.empty())
            );
        }

        if (targetItem.isEmpty() || !targetItem.isDifferent(cursorItem)) {
            final int capacity = targetItem.isEmpty()
                ? this.maxStackSize(cursorItem) : this.maxStackSize(cursorItem) - targetItem.amount();
            final int requested = button == 0 ? cursorItem.amount() : 1;
            final int count = Math.min(capacity, requested);
            if (count <= 0) return null;

            final BedrockItem placed = targetItem.isEmpty() ? this.withAmount(cursorItem, count) : this.withAmount(targetItem, targetItem.amount() + count);
            target.container().setPredictedItem(target.slot(), this.markModified(placed, requestId));
            cursor.setPredictedItem(0, this.markModified(this.withRemovedAmount(cursorItem, count), requestId));
            return new InventoryStackRequest.Place(
                count,
                this.requestSlot(cursor, 0, cursorItem),
                this.requestSlot(target.container(), target.slot(), targetItem)
            );
        }

        cursor.setPredictedItem(0, this.markModified(targetItem.copy(), requestId));
        target.container().setPredictedItem(target.slot(), this.markModified(cursorItem.copy(), requestId));
        return new InventoryStackRequest.Swap(
            this.requestSlot(cursor, 0, cursorItem),
            this.requestSlot(target.container(), target.slot(), targetItem)
        );
    }

    private List<InventoryStackRequest.Action> handlePickupAll(final short clickedJavaSlot, final byte button,
                                                               final InventoryTracker tracker,
                                                               final Map<Container, BedrockItem[]> snapshots,
                                                               final int requestId) {
        if (button != 0 && button != 1) return List.of();

        final Container cursor = tracker.getHudContainer();
        BedrockItem cursorItem = cursor.getItem(0);
        if (cursorItem.isEmpty() || cursorItem.amount() >= this.maxStackSize(cursorItem)) return List.of();

        // Vanilla only starts pickup-all after the first click has emptied the double-clicked slot.
        // Inventory requests are serialized, so that first Take has already been acknowledged here.
        final SlotRef clicked = this.resolveJavaSlot(clickedJavaSlot, tracker);
        if (clicked == null || !clicked.container().getItem(clicked.slot()).isEmpty()) return List.of();

        final int javaSlotCount = this instanceof InventoryContainer || this instanceof CraftingTableContainer
            ? 46 : this.size() + 36;
        final int firstJavaSlot = button == 0 ? 0 : javaSlotCount - 1;
        final int step = button == 0 ? 1 : -1;
        final List<InventoryStackRequest.Action> actions = new ArrayList<>();

        // Match Java's two-pass order: consolidate partial stacks first, then full stacks.
        for (int pass = 0; pass < 2 && cursorItem.amount() < this.maxStackSize(cursorItem); pass++) {
            for (int javaSlot = firstJavaSlot;
                 javaSlot >= 0 && javaSlot < javaSlotCount && cursorItem.amount() < this.maxStackSize(cursorItem);
                 javaSlot += step) {
                if ((this instanceof InventoryContainer || this instanceof CraftingTableContainer) && javaSlot == 0) {
                    continue; // Crafting output requires recipe actions, never a direct Take.
                }

                final SlotRef source = this.resolveJavaSlot(javaSlot, tracker);
                if (source == null || (source.container() == cursor && source.slot() == 0)) continue;
                final BedrockItem sourceItem = source.container().getItem(source.slot());
                if (sourceItem.isEmpty() || sourceItem.isDifferent(cursorItem)) continue;
                if (pass == 0 && sourceItem.amount() >= this.maxStackSize(sourceItem)) continue;

                final int count = Math.min(sourceItem.amount(), this.maxStackSize(cursorItem) - cursorItem.amount());
                if (count <= 0) continue;

                this.snapshot(snapshots, source.container());
                actions.add(new InventoryStackRequest.Take(
                    count,
                    this.requestSlot(source.container(), source.slot(), sourceItem),
                    this.requestSlot(cursor, 0, cursorItem)
                ));

                source.container().setPredictedItem(source.slot(), this.markModified(this.withRemovedAmount(sourceItem, count), requestId));
                cursorItem = this.markModified(this.withAmount(cursorItem, cursorItem.amount() + count), requestId);
                cursor.setPredictedItem(0, cursorItem);
            }
        }
        return actions;
    }

    private InventoryStackRequest.Action handleHotbarSwap(final short javaSlot, final byte button, final InventoryTracker tracker,
                                                          final Map<Container, BedrockItem[]> snapshots, final int requestId) {
        if (button < 0 || button > 8) return null;
        final SlotRef target = this.resolveJavaSlot(javaSlot, tracker);
        if (target == null) return null;

        final InventoryContainer inventory = tracker.getInventoryContainer();
        final int hotbarSlot = button;
        if (target.container() == inventory && target.slot() == hotbarSlot) return null;

        final BedrockItem targetItem = target.container().getItem(target.slot());
        final BedrockItem hotbarItem = inventory.getItem(hotbarSlot);
        if (targetItem.isEmpty() && hotbarItem.isEmpty()) return null;

        this.snapshot(snapshots, target.container());
        this.snapshot(snapshots, inventory);
        target.container().setPredictedItem(target.slot(), this.markModified(hotbarItem.copy(), requestId));
        inventory.setPredictedItem(hotbarSlot, this.markModified(targetItem.copy(), requestId));

        if (hotbarItem.isEmpty()) {
            return new InventoryStackRequest.Place(
                targetItem.amount(),
                this.requestSlot(target.container(), target.slot(), targetItem),
                this.requestSlot(inventory, hotbarSlot, hotbarItem)
            );
        } else if (targetItem.isEmpty()) {
            return new InventoryStackRequest.Place(
                hotbarItem.amount(),
                this.requestSlot(inventory, hotbarSlot, hotbarItem),
                this.requestSlot(target.container(), target.slot(), targetItem)
            );
        }
        return new InventoryStackRequest.Swap(
            this.requestSlot(inventory, hotbarSlot, hotbarItem),
            this.requestSlot(target.container(), target.slot(), targetItem)
        );
    }

    private InventoryStackRequest.Action handleThrow(final short javaSlot, final byte button, final InventoryTracker tracker,
                                                     final Map<Container, BedrockItem[]> snapshots, final int requestId) {
        if (javaSlot == -999) {
            final Container cursor = tracker.getHudContainer();
            final BedrockItem item = cursor.getItem(0);
            if (item.isEmpty()) return null;
            final int count = button == 0 ? 1 : item.amount();
            cursor.setPredictedItem(0, this.markModified(this.withRemovedAmount(item, count), requestId));
            return new InventoryStackRequest.Drop(count, this.requestSlot(cursor, 0, item), false);
        }

        final SlotRef source = this.resolveJavaSlot(javaSlot, tracker);
        if (source == null) return null;
        final BedrockItem item = source.container().getItem(source.slot());
        if (item.isEmpty()) return null;

        this.snapshot(snapshots, source.container());
        final int count = button == 0 ? 1 : item.amount();
        source.container().setPredictedItem(source.slot(), this.markModified(this.withRemovedAmount(item, count), requestId));
        return new InventoryStackRequest.Drop(count, this.requestSlot(source.container(), source.slot(), item), false);
    }

    private List<InventoryStackRequest.Action> handleQuickMove(final short javaSlot, final InventoryTracker tracker,
                                                               final Map<Container, BedrockItem[]> snapshots, final int requestId) {
        final SlotRef source = this.resolveJavaSlot(javaSlot, tracker);
        if (source == null) return List.of();
        BedrockItem sourceItem = source.container().getItem(source.slot());
        if (sourceItem.isEmpty()) return List.of();

        final List<SlotRef> destinations = new ArrayList<>();
        final InventoryContainer inventory = tracker.getInventoryContainer();
        if (this instanceof InventoryContainer) {
            if (source.container() == inventory && source.slot() >= 9) {
                for (int slot = 0; slot < 9; slot++) destinations.add(new SlotRef(inventory, slot));
            } else {
                for (int slot = 9; slot < inventory.size(); slot++) destinations.add(new SlotRef(inventory, slot));
            }
        } else if (source.container() == this) {
            for (int slot = 9; slot < inventory.size(); slot++) destinations.add(new SlotRef(inventory, slot));
            for (int slot = 0; slot < 9; slot++) destinations.add(new SlotRef(inventory, slot));
        } else {
            for (int slot = 0; slot < this.size(); slot++) destinations.add(new SlotRef(this, slot));
        }

        final List<InventoryStackRequest.Action> actions = new ArrayList<>();
        for (boolean merge : new boolean[]{true, false}) {
            for (SlotRef destination : destinations) {
                if (sourceItem.isEmpty()) break;
                if (destination.container() == source.container() && destination.slot() == source.slot()) continue;
                final BedrockItem destinationItem = destination.container().getItem(destination.slot());
                if (merge) {
                    if (destinationItem.isEmpty() || destinationItem.isDifferent(sourceItem) || destinationItem.amount() >= this.maxStackSize(sourceItem)) continue;
                } else if (!destinationItem.isEmpty()) {
                    continue;
                }

                final int count = Math.min(sourceItem.amount(), merge
                    ? this.maxStackSize(sourceItem) - destinationItem.amount() : this.maxStackSize(sourceItem));
                if (count <= 0) continue;
                this.snapshot(snapshots, source.container());
                this.snapshot(snapshots, destination.container());
                actions.add(new InventoryStackRequest.Place(
                    count,
                    this.requestSlot(source.container(), source.slot(), sourceItem),
                    this.requestSlot(destination.container(), destination.slot(), destinationItem)
                ));

                final BedrockItem moved = destinationItem.isEmpty()
                    ? this.withAmount(sourceItem, count)
                    : this.withAmount(destinationItem, destinationItem.amount() + count);
                destination.container().setPredictedItem(destination.slot(), this.markModified(moved, requestId));
                sourceItem = this.markModified(this.withRemovedAmount(sourceItem, count), requestId);
                source.container().setPredictedItem(source.slot(), sourceItem);
            }
        }
        return actions;
    }

    private SlotRef resolveJavaSlot(final int javaSlot, final InventoryTracker tracker) {
        if (javaSlot < 0) return null;
        if (this instanceof InventoryContainer) {
            if (javaSlot >= 1 && javaSlot < 5) {
                // Java exposes the player crafting grid as slots 1-4, while Bedrock
                // stores it in the player-only UI container at slots 28-31.
                return new SlotRef(tracker.getHudContainer(), javaSlot + 27);
            } else if (javaSlot >= 5 && javaSlot < 9) {
                return new SlotRef(tracker.getArmorContainer(), javaSlot - 5);
            } else if (javaSlot == 45) {
                return new SlotRef(tracker.getOffhandContainer(), 0);
            } else if (javaSlot >= 9 && javaSlot < 45) {
                return new SlotRef(tracker.getInventoryContainer(), tracker.getInventoryContainer().bedrockSlot(javaSlot));
            }
            return null; // crafting output requires recipe actions
        }

        if (javaSlot < this.size()) {
            return new SlotRef(this, this.bedrockSlot(javaSlot));
        }
        final int inventoryJavaSlot = javaSlot - this.size() + 9;
        if (inventoryJavaSlot >= 9 && inventoryJavaSlot < 45) {
            return new SlotRef(tracker.getInventoryContainer(), tracker.getInventoryContainer().bedrockSlot(inventoryJavaSlot));
        }
        return null;
    }

    private void snapshot(final Map<Container, BedrockItem[]> snapshots, final Container container) {
        snapshots.computeIfAbsent(container, ignored -> container.getItems());
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

    private int maxStackSize(final BedrockItem item) {
        return this.user.get(ItemRewriter.class).maxStackSize(item);
    }

    private int inventoryCapacity(final BedrockItem item, final InventoryContainer inventory) {
        int capacity = 0;
        final int maxStackSize = this.maxStackSize(item);
        for (int slot = 0; slot < inventory.size(); slot++) {
            final BedrockItem target = inventory.getItem(slot);
            if (target.isEmpty()) {
                capacity += maxStackSize;
            } else if (!target.isDifferent(item)) {
                capacity += Math.max(0, maxStackSize - target.amount());
            }
        }
        return capacity;
    }

    private int addToInventory(final InventoryContainer inventory, final BedrockItem item, final int totalAmount, final int requestId) {
        int remaining = totalAmount;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < inventory.size() && remaining > 0; index++) {
                final int slot = index < inventory.size() - 9 ? index + 9 : index - (inventory.size() - 9);
                final BedrockItem target = inventory.getItem(slot);
                if (pass == 0 && (target.isEmpty() || target.isDifferent(item) || target.amount() >= this.maxStackSize(item))) continue;
                if (pass == 1 && !target.isEmpty()) continue;

                final int count = Math.min(remaining, target.isEmpty()
                    ? this.maxStackSize(item) : this.maxStackSize(item) - target.amount());
                final BedrockItem result = target.isEmpty()
                    ? this.withAmount(item, count) : this.withAmount(target, target.amount() + count);
                inventory.setPredictedItem(slot, this.markModified(result, requestId));
                remaining -= count;
            }
        }
        return totalAmount - remaining;
    }

    private boolean craftingRemaindersFit(final List<CraftingRecipeStorage.ConsumedSlot> consumedSlots,
                                          final InventoryContainer inventory, final BedrockItem output,
                                          final ContainerInput action, final byte button, final int crafts) {
        final BedrockItem[] simulatedInventory = inventory.getItems();
        if (action == ContainerInput.QUICK_MOVE) {
            if (this.addToInventory(simulatedInventory, output, crafts * output.amount()) < crafts * output.amount()) return false;
        } else if (action == ContainerInput.SWAP) {
            simulatedInventory[button] = output.copy();
        }

        for (CraftingRecipeStorage.ConsumedSlot consumed : consumedSlots) {
            final BedrockItem ingredient = this.user.get(InventoryTracker.class).getHudContainer().getItem(consumed.slot());
            final BedrockItem remainder = this.craftingRemainder(ingredient);
            if (remainder.isEmpty()) continue;

            final BedrockItem remaining = this.withRemovedAmount(ingredient, consumed.count() * crafts);
            int remainderAmount = remainder.amount() * consumed.count() * crafts;
            if (remaining.isEmpty()) {
                remainderAmount -= Math.min(remainderAmount, this.maxStackSize(remainder));
            } else if (!remaining.isDifferent(remainder)) {
                remainderAmount -= Math.min(remainderAmount, this.maxStackSize(remainder) - remaining.amount());
            }
            if (remainderAmount > 0 && this.addToInventory(simulatedInventory, remainder, remainderAmount) < remainderAmount) return false;
        }
        return true;
    }

    private int addToInventory(final BedrockItem[] inventory, final BedrockItem item, final int totalAmount) {
        int remaining = totalAmount;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < inventory.length && remaining > 0; index++) {
                final int slot = index < inventory.length - 9 ? index + 9 : index - (inventory.length - 9);
                final BedrockItem target = inventory[slot];
                if (pass == 0 && (target.isEmpty() || target.isDifferent(item) || target.amount() >= this.maxStackSize(item))) continue;
                if (pass == 1 && !target.isEmpty()) continue;

                final int count = Math.min(remaining, target.isEmpty()
                    ? this.maxStackSize(item) : this.maxStackSize(item) - target.amount());
                inventory[slot] = target.isEmpty()
                    ? this.withAmount(item, count) : this.withAmount(target, target.amount() + count);
                remaining -= count;
            }
        }
        return totalAmount - remaining;
    }

    private BedrockItem craftingRemainder(final BedrockItem item) {
        if (item.isEmpty()) return BedrockItem.empty();
        final ItemRewriter itemRewriter = this.user.get(ItemRewriter.class);
        final String identifier = itemRewriter.getItems().inverse().get(item.identifier());
        if ("minecraft:written_book".equals(identifier)
            || (identifier != null && identifier.endsWith("_banner") && item.tag() != null && item.tag().contains("Patterns"))) {
            return this.withAmount(item, 1);
        }

        if (identifier == null) return BedrockItem.empty();
        final String remainderIdentifier = switch (identifier) {
            case "minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:milk_bucket" -> "minecraft:bucket";
            case "minecraft:dragon_breath", "minecraft:honey_bottle" -> "minecraft:glass_bottle";
            default -> null;
        };
        if (remainderIdentifier == null) return BedrockItem.empty();
        final Integer runtimeId = itemRewriter.getItems().get(remainderIdentifier);
        return runtimeId != null ? new BedrockItem(runtimeId) : BedrockItem.empty();
    }

    private BedrockItem markModified(final BedrockItem item, final int requestId) {
        if (!item.isEmpty()) item.setNetId(requestId);
        return item;
    }

    private List<InventoryStackRequest.Action> singleton(final InventoryStackRequest.Action action) {
        return action != null ? List.of(action) : List.of();
    }

    private BedrockItem withAmount(final BedrockItem item, final int amount) {
        final BedrockItem copy = item.copy();
        copy.setAmount(amount);
        return copy;
    }

    private BedrockItem withRemovedAmount(final BedrockItem item, final int amount) {
        return amount >= item.amount() ? BedrockItem.empty() : this.withAmount(item, item.amount() - amount);
    }

    private record SlotRef(Container container, int slot) {
    }

    public void clearItems() {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = BedrockItem.empty();
        }
    }

    public Item getJavaItem(final int slot) {
        return this.user.get(ItemRewriter.class).javaItem(this.getItem(slot));
    }

    public Item[] getJavaItems() {
        return this.user.get(ItemRewriter.class).javaItems(this.items);
    }

    public BedrockItem getItem(final int slot) {
        return this.items[slot];
    }

    public BedrockItem[] getItems() {
        final BedrockItem[] copy = new BedrockItem[this.items.length];
        for (int i = 0; i < this.items.length; i++) {
            copy[i] = this.items[i] != null ? this.items[i].copy() : BedrockItem.empty();
        }
        return copy;
    }

    public boolean setItem(final int slot, final BedrockItem item) {
        if (slot < 0 || slot >= this.items.length) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to set item for " + this.type + ", but slot was out of bounds (" + slot + ")");
            return false;
        }

        final BedrockItem oldItem = this.items[slot];
        this.items[slot] = item;
        this.onSlotChanged(slot, oldItem, item);
        return true;
    }

    /** Updates the request-local mirror without emitting equipment side effects. */
    public boolean setPredictedItem(final int slot, final BedrockItem item) {
        if (slot < 0 || slot >= this.items.length) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to predict item for " + this.type + ", but slot was out of bounds (" + slot + ")");
            return false;
        }

        this.items[slot] = item;
        return true;
    }

    public boolean setItems(final BedrockItem[] items) {
        if (items.length != this.items.length) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Tried to set items for " + this.type + ", but items array length was not correct (" + items.length + " != " + this.items.length + ")");
            return false;
        }

        for (int i = 0; i < items.length; i++) {
            this.setItem(i, items[i] != null ? items[i].copy() : BedrockItem.empty());
        }
        return true;
    }

    public int javaSlot(final int slot) {
        return slot;
    }

    public int bedrockSlot(final int slot) {
        return slot;
    }

    public int stackRequestSlot(final int slot) {
        return slot;
    }

    public int stackResponseSlot(final int slot) {
        return slot;
    }

    public FullContainerName getFullContainerName(final int slot) {
        return new FullContainerName(ContainerEnumName.LevelEntityContainer, null);
    }

    public byte javaContainerId() {
        return this.containerId();
    }

    public int size() {
        return this.items.length;
    }

    public byte containerId() {
        return this.containerId;
    }

    public ContainerType type() {
        return this.type;
    }

    public TextComponent title() {
        return this.title;
    }

    public BlockPosition position() {
        return this.position;
    }

    public boolean isValidBlockTag(final String tag) {
        if (tag == null) {
            return false;
        } else {
            return this.validBlockTags.contains(tag);
        }
    }

    protected void onSlotChanged(final int slot, final BedrockItem oldItem, final BedrockItem newItem) {
    }

}
