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

import com.viaversion.nbt.tag.CompoundTag;
import net.raphimc.viabedrock.protocol.rewriter.ItemRewriter;

public record BedrockTradeOffer(
    int networkId,
    BedrockItem costA,
    BedrockItem costB,
    BedrockItem result,
    int uses,
    int maxUses,
    int traderExperience,
    float priceMultiplier,
    int tier
) {

    public static BedrockTradeOffer fromTag(final CompoundTag tag, final ItemRewriter itemRewriter,
                                            final int fallbackNetworkId) {
        return new BedrockTradeOffer(
            tag.getInt("netId", fallbackNetworkId),
            itemRewriter.bedrockItemFromTag(tag.getCompoundTag("buyA")),
            itemRewriter.bedrockItemFromTag(tag.getCompoundTag("buyB")),
            itemRewriter.bedrockItemFromTag(tag.getCompoundTag("sell")),
            Math.max(0, tag.getInt("uses", 0)),
            Math.max(0, tag.getInt("maxUses", 0)),
            Math.max(0, tag.getInt("traderExp", 0)),
            tag.getFloat("priceMultiplierA", 0F),
            Math.max(0, tag.getInt("tier", 0))
        );
    }

    public boolean outOfStock() {
        return this.maxUses <= 0 || this.uses >= this.maxUses;
    }
}
