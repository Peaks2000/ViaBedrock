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
import net.raphimc.viabedrock.protocol.model.FullContainerName;

public final class AnvilContainer extends Container {

    public AnvilContainer(final UserConnection user, final byte containerId, final TextComponent title,
                          final BlockPosition position) {
        super(user, containerId, ContainerType.ANVIL, title, position, 3, "anvil");
    }

    @Override
    public FullContainerName getFullContainerName(final int slot) {
        final ContainerEnumName name = switch (slot) {
            case 0 -> ContainerEnumName.AnvilInputContainer;
            case 1 -> ContainerEnumName.AnvilMaterialContainer;
            case 2 -> ContainerEnumName.AnvilResultPreviewContainer;
            default -> ContainerEnumName.LevelEntityContainer;
        };
        return new FullContainerName(name, null);
    }

    @Override
    public int stackRequestSlot(final int slot) {
        return switch (slot) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 50;
            default -> slot;
        };
    }

    @Override
    public int stackResponseSlot(final int slot) {
        return switch (slot) {
            case 1 -> 0;
            case 2 -> 1;
            case 50 -> 2;
            default -> slot;
        };
    }
}
