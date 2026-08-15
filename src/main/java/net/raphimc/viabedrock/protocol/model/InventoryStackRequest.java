/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.protocol.model;

import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemStackRequestActionType;

import java.util.List;
import java.util.Objects;

/** Protocol-2168 Cereal model for a server-authoritative inventory request. */
public record InventoryStackRequest(int requestId, List<Action> actions) {

    public InventoryStackRequest {
        if (requestId >= 0 || (requestId & 1) == 0) {
            throw new IllegalArgumentException("Bedrock client request IDs must be negative odd numbers: " + requestId);
        }
        actions = List.copyOf(actions);
        if (actions.isEmpty() || actions.size() > 100) {
            throw new IllegalArgumentException("Inventory requests must contain between 1 and 100 actions");
        }
    }

    public record Slot(FullContainerName container, int slot, int stackNetworkId) {
        public Slot {
            Objects.requireNonNull(container, "container");
            if (slot < 0 || slot > 255) {
                throw new IllegalArgumentException("Inventory request slot must fit an unsigned byte: " + slot);
            }
        }
    }

    public sealed interface Action permits Take, Place, Swap, Drop, Destroy, Consume, Create, CraftRecipe, CraftCreative, CraftResultsDeprecated {

        ItemStackRequestActionType type();

        default int mappedType() {
            return this.type().getValue();
        }
    }

    public record Take(int count, Slot source, Slot destination) implements Action {
        public Take {
            validateStackCount(count);
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Take;
        }
    }

    public record Place(int count, Slot source, Slot destination) implements Action {
        public Place {
            validateStackCount(count);
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Place;
        }
    }

    public record Swap(Slot source, Slot destination) implements Action {
        public Swap {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Swap;
        }
    }

    public record Drop(int count, Slot source, boolean randomly) implements Action {
        public Drop {
            validateStackCount(count);
            Objects.requireNonNull(source, "source");
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Drop;
        }
    }

    public record Destroy(int count, Slot source) implements Action {
        public Destroy {
            validateStackCount(count);
            Objects.requireNonNull(source, "source");
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Destroy;
        }
    }

    public record Consume(int count, Slot source) implements Action {
        public Consume {
            validateStackCount(count);
            Objects.requireNonNull(source, "source");
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Consume;
        }
    }

    public record Create(int slot) implements Action {
        public Create {
            if (slot < 0 || slot > 255) {
                throw new IllegalArgumentException("Created inventory slot must fit an unsigned byte: " + slot);
            }
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.Create;
        }
    }

    public record CraftRecipe(int recipeNetworkId, int requestedCrafts) implements Action {
        public CraftRecipe {
            if (recipeNetworkId <= 0) {
                throw new IllegalArgumentException("Recipe network ID must be positive: " + recipeNetworkId);
            }
            validateCraftCount(requestedCrafts);
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftRecipe;
        }

        @Override
        public int mappedType() {
            return 10; // Protocol 2168 removes the two deprecated item-container actions from the mapped enum.
        }
    }

    public record CraftCreative(int creativeItemNetworkId, int requestedCrafts) implements Action {
        public CraftCreative {
            if (creativeItemNetworkId <= 0) {
                throw new IllegalArgumentException("Creative item network ID must be positive: " + creativeItemNetworkId);
            }
            validateCraftCount(requestedCrafts);
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftCreative;
        }

        @Override
        public int mappedType() {
            return 12; // Protocol 2168 removes the two deprecated item-container actions from the mapped enum.
        }
    }

    public record CraftResultsDeprecated(List<BedrockItem> results, int timesCrafted) implements Action {
        public CraftResultsDeprecated {
            results = List.copyOf(results);
            if (results.isEmpty()) {
                throw new IllegalArgumentException("CraftResultsDeprecated must contain at least one result item");
            }
            for (BedrockItem result : results) {
                if (result == null || result.isEmpty()) {
                    throw new IllegalArgumentException("CraftResultsDeprecated cannot contain an empty item");
                }
            }
            validateCraftCount(timesCrafted);
        }

        @Override
        public ItemStackRequestActionType type() {
            return ItemStackRequestActionType.CraftResults;
        }

        @Override
        public int mappedType() {
            return 17;
        }
    }

    private static void validateStackCount(final int count) {
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("Inventory action count must be between 1 and 64: " + count);
        }
    }

    private static void validateCraftCount(final int count) {
        if (count < 1 || count > 255) {
            throw new IllegalArgumentException("Craft count must fit a non-zero unsigned byte: " + count);
        }
    }
}
