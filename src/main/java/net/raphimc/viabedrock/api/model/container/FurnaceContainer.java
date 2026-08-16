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
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.generated.bedrock.CustomBlockTags;
import net.raphimc.viabedrock.protocol.model.FullContainerName;

public final class FurnaceContainer extends Container {

    public FurnaceContainer(final UserConnection user, final byte containerId, final ContainerType type,
                            final TextComponent title, final BlockPosition position) {
        super(user, containerId, type, title, position, 3, blockTag(type));
    }

    @Override
    public FullContainerName getFullContainerName(final int slot) {
        final ContainerEnumName name = switch (slot) {
            case 0 -> switch (this.type) {
                case BLAST_FURNACE -> ContainerEnumName.BlastFurnaceIngredientContainer;
                case SMOKER -> ContainerEnumName.SmokerIngredientContainer;
                default -> ContainerEnumName.FurnaceIngredientContainer;
            };
            case 1 -> ContainerEnumName.FurnaceFuelContainer;
            case 2 -> ContainerEnumName.FurnaceResultContainer;
            default -> ContainerEnumName.LevelEntityContainer;
        };
        return new FullContainerName(name, null);
    }

    private static String blockTag(final ContainerType type) {
        return switch (type) {
            case FURNACE -> CustomBlockTags.FURNACE;
            case BLAST_FURNACE -> CustomBlockTags.BLAST_FURNACE;
            case SMOKER -> CustomBlockTags.SMOKER;
            default -> throw new IllegalArgumentException("Not a furnace container: " + type);
        };
    }

}
