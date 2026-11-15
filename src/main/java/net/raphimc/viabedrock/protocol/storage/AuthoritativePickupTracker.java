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

import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerID;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.InventorySourceType;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.inventory.InventoryActionData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Correlates a server-applied pickup transaction with its subsequent TakeItemActor animation. */
public final class AuthoritativePickupTracker {

    private static final long MAX_AGE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final int MAX_ENTRIES = 64;

    private final Deque<Pickup> pickups = new ArrayDeque<>();

    public void remember(final List<InventoryActionData> actions, final Set<Integer> updatedPlayerSlots,
                         final long nowNanos) {
        this.expire(nowNanos);
        final List<Pickup> recorded = new ArrayList<>();
        for (final InventoryActionData action : actions) {
            final BedrockItem item = action.fromItem();
            if (action.source().type() != InventorySourceType.World_Interaction || item.isEmpty()) {
                continue;
            }
            final int removed = item.amount() - amountOf(item, action.toItem());
            if (removed <= 0 || (!action.toItem().isEmpty() && item.isDifferent(action.toItem()))) {
                continue;
            }

            // A pickup moves the same item from the world into the player's inventory.
            // A generic slot change, such as a command grant or click, is not evidence of a pickup.
            int added = 0;
            for (final InventoryActionData update : actions) {
                if (update.source().type() == InventorySourceType.Container_Inventory
                    && update.source().containerId() == ContainerID.CONTAINER_ID_INVENTORY.getValue()
                    && updatedPlayerSlots.contains(update.slot())) {
                    added += amountOf(item, update.toItem()) - amountOf(item, update.fromItem());
                }
            }
            for (final Pickup previous : recorded) {
                if (!item.isDifferent(previous.item)) {
                    added -= previous.remaining;
                }
            }
            if (added > 0) {
                if (this.pickups.size() == MAX_ENTRIES) {
                    this.pickups.removeFirst();
                }
                final Pickup pickup = new Pickup(item.copy(), Math.min(removed, added), nowNanos);
                this.pickups.addLast(pickup);
                recorded.add(pickup);
            }
        }
    }

    public int consume(final BedrockItem item, final long nowNanos) {
        this.expire(nowNanos);
        if (item.isEmpty()) {
            return 0;
        }
        final Iterator<Pickup> iterator = this.pickups.iterator();
        while (iterator.hasNext()) {
            final Pickup pickup = iterator.next();
            if (!pickup.item.isDifferent(item)) {
                final int amount = Math.min(pickup.remaining, item.amount());
                pickup.remaining -= amount;
                if (pickup.remaining == 0) {
                    iterator.remove();
                }
                return amount;
            }
        }
        return 0;
    }

    private void expire(final long nowNanos) {
        while (!this.pickups.isEmpty() && nowNanos - this.pickups.peekFirst().receivedNanos >= MAX_AGE_NANOS) {
            this.pickups.removeFirst();
        }
    }

    private static int amountOf(final BedrockItem match, final BedrockItem candidate) {
        return !candidate.isEmpty() && !match.isDifferent(candidate) ? candidate.amount() : 0;
    }

    private static final class Pickup {
        private final BedrockItem item;
        private final long receivedNanos;
        private int remaining;

        private Pickup(final BedrockItem item, final int remaining, final long receivedNanos) {
            this.item = item;
            this.remaining = remaining;
            this.receivedNanos = receivedNanos;
        }
    }
}
