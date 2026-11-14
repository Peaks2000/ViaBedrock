/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.raphimc.viabedrock.protocol.model;

import io.netty.util.AttributeKey;

public record PlayerFluidInput(boolean swimming, boolean inFluid, float velocityX, float velocityY, float velocityZ) {

    // A JDK array keeps the handoff usable across the isolated ViaBedrock classloader.
    public static final AttributeKey<float[]> KEY = AttributeKey.valueOf("viafabricplus:player_fluid_input");

    public static PlayerFluidInput decode(final float[] input) {
        return new PlayerFluidInput(input[0] != 0, input[1] != 0, input[2], input[3], input[4]);
    }
}
