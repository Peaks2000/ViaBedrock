/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package net.raphimc.viabedrock.protocol.storage;

import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tracks the shaped and shapeless recipes needed to turn Java result-slot clicks into Bedrock stack requests. */
public final class CraftingRecipeStorage extends StoredObject {

    private static final int WILDCARD_AUX_VALUE = 32767;

    private final List<Recipe> recipes = new ArrayList<>();

    public CraftingRecipeStorage(final UserConnection user) {
        super(user);
    }

    public void read(final PacketWrapper wrapper) {
        this.recipes.clear();
        final ItemRewriter itemRewriter = this.user().get(ItemRewriter.class);

        final int shapedCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        for (int i = 0; i < shapedCount; i++) {
            this.recipes.add(this.readShaped(wrapper, itemRewriter));
        }

        final int shapelessCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        for (int i = 0; i < shapelessCount; i++) {
            this.recipes.add(this.readShapeless(wrapper, itemRewriter));
        }
    }

    public Match find(final BedrockItem output, final BedrockItem[] gridItems, final int[] gridSlots, final int gridWidth) {
        for (Recipe recipe : this.recipes) {
            if (!sameItem(recipe.output(), output) || recipe.output().amount() != output.amount()) continue;

            final List<ConsumedSlot> consumed = recipe.shaped()
                ? this.matchShaped(recipe, gridItems, gridSlots, gridWidth)
                : this.matchShapeless(recipe, gridItems, gridSlots);
            if (consumed != null) {
                return new Match(recipe.networkId(), consumed);
            }
        }
        return null;
    }

    private Recipe readShaped(final PacketWrapper wrapper, final ItemRewriter itemRewriter) {
        wrapper.read(BedrockTypes.STRING); // recipe id
        final int width = wrapper.read(BedrockTypes.VAR_INT);
        final int height = wrapper.read(BedrockTypes.VAR_INT);
        final List<Ingredient> ingredients = this.readIngredients(wrapper);
        if (ingredients.size() != width * height) {
            throw new IllegalStateException("Invalid shaped recipe ingredient count: " + ingredients.size() + " != " + (width * height));
        }
        final BedrockItem output = this.readFirstOutput(wrapper, itemRewriter);
        wrapper.read(BedrockTypes.LONG_LE); // UUID most-significant bits
        wrapper.read(BedrockTypes.LONG_LE); // UUID least-significant bits
        wrapper.read(BedrockTypes.STRING); // crafting tag
        wrapper.read(BedrockTypes.VAR_INT); // priority
        final boolean assumeSymmetry = wrapper.read(Types.BOOLEAN);
        this.readOptionalRequirement(wrapper);
        final int networkId = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        return new Recipe(networkId, width, height, ingredients, output, true, assumeSymmetry);
    }

    private Recipe readShapeless(final PacketWrapper wrapper, final ItemRewriter itemRewriter) {
        wrapper.read(BedrockTypes.STRING); // recipe id
        final List<Ingredient> ingredients = this.readIngredients(wrapper);
        final BedrockItem output = this.readFirstOutput(wrapper, itemRewriter);
        wrapper.read(BedrockTypes.LONG_LE); // UUID most-significant bits
        wrapper.read(BedrockTypes.LONG_LE); // UUID least-significant bits
        wrapper.read(BedrockTypes.STRING); // crafting tag
        wrapper.read(BedrockTypes.VAR_INT); // priority
        this.readOptionalRequirement(wrapper);
        final int networkId = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        return new Recipe(networkId, 0, 0, ingredients, output, false, false);
    }

    private BedrockItem readFirstOutput(final PacketWrapper wrapper, final ItemRewriter itemRewriter) {
        final int outputCount = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        BedrockItem output = BedrockItem.empty();
        for (int i = 0; i < outputCount; i++) {
            final BedrockItem item = wrapper.read(itemRewriter.itemType()); // NetworkItemInstanceDescriptorData
            if (i == 0) output = item;
        }
        return output;
    }

    private List<Ingredient> readIngredients(final PacketWrapper wrapper) {
        final int count = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        final List<Ingredient> ingredients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ingredients.add(this.readIngredient(wrapper));
        }
        return ingredients;
    }

    private Ingredient readIngredient(final PacketWrapper wrapper) {
        final int mappedType = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
        if (mappedType == 0) {
            wrapper.read(BedrockTypes.VAR_INT); // invalid descriptor auxiliary value
            final int count = wrapper.read(BedrockTypes.VAR_INT);
            return new Ingredient(IngredientType.EMPTY, null, 0, count);
        }

        final String serializedType = wrapper.read(BedrockTypes.STRING);
        final IngredientType type;
        final String value;
        final int aux;
        switch (serializedType) {
            case "name" -> {
                type = IngredientType.ITEM;
                value = wrapper.read(BedrockTypes.STRING);
                aux = wrapper.read(BedrockTypes.VAR_INT);
            }
            case "item_tag" -> {
                type = IngredientType.TAG;
                value = wrapper.read(BedrockTypes.STRING);
                aux = wrapper.read(BedrockTypes.VAR_INT);
            }
            case "molang" -> {
                type = IngredientType.MOLANG;
                value = wrapper.read(BedrockTypes.STRING);
                wrapper.read(BedrockTypes.SHORT_LE); // MoLang version
                aux = 0;
            }
            default -> throw new IllegalStateException("Unknown recipe ingredient descriptor: " + serializedType);
        }
        return new Ingredient(type, value, aux, wrapper.read(BedrockTypes.VAR_INT));
    }

    private void readOptionalRequirement(final PacketWrapper wrapper) {
        if (!wrapper.read(Types.BOOLEAN)) return;
        wrapper.read(BedrockTypes.VAR_INT); // unlocking context
        if (wrapper.read(Types.BOOLEAN)) {
            this.readIngredients(wrapper);
        }
    }

    private List<ConsumedSlot> matchShaped(final Recipe recipe, final BedrockItem[] gridItems, final int[] gridSlots, final int gridWidth) {
        if (recipe.width() > gridWidth || recipe.height() > gridWidth) return null;
        for (boolean mirrored : recipe.assumeSymmetry() ? new boolean[]{false, true} : new boolean[]{false}) {
            for (int offsetY = 0; offsetY <= gridWidth - recipe.height(); offsetY++) {
                for (int offsetX = 0; offsetX <= gridWidth - recipe.width(); offsetX++) {
                    final List<ConsumedSlot> consumed = new ArrayList<>();
                    boolean matches = true;
                    for (int gridY = 0; gridY < gridWidth && matches; gridY++) {
                        for (int gridX = 0; gridX < gridWidth; gridX++) {
                            Ingredient ingredient = Ingredient.EMPTY;
                            if (gridX >= offsetX && gridX < offsetX + recipe.width() && gridY >= offsetY && gridY < offsetY + recipe.height()) {
                                int recipeX = gridX - offsetX;
                                if (mirrored) recipeX = recipe.width() - recipeX - 1;
                                ingredient = recipe.ingredients().get((gridY - offsetY) * recipe.width() + recipeX);
                            }
                            final int index = gridY * gridWidth + gridX;
                            if (!this.matches(ingredient, gridItems[index])) {
                                matches = false;
                                break;
                            }
                            if (ingredient.type() != IngredientType.EMPTY) {
                                consumed.add(new ConsumedSlot(gridSlots[index], ingredient.count()));
                            }
                        }
                    }
                    if (matches) return consumed;
                }
            }
        }
        return null;
    }

    private List<ConsumedSlot> matchShapeless(final Recipe recipe, final BedrockItem[] gridItems, final int[] gridSlots) {
        final List<Integer> occupied = new ArrayList<>();
        for (int i = 0; i < gridItems.length; i++) {
            if (!gridItems[i].isEmpty()) occupied.add(i);
        }
        final List<Ingredient> ingredients = recipe.ingredients().stream().filter(i -> i.type() != IngredientType.EMPTY).toList();
        if (occupied.size() != ingredients.size()) return null;

        final int[] assignedSlots = new int[ingredients.size()];
        final boolean[] used = new boolean[occupied.size()];
        if (!this.matchShapelessIngredient(0, ingredients, occupied, gridItems, used, assignedSlots)) return null;

        final List<ConsumedSlot> consumed = new ArrayList<>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            consumed.add(new ConsumedSlot(gridSlots[assignedSlots[i]], ingredients.get(i).count()));
        }
        return consumed;
    }

    private boolean matchShapelessIngredient(final int ingredientIndex, final List<Ingredient> ingredients, final List<Integer> occupied,
                                             final BedrockItem[] gridItems, final boolean[] used, final int[] assignedSlots) {
        if (ingredientIndex == ingredients.size()) return true;
        for (int i = 0; i < occupied.size(); i++) {
            if (used[i]) continue;
            final int gridIndex = occupied.get(i);
            if (!this.matches(ingredients.get(ingredientIndex), gridItems[gridIndex])) continue;
            used[i] = true;
            assignedSlots[ingredientIndex] = gridIndex;
            if (this.matchShapelessIngredient(ingredientIndex + 1, ingredients, occupied, gridItems, used, assignedSlots)) return true;
            used[i] = false;
        }
        return false;
    }

    private boolean matches(final Ingredient ingredient, final BedrockItem item) {
        if (ingredient.type() == IngredientType.EMPTY) return item.isEmpty();
        if (item.isEmpty() || item.amount() < ingredient.count()) return false;

        final ItemRewriter itemRewriter = this.user().get(ItemRewriter.class);
        final String identifier = itemRewriter.getItems().inverse().get(item.identifier());
        if (identifier == null) return false;
        return switch (ingredient.type()) {
            case ITEM -> ingredient.value().equals(identifier) && (ingredient.aux() == WILDCARD_AUX_VALUE || ingredient.aux() == item.data());
            case TAG -> {
                final Set<String> tags = BedrockProtocol.MAPPINGS.getBedrockItemTags().get(identifier);
                yield tags != null && tags.contains(ingredient.value());
            }
            case EMPTY, MOLANG -> false;
        };
    }

    private static boolean sameItem(final BedrockItem first, final BedrockItem second) {
        return !first.isEmpty() && !second.isEmpty() && first.identifier() == second.identifier() && first.data() == second.data();
    }

    private enum IngredientType {
        EMPTY,
        ITEM,
        TAG,
        MOLANG
    }

    private record Ingredient(IngredientType type, String value, int aux, int count) {
        private static final Ingredient EMPTY = new Ingredient(IngredientType.EMPTY, null, 0, 0);
    }

    private record Recipe(int networkId, int width, int height, List<Ingredient> ingredients, BedrockItem output,
                          boolean shaped, boolean assumeSymmetry) {
    }

    public record ConsumedSlot(int slot, int count) {
    }

    public record Match(int networkId, List<ConsumedSlot> consumedSlots) {
    }
}
